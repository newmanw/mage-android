package mil.nga.giat.mage.map.cache;

/**
 * This class is a {@link CacheOverlay} subclass corresponding to the data
 * in a single table within a GeoPackage.
 *
 * @author osbornb
 */
public abstract class GeoPackageTableCacheOverlay extends CacheOverlay {

    /**
     * Count of data in the table
     */
    private final int count;

    /**
     * Min zoom level of the data
     */
    private final int minZoom;

    /**
     * Max zoom level of the data
     */
    private final int maxZoom;

    /**
     * Constructor
     *
     * @param geoPackage GeoPackage name
     * @param tableName  GeoPackage table name
     * @param count      count
     * @param minZoom    min zoom level
     * @param maxZoom    max zoom level
     */
    GeoPackageTableCacheOverlay(String geoPackage, String tableName, int count, int minZoom, Integer maxZoom) {
        super(tableName, geoPackage, GeoPackageCacheProvider.class);
        this.count = count;
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
    }

    /**
     * Get the GeoPackage name, which is also the {@link #getCacheName() cache name}.
     *
     * @return
     */
    String getGeoPackage() {
        return getCacheName();
    }

    /**
     * Get the name of the table that contains this overlay's data, which is also the {@link #getOverlayName() overlay name}.
     * @return
     */
    String getTableName() {
        return getOverlayName();
    }

    /**
     * Get the count
     *
     * @return
     */
    int getCount() {
        return count;
    }

    /**
     * Get the min zoom
     *
     * @return
     */
    int getMinZoom() {
        return minZoom;
    }

    /**
     * Get the max zoom
     *
     * @return
     */
    int getMaxZoom() {
        return maxZoom;
    }
}
