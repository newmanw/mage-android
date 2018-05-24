package mil.nga.giat.mage.map.cache;


import android.annotation.SuppressLint;
import android.app.Application;
import android.os.AsyncTask;
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
import java.util.Iterator;
import java.util.Map;
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
import mil.nga.giat.mage.sdk.http.resource.LayerResource;
import mil.nga.giat.mage.sdk.login.LoginTaskFactory;


@MainThread
public class StaticFeatureLayerRepository extends MapDataRepository implements MapDataProvider, IEventEventListener {

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
    private final File iconsDir;
    private final NetworkCondition network;
    private final Map<String, IconResolve> resolvedIcons = new HashMap<>();
    private Event currentEvent;
    private RefreshCurrentEventLayers refreshInProgress;
    private RefreshCurrentEventLayers pendingRefresh;
    private boolean cancelling;

    public StaticFeatureLayerRepository(EventHelper eventHelper, LayerHelper layerHelper, StaticFeatureHelper featureHelper, LayerResource layerService, File iconsDir, NetworkCondition network) {
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
        refreshInProgress.begin();
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
        Collection<Layer> fetchedLayers = refreshInProgress.layerFetch.fetchedLayers;
        if (fetchedLayers.isEmpty()) {
            refreshInProgress = null;
            return;
        }
        for (Layer layer : refreshInProgress.layerFetch.fetchedLayers) {
            SyncLayerFeatures featureFetch = new SyncLayerFeatures(layer);
            refreshInProgress.featureFetchForLayer.put(layer.getRemoteId(), featureFetch);
            featureFetch.executeOnExecutor(refreshInProgress.executor);
        }
    }

    private void onFeaturesSynced(SyncLayerFeatures sync) {
        SyncLayerFeatures removed = refreshInProgress.featureFetchForLayer.remove(sync.layer.getRemoteId());
        if (removed != sync) {
            throw new IllegalStateException("feature sync finished for layer " +
                sync.layer.getRemoteId() + " but did not match the expected feature sync instance");
        }
        if (refreshInProgress.isFinished()) {
            refreshInProgress = null;
        }
    }

    @MainThread
    private class RefreshCurrentEventLayers {

        final Executor executor;
        final Event event;
        final FetchEventLayers layerFetch;
        final Map<String, SyncLayerFeatures> featureFetchForLayer = new HashMap<>();
        private boolean cancelled = false;

        private RefreshCurrentEventLayers(Executor executor, Event event) {
            this.executor = executor;
            this.event = event;
            this.layerFetch = new FetchEventLayers(event);
        }

        private void begin() {
            layerFetch.executeOnExecutor(executor);
        }

        private boolean cancel() {
            if (isFinished()) {
                return false;
            }
            cancelled = true;
            if (isStarted()) {
                layerFetch.cancel(false);
                Iterator<SyncLayerFeatures> featureFetches = featureFetchForLayer.values().iterator();
                while (featureFetches.hasNext()) {
                    featureFetches.next().cancel(false);
                    featureFetches.remove();
                }
            }
            return true;
        }

        private boolean isStarted() {
            return layerFetch.getStatus() != AsyncTask.Status.PENDING;
        }

        private boolean isFinished() {
            return layerFetch.getStatus() == AsyncTask.Status.FINISHED && featureFetchForLayer.isEmpty();
        }

