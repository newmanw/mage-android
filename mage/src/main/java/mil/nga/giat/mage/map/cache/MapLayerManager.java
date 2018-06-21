package mil.nga.giat.mage.map.cache;

import android.os.AsyncTask;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.view.View;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileProvider;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import mil.nga.giat.mage.map.BasicMapElementContainer;
import mil.nga.giat.mage.map.MapElements;
import mil.nga.giat.mage.map.MapElementOperation;
import mil.nga.giat.mage.map.MapElementSpec;

/**
 * A {@code MapLayerManager} binds {@link MapLayerDescriptor layer data} from various
 * {@link MapDataRepository sources} to visual objects on a {@link GoogleMap}.
 */
@MainThread
public class MapLayerManager implements MapDataManager.MapDataListener, GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener, GoogleMap.OnCircleClickListener, GoogleMap.OnPolylineClickListener, GoogleMap.OnPolygonClickListener, GoogleMap.OnGroundOverlayClickListener {

    public interface MapLayerAdapter extends MapElementSpec.MapElementOwner {

        default Callable<String> onClick(Circle x, Object id) { return null; }
        default Callable<String> onClick(GroundOverlay x, Object id) { return null; }
        default Callable<String> onClick(Marker x, Object id) { return null; }
        default Callable<String> onClick(Polygon x, Object id) { return null; }
        default Callable<String> onClick(Polyline x, Object id) { return null; }
        default Callable<String> onClick(LatLng pos, View mapView) { return null; }

        /**
         * Release resources and prepare for garbage collection.
         */
        void onLayerRemoved();

        @WorkerThread
        Iterator<? extends MapElementSpec> elementsInBounds(LatLngBounds bounds);
    }

    public interface MapLayerListener {

        /**
         * Notify the listener that the {@link MapDataManager} has updated the {@link #getLayersInZOrder() cache list}.
         * {@link MapLayerManager} will not invoke this method as result of its own on-map interactions,
         * such as adding, removing, showing, and hiding overlays.
         */
        void layersChanged();
    }

    /**
     * Load the data for the given layer and create the objects to display the layer data on a map.
     * Using {@link #publishProgress(Object[])}, subclasses can add {@link MapElementSpec visual elements}
     * to the map as they are created, which should provide some more immediate visual results for the user
     * and prevent blocking the main thread when there are many objects to display after the layer construction
     * is complete.
     */
    private static class UpdateLayerMapElements extends AsyncTask<Void, MapElementSpec, Void> implements MapElementSpec.MapElementOwner {

        private final MapLayerDescriptor layerDescriptor;
        private final MapLayerAdapter layerAdapter;
        private final MapLayer layer;
        private final LatLngBounds bounds;
        private final MapLayerManager mapLayerManager;

        private UpdateLayerMapElements(MapLayerDescriptor layerDescriptor, MapLayerAdapter layerAdapter, MapLayer layer, LatLngBounds bounds, MapLayerManager mapLayerManager) {
            this.layerDescriptor = layerDescriptor;
            this.layerAdapter = layerAdapter;
            this.layer = layer;
            this.bounds = bounds;
            this.mapLayerManager = mapLayerManager;
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Iterator<? extends MapElementSpec> specs = layerAdapter.elementsInBounds(bounds);
            while (specs.hasNext() && !isCancelled()) {
                MapElementSpec spec = specs.next();
                publishProgress(spec);
            }
            return null;
        }

        @Override
        protected final void onProgressUpdate(MapElementSpec... values) {
            for (MapElementSpec o : values) {
                o.createFor(this, mapLayerManager.getMap());
            }
        }

        @Override
        public void addedToMap(MapElementSpec.MapCircleSpec spec, Circle x) {
            layer.mapElements.add(x, spec.id == null ? x.getId() : spec.id);
        }

        @Override
        public void addedToMap(MapElementSpec.MapGroundOverlaySpec spec, GroundOverlay x) {
            layer.mapElements.add(x, spec.id == null ? x.getId() : spec.id);
        }

