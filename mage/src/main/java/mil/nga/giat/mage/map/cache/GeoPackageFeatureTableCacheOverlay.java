package mil.nga.giat.mage.map.cache;

import java.util.List;

import mil.nga.giat.mage.R;

/**
 *
 * @author osbornb
 */
public class GeoPackageFeatureTableCacheOverlay extends GeoPackageTableCacheOverlay {

    /**
     * Max zoom for features
     */
    static final int MAX_ZOOM = 21;

    /**
     * Indexed flag, true when the feature table is indexed
     */
    private final boolean indexed;

    /**
     * Linked tile table cache overlays
     */
    private final List<GeoPackageTileTableCacheOverlay> linkedTiles;

    /**
     * Constructor
     *
     * @param geoPackage   GeoPackage name
     * @param tableName    GeoPackage table name
     * @param count        count
     * @param minZoom      min zoom level
     * @param indexed      indexed flag
     */
    GeoPackageFeatureTableCacheOverlay(String geoPackage, String tableName, int count, int minZoom, boolean indexed, List<GeoPackageTileTableCacheOverlay> linkedTiles) {
        super(geoPackage, tableName, count, minZoom, MAX_ZOOM);
        this.indexed = indexed;
        this.linkedTiles = linkedTiles;
    }

    @Override
    public Integer getIconImageResourceId() {
        return R.drawable.ic_timeline_gray_24dp;
    }

    @Override
    public String getInfo() {
        int minZoom = getMinZoom();
        int maxZoom = getMaxZoom();
        for(GeoPackageTileTableCacheOverlay linkedTileTable: linkedTiles){
            minZoom = Math.min(minZoom, linkedTileTable.getMinZoom());
            maxZoom = Math.max(maxZoom, linkedTileTable.getMaxZoom());
        }
        return "features: " + getCount() + ", zoom: " + minZoom + " - " + maxZoom;
    }

    /**
     * Determine if the feature table is indexed
     *
     * @return true if indexed
     */
    public boolean isIndexed() {
        return indexed;
    }

    /**
     * Get the linked tile table cache overlays
     *
     * @return linked tile table cache overlays
     */
    public List<GeoPackageTileTableCacheOverlay> getLinkedTileTables(){
        return linkedTiles;
    }
}
