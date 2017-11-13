package mil.nga.giat.mage.map.cache;

import mil.nga.giat.mage.R;

/**
 * GeoPackage Tile Table cache overlay
 *
 * @author osbornb
 */
public class GeoPackageTileTableCacheOverlay extends GeoPackageTableCacheOverlay {

    /**
     * Constructor
     *
     * @param geoPackage GeoPackage name
     * @param tableName  GeoPackage table name
     * @param count      count
     * @param minZoom    min zoom level
     * @param maxZoom    max zoom level
     */
    GeoPackageTileTableCacheOverlay(String geoPackage, String tableName, int count, int minZoom, int maxZoom) {
        super(geoPackage, tableName, count, minZoom, maxZoom);
    }

    @Override
    public Integer getIconImageResourceId() {
        return R.drawable.ic_layers_gray_24dp;
    }

    @Override
    public String getInfo() {
        return "tiles: " + getCount() + ", zoom: " + getMinZoom() + " - " + getMaxZoom();
    }
}
