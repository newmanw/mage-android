package mil.nga.giat.mage.map.cache;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.support.annotation.MainThread;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@code MapLayerManager} binds {@link MapLayerDescriptor layer data} from various
 * {@link MapDataRepository sources} to visual objects on a {@link GoogleMap}.
 */
@MainThread
public class MapLayerManager implements MapDataManager.MapDataListener {

    public interface MapLayerListener {

        /**
         * Notify the listener that the {@link MapDataManager} has updated the {@link #getOverlaysInZOrder() cache list}.
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
         * Add this overlay's objects to the map, e.g. {@link com.google.android.gms.maps.model.TileOverlay}s,
         * {@link com.google.android.gms.maps.model.Marker}s, {@link com.google.android.gms.maps.model.Polygon}s, etc.
         *
         */
        abstract protected void addToMap();

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

        /**
         * Return true if this overlay's {@link #addToMap() map objects} have been
         * created and added to the map, regardless of visibility.
         *
         * @return
         */
        abstract protected boolean isOnMap();
        abstract protected boolean isVisible();
        // TODO: this is awkward passing the map view and returning a string; probably can do better
        abstract protected String onMapClick(LatLng latLng, MapView mapView);

        /**
         * Clear all the resources this overlay might hold such as data source connections or large
         * geometry collections and prepare for garbage collection.
         */
        abstract protected void dispose();
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
     * {@link #getOverlaysInZOrder() z-order}.
     * @param count
     * @return
     */
    public static float zIndexStepForObjectCount(int count) {
        return 1.0f / (count + 1.0f);
    }

    private static String keyForCache(MapDataResource cache) {
        return cache.getResolved().getName() + ":" + cache.getResolved().getType().getName();
    }

    private static String keyForCache(MapLayerDescriptor overlay) {
        return overlay.getResourceUri() + ":" + overlay.getDataType().getName();
    }

    private final MapDataManager mapDataManager;
    private final GoogleMap map;
    private final Map<Class<? extends MapDataProvider>, MapDataProvider> providers = new HashMap<>();
    private final Map<MapLayerDescriptor, MapLayer> overlaysOnMap = new HashMap<>();
    private final List<MapLayerListener> listeners = new ArrayList<>();
    private List<MapLayerDescriptor> overlaysInZOrder = new ArrayList<>();

    public MapLayerManager(MapDataManager mapDataManager, List<MapDataProvider> providers, GoogleMap map) {
        this.mapDataManager = mapDataManager;
        this.map = map;
        for (MapDataProvider provider : providers) {
            this.providers.put(provider.getClass(), provider);
        }
        for (MapDataResource cache : mapDataManager.getResources()) {
            overlaysInZOrder.addAll(cache.getLayers().values());
        }
        mapDataManager.addUpdateListener(this);
    }

    void onMapDataUpdated(Set<MapLayerDescriptor> descriptors) {
        Map<String, MapLayerDescriptor> updateIndex = new HashMap<>(descriptors.size());
        for (MapLayerDescriptor desc : descriptors) {
            updateIndex.put(keyForCache(desc), desc);
        }
        int position = 0;
        Iterator<MapLayerDescriptor> orderIterator = overlaysInZOrder.iterator();
        while (orderIterator.hasNext()) {
            MapLayerDescriptor overlay = orderIterator.next();
            String cacheKey = keyForCache(overlay);
            MapLayerDescriptor updated = updateIndex.get(cacheKey);
            if (updated == null) {
                removeFromMapReturningVisibility(overlay);
                orderIterator.remove();
                position--;
            }
            else if (updated != overlay) {
                refreshOverlayAtPositionFromUpdatedCache(position, updated);
            }
            position++;
        }
    }

    @Override
    public void onMapDataUpdated(MapDataManager.MapDataUpdate update) {
        Set<String> removedCacheNames = new HashSet<>(update.getRemoved().size());
        for (MapDataResource removed : update.getRemoved()) {
			removedCacheNames.add(removed.getResolved().getName());
		}
		Map<String, Map<String, MapLayerDescriptor>> updatedCaches = new HashMap<>(update.getUpdated().size());
        for (MapDataResource cache : update.getUpdated()) {
            Map<String, MapLayerDescriptor> updatedOverlays = new HashMap<>(cache.getLayers());
            updatedCaches.put(keyForCache(cache), updatedOverlays);
        }

        int position = 0;
        Iterator<MapLayerDescriptor> orderIterator = overlaysInZOrder.iterator();
        while (orderIterator.hasNext()) {
            MapLayerDescriptor overlay = orderIterator.next();
            if (removedCacheNames.contains(overlay.getResourceUri())) {
                removeFromMapReturningVisibility(overlay);
                orderIterator.remove();
                position--;
            }
            else {
                String cacheKey = keyForCache(overlay);
                Map<String, MapLayerDescriptor> updatedCacheOverlays = updatedCaches.get(cacheKey);
                if (updatedCacheOverlays != null) {
                    MapLayerDescriptor updatedOverlay = updatedCacheOverlays.remove(overlay.getLayerName());
                    if (updatedOverlay != null) {
                        refreshOverlayAtPositionFromUpdatedCache(position, updatedOverlay);
                    }
                    else {
                        removeFromMapReturningVisibility(overlay);
                        orderIterator.remove();
                        position--;
                    }
                }
            }
            position++;
        }

        for (Map<String, MapLayerDescriptor> newOverlaysFromUpdatedCaches : updatedCaches.values()) {
            overlaysInZOrder.addAll(newOverlaysFromUpdatedCaches.values());
        }

        for (MapDataResource added : update.getAdded()) {
            overlaysInZOrder.addAll(added.getLayers().values());
        }

        for (MapLayerListener listener : listeners) {
		    listener.layersChanged();
        }
    }

    public void addOverlayOnMapListener(MapLayerListener x) {
        listeners.add(x);
    }

    public void removeOverlayOnMapListener(MapLayerListener x) {
        listeners.remove(x);
    }

    public GoogleMap getMap() {
        return map;
    }

    /**
     * Return a modifiable copy of the overlay list in z-order.  The last element
     * (index {@code size() - 1}) in the list is the top-most element.
     */
    public List<MapLayerDescriptor> getOverlaysInZOrder() {
        return new ArrayList<>(overlaysInZOrder);
    }

    public void showOverlay(MapLayerDescriptor layerDesc) {
        addOverlayToMap(layerDesc);
    }

    public void hideOverlay(MapLayerDescriptor layerDesc) {
        MapLayer onMap = overlaysOnMap.get(layerDesc);
        if (onMap == null || !onMap.isVisible()) {
            return;
        }
        onMap.hide();
    }

    public boolean isOverlayVisible(MapLayerDescriptor layerDesc) {
        MapLayer onMap = overlaysOnMap.get(layerDesc);
        return onMap != null && onMap.isVisible();
    }

    public void onMapClick(LatLng latLng, MapView mapView) {
        for (MapLayerDescriptor overlay : overlaysInZOrder) {
            MapLayer onMap = overlaysOnMap.get(overlay);
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
            MapLayer onMap = overlaysOnMap.get(overlay);
            if (onMap != null) {
                onMap.setZIndex(zIndex);
            }
            zIndex += 1;
        }
        return true;
    }

    public boolean moveZIndex(int fromPosition, int toPosition) {
        List<MapLayerDescriptor> order = getOverlaysInZOrder();
        MapLayerDescriptor target = order.remove(fromPosition);
        order.add(toPosition, target);
        return setZOrder(order);
    }

    public void dispose() {
        // TODO: remove and dispose all overlays/notify providers
        mapDataManager.removeUpdateListener(this);
        Iterator<Map.Entry<MapLayerDescriptor, MapLayer>> entries = overlaysOnMap.entrySet().iterator();
        while (entries.hasNext()) {
            MapLayer onMap = entries.next().getValue();
            onMap.removeFromMap();
            onMap.dispose();
            entries.remove();
        }
    }

    private boolean removeFromMapReturningVisibility(MapLayerDescriptor overlay) {
        boolean wasVisible = false;
        MapLayer onMap = overlaysOnMap.remove(overlay);
        if (onMap != null) {
            wasVisible = onMap.isVisible();
            onMap.removeFromMap();
        }
        return wasVisible;
    }

    private void refreshOverlayAtPositionFromUpdatedCache(int position, MapLayerDescriptor updatedOverlay) {
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
        MapLayerDescriptor overlay = overlaysInZOrder.get(position);
        MapLayer onMap = overlaysOnMap.remove(overlay);
        if (onMap == null) {
            // TODO: create a PendingLayer MapLayer implementation with a reference to the CreateLayer task
            MapDataProvider provider = providers.get(overlay.getDataType());
            new CreateLayerTask(overlay, provider, position).execute();
        }
        else {
            addAndShowLayer(overlay, onMap);
        }
    }

    private void addAndShowLayer(MapLayerDescriptor desc, MapLayer layer) {
        overlaysOnMap.put(desc, layer);
        if (!layer.isOnMap()) {
            layer.addToMap();
        }
        layer.show();
    }

    @SuppressLint("StaticFieldLeak")
    private class CreateLayerTask extends AsyncTask<Void, Void, MapLayer> {

        private final MapLayerDescriptor layerDesc;
        private final MapDataProvider provider;
        private final int zIndex;

        private CreateLayerTask(MapLayerDescriptor layerDesc, MapDataProvider provider, int zIndex) {
            this.layerDesc = layerDesc;
            this.provider = provider;
            this.zIndex = zIndex;
        }

        @Override
        protected MapLayer doInBackground(Void... nothing) {
            MapLayer layer = provider.createMapLayerFromDescriptor(layerDesc, MapLayerManager.this);
            layer.setZIndex(zIndex);
            return layer;
        }

        @Override
        protected void onPostExecute(MapLayer mapLayer) {
            addAndShowLayer(layerDesc, mapLayer);
        }
    }

    // TODO: finish and use this
    private class PendingLayer extends MapLayer {

        private final CreateLayerTask creating;

        private PendingLayer(MapLayerDescriptor desc, MapDataProvider provider, int zIndex) {
            this.creating = new CreateLayerTask(desc, provider, zIndex);
        }

        @Override
        protected void addToMap() {

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
        protected boolean isOnMap() {
            return false;
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
