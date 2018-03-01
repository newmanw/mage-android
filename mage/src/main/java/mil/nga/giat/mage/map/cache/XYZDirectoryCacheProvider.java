package mil.nga.giat.mage.map.cache;

import android.support.annotation.Nullable;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import mil.nga.giat.mage.map.FileSystemTileProvider;

public class XYZDirectoryCacheProvider implements CacheProvider {

    static class OnMap extends OverlayOnMapManager.OverlayOnMap {

        private final GoogleMap map;
        private final XYZDirectoryCacheOverlay cache;
        private final TileOverlayOptions overlayOptions;
        private TileOverlay overlay;

        OnMap(OverlayOnMapManager manager, XYZDirectoryCacheOverlay cache) {
            manager.super();
            this.map = manager.getMap();
            this.cache = cache;
            overlayOptions = new TileOverlayOptions();
            overlayOptions.tileProvider(new FileSystemTileProvider(256, 256, cache.getDirectory().getAbsolutePath()));
        }

        @Override
        public void addToMap() {
            overlay = map.addTileOverlay(overlayOptions);
        }

        @Override
        public void removeFromMap() {
            overlay.remove();
            overlay = null;
        }

        @Override
        public void zoomMapToBoundingBox() {
            // TODO
        }

        @Override
        public void show() {
            overlayOptions.visible(true);
            if (overlay != null) {
                overlay.setVisible(true);
            }
        }

        @Override
        public void hide() {
            overlayOptions.visible(false);
            if (overlay != null) {
                overlay.setVisible(false);
            }
        }

        @Override
        protected void setZIndex(int z) {
            overlayOptions.zIndex(z);
            if (overlay != null) {
                overlay.setZIndex(z);
            }
        }

        @Nullable
        @Override
        public String onMapClick(LatLng latLng, MapView mapView) {
            return null;
        }

        @Override
        public boolean isOnMap() {
            return overlay != null;
        }

        @Override
        public boolean isVisible() {
            return overlay == null ? overlayOptions.isVisible() : overlay.isVisible();
        }

        @Override
        protected void dispose() {
        }
    }

    @Override
    public boolean isCacheFile(URI resource) {
        if ("file".equalsIgnoreCase(resource.getScheme())) {
            return false;
        }
        File localPath = new File(resource);
        return localPath.isDirectory();
    }

    @Override
    public MapCache importCacheFromFile(URI resource) throws CacheImportException {
        File xyzDir = new File(resource);
        if (!xyzDir.isDirectory()) {
            throw new CacheImportException(resource, "resource is not a directory: " + resource);
        }
        Set<CacheOverlay> overlays = new HashSet<>();
        overlays.add(new XYZDirectoryCacheOverlay(xyzDir.getName(), xyzDir.getName(), xyzDir));
        return new MapCache(xyzDir.getName(), getClass(), resource, Collections.unmodifiableSet(overlays));
    }

    @Override
    public Set<MapCache> refreshCaches(Set<MapCache> existingCaches) {
        // TODO
        return Collections.emptySet();
    }

    @Override
    public OverlayOnMapManager.OverlayOnMap createOverlayOnMapFromCache(CacheOverlay cache, OverlayOnMapManager mapManager) {
        return new OnMap(mapManager, (XYZDirectoryCacheOverlay) cache);
    }

    /**
     * TODO: this was originally in TileOverlayPreferenceActivity - delete should be function of the provider
     */
    private void deleteXYZCacheOverlay(XYZDirectoryCacheOverlay xyzCacheOverlay){

        File directory = xyzCacheOverlay.getDirectory();

        if (directory.canWrite()) {
            deleteFile(directory);
        }
    }

    private void deleteFile(File base) {
        if (base.isDirectory()) {
            for (File file : base.listFiles()) {
                deleteFile(file);
            }
        }
        base.delete();
    }
}
