package mil.nga.giat.mage.map.cache;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileProvider;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import kotlin.ranges.IntRange;
import mil.nga.giat.mage.data.Resource;
import mil.nga.giat.mage.map.BasicMapElementContainer;
import mil.nga.giat.mage.map.MapCircleSpec;
import mil.nga.giat.mage.map.MapElementOperation;
import mil.nga.giat.mage.map.MapElementSpec;
import mil.nga.giat.mage.map.MapElements;
import mil.nga.giat.mage.map.MapGroundOverlaySpec;
import mil.nga.giat.mage.map.MapMarkerSpec;
import mil.nga.giat.mage.map.MapPolygonSpec;
import mil.nga.giat.mage.map.MapPolylineSpec;
import mil.nga.giat.mage.map.MapTileOverlaySpec;
import mil.nga.giat.mage.map.view.MapLayersViewModel;
import mil.nga.giat.mage.map.view.MapOwner;

/**
 * A {@code MapLayerManager} binds {@link MapLayerDescriptor layer data} from various
 * {@link MapDataRepository sources} to visual objects on a {@link GoogleMap}.
 */
public class MapLayerManager implements
    GoogleMap.OnCameraIdleListener,
    GoogleMap.OnMarkerClickListener,
    GoogleMap.OnMapClickListener,
    GoogleMap.OnCircleClickListener,
    GoogleMap.OnPolylineClickListener,
    GoogleMap.OnPolygonClickListener,
    GoogleMap.OnGroundOverlayClickListener,
    MapLayersViewModel.LayerListener {

    private static final String LOG_NAME = MapLayerManager.class.getSimpleName();

    public interface MapLayerAdapter extends MapElementSpec.MapElementOwner {

        @WorkerThread
        Iterator<? extends MapElementSpec> elementsInBounds(LatLngBounds bounds);

        /**
         * Release resources and prepare for garbage collection.
         */
        @UiThread
        void onLayerRemoved();

        @UiThread
        default Callable<String> onClick(Circle x, Object id) { return null; }
        @UiThread
        default Callable<String> onClick(GroundOverlay x, Object id) { return null; }
        @UiThread
        default Callable<String> onClick(Marker x, Object id) { return null; }
        @UiThread
        default Callable<String> onClick(Polygon x, Object id) { return null; }
        @UiThread
        default Callable<String> onClick(Polyline x, Object id) { return null; }
        @UiThread
        default Callable<String> onClick(LatLng pos) { return null; }
    }

    /**
     * Load the data for the given layer and create the objects to display the layer data on a map.
     * Using {@link #publishProgress(Object[])}, subclasses can add {@link MapElementSpec visual elements}
     * to the map as they are created, which should provide some more immediate visual results for the user
     * and prevent blocking the main thread when there are many objects to display after the layer construction
     * is complete.
     */
    private static class UpdateLayerMapElements extends AsyncTask<Void, MapElementSpec, Void> implements MapElementSpec.MapElementOwner {

        private final MapLayerManager layerManager;
        private final MapLayerDescriptor layerDescriptor;
        private final MapLayer layer;
        private final Callable<? extends MapLayerAdapter> createLayerAdapter;
        private final LatLngBounds bounds;
        private final SetSpecZIndex initZIndex;

        private UpdateLayerMapElements(MapLayerManager layerManager, MapLayerDescriptor layerDescriptor, MapLayer layer) {
            this.layerManager = layerManager;
            this.layerDescriptor = layerDescriptor;
            this.layer = layer;
            if (layer.adapter.get() == null) {
                createLayerAdapter = layerManager.providers.get(layerDescriptor.getDataType()).createMapLayerAdapter(layerDescriptor, layerManager.mapOwner);
            }
            else {
                createLayerAdapter = null;
            }
            bounds = layerManager.mapOwner.getMap().getProjection().getVisibleRegion().latLngBounds;
            initZIndex = new SetSpecZIndex(layer.zIndex);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                if (createLayerAdapter != null) {
                    layer.adapter.compareAndSet(null, createLayerAdapter.call());
                }
            }
            catch (Exception e) {
                Log.e(LOG_NAME, "error creating layer adapter for descriptor " + layerDescriptor.getLayerUri(), e);
                return null;
            }
            Iterator<? extends MapElementSpec> specs = layer.adapter.get().elementsInBounds(bounds);
            while (specs.hasNext() && !isCancelled()) {
                MapElementSpec spec = specs.next();
                spec.accept(initZIndex);
                publishProgress(spec);
            }
            return null;
        }

        @Override
        protected final void onProgressUpdate(MapElementSpec... values) {
            for (MapElementSpec o : values) {
                if (!layer.mapElements.contains(o)) {
                    o.createFor(this, layerManager.mapOwner.getMap());
                }
            }
        }

        @Override
        public void addedToMap(@NonNull MapCircleSpec spec, @NonNull Circle x) {
            layer.mapElements.add(x, spec.getId());
            layer.adapter.get().addedToMap(spec, x);
        }

        @Override
        public void addedToMap(@NonNull MapGroundOverlaySpec spec, @NonNull GroundOverlay x) {
            layer.mapElements.add(x, spec.getId());
            layer.adapter.get().addedToMap(spec, x);
        }

        @Override
        public void addedToMap(@NonNull MapMarkerSpec spec, @NonNull Marker x) {
            layer.mapElements.add(x, spec.getId());
            layer.adapter.get().addedToMap(spec, x);
        }

        @Override
        public void addedToMap(@NonNull MapPolygonSpec spec, @NonNull Polygon x) {
            layer.mapElements.add(x, spec.getId());
            layer.adapter.get().addedToMap(spec, x);
        }

        @Override
        public void addedToMap(@NonNull MapPolylineSpec spec, @NonNull Polyline x) {
            layer.mapElements.add(x, spec.getId());
            layer.adapter.get().addedToMap(spec, x);
        }

        @Override
        public void addedToMap(@NonNull MapTileOverlaySpec spec, @NonNull TileOverlay x) {
            layer.mapElements.add(x, spec.getId());
            layer.adapter.get().addedToMap(spec, x);
        }

        @Override
        protected final void onPostExecute(Void nothing) {
//            layerManager.onLayerComplete(this);
        }
    }

    private static class SetSpecZIndex implements MapElementSpec.MapElementSpecVisitor<Void> {

        private final int zIndex;

        private SetSpecZIndex(int zIndex) {
            this.zIndex = zIndex;
        }

        @Override
        public Void visit(@NonNull MapCircleSpec x) {
            x.getOptions().zIndex(zIndex);
            return null;
        }

        @Override
        public Void visit(@NonNull MapGroundOverlaySpec x) {
            x.getOptions().zIndex(zIndex);
            return null;
        }

        @Override
        public Void visit(@NonNull MapMarkerSpec x) {
            x.getOptions().zIndex(zIndex);
            return null;
        }

        @Override
        public Void visit(@NonNull MapPolygonSpec x) {
            x.getOptions().zIndex(zIndex);
            return null;
        }

        @Override
        public Void visit(@NonNull MapPolylineSpec x) {
            x.getOptions().zIndex(zIndex);
            return null;
        }

        @Override
        public Void visit(@NonNull MapTileOverlaySpec x) {
            x.getOptions().zIndex(zIndex);
            return null;
        }
    }


    /**
     * A {@code MapLayer} is the visual representation on a {@link GoogleMap} of the data
     * a {@link MapLayerDescriptor} describes.  A {@link MapDataProvider} creates instances
     * of this class from the data it provides to be added to a map.  Instances of this
     * class comprise visual objects from the Google Maps API, such as
     * {@link TileProvider tiles},
     * {@link Marker markers},
     * {@link Polygon polygons},
     * etc.
     */
    @UiThread
    private class MapLayer {

        private final MapElements mapElements = new BasicMapElementContainer();
        private final AtomicReference<MapLayerAdapter> adapter = new AtomicReference<>();
        private UpdateLayerMapElements update;
        private boolean visible = true;
        private int zIndex = 0;

        /**
         * Remove this layer's visible and hidden objects from the map.
         * Clear whatever resources this layer might hold such as data source
         * connections or large geometry collections and prepare for garbage collection.
         */
        private void removeFromMap() {
            mapElements.forEach(MapElementOperation.REMOVE);
            adapter.get().onLayerRemoved();
        }

        private void show() {
            mapElements.forEach(MapElementOperation.SHOW);
            visible = true;
        }

        private void hide() {
            mapElements.forEach(MapElementOperation.HIDE);
            visible = false;
        }

        private void setZIndex(int z) {
            zIndex = z;
            mapElements.forEach(new MapElementOperation.SetZIndex(z));
        }

        private boolean isVisible() {
            return visible;
        }

        private boolean ownsElement(Circle x) {
            return mapElements.contains(x);
        }

        private boolean ownsElement(GroundOverlay x) {
            return mapElements.contains(x);
        }

        private boolean ownsElement(Marker x) {
            return mapElements.contains(x);
        }

        private boolean ownsElement(Polygon x) {
            return mapElements.contains(x);
        }

        private boolean ownsElement(Polyline x) {
            return mapElements.contains(x);
        }

        private void onMarkerClick(Marker x) {
            Callable<String> getInfo = mapElements.withElement(x, (x1, id) -> adapter.get().onClick(x1 , id));
            // TODO: get the info on background thread and get it on the map popup
        }

        private void onCircleClick(Circle x) {
            Callable<String> getInfo = mapElements.withElement(x, (x1, id) -> adapter.get().onClick(x1, id));
            // TODO: get the info on background thread and get it on the map popup
        }

        private void onPolylineClick(Polyline x) {
            Callable<String> getInfo = mapElements.withElement(x, (x1, id) -> adapter.get().onClick(x1, id));
            // TODO: get the info on background thread and get it on the map popup
        }

        private void onPolygonClick(Polygon x) {
            Callable<String> getInfo = mapElements.withElement(x, (x1, id) -> adapter.get().onClick(x1, id));
            // TODO: get the info on background thread and get it on the map popup
        }

        private void onGroundOverlayClick(GroundOverlay x) {
            Callable<String> getInfo = mapElements.withElement(x, (x1, id) -> adapter.get().onClick(x1, id));
            // TODO: get the info on background thread and get it on the map popup
        }

        private void onMapClick(LatLng pos) {
            adapter.get().onClick(pos);
        }
    }

    private static final LatLngBounds ANYWHERE = new LatLngBounds(new LatLng(-90f, -180f), new LatLng(90f, 180f));

    private final MapOwner mapOwner;
    private final MapLayersViewModel layersModel;
    private final Map<Class<? extends MapDataProvider>, MapDataProvider> providers;
    private final Map<MapLayerDescriptor, MapLayer> layersOnMap = new HashMap<>();

    public MapLayerManager(MapOwner mapOwner, MapLayersViewModel layersModel, MapDataManager mapDataManager) {
        this.mapOwner = mapOwner;
        this.layersModel = layersModel;
        this.providers = mapDataManager.getProviders();
        layersModel.getLayersInZOrder().observe(mapOwner, this::onMapLayersChanged);
        layersModel.getLayerEvents().listen(mapOwner, this);
    }

    public GoogleMap getMap() {
        return mapOwner.getMap();
    }

    @Override
    public void layerVisibilityChanged(@NotNull MapLayersViewModel.Layer modelLayer, int position) {
        MapLayer viewLayer = layersOnMap.get(modelLayer.getDesc());
        if (modelLayer.isVisible()) {
            if (viewLayer == null) {
                // load layer elements for bounds
                MapLayer mapLayer = new MapLayer();
                mapLayer.update = new UpdateLayerMapElements(this, modelLayer.getDesc(), mapLayer);
                mapLayer.update.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
            }
            else {
                viewLayer.show();
            }
        }
        else if (viewLayer != null) {
            viewLayer.hide();
        }
    }

    @Override
    public void zOrderShift(@NotNull IntRange range) {
        for (int z : range) {
            MapLayersViewModel.Layer modelLayer = layersModel.layerAt(z);
            MapLayer mapLayer = layersOnMap.get(modelLayer.getDesc());
            if (mapLayer != null) {
                mapLayer.setZIndex(z);
            }
        }
    }

    @Override
    public void layerElementsChanged(@NotNull MapLayersViewModel.Layer layer, int position, @NotNull Map<Object, ? extends MapElementSpec> removed) {

    }

    @Override
    public void onCameraIdle() {
        Resource<List<MapLayersViewModel.Layer>> modelLayersResource = layersModel.getLayersInZOrder().getValue();
        if (modelLayersResource == null) {
            return;
        }
        List<MapLayersViewModel.Layer> modelLayers = modelLayersResource.getContent();
        if (modelLayers == null) {
            return;
        }
        ListIterator<MapLayersViewModel.Layer> cursor = modelLayers.listIterator(modelLayers.size());
        while (cursor.hasPrevious()) {
            MapLayersViewModel.Layer modelLayer = cursor.previous();
            MapLayer layer = layersOnMap.get(modelLayer.getDesc());
            if (layer != null) {
                // load layer elements for bounds
                layer.update = new UpdateLayerMapElements(this, modelLayer.getDesc(), layer);
            }
        }
    }

    @Override
    public void onMapClick(LatLng pos) {
        Resource<List<MapLayersViewModel.Layer>> modelLayersResource = layersModel.getLayersInZOrder().getValue();
        if (modelLayersResource == null) {
            return;
        }
        List<MapLayersViewModel.Layer> modelLayers = modelLayersResource.getContent();
        if (modelLayers == null) {
            return;
        }
        ListIterator<MapLayersViewModel.Layer> cursor = modelLayers.listIterator(modelLayers.size());
        while (cursor.hasPrevious()) {
            MapLayersViewModel.Layer modelLayer = cursor.previous();
            MapLayer layer = layersOnMap.get(modelLayer.getDesc());
            if (layer != null) {
                layer.onMapClick(pos);
            }
        }
    }

    @Override
    public void onCircleClick(Circle x) {
        for (MapLayer layer : layersOnMap.values()) {
            if (layer.ownsElement(x)) {
                layer.onCircleClick(x);
                return;
            }
        }
    }

    @Override
    public void onGroundOverlayClick(GroundOverlay x) {
        for (MapLayer layer : layersOnMap.values()) {
            if (layer.ownsElement(x)) {
                layer.onGroundOverlayClick(x);
                return;
            }
        }
    }

    @Override
    public boolean onMarkerClick(Marker x) {
        for (MapLayer layer : layersOnMap.values()) {
            if (layer.ownsElement(x)) {
                layer.onMarkerClick(x);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onPolygonClick(Polygon x) {
        for (MapLayer layer : layersOnMap.values()) {
            if (layer.ownsElement(x)) {
                layer.onPolygonClick(x);
                return;
            }
        }
    }

    @Override
    public void onPolylineClick(Polyline x) {
        for (MapLayer layer : layersOnMap.values()) {
            if (layer.ownsElement(x)) {
                layer.onPolylineClick(x);
                return;
            }
        }
    }

    private void onMapLayersChanged(Resource<List<MapLayersViewModel.Layer>> change) {

    }

    public void dispose() {
        // TODO: remove and dispose all overlays/notify providers
        Iterator<Map.Entry<MapLayerDescriptor, MapLayer>> entries = layersOnMap.entrySet().iterator();
        while (entries.hasNext()) {
            MapLayer onMap = entries.next().getValue();
            onMap.removeFromMap();
            entries.remove();
        }
    }

    private boolean removeFromMapReturningVisibility(MapLayerDescriptor overlay) {
        boolean wasVisible = false;
        MapLayer onMap = layersOnMap.remove(overlay);
        if (onMap != null) {
            wasVisible = onMap.isVisible();
            onMap.removeFromMap();
        }
        return wasVisible;
    }

//    private void refreshOverlayAtPositionFromUpdatedResource(int position, MapLayerDescriptor updatedOverlay) {
//        MapLayerDescriptor currentOverlay = overlaysInZOrder.get(position);
//        if (currentOverlay == updatedOverlay) {
//            return;
//        }
//        overlaysInZOrder.set(position, updatedOverlay);
//        if (removeFromMapReturningVisibility(currentOverlay)) {
//            addOverlayToMapAtPosition(position);
//        }
//    }
//
//    private void addOverlayToMap(MapLayerDescriptor overlay) {
//        int position = overlaysInZOrder.indexOf(overlay);
//        if (position > -1) {
//            addOverlayToMapAtPosition(position);
//        }
//    }
//
//    private void addOverlayToMapAtPosition(int position) {
//        MapLayerDescriptor layerDesc = overlaysInZOrder.get(position);
//        MapLayer layer = layersOnMap.get(layerDesc);
//        if (layer == null) {
//            Class<? extends MapDataProvider> resourceType = layerDesc.getDataType();
//            MapDataProvider provider = providers.get(resourceType);
//            Callable<? extends MapLayerAdapter> createLayerAdapter = provider.createMapLayerAdapter(layerDesc, mapOwner);
//            UpdateLayerMapElements loadLayerElements = (UpdateLayerMapElements) new UpdateLayerMapElements(
//                ANYWHERE, layerDesc, createLayerAdapter).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
//            layersOnMap.put(layerDesc, new PendingLayer(loadLayerElements));
//        }
//        else {
//            layer.show();
//        }
//    }
//
//    private void onLayerComplete(UpdateLayerMapElements updateLayerTask) {
//        PendingLayer pending = (PendingLayer) layersOnMap.put(updateLayerTask.layerDescriptor, updateLayerTask.layer.get());
//        if (pending.addLayer != updateLayerTask) {
//            throw new IllegalStateException("layer task for descriptor " + updateLayerTask.layerDescriptor + " did not match expected pending layer task");
//        }
//    }
//
//    // TODO: finish and use this
//    private class PendingLayer extends MapLayer {
//
//        private final UpdateLayerMapElements addLayer;
//
//        private PendingLayer(UpdateLayerMapElements addLayer) {
//            super(PENDING_LAYER_ADAPTER);
//            this.addLayer = addLayer;
//        }
//    }
//
//    private static final MapLayerAdapter PENDING_LAYER_ADAPTER = new MapLayerAdapter() {
//
//        @Override
//        public void onLayerRemoved() {}
//
//        @Override
//        public Iterator<? extends MapElementSpec> elementsInBounds(LatLngBounds bounds) {
//            return Collections.emptyIterator();
//        }
//    };
}
