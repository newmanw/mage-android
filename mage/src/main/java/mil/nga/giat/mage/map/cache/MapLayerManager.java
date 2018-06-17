package mil.nga.giat.mage.map.cache;

import android.os.AsyncTask;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import mil.nga.giat.mage.map.MapDisplayObject;

/**
 * A {@code MapLayerManager} binds {@link MapLayerDescriptor layer data} from various
 * {@link MapDataRepository sources} to visual objects on a {@link GoogleMap}.
 */
@MainThread
public class MapLayerManager implements MapDataManager.MapDataListener {


    /**
     * Load the data for the given layer and create the objects to display the layer data on a map.
     * Using {@link #publishProgress(Object[])}, subclasses can add {@link MapDisplayObject visual elements}
     * to the map as they are created, which should provide some more immediate visual results for the user
     * and prevent blocking the main thread when there are many objects to display after the layer construction
     * is complete.
     *
     * @param <T> the type of layer descriptor the subclass uses
     * @param <L> the type of layer the subclass produces
     */
    public static abstract class LoadLayerMapObjects<T extends MapLayerDescriptor, L extends MapLayer> extends AsyncTask<Void, MapDisplayObject, L> implements MapDisplayObject.MapOwner {

        protected final T layerDescriptor;
        protected final MapLayerManager mapLayerManager;

        protected LoadLayerMapObjects(T layerDescriptor, MapLayerManager mapLayerManager) {
            this.layerDescriptor = layerDescriptor;
            this.mapLayerManager = mapLayerManager;
        }

        protected abstract MapLayer prepareLayer(L layer);

        @NonNull
        @Override
        public final GoogleMap getMap() {
            return mapLayerManager.getMap();
        }

        @Override
        protected final void onProgressUpdate(MapDisplayObject... values) {
            for (MapDisplayObject o : values) {
                o.createFor(this);
            }
        }

        @Override
        protected final void onPostExecute(L layer) {
            MapLayer prepared = prepareLayer(layer);
            mapLayerManager.onLayerComplete(this, prepared);
        }
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
     * A {@code MapLayer} is the visual representation on a {@link GoogleMap} of the data
     * a {@link MapLayerDescriptor} describes.  A {@link MapDataProvider} creates instances
     * of this class from the data it provides to be added to a map.  Instances of this
     * class comprise visual objects from the Google Maps API, such as
     * {@link com.google.android.gms.maps.model.TileProvider tiles},
     * {@link com.google.android.gms.maps.model.Marker markers},
     * {@link com.google.android.gms.maps.model.Polygon polygons},
     * etc.
     */
    public abstract class MapLayer {

        /**
         * Remove this overlay's objects visible and hidden objects from the map.
         */
        abstract protected void removeFromMap();
        abstract protected void show();
        abstract protected void hide();
        abstract protected void setZIndex(int z);

        /**
         * TODO: change to MapLayerDescriptor.getBoundingBox() instead so MapLayerManager can do the zoom
         */
        abstract protected void zoomMapToBoundingBox();

        abstract protected boolean isVisible();
        // TODO: this is awkward passing the map view and returning a string; probably can do better
        abstract protected String onMapClick(LatLng latLng, MapView mapView);

        /**
         * Clear all the resources this overlay might hold such as data source connections or large
         * geometry collections and prepare for garbage collection.
         */
        abstract protected void dispose();

        protected final GoogleMap getMap() {
            return MapLayerManager.this.getMap();
        }
    }

    /**
     * Compute the fractional step necessary to stack {@code count} map objects
     * within a single integral zoom level.  This is a convenience for {@link MapDataProvider}
     * implementations of {@link MapLayer} to use when setting the integer
     * {@link MapLayer#setZIndex(int) z-index}.  The implementation can use the
     * return value to increment the float z-index of {@code count} {@link GoogleMap} objects
     * logically contained in a single overlay.  For example, if an overlay contains 5 map
     * objects, such as {@link com.google.android.gms.maps.model.TileOverlay tiles} and
     * {@link com.google.android.gms.maps.model.Polygon polygons}, and the integer z-index
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

    public void onMapClick(LatLng latLng, MapView mapView) {
        for (MapLayerDescriptor overlay : overlaysInZOrder) {
            MapLayer onMap = layersOnMap.get(overlay);
            if (onMap != null) {
                onMap.onMapClick(latLng, mapView);
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
            onMap.dispose();
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

    private void disposeOverlay(MapLayerDescriptor overlay) {

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
            // TODO: create a PendingLayer MapLayer implementation with a reference to the CreateLayer task
            MapDataResource resource = mapDataManager.getResources().get(layerDesc.getResourceUri());
            Class<? extends MapDataProvider> resourceType = resource.getResolved().getType();
            MapDataProvider provider = providers.get(resourceType);
            LoadLayerMapObjects addLayer = (LoadLayerMapObjects) provider.createMapLayerFromDescriptor(layerDesc, this).execute();
            layersOnMap.put(layerDesc, new PendingLayer(addLayer));
        }
        else {
            layer.show();
        }
    }

    private void onLayerComplete(LoadLayerMapObjects addLayer, MapLayer layer) {
        PendingLayer pending = (PendingLayer) layersOnMap.put(addLayer.layerDescriptor, layer);
        if (pending.addLayer != addLayer) {
            throw new IllegalStateException("layer task for descriptor " + addLayer.layerDescriptor + " did not match expected pending layer task");
        }
    }

    // TODO: finish and use this
    private class PendingLayer extends MapLayer {

        private final LoadLayerMapObjects addLayer;

        private PendingLayer(LoadLayerMapObjects addLayer) {
            this.addLayer = addLayer;
        }

        @Override
        protected void removeFromMap() {

        }

        @Override
        protected void show() {

        }

        @Override
        protected void hide() {

        }

        @Override
        protected void setZIndex(int z) {

        }

        @Override
        protected void zoomMapToBoundingBox() {

        }

        @Override
        protected boolean isVisible() {
            return false;
        }

        @Override
        protected String onMapClick(LatLng latLng, MapView mapView) {
            return null;
        }

        @Override
        protected void dispose() {

        }
    }
}
