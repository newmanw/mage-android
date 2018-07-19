package mil.nga.giat.mage.map.cache;


import android.annotation.SuppressLint;
import android.app.Application;
import android.os.AsyncTask;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import mil.nga.giat.mage.data.BasicResource;
import mil.nga.giat.mage.data.Resource;
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

import static mil.nga.giat.mage.data.Resource.Status.Error;
import static mil.nga.giat.mage.data.Resource.Status.Success;


@MainThread
public class StaticFeatureLayerRepository extends MapDataRepository implements IEventEventListener {

    public static final String PROP_ICON_URL = "styleiconstyleiconhref";

    /**
     * TODO: This interface exists solely for the ability to inject the statement
     * {@code !ConnectivityUtility.isOnline(context) || LoginTaskFactory.getInstance(context).isLocalLogin()}
     * for testing {@link StaticFeatureLayerRepository}.  Hopefully something better and global to the rest
     * of the app that allows injection comes along eventually.
     */
    @FunctionalInterface
    public interface NetworkCondition {
        boolean isConnected();
    }

    static final URI RESOURCE_URI;
    static {
        try {
            RESOURCE_URI = new URI("mage", null, "/current_event/layers", null, null);
        }
        catch (URISyntaxException e) {
            throw new Error("unexpected error initializing resource uri", e);
        }
    }
    private static final String LOG_NAME = StaticFeatureLayerRepository.class.getName();
    private static final String RESOURCE_NAME = "Event Layers";

    @SuppressLint("StaticFieldLeak")
    private static StaticFeatureLayerRepository instance = null;

