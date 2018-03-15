package mil.nga.giat.mage.map.cache;


import android.annotation.SuppressLint;
import android.app.Application;
import android.os.AsyncTask;
import android.support.annotation.MainThread;
import android.util.Log;

import com.google.common.io.ByteStreams;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import mil.nga.giat.mage.sdk.connectivity.ConnectivityUtility;
import mil.nga.giat.mage.sdk.datastore.DaoStore;
import mil.nga.giat.mage.sdk.datastore.layer.Layer;
import mil.nga.giat.mage.sdk.datastore.layer.LayerHelper;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeature;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeatureHelper;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeatureProperty;
import mil.nga.giat.mage.sdk.datastore.user.Event;
import mil.nga.giat.mage.sdk.datastore.user.EventHelper;
import mil.nga.giat.mage.sdk.exceptions.LayerException;
import mil.nga.giat.mage.sdk.http.resource.LayerResource;
import mil.nga.giat.mage.sdk.login.LoginTaskFactory;


@MainThread
public class MAGEStaticFeatureLayerRepository extends MapDataRepository implements MapDataProvider {

    private static final String LOG_NAME = MAGEStaticFeatureLayerRepository.class.getName();
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
    private static MAGEStaticFeatureLayerRepository instance = null;

    public static synchronized void initialize(Application context) {
        instance = new MAGEStaticFeatureLayerRepository(context);
    }

    public static MAGEStaticFeatureLayerRepository getInstance() {
        return instance;
    }

    private final Application context;
    private final LayerResource layerService;
    private final Map<String, IconResolve> resolvedIcons = new HashMap<>();
    private Event currentEvent;
    private PurgeAllLayers pendingPurge;
    private SyncEventLayers pendingLayerSync;
    private Executor currentSyncExecutor;
    private Map<Layer, FetchLayerFeatures> pendingFeatureLoads = new LinkedHashMap<>();
    private boolean cancelling;

    private MAGEStaticFeatureLayerRepository(Application context) {
        this.context = context;
        this.layerService = new LayerResource(context);
    }

    @NotNull
    @Override
    public Status getStatus() {
        return null;
    }

    @Override
    public int getStatusCode() {
        return 0;
    }

    @Nullable
    @Override
    public String getStatusMessage() {
        return null;
    }

    @Override
    public boolean ownsResource(URI resourceUri) {
        return false;
    }

    @Override
    public void refreshAvailableMapData(Map<URI, MapDataResource> resolvedResources, Executor executor) {
        Event currentEventTest = EventHelper.getInstance(context).getCurrentEvent();
        if (currentEvent != null && currentEvent.equals(currentEventTest) && isSyncingLayers()) {
            return;
        }
        currentEvent = currentEventTest;
        cancelThenStartNewSync(executor);
    }

    @Override
    public boolean canHandleResource(MapDataResource resource) {
        return false;
    }

    @Override
    public MapDataResource resolveResource(MapDataResource resource) throws MapDataResolveException {
        return null;
    }

    @Override
    public MapLayerManager.MapLayer createMapLayerFromDescriptor(MapLayerDescriptor layerDescriptor, MapLayerManager map) {
        return null;
    }

    public void purgeAndRefreshAllLayers(Executor executor) {
        if (pendingPurge != null) {
            return;
        }
        pendingPurge = new PurgeAllLayers(executor);
        cancelThenStartNewSync(executor);
    }

    public void loadFeaturesOfLayer(Layer layer, Executor executor) {
        if (isLoadingFeatures(layer)) {
            return;
        }
        pendingFeatureLoads.put(layer, new FetchLayerFeatures(layer));
        proceedIfReady();
    }

    public void cancelSync() {
        cancelling = false;
        resolvedIcons.clear();
        if (pendingLayerSync != null) {
            cancelling = pendingLayerSync.cancel(false);
        }
        for (FetchLayerFeatures featuresTask : pendingFeatureLoads.values()) {
            cancelling = cancelling || featuresTask.cancel(false);
        }
    }

    /**
     * Return true if a sync, purge, or cancellation is pending.  This is independent
     * of loading features.
     *
     * @return
     */
    public boolean isSyncingLayers() {
        return cancelling || pendingPurge != null ||
            (pendingLayerSync != null && pendingLayerSync.getStatus() != AsyncTask.Status.PENDING);
    }

    public boolean isLoadingFeatures() {
        return !pendingFeatureLoads.isEmpty();
    }

    public boolean isLoadingFeatures(Layer layer) {
        return pendingFeatureLoads.get(layer) != null;
    }

    private void cancelThenStartNewSync(Executor executor) {
        cancelSync();
        currentSyncExecutor = executor;
        pendingLayerSync = new SyncEventLayers(currentEvent);
        proceedIfReady();
    }

    private void onLayersPurged() {
        pendingPurge = null;
        proceedIfReady();
    }

    private void onLayerSyncFinished(SyncEventLayers finished, Collection<Layer> layers) {
        if (finished == pendingLayerSync) {
            pendingLayerSync = null;
        }
        if (finished.isCancelled()) {
            updateCancellation();
        }
        else {
            Set<MapLayerDescriptor> descriptors = new HashSet<>();
            for (Layer layer : layers) {
                descriptors.add(new LayerDescriptor(layer));
            }
            MapDataResource.Resolved resolved = new MapDataResource.Resolved(RESOURCE_NAME, getClass(), descriptors);
            MapDataResource resource = new MapDataResource(RESOURCE_URI, this, System.currentTimeMillis(), resolved);
            setValue(Collections.singleton(resource));
        }
        proceedIfReady();
    }

