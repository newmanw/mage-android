package mil.nga.giat.mage.map.cache;

import android.app.Application;
import android.os.AsyncTask;
import android.support.annotation.MainThread;

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

/**
 * Created by wnewman on 2/11/16.
 */
@MainThread
public class MapDataManager {

    /**
     * Implement this interface and {@link #addUpdateListener(CacheOverlaysUpdateListener) register}
     * an instance to receive {@link #onCacheOverlaysUpdated(CacheOverlayUpdate) notifications} when the set of caches changes.
     */
    public interface CacheOverlaysUpdateListener {
        void onCacheOverlaysUpdated(CacheOverlayUpdate update);
    }

    /**
     * The create update permission is an opaque interface that enforces only holders of
     * the the permission instance have the ability to create a {@link CacheOverlayUpdate}
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
     * <code>MapDataManager</code> instance to {@link CacheOverlaysUpdateListener#onCacheOverlaysUpdated(CacheOverlayUpdate) deliver}
     * to listeners.
     */
    public interface CreateUpdatePermission {};

    public final class CacheOverlayUpdate {
        public final Set<MapDataResource> added;
        public final Set<MapDataResource> updated;
        public final Set<MapDataResource> removed;
        public final MapDataManager source = MapDataManager.this;

        public CacheOverlayUpdate(CreateUpdatePermission updatePermission, Set<MapDataResource> added, Set<MapDataResource> updated, Set<MapDataResource> removed) {
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
        private Executor executor = AsyncTask.SERIAL_EXECUTOR;

        public Config context(Application x) {
            context = x;
            return this;
        }

        public Config updatePermission(CreateUpdatePermission x) {
            updatePermission = x;
            return this;
        }

        /**
         * Paths to directories to search for cache files
         * @param x
         * @return
         */
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
    private final Collection<CacheOverlaysUpdateListener> cacheOverlayListeners = new ArrayList<>();
    private Set<MapDataResource> caches = Collections.emptySet();
    private RefreshAvailableCachesTask refreshTask;
    private FindNewCacheFilesInProvidedLocationsTask findNewCacheFilesTask;
    private ImportCacheFileTask importCacheFilesForRefreshTask;

    public MapDataManager(Config config) {
        if (config.updatePermission == null) {
            throw new IllegalArgumentException("update permission object must be non-null");
        }
        updatePermission = config.updatePermission;
        executor = config.executor;
        repositories = config.repositories;
        providers = config.providers;
    }

    public void addUpdateListener(CacheOverlaysUpdateListener listener) {
        cacheOverlayListeners.add(listener);
    }

    public void removeUpdateListener(CacheOverlaysUpdateListener listener) {
        cacheOverlayListeners.remove(listener);
    }

    public void tryImportResource(URI cacheFile) {
        MapDataResource resource = new MapDataResource(cacheFile);
        new ImportCacheFileTask().executeOnExecutor(executor, resource);
    }

    public void removeCacheOverlay(String name) {
        // TODO: rename to delete, implement MapDataProvider.deleteCache()
    }

    public Set<MapDataResource> getCaches() {
        return caches;
    }

    /**
     * Discover new caches available in standard {@link MapDataRepository locations}, then remove defunct caches.
     * Asynchronous notifications to {@link #addUpdateListener(CacheOverlaysUpdateListener) listeners}
     * will result, one notification per refresh, per listener.  Only one refresh can be active at any moment.
     */
    public void refreshAvailableCaches() {
        if (refreshTask != null) {
            return;
        }
        findNewCacheFilesTask = new FindNewCacheFilesInProvidedLocationsTask();
        importCacheFilesForRefreshTask = new ImportCacheFileTask();
        refreshTask = new RefreshAvailableCachesTask();
        findNewCacheFilesTask.executeOnExecutor(executor);
    }

    public OverlayOnMapManager createMapManager(GoogleMap map) {
        return new OverlayOnMapManager(this, Arrays.asList(providers), map);
    }

    private void findNewCacheFilesFinished(FindNewCacheFilesInProvidedLocationsTask task) {
        if (task != findNewCacheFilesTask) {
            throw new IllegalStateException(FindNewCacheFilesInProvidedLocationsTask.class.getSimpleName() + " task finished but did not match stored task");
        }
        try {
            importCacheFilesForRefreshTask.executeOnExecutor(executor, task.get());
        }
        catch (Exception e) {
            throw new IllegalStateException("interrupted while retrieving new cache files to import");
        }
    }

    private void cacheFileImportFinished(ImportCacheFileTask task) {
        if (task == importCacheFilesForRefreshTask) {
            if (refreshTask == null) {
                throw new IllegalStateException("import task for refresh finished but refresh task is null");
            }
            refreshTask.executeOnExecutor(executor, caches.toArray(new MapDataResource[caches.size()]));
        }
        else {
            updateCaches(task, null);
        }
    }

    private void refreshFinished(RefreshAvailableCachesTask task) {
        if (task != refreshTask) {
            throw new IllegalStateException(RefreshAvailableCachesTask.class.getSimpleName() + " task completed but did not match stored task");
        }

        ImportCacheFileTask localImportTask = importCacheFilesForRefreshTask;
        RefreshAvailableCachesTask localRefreshTask = refreshTask;
        importCacheFilesForRefreshTask = null;
        findNewCacheFilesTask = null;
        refreshTask = null;

        updateCaches(localImportTask, localRefreshTask);
    }

    private void updateCaches(ImportCacheFileTask importTask, RefreshAvailableCachesTask refreshTask) {
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
        added.removeAll(caches);
        Set<MapDataResource> removed = new HashSet<>();
        Set<MapDataResource> updated = new HashSet<>();
        for (MapDataResource existing : caches) {
            MapDataResource incoming = incomingIndex.get(existing);
            if (incoming == null) {
                removed.add(existing);
            }
            else if (incoming != existing) {
                updated.add(incoming);
            }
        }

        caches = Collections.unmodifiableSet(new HashSet<>(incomingIndex.keySet()));

        CacheOverlayUpdate update = new CacheOverlayUpdate(
            updatePermission,
            Collections.unmodifiableSet(added),
            Collections.unmodifiableSet(updated),
            Collections.unmodifiableSet(removed));
        for (CacheOverlaysUpdateListener listener : cacheOverlayListeners) {
            listener.onCacheOverlaysUpdated(update);
        }
    }

    private static class CacheImportResult {
        private final Set<MapDataResource> imported;
        // TODO: propagate failed imports to user somehow
        private final List<CacheImportException> failed;

        private CacheImportResult(Set<MapDataResource> imported, List<CacheImportException> failed) {
            this.imported = imported;
            this.failed = failed;
        }
    }

    private class ImportCacheFileTask extends AsyncTask<MapDataResource, Void, CacheImportResult> {

        private MapDataResource importFromFirstCapableProvider(MapDataResource resource) throws CacheImportException {
            URI uri = resource.getUri();
            for (MapDataProvider provider : providers) {
                if (uri.getScheme().equalsIgnoreCase("file")) {
                    File cacheFile = new File(uri.getPath());
                    if (!cacheFile.canRead()) {
                        throw new CacheImportException(uri, "cache file is not readable or does not exist: " + cacheFile.getName());
                    }
                }
                if (provider.canHandleResource(uri)) {
                    return provider.importResource(uri);
                }
            }
            throw new CacheImportException(uri, "no cache provider could handle file " + resource);
        }

        @Override
        protected CacheImportResult doInBackground(MapDataResource... resources) {
            Set<MapDataResource> caches = new HashSet<>(resources.length);
            List<CacheImportException> fails = new ArrayList<>(resources.length);
            for (MapDataResource resource : resources) {
                MapDataResource imported;
                try {
                    imported = importFromFirstCapableProvider(resource);
                    caches.add(imported);
                }
                catch (CacheImportException e) {
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

    private final class RefreshAvailableCachesTask extends AsyncTask<MapDataResource, Void, Set<MapDataResource>> {

        @Override
        protected final Set<MapDataResource> doInBackground(MapDataResource... existingCaches) {
            Map<Class<? extends MapDataProvider>, Set<MapDataResource>> cachesByProvider = new HashMap<>(providers.length);
            for (MapDataResource cache : existingCaches) {
                Set<MapDataResource> providerCaches = cachesByProvider.get(cache.getType());
                if (providerCaches == null) {
                    providerCaches = new HashSet<>();
                    cachesByProvider.put(cache.getType(), providerCaches);
                }
                providerCaches.add(cache);
            }
            Set<MapDataResource> caches = new HashSet<>();
            for (MapDataProvider provider : providers) {
                Set<MapDataResource> providerCaches = cachesByProvider.get(provider.getClass());
                if (providerCaches == null) {
                    providerCaches = Collections.emptySet();
                }
                caches.addAll(provider.refreshResources(providerCaches));
            }
            return caches;
        }

        @Override
        protected void onPostExecute(Set<MapDataResource> caches) {
            refreshFinished(this);
        }
    }

    private final class FindNewCacheFilesInProvidedLocationsTask extends AsyncTask<Void, Void, MapDataResource[]> {

        @Override
        protected MapDataResource[] doInBackground(Void... voids) {
            Set<MapDataResource> allResources = new HashSet<>();
            for (MapDataRepository repo : repositories) {
                Set<MapDataResource> resources = repo.retrieveMapDataResources();
                allResources.addAll(resources);
            }
            return allResources.toArray(new MapDataResource[allResources.size()]);
        }

        @Override
        protected void onPostExecute(MapDataResource[] result) {
            findNewCacheFilesFinished(this);
        }
    }
}
