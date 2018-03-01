package mil.nga.giat.mage.map.cache;

import com.google.android.gms.maps.model.LatLngBounds;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class MapCache {

    private final String name;
    private final Class<? extends CacheProvider> type;
    private final URI sourceFile;
    private final Map<String, CacheOverlay> overlays;
    private long refreshTimestamp;


    public MapCache(String name, Class<? extends CacheProvider> type, URI resource, Set<CacheOverlay> overlays) {
        this.name = name;
        this.type = type;
        this.sourceFile = resource;
        Map<String, CacheOverlay> overlayMap = new HashMap<>(overlays.size());
        for (CacheOverlay overlay : overlays) {
            overlayMap.put(overlay.getOverlayName(), overlay);
        }
        this.overlays = Collections.unmodifiableMap(overlayMap);
        updateRefreshTimestamp();
    }

    public String getName() {
        return name;
    }

    public Class<? extends CacheProvider> getType() {
        return type;
    }

    public URI getSourceFile() {
        return sourceFile;
    }

    /**
     * Return a map of {@link CacheOverlay overlays} keyed by their {@link CacheOverlay#getOverlayName() names}.
     * @return
     */
    public Map<String, CacheOverlay> getCacheOverlays() {
        return overlays;
    }

    public long getRefreshTimestamp() {
        return refreshTimestamp;
    }

    public void updateRefreshTimestamp() {
        this.refreshTimestamp = System.currentTimeMillis();
    }

    public LatLngBounds getBounds() {
        return null;
    }

    /**
     * Two {@link MapCache} objects are equal if and only if their {@link #getType() types}
     * ane {@link #getName() names} are equal.
     * @param obj
     * @return
     */
    public boolean equals(Object obj) {
        if (!(obj instanceof MapCache)) {
            return false;
        }
        MapCache other = (MapCache) obj;
        return getType().equals(other.getType()) && getName().equals(other.getName());
    }

    public int hashCode() {
        return getName().hashCode();
    }

    public String toString() {
        return getName() + ":" + getType().getSimpleName();
    }
}
