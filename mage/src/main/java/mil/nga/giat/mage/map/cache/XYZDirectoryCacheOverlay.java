package mil.nga.giat.mage.map.cache;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;

import java.io.File;

import mil.nga.giat.mage.R;
import mil.nga.giat.mage.map.FileSystemTileProvider;

/**
 * XYZ directory of tiles cache overlay
 *
 * @author osbornb
 */
public class XYZDirectoryCacheOverlay extends CacheOverlay {

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
    public XYZDirectoryCacheOverlay(String overlayName, String cacheName, File directory) {
        super(overlayName, cacheName, XYZDirectoryCacheProvider.class);
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
        return other instanceof XYZDirectoryCacheOverlay && getDirectory().equals(((XYZDirectoryCacheOverlay) other).getDirectory());
    }

    @Override
    public int hashCode() {
        return getDirectory().hashCode();
    }
}