        @Override
        public void addedToMap(MapElementSpec.MapMarkerSpec spec, Marker x) {
            layer.mapElements.add(x, spec.id == null ? x.getId() : spec.id);
        }

        @Override
        public void addedToMap(MapElementSpec.MapPolygonSpec spec, Polygon x) {
            layer.mapElements.add(x, spec.id == null ? x.getId() : spec.id);
        }

        @Override
        public void addedToMap(MapElementSpec.MapPolylineSpec spec, Polyline x) {
            layer.mapElements.add(x, spec.id == null ? x.getId() : spec.id);
        }

        @Override
        public void addedToMap(MapElementSpec.MapTileOverlaySpec spec, TileOverlay x) {
            layer.mapElements.add(x, spec.id == null ? x.getId() : spec.id);
        }

        @Override
        protected final void onPostExecute(Void nothing) {
            mapLayerManager.onLayerComplete(this);
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

        protected MapElements mapElements;
        protected MapLayerAdapter adapter;
        private boolean visible = true;

        private MapLayer(MapElements mapElements) {
            this.mapElements = mapElements;
        }

        /**
         * Remove this layer's visible and hidden objects from the map.
         * Clear whatever resources this layer might hold such as data source
         * connections or large geometry collections and prepare for garbage collection.
         */
        protected void removeFromMap() {
            mapElements.forEach(MapElementOperation.REMOVE);
        }

        protected void show() {
            mapElements.forEach(MapElementOperation.SHOW);
            visible = true;
        }

        protected void hide() {
            mapElements.forEach(MapElementOperation.HIDE);
            visible = false;
        }

        protected void setZIndex(int z) {
            mapElements.forEach(new MapElementOperation.SetZIndex(z));
        }

        protected boolean isVisible() {
            return visible;
        }

        protected final GoogleMap getMap() {
            return MapLayerManager.this.getMap();
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
        }

        private void onCircleClick(Circle x) {
        }

        private void onPolylineClick(Polyline x) {
        }

        private void onPolygonClick(Polygon x) {
        }

        private void onGroundOverlayClick(GroundOverlay x) {
        }

        protected void onMapClick(LatLng pos) {
        }

        // TODO: this is awkward passing the map view and returning a string; probably can do better
        protected void onMapClick(LatLng latLng, MapView mapView) {
        }
    }

    /**
     * Compute the fractional step necessary to stack {@code count} map objects
     * within a single integral zoom level.  This is a convenience for {@link MapDataProvider}
     * implementations of {@link MapLayer} to use when setting the integer
     * {@link MapLayer#setZIndex(int) z-index}.  The implementation can use the
     * return value to increment the float z-index of {@code count} {@link GoogleMap} objects
     * logically contained in a single overlay.  For example, if an overlay contains 5 map
     * objects, such as {@link TileOverlay tiles} and
     * {@link Polygon polygons}, and the integer z-index
     * to set on the overlay is 6, this method will return 1 / (5 + 1), ~= 0.167, and map
     * objects can have fractional zoom levels (6 + 1 * 0.167), (6 + 2 * 0.167), etc.,
     * without intruding on the next integral zoom level, 7, which {@link MapLayerManager}
     * will assign to the next {@link MapLayerDescriptor}/{@link MapLayer} in the
     * {@link #getLayersInZOrder() z-order}.
     * @param count
     * @return
     */
    public static float zIndexStepForObjectCount(int count) {
        return 1.0f / (count + 1.0f);
    }

    private static final LatLngBounds ANYWHERE = new LatLngBounds(new LatLng(-90f, -180f), new LatLng(90f, 180f));

    private final MapDataManager mapDataManager;
    private final GoogleMap map;
    private final Map<Class<? extends MapDataProvider>, MapDataProvider> providers = new HashMap<>();
    private final Map<MapLayerDescriptor, MapLayer> layersOnMap = new HashMap<>();
    private final List<MapLayerListener> listeners = new ArrayList<>();
    private List<MapLayerDescriptor> overlaysInZOrder = new ArrayList<>();

