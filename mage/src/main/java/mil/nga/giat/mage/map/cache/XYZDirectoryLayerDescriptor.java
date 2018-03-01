package mil.nga.giat.mage.map.cache;

import java.io.File;

import mil.nga.giat.mage.R;

/**
 * XYZ directory of tiles cache overlay
 *
 * @author osbornb
 */
public class XYZDirectoryLayerDescriptor extends MapLayerDescriptor {

    /**
     * Tile directory
     */
    private File directory;

    /**
     * Constructor
     *
     * @param cacheName cache name
     * @param directory tile directory
     */
    public XYZDirectoryLayerDescriptor(String overlayName, String cacheName, File directory) {
        super(overlayName, cacheName, XYZDirectoryProvider.class);
        this.directory = directory;
    }

    @Override
    public Integer getIconImageResourceId() {
        return R.drawable.ic_layers_gray_24dp;
    }

    File getDirectory() {
        return directory;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof XYZDirectoryLayerDescriptor && getDirectory().equals(((XYZDirectoryLayerDescriptor) other).getDirectory());
    }

    @Override
    public int hashCode() {
        return getDirectory().hashCode();
    }
}