    private void onFeatureLoadFinished(FetchLayerFeatures finished, Layer layer) {
        pendingFeatureLoads.remove(layer);
        if (finished.isCancelled()) {
            updateCancellation();
        }
        else {
            // TODO: cancellation api
//            for (OnStaticLayersListener listener : listeners) {
//                listener.onFeaturesLoaded(layer);
//            }
        }
        proceedIfReady();
    }

    private void proceedIfReady() {
        if (isSyncingLayers() || isLoadingFeatures()) {
            return;
        }
        if (pendingPurge != null) {
            pendingPurge.executeOnExecutor(currentSyncExecutor);
        }
        else if (pendingLayerSync != null && pendingLayerSync.getStatus() != AsyncTask.Status.RUNNING) {
            pendingLayerSync.executeOnExecutor(currentSyncExecutor);
        }
        else {
            List<FetchLayerFeatures> defendConcurrentModificationException = new ArrayList<>(pendingFeatureLoads.values());
            for (FetchLayerFeatures featureLoad : defendConcurrentModificationException) {
                featureLoad.executeOnExecutor(currentSyncExecutor);
            }
        }
    }

    private void updateCancellation() {
        if (isLoadingFeatures()) {
            return;
        }
        cancelling = false;
    }

    private class PurgeAllLayers extends AsyncTask<Void, Void, Executor> {

        private final Executor afterPurgeExecutor;

        private PurgeAllLayers(Executor afterPurgeExecutor) {
            this.afterPurgeExecutor = afterPurgeExecutor;
        }

        @Override
        protected Executor doInBackground(Void... nothing) {
            try {
                LayerHelper.getInstance(context).deleteAll();
            }
            catch (LayerException e) {
                Log.e(LOG_NAME, "error purging layers", e);
            }
            return afterPurgeExecutor;
        }

        @Override
        protected void onPostExecute(Executor afterPurgeExecutor) {
            onLayersPurged();
        }
    }

    private class SyncEventLayers extends AsyncTask<Void, Void, Collection<Layer>> {

        private final Event event;

        private SyncEventLayers(Event event) {
            this.event = event;
        }

        @Override
        protected Collection<Layer> doInBackground(Void... nothing) {
            if (!ConnectivityUtility.isOnline(context) || LoginTaskFactory.getInstance(context).isLocalLogin()) {
                Log.d(LOG_NAME, "disconnected, skipping static layer fetch");
                return Collections.emptyList();
            }

            LayerHelper layerHelper = LayerHelper.getInstance(context);

            Collection<Layer> remoteLayers;
            try {
                remoteLayers = layerService.getLayers(event);
            }
            catch (IOException e) {
                Log.e(LOG_NAME,"error fetching static layers", e);
                return Collections.emptyList();
            }

            Iterator<Layer> layerIter = remoteLayers.iterator();
            while (!isCancelled() && layerIter.hasNext()) {
                Layer layer = layerIter.next();
                try {
                    layerHelper.create(layer);
                }
                catch (LayerException e) {
                    Log.e(LOG_NAME, "error creating static layer " + layer.getName() + " (" + layer.getRemoteId() + ")", e);
                }
            }

            Collection<Layer> localLayers = null;
            try {
                localLayers = layerHelper.readAll();
            }
            catch (LayerException e) {
                Log.e(LOG_NAME, "error reading layers from database after fectch", e);
            }

            return localLayers;
        }

        @Override
        protected void onPostExecute(Collection<Layer> layers) {
            onLayerSyncFinished(this, layers);
        }

        @Override
        protected void onCancelled(Collection<Layer> layers) {
            onLayerSyncFinished(this, layers);
        }
    }

    private class FetchLayerFeatures extends AsyncTask<Void, IconResolve, Layer> {

        private final Layer layer;
        private final Map<String, IconResolve> resolvedIcons;

        private FetchLayerFeatures(Layer layer) {
            this.layer = layer;
            this.resolvedIcons = new HashMap<>(MAGEStaticFeatureLayerRepository.this.resolvedIcons);
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

            Iterator<StaticFeature> featureIter = staticFeatures.iterator();
            while (!isCancelled() && featureIter.hasNext()) {
                StaticFeature feature = featureIter.next();
                IconResolve iconResolve = resolveIconForFeature(feature);
                if (iconResolve.iconUrlStr != null) {
                    resolvedIcons.put(iconResolve.iconUrlStr, iconResolve);
                }
                if (iconResolve.iconFileName != null) {
                    feature.setLocalPath(iconResolve.iconFileName);
                }
                publishProgress(iconResolve);
            }

            if (isCancelled()) {
                return layer;
            }

            Layer layerWithFeatures = null;
            try {
                layerWithFeatures = StaticFeatureHelper.getInstance(context).createAll(staticFeatures, layer);
                DaoStore.getInstance(context).getLayerDao().update(layer);
            }
            catch (SQLException e) {
                Log.e(LOG_NAME, "failed to mark the layer record as loaded: " + layer.getName(), e);
            }

            return layerWithFeatures;
        }

        @Override
        protected void onProgressUpdate(IconResolve... values) {
            IconResolve iconResolve = values[0];
            if (iconResolve.iconUrlStr != null) {
                MAGEStaticFeatureLayerRepository.this.resolvedIcons.put(iconResolve.iconUrlStr, iconResolve);
            }
        }

        @Override
        protected void onPostExecute(Layer layer) {
            onFeatureLoadFinished(this, layer);
        }

        @Override
        protected void onCancelled(Layer layer) {
            onFeatureLoadFinished(this, layer);
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
            File iconFile = new File(context.getFilesDir() + "/icons/staticfeatures", fileName);
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
            super(subject.getName(), RESOURCE_URI, MAGEStaticFeatureLayerRepository.class);
            this.subject = subject;
        }
    }
}