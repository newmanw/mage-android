package mil.nga.giat.mage.map.cache;


import android.annotation.SuppressLint;
import android.app.Application;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.io.ByteStreams;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import mil.nga.giat.mage.sdk.connectivity.ConnectivityUtility;
import mil.nga.giat.mage.sdk.datastore.layer.Layer;
import mil.nga.giat.mage.sdk.datastore.layer.LayerHelper;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeature;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeatureHelper;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeatureProperty;
import mil.nga.giat.mage.sdk.datastore.user.Event;
import mil.nga.giat.mage.sdk.datastore.user.EventHelper;
import mil.nga.giat.mage.sdk.event.IEventEventListener;
import mil.nga.giat.mage.sdk.exceptions.LayerException;
import mil.nga.giat.mage.sdk.exceptions.StaticFeatureException;
import mil.nga.giat.mage.sdk.http.resource.LayerResource;
import mil.nga.giat.mage.sdk.login.LoginTaskFactory;


@MainThread
public class StaticFeatureLayerRepository extends MapDataRepository implements MapDataProvider, IEventEventListener {

    public static final String PROP_ICON_URL = "styleiconstyleiconhref";

    /**
     * TODO: This interface exists solely for the ability to inject the statement
     * {@code !ConnectivityUtility.isOnline(context) || LoginTaskFactory.getInstance(context).isLocalLogin()}
     * for testing {@link StaticFeatureLayerRepository}.  Hopefully something better and global to the rest
     * of the app that allows injection comes along eventually.
     */
    public interface NetworkCondition {
        boolean isConnected();
    }

    private static final String LOG_NAME = StaticFeatureLayerRepository.class.getName();
    private static final URI RESOURCE_URI;
    static {
        try {
            RESOURCE_URI = new URI("mage", null, "/current_event/layers", null, null);
        }
        catch (URISyntaxException e) {
            throw new Error("unexpected error initializing resource uri", e);
        }
    }
    private static final String RESOURCE_NAME = "Event Layers";

    @SuppressLint("StaticFieldLeak")
    private static StaticFeatureLayerRepository instance = null;

    public static synchronized void initialize(Application context) {
        EventHelper eventHelper = EventHelper.getInstance(context);
        LayerHelper layerHelper = LayerHelper.getInstance(context);
        StaticFeatureHelper featureHelper = StaticFeatureHelper.getInstance(context);
        LayerResource layerResource = new LayerResource(context);
        File iconsDir = new File(context.getFilesDir(), "icons/staticfeatures");
        NetworkCondition network = () -> !ConnectivityUtility.isOnline(context) || LoginTaskFactory.getInstance(context).isLocalLogin();
        instance = new StaticFeatureLayerRepository(eventHelper, layerHelper, featureHelper, layerResource, iconsDir, network);
    }

    public static StaticFeatureLayerRepository getInstance() {
        return instance;
    }

    private final EventHelper eventHelper;
    private final LayerHelper layerHelper;
    private final StaticFeatureHelper featureHelper;
    private final LayerResource layerService;
    private final NetworkCondition network;
    private final File iconsDir;
    private Event currentEvent;
    private RefreshCurrentEventLayers refreshInProgress;
    private RefreshCurrentEventLayers pendingRefresh;

    StaticFeatureLayerRepository(EventHelper eventHelper, LayerHelper layerHelper, StaticFeatureHelper featureHelper, LayerResource layerService, File iconsDir, NetworkCondition network) {
        this.eventHelper = eventHelper;
        this.layerHelper = layerHelper;
        this.featureHelper = featureHelper;
        this.layerService = layerService;
        this.iconsDir = iconsDir;
        this.network = network;
    }

    @NotNull
    @Override
    public Status getStatus() {
        if (refreshInProgress != null) {
            return Status.Loading;
        }
        return Status.Success;
    }

    @Override
    public int getStatusCode() {
        return getStatus().ordinal();
    }

    @Nullable
    @Override
    public String getStatusMessage() {
        return getStatus().toString();
    }