    public MapLayerManager(MapDataManager mapDataManager, List<MapDataProvider> providers, GoogleMap map) {
        this.mapDataManager = mapDataManager;
        this.map = map;
        for (MapDataProvider provider : providers) {
            this.providers.put(provider.getClass(), provider);
        }
        for (MapDataResource cache : mapDataManager.getResources().values()) {
            overlaysInZOrder.addAll(cache.getLayers().values());
        }
        mapDataManager.addUpdateListener(this);
    }

    @Override
    public void onMapDataUpdated(@NonNull MapDataManager.MapDataUpdate update) {
        int position = 0;
        Iterator<MapLayerDescriptor> orderIterator = overlaysInZOrder.iterator();
        Map<URI, MapLayerDescriptor> allLayers = update.getSource().getLayers();
        while (orderIterator.hasNext()) {
            MapLayerDescriptor existingLayer = orderIterator.next();
            MapLayerDescriptor updatedLayer = allLayers.get(existingLayer.getLayerUri());
            if (updatedLayer == null) {
                removeFromMapReturningVisibility(existingLayer);
                orderIterator.remove();
                position--;
            }
            else {
                refreshOverlayAtPositionFromUpdatedResource(position, updatedLayer);
            }
            position++;
        }
        SortedSet<MapLayerDescriptor> addedLayersSorted = new TreeSet<>(
            (MapLayerDescriptor a, MapLayerDescriptor b) -> a.getLayerTitle().compareTo(b.getLayerTitle()));
        for (MapDataResource resource : update.getAdded().values()) {
            addedLayersSorted.addAll(resource.getLayers().values());
        }
        overlaysInZOrder.addAll(addedLayersSorted);
        for (MapLayerListener listener : listeners) {
            listener.layersChanged();
        }
    }

    public void addListener(MapLayerListener x) {
        listeners.add(x);
    }

    public void removeListener(MapLayerListener x) {
        listeners.remove(x);
    }

    public GoogleMap getMap() {
        return map;
    }

    /**
     * Return a modifiable copy of the overlay list in z-order.  The last element
     * (index {@code size() - 1}) in the list is the top-most element.
     */
    public List<MapLayerDescriptor> getLayersInZOrder() {
        return new ArrayList<>(overlaysInZOrder);
    }

    public void showLayer(MapLayerDescriptor layerDesc) {
        addOverlayToMap(layerDesc);
    }

    public void hideLayer(MapLayerDescriptor layerDesc) {
        MapLayer onMap = layersOnMap.get(layerDesc);
        if (onMap == null || !onMap.isVisible()) {
            return;
        }
        onMap.hide();
    }

    public boolean isLayerVisible(MapLayerDescriptor layerDesc) {
        MapLayer onMap = layersOnMap.get(layerDesc);
        return onMap != null && onMap.isVisible();
    }

