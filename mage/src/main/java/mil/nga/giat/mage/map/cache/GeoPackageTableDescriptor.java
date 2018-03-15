package mil.nga.giat.mage.map.cache;

import java.net.URI;

/**
 * This class is a {@link MapLayerDescriptor} subclass corresponding to the data
 * in a single table within a GeoPackage.
 *
 * @author osbornb
 */
public abstract class GeoPackageTableDescriptor extends MapLayerDescriptor {

    /**
     * the GeoPackage name
     */
    private final String geoPackage;
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
    GeoPackageTableDescriptor(URI resourceUri, String geoPackage, String tableName, int count, int minZoom, Integer maxZoom) {
        super(tableName, resourceUri, GeoPackageProvider.class);
        this.geoPackage = geoPackage;
        this.count = count;
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
    }

    /**
     * Get the GeoPackage name
     */
    String getGeoPackage() {
        return geoPackage;
    }

    /**
     * Get the name of the table that contains this overlay's data, which is also the {@link #getLayerName() layer name}.
     */
    String getTableName() {
        return getLayerName();
    }

    /**
     * Get the count
     */
    int getCount() {
        return count;
    }

    /**
     * Get the min zoom
     */
    int getMinZoom() {
        return minZoom;
    }

    /**
     * Get the max zoom
     */
    int getMaxZoom() {
        return maxZoom;
    }
}