        private boolean isCancelled() {
            return cancelled;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class FetchEventLayers extends AsyncTask<Void, Void, Collection<Layer>> {

        private final Event event;
        private Collection<Layer> fetchedLayers;

        private FetchEventLayers(Event event) {
            this.event = event;
        }

        @Override
        protected Collection<Layer> doInBackground(Void... nothing) {
            if (!network.isConnected()) {
                Log.d(LOG_NAME, "disconnected, skipping static layer fetch");
                return Collections.emptyList();
            }
            try {
                return layerService.getLayers(event);
            }
            catch (IOException e) {
                Log.e(LOG_NAME,"error fetching static layers", e);
            }
            return null;

//            Iterator<Layer> layerIter = remoteLayers.iterator();
//            while (!isCancelled() && layerIter.hasNext()) {
//                Layer layer = layerIter.next();
//                try {
//                    layerHelper.create(layer);
//                }
//                catch (LayerException e) {
//                    Log.e(LOG_NAME, "error creating static layer " + layer.getName() + " (" + layer.getRemoteId() + ")", e);
//                }
//            }
//
//            Collection<Layer> localLayers = null;
//            try {
//                localLayers = layerHelper.readAll();
//            }
//            catch (LayerException e) {
//                Log.e(LOG_NAME, "error reading layers from database after fectch", e);
//            }
//
//            return localLayers;
        }

        @Override
        protected void onPostExecute(Collection<Layer> layers) {
            fetchedLayers = layers;
            onLayerFetchFinished();
        }

        @Override
        protected void onCancelled(Collection<Layer> layers) {
            onPostExecute(layers);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class SyncLayerFeatures extends AsyncTask<Void, IconResolve, Layer> {

        private final Layer layer;
        private final Map<String, IconResolve> resolvedIcons;
        private Layer syncedLayer;

        private SyncLayerFeatures(Layer layer) {
            this.layer = layer;
            this.resolvedIcons = new HashMap<>(StaticFeatureLayerRepository.this.resolvedIcons);
        }

        @Override
        protected Layer doInBackground(Void... nothing) {
            Collection<StaticFeature> staticFeatures;
            try {
                staticFeatures = layerService.getFeatures(layer);
            }
            catch (IOException e) {
                return null;
            }

//            Iterator<StaticFeature> featureIter = staticFeatures.iterator();
//            while (!isCancelled() && featureIter.hasNext()) {
//                StaticFeature feature = featureIter.next();
//                IconResolve iconResolve = resolveIconForFeature(feature);
//                if (iconResolve.iconUrlStr != null) {
//                    resolvedIcons.put(iconResolve.iconUrlStr, iconResolve);
//                }
//                if (iconResolve.iconFileName != null) {
//                    feature.setLocalPath(iconResolve.iconFileName);
//                }
//                publishProgress(iconResolve);
//            }
//
//            if (isCancelled()) {
//                return layer;
//            }

            Layer layerWithFeatures = null;
            try {
                layerWithFeatures = featureHelper.createAll(staticFeatures, layer);
                layerHelper.update(layerWithFeatures);
            }
            catch (LayerException e) {
                Log.e(LOG_NAME, "failed to save fetched features for layer " + layer.getName(), e);
            }

            return layerWithFeatures;
        }

        @Override
        protected void onProgressUpdate(IconResolve... values) {
            IconResolve iconResolve = values[0];
            if (iconResolve.iconUrlStr != null) {
                StaticFeatureLayerRepository.this.resolvedIcons.put(iconResolve.iconUrlStr, iconResolve);
            }
        }

        @Override
        protected void onPostExecute(Layer layer) {
            syncedLayer = layer;
            onFeaturesSynced(this);
        }

        @Override
        protected void onCancelled(Layer layer) {
            onPostExecute(layer);
        }

        private IconResolve resolveIconForFeature(StaticFeature feature) {
            StaticFeatureProperty iconProperty = feature.getPropertiesMap().get("styleiconstyleiconhref");
            if (iconProperty == null) {
                return new IconResolve(null, null);
            }
            String iconUrlStr = iconProperty.getValue();
            if (iconUrlStr == null) {
                return new IconResolve(null, null);
            }
            IconResolve result = resolvedIcons.get(iconUrlStr);
            if (result != null) {
                return result;
            }
            URL iconUrl;
            try {
                iconUrl = new URL(iconUrlStr);
            }
            catch (MalformedURLException e) {
                Log.e(LOG_NAME, "bad icon url for static feature " + feature.getRemoteId() + " of layer " + feature.getLayer().getName() + " (" + feature.getLayer().getRemoteId() + ")", e);
                return new IconResolve(null, iconUrlStr);
            }
            String fileName = iconUrl.getPath();
            if (fileName == null) {
                return new IconResolve(null, iconUrlStr);
            }
            fileName = fileName.replaceAll("^/+", "");
            File iconFile = new File(iconsDir, fileName);
            if (!iconFile.exists()) {
                InputStream inputStream;
                try {
                    inputStream = layerService.getFeatureIcon(iconUrlStr);
                }
                catch (IOException e) {
                    Log.e(LOG_NAME, "error fetching icon " + iconUrlStr, e);
                    return new IconResolve(null, iconUrlStr);
                }
                if (!iconFile.getParentFile().mkdirs()) {
                    Log.e(LOG_NAME,"error creating parent dir for icon: " + iconFile);
                    return new IconResolve(null, iconUrlStr);
                }
                FileOutputStream iconOut = null;
                try {
                    iconOut = new FileOutputStream(iconFile);
                    ByteStreams.copy(inputStream, iconOut);
                }
                catch (IOException e) {
                    Log.e(LOG_NAME, "error writing icon file " + iconFile, e);
                    if (iconFile.exists() && !iconFile.delete()) {
                        Log.e(LOG_NAME, "failed to delete icon file after bad file write: " + iconFile);
                    }
                    return new IconResolve(null, iconUrlStr);
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
            }
            return new IconResolve(iconFile.getAbsolutePath(), iconUrlStr);
        }
    }

    private static class IconResolve {

        private final String iconFileName;
        private final String iconUrlStr;

        private IconResolve(String iconFileName, String iconUrlStr) {
            this.iconFileName = iconFileName;
            this.iconUrlStr = iconUrlStr;
        }
    }

    private static class LayerDescriptor extends MapLayerDescriptor {

        private final Layer subject;

        private LayerDescriptor(Layer subject) {
            super(subject.getName(), RESOURCE_URI, StaticFeatureLayerRepository.class);
            this.subject = subject;
        }
    }
}