    @Override
    public void onMapClick(LatLng latLng) {
        for (MapLayerDescriptor layerDesc : getLayersInZOrder()) {
            MapLayer layer = layersOnMap.get(layerDesc);
            if (layer != null) {
                layer.onMapClick(latLng);
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

    public boolean setZOrder(List<MapLayerDescriptor> order) {
        if (order.size() != overlaysInZOrder.size()) {
            return false;
        }
        Map<MapLayerDescriptor, MapLayerDescriptor> index = new HashMap<>(overlaysInZOrder.size());
        for (MapLayerDescriptor overlay : overlaysInZOrder) {
            index.put(overlay, overlay);
        }
        List<MapLayerDescriptor> targetOrder = new ArrayList<>(overlaysInZOrder.size());
        for (MapLayerDescriptor overlayToMove : order) {
            MapLayerDescriptor target = index.remove(overlayToMove);
            if (target == null) {
                return false;
            }
            targetOrder.add(target);
        }
        if (index.size() > 0) {
            return false;
        }
        overlaysInZOrder = targetOrder;
        int zIndex = 0;
        for (MapLayerDescriptor overlay : overlaysInZOrder) {
            MapLayer onMap = layersOnMap.get(overlay);
            if (onMap != null) {
                onMap.setZIndex(zIndex);
            }
            zIndex += 1;
        }
        return true;
    }

    public boolean moveZIndex(int fromPosition, int toPosition) {
        List<MapLayerDescriptor> order = getLayersInZOrder();
        MapLayerDescriptor target = order.remove(fromPosition);
        order.add(toPosition, target);
        return setZOrder(order);
    }

    public void dispose() {
        // TODO: remove and dispose all overlays/notify providers
        mapDataManager.removeUpdateListener(this);
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

    private void refreshOverlayAtPositionFromUpdatedResource(int position, MapLayerDescriptor updatedOverlay) {
        MapLayerDescriptor currentOverlay = overlaysInZOrder.get(position);
        if (currentOverlay == updatedOverlay) {
            return;
        }
        overlaysInZOrder.set(position, updatedOverlay);
        if (removeFromMapReturningVisibility(currentOverlay)) {
            addOverlayToMapAtPosition(position);
        }
    }

    private void addOverlayToMap(MapLayerDescriptor overlay) {
        int position = overlaysInZOrder.indexOf(overlay);
        if (position > -1) {
            addOverlayToMapAtPosition(position);
        }
    }

    private void addOverlayToMapAtPosition(int position) {
        MapLayerDescriptor layerDesc = overlaysInZOrder.get(position);
        MapLayer layer = layersOnMap.get(layerDesc);
        if (layer == null) {
            MapDataResource resource = mapDataManager.getResources().get(layerDesc.getResourceUri());
            Class<? extends MapDataProvider> resourceType = Objects.requireNonNull(resource.getResolved()).getType();
            MapDataProvider provider = providers.get(resourceType);
            MapLayerAdapter layerAdapter = provider.createMapLayerAdapter(layerDesc, getMap());
            layer = new MapLayer(new BasicMapElementContainer());
            UpdateLayerMapElements loadLayerElements = (UpdateLayerMapElements) new UpdateLayerMapElements(
                layerDesc, layerAdapter, layer, ANYWHERE, this).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
            layersOnMap.put(layerDesc, new PendingLayer(loadLayerElements));
        }
        else {
            layer.show();
        }
    }

    private void onLayerComplete(UpdateLayerMapElements addLayer) {
        PendingLayer pending = (PendingLayer) layersOnMap.put(addLayer.layerDescriptor, addLayer.layer);
        if (pending.addLayer != addLayer) {
            throw new IllegalStateException("layer task for descriptor " + addLayer.layerDescriptor + " did not match expected pending layer task");
        }
    }

    // TODO: finish and use this
    private class PendingLayer extends MapLayer {

        private final UpdateLayerMapElements addLayer;

        private PendingLayer(UpdateLayerMapElements addLayer) {
            super(NO_ELEMENTS);
            this.addLayer = addLayer;
        }
    }

    private static final MapElements NO_ELEMENTS = new MapElements() {

        @Override
        public MapElements add(Circle x, Object id) {
            return this;
        }

        @Override
        public MapElements add(GroundOverlay x, Object id) {
            return this;
        }

        @Override
        public MapElements add(Marker x, Object id) {
            return this;
        }

        @Override
        public MapElements add(Polygon x, Object id) {
            return this;
        }

        @Override
        public MapElements add(Polyline x, Object id) {
            return this;
        }

        @Override
        public MapElements add(TileOverlay x, Object id) {
            return this;
        }

        @Override
        public boolean contains(Circle x) {
            return false;
        }

        @Override
        public boolean contains(GroundOverlay x) {
            return false;
        }

        @Override
        public boolean contains(Marker x) {
            return false;
        }

        @Override
        public boolean contains(Polygon x) {
            return false;
        }

        @Override
        public boolean contains(Polyline x) {
            return false;
        }

        @Override
        public boolean contains(TileOverlay x) {
            return false;
        }

        @Override
        public void remove(Circle x) {}

        @Override
        public void remove(GroundOverlay x) {}

        @Override
        public void remove(Marker x) {}

        @Override
        public void remove(Polygon x) {}

        @Override
        public void remove(Polyline x) {}

        @Override
        public void remove(TileOverlay x) {}

        @Override
        public void forEach(ComprehensiveMapElementVisitor... v) {}

        @Override
        public int count() {
            return 0;
        }
    };
}
