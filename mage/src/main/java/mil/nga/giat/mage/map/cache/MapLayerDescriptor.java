package mil.nga.giat.mage.map.cache;

import android.support.annotation.Nullable;

/**
 * A <code>MapLayerDescriptor</code> represents a cached data set which can appear on a map.
 * A {@link MapDataProvider} implementation will create instances of its associated
 * <code>MapLayerDescriptor</code> subclass.  Note that this class provides default
 * {@link #equals(Object)} and {@link #hashCode()} implementations because
 * {@link MapDataManager} places <code>MapLayerDescriptor</code> instances in sets and they
 * may also be used as {@link java.util.HashMap} keys.  Subclasses must take care
 * those methods work properly if overriding those or other methods on which
 * <code>equals()</code> and <code>hashCode()</code> depend.
 *
 * @author osbornb
 */
public abstract class MapLayerDescriptor {

    /**
     * Name of this cache overlay
     */
    private final String overlayName;

    /**
     * The {@link MapDataResource#getName() name} of the cache that contains this overlay's data
     */
    private final String cacheName;

    /**
     * The {@link MapDataResource#getType() type} of the cache that contains this overlay's data
     */
    private final Class<? extends MapDataProvider> cacheType;

    /**
     * Constructor
     * @param overlayName a unique, persistent name for the overlay
     */
    protected MapLayerDescriptor(String overlayName, String cacheName, Class<? extends MapDataProvider> cacheType) {
        this.overlayName = overlayName;
        this.cacheName = cacheName;
        this.cacheType = cacheType;
    }

    public String getOverlayName() {
        return overlayName;
    }

    /**
     * Return the name of the {@link MapDataResource#getName() cache} that contains this overlay.
     * @return a {@link MapDataResource} instance
     */
    public String getCacheName() {
        return cacheName;
    }

    /**
     * Return the {@link MapDataProvider provider} type.  This just returns
     * the result of this cache overlay's comprising {@link #getCacheName() cache}.
     * @return the {@link MapDataProvider} type
     */
    public Class<? extends MapDataProvider> getCacheType() {
        return cacheType;
    }

    /**
     * Get the icon image resource id for the cacheName
     * @return a {@link android.content.res.Resources resource} ID or null
     */
    @Nullable
    public Integer getIconImageResourceId() {
        return null;
    }

    /**
     * Get information about the cacheName to display
     * @return an info string or null
     */
    @Nullable
    public String getInfo() {
        return null;
    }

    /**
     * Two <code>MapLayerDescriptor</code> instances are equal if they have the
     * same {@link #getOverlayName() name} and their comprising caches' {@link #getCacheName() name}
     * and {@link #getCacheType() type} are {@link MapDataResource#equals(Object) equal} as well.
     * @param obj
     * @return
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MapLayerDescriptor)) {
            return false;
        }
        MapLayerDescriptor other = (MapLayerDescriptor)obj;
        return
            getCacheType().equals(other.getCacheType()) &&
            getCacheName().equals(other.getCacheName()) &&
            getOverlayName().equals(other.getOverlayName());
    }

    @Override
    public int hashCode() {
        return getOverlayName().hashCode();
    }

    @Override
    public String toString() {
        return getCacheName() + ":" + getOverlayName() + "(" + getCacheType() + ")";
    }

    public boolean isTypeOf(Class<? extends MapDataProvider> providerType) {
        return providerType.isAssignableFrom(getCacheType());
    }
}