    @Override
    public boolean ownsResource(URI resourceUri) {
        return RESOURCE_URI.equals(resourceUri);
    }

    @Override
    public void refreshAvailableMapData(Map<URI, MapDataResource> resolvedResources, Executor executor) {
        Event currentEventTest = eventHelper.getCurrentEvent();
        if (currentEvent != null && currentEvent.equals(currentEventTest) && getStatus() == Status.Loading) {
            return;
        }
        currentEvent = currentEventTest;
        refreshInProgress = new RefreshCurrentEventLayers(executor, currentEvent);
        refreshInProgress.layerFetch.executeOnExecutor(executor);
    }

    @Override
    public boolean canHandleResource(MapDataResource resource) {
        return RESOURCE_URI.equals(resource.getUri());
    }

    @Override
    public MapDataResource resolveResource(MapDataResource resource) throws MapDataResolveException {
        return null;
    }

    @Override
    public MapLayerManager.MapLayer createMapLayerFromDescriptor(MapLayerDescriptor layerDescriptor, MapLayerManager map) {
        return null;
    }

    @Override
    public void onEventChanged() {
        refreshAvailableMapData(Collections.emptyMap(), AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void disable() {
        // TODO: cancel syncing in progress and disallow future syncing
    }

    private void onLayerFetchFinished() {
        FetchEventLayers fetch = refreshInProgress.layerFetch;
        Collection<Layer> remoteLayers = fetch.result.layers;
        if (remoteLayers == null || fetch.result.failure != null) {
            refreshInProgress.cancel();
        }
        else {
            for (Layer layer : remoteLayers) {
                SyncLayerFeatures featureFetch = new SyncLayerFeatures(layer);
                refreshInProgress.featureSyncForLayer.put(layer.getRemoteId(), featureFetch);
                featureFetch.executeOnExecutor(refreshInProgress.executor);
            }
        }
        if (refreshInProgress.isFinished()) {
            finishRefresh();
        }
    }

    private void onFeaturesSynced(SyncLayerFeatures sync) {
        SyncLayerFeatures removed = refreshInProgress.featureSyncForLayer.remove(sync.layer.getRemoteId());
        if (removed != sync) {
            throw new IllegalStateException("feature sync finished for layer " +
                sync.layer.getRemoteId() + " but did not match the expected feature sync in progress");
        }
        Map<String, Set<Long>> featuresForIconUrl = Collections.emptyMap();
        if (sync.result != null) {
            featuresForIconUrl = sync.result.featuresForIconUrl;
        }
        for (Map.Entry<String, Set<Long>> entry : featuresForIconUrl.entrySet()) {
            Set<Long> features = refreshInProgress.featuresForIconUrl.get(entry.getKey());
            if (features == null) {
                features = new HashSet<>();
                refreshInProgress.featuresForIconUrl.put(entry.getKey(), features);
            }
            features.addAll(entry.getValue());
        }
        // TODO: this is basically a barrier that prevents starting to sync icons until all features have been saved
        // and could be improved by starting the icon syncs immediately, but that requires a bit more work to
        // ensure that icon URLs are only fetched once in the course of a refresh and updating all the features
        // when an icon URL is resolved, or maybe this is just good enough
        if (!refreshInProgress.featureSyncForLayer.isEmpty()) {
            return;
        }
        Iterator<String> iconUrlCursor = refreshInProgress.featuresForIconUrl.keySet().iterator();
        while (iconUrlCursor.hasNext()) {
            String iconUrl = iconUrlCursor.next();
            Set<Long> featureIds = refreshInProgress.featuresForIconUrl.get(iconUrl);
            SyncIconToFeatures iconSync = new SyncIconToFeatures(iconUrl, featureIds);
            refreshInProgress.iconSyncForIconUrl.put(iconUrl, iconSync);
            iconSync.executeOnExecutor(refreshInProgress.executor);
            iconUrlCursor.remove();
        }
        if (refreshInProgress.isFinished()) {
            finishRefresh();
        }
    }

    private void onFeatureIconsSynced(SyncIconToFeatures sync) {
        SyncIconToFeatures removed = refreshInProgress.iconSyncForIconUrl.remove(sync.iconUrlStr);
        if (sync != removed) {
            throw new IllegalStateException("icon sync finished for url " +
                sync.iconUrlStr + " but did not match the expected icon sync in progress");
        }
        finishRefresh();
    }

    private void onMapDataCreated(SyncMapDataFromDatabase sync) {
        refreshInProgress = null;
        setValue(Collections.singleton(sync.mapData));
    }

    private void finishRefresh() {
        if (refreshInProgress.isCancelled() && getValue() != null) {
            if (refreshInProgress.isFinished()) {
                refreshInProgress = null;
            }
            return;
        }
        refreshInProgress.syncMapData = new SyncMapDataFromDatabase(refreshInProgress.event);
        refreshInProgress.syncMapData.executeOnExecutor(refreshInProgress.executor);
    }

    @MainThread
    private class RefreshCurrentEventLayers {

        private final Executor executor;
        private final Event event;
        private final Map<String, SyncLayerFeatures> featureSyncForLayer = new HashMap<>();
        private final Map<String, Set<Long>> featuresForIconUrl = new HashMap<>();
        private final Map<String, SyncIconToFeatures> iconSyncForIconUrl = new HashMap<>();
        private final FetchEventLayers layerFetch;
        private SyncMapDataFromDatabase syncMapData;
        private boolean cancelled = false;

        private RefreshCurrentEventLayers(Executor executor, Event event) {
            this.executor = executor;
            this.event = event;
            this.layerFetch = new FetchEventLayers(event);
        }

        private void cancel() {
            layerFetch.cancel(false);
            Iterator<SyncLayerFeatures> featureFetches = featureSyncForLayer.values().iterator();
            while (featureFetches.hasNext()) {
                featureFetches.next().cancel(false);
                featureFetches.remove();
            }
            cancelled = true;
        }

        private boolean isStarted() {
            return layerFetch.getStatus() != AsyncTask.Status.PENDING;
        }

        private boolean isFinished() {
            return layerFetch.result != null && featureSyncForLayer.isEmpty() && iconSyncForIconUrl.isEmpty();
        }

        private boolean isCancelled() {
            return cancelled;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class FetchEventLayers extends AsyncTask<Void, Void, FetchEventLayersResult> {

        private final Event event;
        private FetchEventLayersResult result;

        private FetchEventLayers(Event event) {
            this.event = event;
        }

        @Override
        protected FetchEventLayersResult doInBackground(Void... nothing) {
            if (!network.isConnected()) {
                return new FetchEventLayersResult(null, null);
            }
            try {
                Collection<Layer> remoteLayers = layerService.getLayers(event);
                if (remoteLayers.isEmpty()) {
                    layerHelper.deleteByEvent(event);
                    return new FetchEventLayersResult(Collections.emptySet(), null);
                }
                Collection<Layer> localLayers = layerHelper.readByEvent(event);
                if (localLayers.isEmpty()) {
                    return new FetchEventLayersResult(remoteLayers, null);
                }
                Set<Layer> layersToDelete = new HashSet<>(localLayers);
                layersToDelete.removeAll(remoteLayers);
                for (Layer localLayer : layersToDelete) {
                    layerHelper.delete(localLayer.getId());
                }
                return new FetchEventLayersResult(remoteLayers, null);
            }
            catch (IOException e) {
                Log.e(LOG_NAME,"error fetching static layers", e);
                return new FetchEventLayersResult(null, e);
            }
            catch (LayerException e) {
                Log.e(LOG_NAME, "error deleting layers for event " + event.getName() + " (" + event.getRemoteId() + ") after empty fetch result");
                return new FetchEventLayersResult(null, e);
            }
        }

        @Override
        protected void onPostExecute(FetchEventLayersResult result) {
            this.result = result;
            onLayerFetchFinished();
        }

        @Override
        protected void onCancelled(FetchEventLayersResult result) {
            onPostExecute(result);
        }
    }

    private static class FetchEventLayersResult {

        private final Collection<Layer> layers;
        private final Exception failure;

        private FetchEventLayersResult(Collection<Layer> layers, Exception failure) {
            this.layers = layers;
            this.failure = failure;
        }
    }

    /**
     * Fetch the features for the given layer from the server and save them to the database,
     * deleting the existing layer and features if applicable.  Also, compute the icon URLs
     * that need to be resolved and saved to the feature records.
     */
    @SuppressLint("StaticFieldLeak")
    private class SyncLayerFeatures extends AsyncTask<Void, Void, SyncLayerFeaturesResult> {

        private final Layer layer;
        private SyncLayerFeaturesResult result;

        private SyncLayerFeatures(Layer layer) {
            this.layer = layer;
        }

        @Override
        protected SyncLayerFeaturesResult doInBackground(Void... nothing) {
            Collection<StaticFeature> staticFeatures;
            try {
                staticFeatures = layerService.getFeatures(layer);
            }
            catch (IOException e) {
                Log.e(LOG_NAME, "error fetching features for layer " + layer.getRemoteId(), e);
                return null;
            }
            try {
                Layer existing = layerHelper.read(layer.getRemoteId());
                if (existing != null) {
                    layerHelper.delete(existing.getId());
                }
                Layer layerWithFeatures = layerHelper.create(layer);
                Map<String, Set<Long>> featuresForIconUrl = new HashMap<>();
                staticFeatures = featureHelper.createAll(staticFeatures, layerWithFeatures);
                for (StaticFeature feature : staticFeatures) {
                    StaticFeatureProperty iconUrlProp = feature.getPropertiesMap().get(PROP_ICON_URL);
                    if (iconUrlProp != null && iconUrlProp.getValue() != null) {
                        String iconUrl = iconUrlProp.getValue();
                        Set<Long> featureIds = featuresForIconUrl.get(iconUrl);
                        if (featureIds == null) {
                            featureIds = new HashSet<>();
                            featuresForIconUrl.put(iconUrl, featureIds);
                        }
                        featureIds.add(feature.getId());
                    }
                }
                //noinspection unchecked
                return new SyncLayerFeaturesResult(layerWithFeatures, featuresForIconUrl);
            }
            catch (LayerException e) {
                Log.e(LOG_NAME, "failed to save fetched layer " + layer.getName() + " (" + layer.getRemoteId() + ")", e);
            }
            catch (StaticFeatureException e) {
                Log.e(LOG_NAME, "failed to save static features for layer " + layer.getName() + " (" + layer.getRemoteId() + ")", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(SyncLayerFeaturesResult result) {
            this.result = result;
            onFeaturesSynced(this);
        }

        @Override
        protected void onCancelled(SyncLayerFeaturesResult result) {
            onPostExecute(result);
        }
    }

    private static class SyncLayerFeaturesResult {
        private final Layer syncedLayer;
        private final Map<String, Set<Long>> featuresForIconUrl;

        private SyncLayerFeaturesResult(Layer syncedLayer, Map<String, Set<Long>> featuresForIconUrl) {
            this.syncedLayer = syncedLayer;
            this.featuresForIconUrl = featuresForIconUrl;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class SyncIconToFeatures extends AsyncTask<Void, Void, Void> {

        private final String iconUrlStr;
        private final Set<Long> featureIds;
        private volatile Exception iconResolveFailure;
        private volatile Map<Long, Exception> featureUpdateFailures;

        private SyncIconToFeatures(String iconUrlStr, Set<Long> featureIds) {
            this.iconUrlStr = iconUrlStr;
            this.featureIds = featureIds;
        }

        @Override
        protected Void doInBackground(Void... nothing) {
            File iconFile;
            try {
                iconFile = resolveIcon();
            }
            catch (Exception e) {
                Log.e(LOG_NAME, "error resolving icon url " + iconUrlStr, e);
                iconResolveFailure = e;
                return null;
            }
            String iconPath = iconFile.getAbsolutePath();
            @SuppressLint("UseSparseArrays")
            Map<Long, Exception> updateFailures = new HashMap<>();
            for (Long featureId : featureIds) {
                try {
                    StaticFeature feature = featureHelper.read(featureId);
                    feature.setLocalPath(iconPath);
                    featureHelper.update(feature);
                }
                catch (Exception e) {
                    Log.e(LOG_NAME, "error updating local icon path of static feature " + featureId, e);
                    updateFailures.put(featureId, e);
                }
            }
            featureUpdateFailures = updateFailures;
            return null;
        }

        @Override
        protected void onPostExecute(Void nothing) {
            onFeatureIconsSynced(this);
        }

        @Override
        protected void onCancelled(Void nothing) {
            onFeatureIconsSynced(this);
        }

        private File resolveIcon() throws Exception {
            URL iconUrl;
            try {
                iconUrl = new URL(iconUrlStr);
            }
            catch (MalformedURLException e) {
                throw e;
            }
            String fileName = iconUrl.getPath();
            if (fileName == null) {
                throw new IllegalArgumentException("icon url has no path component: " + iconUrlStr);
            }
            fileName = fileName.replaceAll("^/+", "");
            File iconFile = new File(iconsDir, fileName);
            if (iconFile.exists()) {
                return iconFile;
            }
            InputStream inputStream = layerService.getFeatureIcon(iconUrlStr);
            if (!iconFile.getParentFile().mkdirs()) {
                throw new IOException("error creating parent dir for icon: " + iconFile);
            }
            FileOutputStream iconOut = null;
            try {
                iconOut = new FileOutputStream(iconFile);
                ByteStreams.copy(inputStream, iconOut);
            }
            catch (IOException e) {
                if (iconFile.exists() && !iconFile.delete()) {
                    Log.e(LOG_NAME, "failed to delete icon file after bad file write: " + iconFile);
                }
                throw new IOException("error writing icon file " + iconFile, e);
            }
            finally {
                try {
                    if (iconOut != null) {
                        iconOut.close();
                    }
                }
                catch (IOException e) {
                    // sigh
                    Log.e(LOG_NAME,"error finally closing icon file stream: " + iconFile, e);
                }
            }
            return iconFile;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class SyncMapDataFromDatabase extends AsyncTask<Void, LayerException, MapDataResource> {

        private final Event event;
        private MapDataResource mapData;
        private LayerException failure;

        private SyncMapDataFromDatabase(Event event) {
            this.event = event;
        }

        @Override
        protected MapDataResource doInBackground(Void[] nothing) {
            try {
                Collection<Layer> layers = layerHelper.readByEvent(event);
                Set<LayerDescriptor> descriptors = new HashSet<>(layers.size());
                for (Layer layer : layers) {
                    descriptors.add(new LayerDescriptor(layer));
                }
                MapDataResource.Resolved resolvedLayers = new MapDataResource.Resolved(RESOURCE_NAME, StaticFeatureLayerRepository.class, descriptors);
                return new MapDataResource(RESOURCE_URI, StaticFeatureLayerRepository.this, 0, resolvedLayers);
            }
            catch (LayerException e) {
                onProgressUpdate(e);
            }
            return new MapDataResource(RESOURCE_URI, StaticFeatureLayerRepository.this, 0,
                new MapDataResource.Resolved(RESOURCE_NAME, StaticFeatureLayerRepository.class));
        }

        @Override
        protected void onProgressUpdate(LayerException... values) {
            failure = values[0];
        }

        @Override
        protected void onPostExecute(MapDataResource resource) {
            mapData = resource;
            onMapDataCreated(this);
        }
    }

    private static class LayerDescriptor extends MapLayerDescriptor {

        private final Layer subject;

        private LayerDescriptor(Layer subject) {
            super(subject.getRemoteId(), RESOURCE_URI, StaticFeatureLayerRepository.class);
            this.subject = subject;
        }

        @NotNull
        @Override
        public String getLayerTitle() {
            return subject.getName();
        }
    }
}