    public static synchronized void initialize(Application context) {
        EventHelper eventHelper = EventHelper.getInstance(context);
        LayerHelper layerHelper = LayerHelper.getInstance(context);
        StaticFeatureHelper featureHelper = StaticFeatureHelper.getInstance(context);
        LayerResource layerResource = new LayerResource(context);
        File iconsDir = new File(context.getFilesDir(), "icons/static_features");
        NetworkCondition network = () -> ConnectivityUtility.isOnline(context) || LoginTaskFactory.getInstance(context).isLocalLogin();
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
    private Event lastSyncedEvent;
    private RefreshCurrentEventLayers refreshInProgress;
    private RefreshCurrentEventLayers pendingRefresh;
    private long contentTimestamp = 0;

    StaticFeatureLayerRepository(EventHelper eventHelper, LayerHelper layerHelper, StaticFeatureHelper featureHelper, LayerResource layerService, File iconsDir, NetworkCondition network) {
        this.eventHelper = eventHelper;
        this.layerHelper = layerHelper;
        this.featureHelper = featureHelper;
        this.layerService = layerService;
        this.iconsDir = iconsDir;
        this.network = network;
    }

    @Override
    public boolean ownsResource(@NonNull URI resourceUri) {
        return RESOURCE_URI.equals(resourceUri);
    }

    @Override
    public void refreshAvailableMapData(@NonNull Map<URI, MapDataResource> resolvedResources, @NonNull Executor executor) {
        // TODO: this db query should really be on background thread too, but this should be fixed with Room/LiveData later
        Event currentEvent = eventHelper.getCurrentEvent();
        if (refreshInProgress != null && currentEvent.equals(refreshInProgress.event)) {
            return;
        }
        setValue(BasicResource.loading());
        pendingRefresh = new RefreshCurrentEventLayers(executor, currentEvent);
        if (refreshInProgress != null) {
            refreshInProgress.cancel();
        }
        else {
            beginPendingRefresh();
        }
    }

    @Override
    public void onEventChanged() {
        refreshAvailableMapData(Collections.emptyMap(), AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void disable() {
        // TODO: cancel syncing in progress and disallow future syncing
    }

    private void beginPendingRefresh() {
        if (refreshInProgress != null) {
            throw new IllegalStateException("attempt to begin pending refresh before refresh in progress completed for event " +
                refreshInProgress.event.getRemoteId() + " (" + refreshInProgress.event.getName() + ")");
        }
        refreshInProgress = pendingRefresh;
        if (pendingRefresh == null) {
            return;
        }
        pendingRefresh = null;
        refreshInProgress.layerFetch.executeOnExecutor(refreshInProgress.executor);
    }

    private void onLayerFetchFinished() {
        FetchEventLayers fetch = refreshInProgress.layerFetch;
        Collection<Layer> remoteLayers = fetch.result.layers;
        if (remoteLayers == null && fetch.result.failure != null) {
            refreshInProgress.cancel();
            refreshInProgress.status = Error;
            refreshInProgress.statusMessage.add("Error fetching layers for event "
                + refreshInProgress.event.getName() + ": " + fetch.result.failure.getLocalizedMessage());
            createMapDataFromSyncedData();
        }
        else if (!refreshInProgress.isCancelled() && remoteLayers != null && !remoteLayers.isEmpty()) {
            for (Layer layer : remoteLayers) {
                SyncLayerFeatures featureFetch = new SyncLayerFeatures(layer);
                refreshInProgress.featureSyncForLayer.put(layer.getRemoteId(), featureFetch);
                featureFetch.executeOnExecutor(refreshInProgress.executor);
            }
        }
        else {
            createMapDataFromSyncedData();
        }
    }

    private void onFeaturesSynced(SyncLayerFeatures sync) {
        SyncLayerFeatures removed = refreshInProgress.featureSyncForLayer.remove(sync.layer.getRemoteId());
        if (removed != sync) {
            throw new IllegalStateException("feature sync finished for layer " +
                sync.layer.getRemoteId() + " but did not match the expected feature sync in progress");
        }
        if (sync.result.failure != null) {
            refreshInProgress.status = Error;
            refreshInProgress.statusMessage.add("Error syncing feature data for layer " + sync.layer.getName() +
                " (" + sync.layer.getRemoteId() + "): " + sync.result.failure.getLocalizedMessage());
            if (refreshInProgress.isFinishedSyncing()) {
                createMapDataFromSyncedData();
            }
            return;
        }
        Map<String, Set<Long>> featuresForIconUrl = sync.result.featuresForIconUrl;
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
        // when an icon URL is resolved, because some features that us a particular icon url may not have yet
        // been fetched by the time the icon fetch is complete, or maybe this is just good enough
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
        if (refreshInProgress.isFinishedSyncing()) {
            createMapDataFromSyncedData();
        }
    }

    private void onFeatureIconsSynced(SyncIconToFeatures sync) {
        SyncIconToFeatures removed = refreshInProgress.iconSyncForIconUrl.remove(sync.iconUrlStr);
        if (sync != removed) {
            throw new IllegalStateException("icon sync finished for url " +
                sync.iconUrlStr + " but did not match the expected icon sync in progress");
        }
        if (sync.result.iconResolveFailure != null || sync.result.featureUpdateFailures != null) {
            refreshInProgress.status = Error;
            if (sync.result.iconResolveFailure != null) {
                refreshInProgress.statusMessage.add("Error fetching feature icon at " +
                    sync.iconUrlStr + ": " + sync.result.iconResolveFailure.getLocalizedMessage());
            }
            else {
                refreshInProgress.statusMessage.add("Error saving path of icon " +
                    sync.iconUrlStr + " to features: " + sync.result.featureUpdateFailures.keySet());
            }
        }
        if (refreshInProgress.isFinishedSyncing()) {
            createMapDataFromSyncedData();
        }
    }

    private void createMapDataFromSyncedData() {
        refreshInProgress.syncMapData.executeOnExecutor(refreshInProgress.executor);
    }

    private void onMapDataCreated(SyncMapDataFromDatabase sync) {
        RefreshCurrentEventLayers refresh = refreshInProgress;
        refreshInProgress = null;
        StringBuilder message = new StringBuilder();
        for (String messagePart : refresh.statusMessage) {
            if (message.length() > 0 && messagePart.length() > 0) {
                message.append(System.lineSeparator());
            }
            message.append(messagePart);
        }
        if (!refresh.isCancelled() || (pendingRefresh == null && (!refresh.event.equals(lastSyncedEvent) || getValue() == null))) {
            lastSyncedEvent = refresh.event;
            setValue(new BasicResource<>(Collections.singleton(sync.result.mapData), refresh.status, refresh.status.ordinal(), message.toString()));
        }
        beginPendingRefresh();
    }


    @MainThread
    private class RefreshCurrentEventLayers {

        private final Executor executor;
        private final Event event;
        private final Map<String, SyncLayerFeatures> featureSyncForLayer = new HashMap<>();
        private final Map<String, Set<Long>> featuresForIconUrl = new HashMap<>();
        private final Map<String, SyncIconToFeatures> iconSyncForIconUrl = new HashMap<>();
        private final FetchEventLayers layerFetch;
        private final SyncMapDataFromDatabase syncMapData;
        private boolean cancelled = false;
        private Resource.Status status = Success;
        private List<String> statusMessage = new ArrayList<>();

        private RefreshCurrentEventLayers(Executor executor, Event event) {
            this.executor = executor;
            this.event = event;
            layerFetch = new FetchEventLayers(event);
            syncMapData = new SyncMapDataFromDatabase(event);
        }

        private void cancel() {
            layerFetch.cancel(false);
            for (SyncLayerFeatures syncLayerFeatures : featureSyncForLayer.values()) {
                syncLayerFeatures.cancel(false);
            }
            for (SyncIconToFeatures syncIconToFeatures : iconSyncForIconUrl.values()) {
                syncIconToFeatures.cancel(false);
            }
            cancelled = true;
        }

        private boolean isFinishedSyncing() {
            return layerFetch.result != null && featureSyncForLayer.isEmpty() && iconSyncForIconUrl.isEmpty();
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
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
            try {
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
                    Log.e(LOG_NAME, "error fetching static layers", e);
                    return new FetchEventLayersResult(null, e);
                }
                catch (LayerException e) {
                    Log.e(LOG_NAME, "error deleting layers for event " + event.getName() + " (" + event.getRemoteId() + ") after empty fetch result");
                    return new FetchEventLayersResult(null, e);
                }
            }
            catch (Exception e) {
                Log.e(LOG_NAME, "unexpected error syncing layers for event " + event.getName() + " (" + event.getRemoteId() + ")", e);
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
            if (result == null) {
                result = new FetchEventLayersResult(Collections.emptySet(), null);
            }
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
            try {
                Collection<StaticFeature> staticFeatures;
                try {
                    staticFeatures = layerService.getFeatures(layer);
                }
                catch (IOException e) {
                    Log.e(LOG_NAME, "error fetching features for layer " + layer.getRemoteId(), e);
                    return new SyncLayerFeaturesResult(Collections.emptyMap(), e);
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
                    return new SyncLayerFeaturesResult(featuresForIconUrl, null);
                }
                catch (LayerException e) {
                    Log.e(LOG_NAME, "failed to save fetched layer " + layer.getName() + " (" + layer.getRemoteId() + ")", e);
                    return new SyncLayerFeaturesResult(Collections.emptyMap(), e);
                }
                catch (StaticFeatureException e) {
                    Log.e(LOG_NAME, "failed to save static features for layer " + layer.getName() + " (" + layer.getRemoteId() + ")", e);
                    return new SyncLayerFeaturesResult(Collections.emptyMap(), e);
                }
            }
            catch (Exception e) {
                Log.e(LOG_NAME, "unexpected error syncing features for layer " + layer.getRemoteId(), e);
                return new SyncLayerFeaturesResult(Collections.emptyMap(), e);
            }
        }

        @Override
        protected void onPostExecute(SyncLayerFeaturesResult result) {
            if (result == null) {
                result = new SyncLayerFeaturesResult(Collections.emptyMap(), null);
            }
            this.result = result;
            onFeaturesSynced(this);
        }

        @Override
        protected void onCancelled(SyncLayerFeaturesResult result) {
            onPostExecute(result);
        }
    }

    private static class SyncLayerFeaturesResult {
        private final Map<String, Set<Long>> featuresForIconUrl;
        private final Exception failure;

        private SyncLayerFeaturesResult(Map<String, Set<Long>> featuresForIconUrl, Exception failure) {
            this.featuresForIconUrl = featuresForIconUrl;
            this.failure = failure;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class SyncIconToFeatures extends AsyncTask<Void, Void, SyncIconToFeaturesResult> {

        private final String iconUrlStr;
        private final Set<Long> featureIds;
        private SyncIconToFeaturesResult result;

        private SyncIconToFeatures(String iconUrlStr, Set<Long> featureIds) {
            this.iconUrlStr = iconUrlStr;
            this.featureIds = featureIds;
        }

        @Override
        protected SyncIconToFeaturesResult doInBackground(Void... nothing) {
            File iconFile;
            try {
                iconFile = resolveIcon();
            }
            catch (Exception e) {
                Log.e(LOG_NAME, "error resolving icon url " + iconUrlStr, e);
                return new SyncIconToFeaturesResult(e, null);
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
            return new SyncIconToFeaturesResult(null, updateFailures.isEmpty() ? null : updateFailures);
        }

        @Override
        protected void onPostExecute(SyncIconToFeaturesResult result) {
            this.result = result;
            onFeatureIconsSynced(this);
        }

        @Override
        protected void onCancelled(SyncIconToFeaturesResult result) {
            if (result == null) {
                result = new SyncIconToFeaturesResult(null, Collections.emptyMap());
            }
            onPostExecute(result);
        }

        private File resolveIcon() throws Exception {
            URL iconUrl = new URL(iconUrlStr);
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

    private static class SyncIconToFeaturesResult {

        private final Exception iconResolveFailure;
        private final Map<Long, Exception> featureUpdateFailures;

        private SyncIconToFeaturesResult(Exception iconResolveFailure, Map<Long, Exception> featureUpdateFailures) {
            this.iconResolveFailure = iconResolveFailure;
            this.featureUpdateFailures = featureUpdateFailures;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class SyncMapDataFromDatabase extends AsyncTask<Void, Void, SyncMapDataFromDatabaseResult> {

        private final Event event;
        private SyncMapDataFromDatabaseResult result;

        private SyncMapDataFromDatabase(Event event) {
            this.event = event;
        }

        @Override
        protected SyncMapDataFromDatabaseResult doInBackground(Void[] nothing) {
            try {
                Collection<Layer> layers = layerHelper.readByEvent(event);
                Set<StaticFeatureLayerProvider.StaticFeatureLayerDescriptor> descriptors = new HashSet<>(layers.size());
                for (Layer layer : layers) {
                    descriptors.add(new StaticFeatureLayerProvider.StaticFeatureLayerDescriptor(layer));
                }
                MapDataResource.Resolved resolvedLayers = new MapDataResource.Resolved(RESOURCE_NAME, StaticFeatureLayerProvider.class, descriptors);
                MapDataResource mapData = new MapDataResource(
                    RESOURCE_URI, StaticFeatureLayerRepository.this, ++contentTimestamp, resolvedLayers);
                return new SyncMapDataFromDatabaseResult(mapData, null);
            }
            catch (LayerException e) {
                MapDataResource mapData = new MapDataResource(RESOURCE_URI, StaticFeatureLayerRepository.this, 0,
                    new MapDataResource.Resolved(RESOURCE_NAME, StaticFeatureLayerProvider.class));
                return new SyncMapDataFromDatabaseResult(mapData, e);
            }
        }

        @Override
        protected void onPostExecute(SyncMapDataFromDatabaseResult result) {
            this.result = result;
            onMapDataCreated(this);
        }
    }

    private static class SyncMapDataFromDatabaseResult {

        private final MapDataResource mapData;
        private final Exception failure;

        private SyncMapDataFromDatabaseResult(MapDataResource mapData, Exception failure) {
            this.mapData = mapData;
            this.failure = failure;
        }
    }
}