package mil.nga.giat.mage.map.cache;

import android.support.annotation.Nullable;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.TileOverlayOptions;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Callable;

import mil.nga.giat.mage.map.FileSystemTileProvider;
import mil.nga.giat.mage.map.MapElementSpec;

public class XYZDirectoryProvider implements MapDataProvider {

    static class LayerAdapter implements MapLayerManager.MapLayerAdapter {

        private final MapElementSpec.MapTileOverlaySpec tilesSpec;

        LayerAdapter(XYZDirectoryLayerDescriptor cache) {
            TileOverlayOptions overlayOptions = new TileOverlayOptions();
            overlayOptions.tileProvider(new FileSystemTileProvider(256, 256, cache.getDirectory().getAbsolutePath()));
            tilesSpec = new MapElementSpec.MapTileOverlaySpec(null, null, overlayOptions);
        }

        @Override
        public void onLayerRemoved() {}

        @Override
        public Iterator<? extends MapElementSpec> elementsInBounds(LatLngBounds bounds) {
            return Collections.singleton(tilesSpec).iterator();
        }
    }

    @Override
    public boolean canHandleResource(MapDataResource resource) {
        // TODO: bit more intelligent inspection of the directory contents
        if ("file".equalsIgnoreCase(resource.getUri().getScheme())) {
            return false;
        }
        File localPath = new File(resource.getUri());
        return localPath.isDirectory();
    }

    @Override
    public MapDataResource resolveResource(MapDataResource resource) throws MapDataResolveException {
        File xyzDir = new File(resource.getUri());
        if (!xyzDir.isDirectory()) {
            throw new MapDataResolveException(resource.getUri(), "resource is not a directory: " + resource.getUri());
        }
        MapLayerDescriptor desc = new XYZDirectoryLayerDescriptor(xyzDir);
        return resource.resolve(new MapDataResource.Resolved(xyzDir.getName(), getClass(), Collections.singleton(desc)), xyzDir.lastModified());
    }

    @Nullable
    @Override
    public Callable<? extends MapLayerManager.MapLayerAdapter> createMapLayerAdapter(MapLayerDescriptor layerDescriptor, GoogleMap map) {
        return () -> new LayerAdapter((XYZDirectoryLayerDescriptor) layerDescriptor);
    }

    /**
     * TODO: this was originally in TileOverlayPreferenceActivity - delete should be function of the provider
     */
    private void deleteXYZCacheOverlay(XYZDirectoryLayerDescriptor xyzCacheOverlay){
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
