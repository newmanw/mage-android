package mil.nga.giat.mage.map.cache;

import android.support.annotation.Nullable;

/**
 * A <code>CacheOverlay</code> represents a cached data set which can appear on a map.
 * A {@link CacheProvider} implementation will create instances of its associated
 * <code>CacheOverlay</code> subclass.  Note that this class provides default
 * {@link #equals(Object)} and {@link #hashCode()} implementations because
 * {@link CacheManager} places <code>CacheOverlay</code> instances in sets and they
 * may also be used as {@link java.util.HashMap} keys.  Subclasses must take care
 * those methods work properly if overriding those or other methods on which
 * <code>equals()</code> and <code>hashCode()</code> depend.
 *
 * @author osbornb
 */
public abstract class CacheOverlay {

    /**
     * Name of this cache overlay
     */
    private final String overlayName;

    /**
     * The {@link MapCache#getName() name} of the cache that contains this overlay's data
     */
    private final String cacheName;

    /**
     * The {@link MapCache#getType() type} of the cache that contains this overlay's data
     */
    private final Class<? extends CacheProvider> cacheType;

    /**
     * Constructor
     * @param overlayName a unique, persistent name for the overlay
     */
    protected CacheOverlay(String overlayName, String cacheName, Class<? extends CacheProvider> cacheType) {
        this.overlayName = overlayName;
        this.cacheName = cacheName;
        this.cacheType = cacheType;
    }

    public String getOverlayName() {
        return overlayName;
    }

    /**
     * Return the name of the {@link MapCache#getName() cache} that contains this overlay.
     * @return a {@link MapCache} instance
     */
    public String getCacheName() {
        return cacheName;
    }

    /**
     * Return the {@link CacheProvider provider} type.  This just returns
     * the result of this cache overlay's comprising {@link #getCacheName() cache}.
     * @return the {@link CacheProvider} type
     */
    public Class<? extends CacheProvider> getCacheType() {
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
     * Two <code>CacheOverlay</code> instances are equal if they have the
     * same {@link #getOverlayName() name} and their comprising caches' {@link #getCacheName() name}
     * and {@link #getCacheType() type} are {@link MapCache#equals(Object) equal} as well.
     * @param obj
     * @return
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CacheOverlay)) {
            return false;
        }
        CacheOverlay other = (CacheOverlay)obj;
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

    public boolean isTypeOf(Class<? extends CacheProvider> providerType) {
        return providerType.isAssignableFrom(getCacheType());
    }
}