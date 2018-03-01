package mil.nga.giat.mage.map.cache;

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

@MainThread
public class OverlayOnMapManager implements MapDataManager.CacheOverlaysUpdateListener {

    public interface OverlayOnMapListener {

        /**
         * Notify the listener that the {@link MapDataManager} has updated the {@link #getOverlaysInZOrder() cache list}.
         * {@link OverlayOnMapManager} will not invoke this method as result of its own on-map interactions,
         * such as adding, removing, showing, and hiding overlays.
         */
        void overlaysChanged();
    }

    public abstract class OverlayOnMap {

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
         * TODO: change to MapLayerDescriptor.getBoundingBox() instead so OverlayOnMapManager can do the zoom
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
     * within a single integral zoom level.  This is a convenience for {@link CacheProvider}
     * implementations of {@link OverlayOnMap} to use when setting the integer
     * {@link OverlayOnMap#setZIndex(int) z-index}.  The implementation can use the
     * return value to increment the float z-index of {@code count} {@link GoogleMap} objects
     * logically contained in a single overlay.  For example, if an overlay contains 5 map
     * objects, such as {@link com.google.android.gms.maps.model.TileOverlay tiles} and
     * {@link com.google.android.gms.maps.model.Polygon polygons}, and the integer z-index
     * to set on the overlay is 6, this method will return 1 / (5 + 1), ~= 0.167, and map
     * objects can have fractional zoom levels (6 + 1 * 0.167), (6 + 2 * 0.167), etc.,
     * without intruding on the next integral zoom level, 7, which {@link OverlayOnMapManager}
     * will assign to the next {@link MapLayerDescriptor}/{@link OverlayOnMap} in the
     * {@link #getOverlaysInZOrder() z-order}.
     * @param count
     * @return
     */
    public static float zIndexStepForObjectCount(int count) {
        return 1.0f / (count + 1.0f);
    }

    private static String keyForCache(MapCache cache) {
        return cache.getName() + ":" + cache.getType().getName();
    }

    private static String keyForCache(MapLayerDescriptor overlay) {
        return overlay.getCacheName() + ":" + overlay.getCacheType().getName();
    }

    private final MapDataManager mapDataManager;
    private final GoogleMap map;
    private final Map<Class<? extends CacheProvider>, CacheProvider> providers = new HashMap<>();
    private final Map<MapLayerDescriptor, OverlayOnMap> overlaysOnMap = new HashMap<>();
    private final List<OverlayOnMapListener> listeners = new ArrayList<>();
    private List<MapLayerDescriptor> overlaysInZOrder = new ArrayList<>();

    public OverlayOnMapManager(MapDataManager mapDataManager, List<CacheProvider> providers, GoogleMap map) {
        this.mapDataManager = mapDataManager;
        this.map = map;
        for (CacheProvider provider : providers) {
            this.providers.put(provider.getClass(), provider);
        }
        for (MapCache cache : mapDataManager.getCaches()) {
            overlaysInZOrder.addAll(cache.getLayers().values());
        }
        mapDataManager.addUpdateListener(this);
    }

    @Override
    public void onCacheOverlaysUpdated(MapDataManager.CacheOverlayUpdate update) {
        Set<String> removedCacheNames = new HashSet<>(update.removed.size());
        for (MapCache removed : update.removed) {
			removedCacheNames.add(removed.getName());
		}
		Map<String, Map<String, MapLayerDescriptor>> updatedCaches = new HashMap<>(update.updated.size());
        for (MapCache cache : update.updated) {
            Map<String, MapLayerDescriptor> updatedOverlays = new HashMap<>(cache.getLayers());
            updatedCaches.put(keyForCache(cache), updatedOverlays);
        }

        int position = 0;
        Iterator<MapLayerDescriptor> orderIterator = overlaysInZOrder.iterator();
        while (orderIterator.hasNext()) {
            MapLayerDescriptor overlay = orderIterator.next();
            if (removedCacheNames.contains(overlay.getCacheName())) {
                removeFromMapReturningVisibility(overlay);
                orderIterator.remove();
                position--;
            }
            else {
                String cacheKey = keyForCache(overlay);
                Map<String, MapLayerDescriptor> updatedCacheOverlays = updatedCaches.get(cacheKey);
                if (updatedCacheOverlays != null) {
                    MapLayerDescriptor updatedOverlay = updatedCacheOverlays.remove(overlay.getOverlayName());
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

        for (MapCache added : update.added) {
            overlaysInZOrder.addAll(added.getLayers().values());
        }

        for (OverlayOnMapListener listener : listeners) {
		    listener.overlaysChanged();
        }
    }

    public void addOverlayOnMapListener(OverlayOnMapListener x) {
        listeners.add(x);
    }

    public void removeOverlayOnMapListener(OverlayOnMapListener x) {
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
        OverlayOnMap onMap = overlaysOnMap.get(layerDesc);
        if (onMap == null || !onMap.isVisible()) {
            return;
        }
        onMap.hide();
    }

    public boolean isOverlayVisible(MapLayerDescriptor layerDesc) {
        OverlayOnMap onMap = overlaysOnMap.get(layerDesc);
        return onMap != null && onMap.isVisible();
    }

    public void onMapClick(LatLng latLng, MapView mapView) {
        for (MapLayerDescriptor overlay : overlaysInZOrder) {
            OverlayOnMap onMap = overlaysOnMap.get(overlay);
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
            OverlayOnMap onMap = overlaysOnMap.get(overlay);
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
        Iterator<Map.Entry<MapLayerDescriptor, OverlayOnMap>> entries = overlaysOnMap.entrySet().iterator();
        while (entries.hasNext()) {
            OverlayOnMap onMap = entries.next().getValue();
            onMap.removeFromMap();
            onMap.dispose();
            entries.remove();
        }
    }

    private boolean removeFromMapReturningVisibility(MapLayerDescriptor overlay) {
        boolean wasVisible = false;
        OverlayOnMap onMap = overlaysOnMap.remove(overlay);
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
        OverlayOnMap onMap = overlaysOnMap.remove(overlay);
        if (onMap == null) {
            CacheProvider provider = providers.get(overlay.getCacheType());
            onMap = provider.createOverlayOnMapFromCache(overlay, this);
            onMap.setZIndex(position);
        }
        overlaysOnMap.put(overlay, onMap);
        if (!onMap.isOnMap()) {
            onMap.addToMap();
        }
        onMap.show();
    }
}
