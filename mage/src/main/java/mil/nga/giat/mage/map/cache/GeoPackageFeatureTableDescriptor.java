package mil.nga.giat.mage.map.cache;

import java.util.List;

import mil.nga.giat.mage.R;

/**
 *
 * @author osbornb
 */
public class GeoPackageFeatureTableDescriptor extends GeoPackageTableDescriptor {

    /**
     * Max zoom for features
     */
    static final int MAX_ZOOM = 21;

    private final boolean indexed;

    private final List<GeoPackageTileTableDescriptor> linkedTiles;

    GeoPackageFeatureTableDescriptor(String geoPackage, String tableName, int count, int minZoom, boolean indexed, List<GeoPackageTileTableDescriptor> linkedTiles) {
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
        for(GeoPackageTileTableDescriptor linkedTileTable: linkedTiles){
            minZoom = Math.min(minZoom, linkedTileTable.getMinZoom());
            maxZoom = Math.max(maxZoom, linkedTileTable.getMaxZoom());
        }
        return "features: " + getCount() + ", zoom: " + minZoom + " - " + maxZoom;
    }

    boolean isIndexed() {
        return indexed;
    }

    List<GeoPackageTileTableDescriptor> getLinkedTileTables(){
        return linkedTiles;
    }
}
