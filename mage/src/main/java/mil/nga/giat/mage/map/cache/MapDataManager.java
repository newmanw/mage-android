package mil.nga.giat.mage.map.cache;

import android.app.Application;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LifecycleRegistry;
import android.arch.lifecycle.Observer;
import android.os.AsyncTask;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.maps.GoogleMap;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;


@MainThread
public class MapDataManager implements LifecycleOwner {

    /**
     * Implement this interface and {@link #addUpdateListener(MapDataListener) register}
     * an instance to receive {@link #onMapDataUpdated(MapDataUpdate) notifications} when the set of mapData changes.
     */
    public interface MapDataListener {
        void onMapDataUpdated(MapDataUpdate update);
    }

    /**
     * The create update permission is an opaque interface that enforces only holders of
     * the the permission instance have the ability to create a {@link MapDataUpdate}
     * associated with a given instance of {@link MapDataManager}.  This can simply be an
     * anonymous implementation created at the call site of the {@link Config#updatePermission(CreateUpdatePermission) configuration}.
     * For example:
     * <p>
     * <pre>
     * new MapDataManager(new MapDataManager.Config()<br>
     *     .updatePermission(new MapDataManager.CreateUpdatePermission(){})
     *     // other config items
     *     );
     * </pre>
     * </p>
     * This prevents the programmer error of creating update objects outside of the
     * <code>MapDataManager</code> instance to {@link MapDataListener#onMapDataUpdated(MapDataUpdate) deliver}
     * to listeners.
     */
    public interface CreateUpdatePermission {};

    public final class MapDataUpdate {
        public final Set<MapDataResource> added;
        public final Set<MapDataResource> updated;
        public final Set<MapDataResource> removed;
        public final MapDataManager source = MapDataManager.this;

        public MapDataUpdate(CreateUpdatePermission updatePermission, Set<MapDataResource> added, Set<MapDataResource> updated, Set<MapDataResource> removed) {
            if (updatePermission != source.updatePermission) {
                throw new Error("erroneous attempt to create update from cache manager instance " + MapDataManager.this);
            }
            this.added = added;
            this.updated = updated;
            this.removed = removed;
        }
    }

    private static final String LOG_NAME = MapDataManager.class.getName();

    private static MapDataManager instance = null;

    public static class Config {
        private Application context;
        private CreateUpdatePermission updatePermission;
        private MapDataRepository[] repositories;
        private MapDataProvider[] providers;
        private Executor executor = AsyncTask.THREAD_POOL_EXECUTOR;

        public Config context(Application x) {
            context = x;
            return this;
        }

        public Config updatePermission(CreateUpdatePermission x) {
            updatePermission = x;
            return this;
        }

        public Config repositories(MapDataRepository... x) {
            repositories = x;
            return this;
        }

        public Config providers(MapDataProvider... x) {
            providers = x;
            return this;
        }

        public Config executor(Executor x) {
            executor = x;
            return this;
        }
    }

    public static synchronized void initialize(Config config) {
        if (instance == null) {
            instance = new MapDataManager(config);
            return;
        }
        throw new Error("attempt to initialize " + MapDataManager.class + " singleton more than once");
    }

    public static MapDataManager getInstance() {
        return instance;
    }

    private final CreateUpdatePermission updatePermission;
    private final Executor executor;
    private final MapDataRepository[] repositories;
    private final MapDataProvider[] providers;
    private final Collection<MapDataListener> cacheOverlayListeners = new ArrayList<>();
    private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
    private Set<MapDataResource> mapData = Collections.emptySet();
    private ImportResourcesTask importResourcesForRefreshTask;

    public MapDataManager(Config config) {
        if (config.updatePermission == null) {
            throw new IllegalArgumentException("update permission object must be non-null");
        }
        updatePermission = config.updatePermission;
        executor = config.executor;
        repositories = config.repositories;
        providers = config.providers;
        lifecycle.markState(Lifecycle.State.CREATED);
        for (MapDataRepository repo : repositories) {
            repo.observe(this, new RepositoryObserver(repo));
        }
    }

    public void addUpdateListener(MapDataListener listener) {
        cacheOverlayListeners.add(listener);
    }

    public void removeUpdateListener(MapDataListener listener) {
        cacheOverlayListeners.remove(listener);
    }

    public void tryImportResource(URI resourceUri) {
        new ImportResourcesTask().executeOnExecutor(executor, resource);
    }

    public void removeCacheOverlay(String name) {
        // TODO: rename to delete, implement MapDataProvider.deleteCache()
    }

    public Set<MapDataResource> getMapData() {
        return mapData;
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    /**
     * Discover new mapData available in standard {@link MapDataRepository locations}, then remove defunct mapData.
     * Asynchronous notifications to {@link #addUpdateListener(MapDataListener) listeners}
     * will result, one notification per refresh, per listener.  Only one refresh can be active at any moment.
     */
    public void refreshAvailableCaches() {
        for (MapDataRepository repo : repositories) {
            repo.refreshAvailableMapData(executor);
        }
    }

    public MapLayerManager createMapManager(GoogleMap map) {
        return new MapLayerManager(this, Arrays.asList(providers), map);
    }

    private void onMapDataChanged(Set<MapDataResource> data, MapDataRepository source) {

    }

    private void cacheFileImportFinished(ImportResourcesTask task) {
        if (task == importResourcesForRefreshTask) {
            if (refreshTask == null) {
                throw new IllegalStateException("import task for refresh finished but refresh task is null");
            }
            refreshTask.executeOnExecutor(executor, mapData.toArray(new MapDataResource[mapData.size()]));
        }
        else {
            updateCaches(task, null);
        }
    }

    private void refreshFinished(RefreshAvailableResourcesTask task) {
        if (task != refreshTask) {
            throw new IllegalStateException(RefreshAvailableResourcesTask.class.getSimpleName() + " task completed but did not match stored task");
        }

        ImportResourcesTask localImportTask = importResourcesForRefreshTask;
        RefreshAvailableResourcesTask localRefreshTask = refreshTask;
        importResourcesForRefreshTask = null;
        refreshTask = null;

        updateCaches(localImportTask, localRefreshTask);
    }

    private void updateCaches(ImportResourcesTask importTask, RefreshAvailableResourcesTask refreshTask) {
        Set<MapDataResource> allIncoming;
        try {
            CacheImportResult importResult = importTask.get();
            allIncoming = importResult.imported;
            if (refreshTask != null) {
                allIncoming.addAll(refreshTask.get());
            }
        }
        catch (Exception e) {
            throw new IllegalStateException("unexpected error retrieving cache update results", e);
        }

        Map<MapDataResource, MapDataResource> incomingIndex = new HashMap<>();
        for (MapDataResource cache : allIncoming) {
            incomingIndex.put(cache, cache);
        }
        Set<MapDataResource> added = new HashSet<>(allIncoming);
        added.removeAll(mapData);
        Set<MapDataResource> removed = new HashSet<>();
        Set<MapDataResource> updated = new HashSet<>();
        for (MapDataResource existing : mapData) {
            MapDataResource incoming = incomingIndex.get(existing);
            if (incoming == null) {
                removed.add(existing);
            }
            else if (incoming != existing) {
                updated.add(incoming);
            }
        }

        mapData = Collections.unmodifiableSet(new HashSet<>(incomingIndex.keySet()));

        MapDataUpdate update = new MapDataUpdate(
            updatePermission,
            Collections.unmodifiableSet(added),
            Collections.unmodifiableSet(updated),
            Collections.unmodifiableSet(removed));
        for (MapDataListener listener : cacheOverlayListeners) {
            listener.onMapDataUpdated(update);
        }
    }

    private class RepositoryObserver implements Observer<Set<MapDataResource>> {

        private final MapDataRepository repo;

        private RepositoryObserver(MapDataRepository repo) {
            this.repo = repo;
        }

        @Override
        public void onChanged(@Nullable Set<MapDataResource> data) {
            onMapDataChanged(data, repo);
        }
    }

    private static class CacheImportResult {
        private final Set<MapDataResource> imported;
        // TODO: propagate failed imports to user somehow
        private final List<MapDataImportException> failed;

        private CacheImportResult(Set<MapDataResource> imported, List<MapDataImportException> failed) {
            this.imported = imported;
            this.failed = failed;
        }
    }

    private class ImportResourcesTask extends AsyncTask<MapDataResource, Void, CacheImportResult> {

        private MapDataResource importFromFirstCapableProvider(MapDataResource resource) throws MapDataImportException {
            URI uri = resource.getUri();
            for (MapDataProvider provider : providers) {
                if (uri.getScheme().equalsIgnoreCase("file")) {
                    File cacheFile = new File(uri.getPath());
                    if (!cacheFile.canRead()) {
                        throw new MapDataImportException(uri, "cache file is not readable or does not exist: " + cacheFile.getName());
                    }
                }
                if (provider.canHandleResource(uri)) {
                    return provider.importResource(uri);
                }
            }
            throw new MapDataImportException(uri, "no cache provider could handle file " + resource);
        }

        @Override
        protected CacheImportResult doInBackground(MapDataResource... resources) {
            Set<MapDataResource> caches = new HashSet<>(resources.length);
            List<MapDataImportException> fails = new ArrayList<>(resources.length);
            for (MapDataResource resource : resources) {
                MapDataResource imported;
                try {
                    imported = importFromFirstCapableProvider(resource);
                    caches.add(imported);
                }
                catch (MapDataImportException e) {
                    fails.add(e);
                }
            }
            return new CacheImportResult(caches, fails);
        }

        @Override
        protected void onPostExecute(CacheImportResult result) {
            cacheFileImportFinished(this);
        }
    }
}
