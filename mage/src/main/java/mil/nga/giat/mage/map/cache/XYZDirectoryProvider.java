package mil.nga.giat.mage.map.cache;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.TileOverlayOptions;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;

import mil.nga.giat.mage.map.FileSystemTileProvider;
import mil.nga.giat.mage.map.MapElementSpec;
import mil.nga.giat.mage.map.MapTileOverlaySpec;
import mil.nga.giat.mage.map.view.MapOwner;

public class XYZDirectoryProvider implements MapDataProvider {

    @Override
    public boolean canHandleResource(@NonNull MapDataResource resource) {
        // TODO: bit more intelligent inspection of the directory contents
        if ("file".equalsIgnoreCase(resource.getUri().getScheme())) {
            return false;
        }
        File localPath = new File(resource.getUri());
        return localPath.isDirectory();
    }

    @NonNull
    @Override
    public MapDataResource resolveResource(@NonNull MapDataResource resource) throws MapDataResolveException {
        File xyzDir = new File(resource.getUri());
        if (!xyzDir.isDirectory()) {
            throw new MapDataResolveException(resource.getUri(), "resource is not a directory: " + resource.getUri());
        }
        MapLayerDescriptor desc = new XYZDirectoryLayerDescriptor(xyzDir);
        return resource.resolve(new MapDataResource.Resolved(xyzDir.getName(), getClass(), Collections.singleton(desc)), xyzDir.lastModified());
    }

    @NonNull
    @Override
    public LayerQuery createQueryForLayer(@NonNull MapLayerDescriptor layer) {
        return new Query((XYZDirectoryLayerDescriptor) layer);
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void deleteFile(File base) {
        if (base.isDirectory()) {
            for (File file : base.listFiles()) {
                deleteFile(file);
            }
        }
        base.delete();
    }

    static class Query implements MapDataProvider.LayerQuery {

        final Map<Object, MapElementSpec> elements;

        Query(XYZDirectoryLayerDescriptor layerDesc) {
            TileOverlayOptions overlayOptions = new TileOverlayOptions();
            overlayOptions.tileProvider(new FileSystemTileProvider(256, 256, layerDesc.getDirectory().getAbsolutePath()));
            MapTileOverlaySpec tilesSpec = new MapTileOverlaySpec(layerDesc.getLayerUri(), null, overlayOptions);
            elements = Collections.singletonMap(tilesSpec.getId(), tilesSpec);
        }

        @Override
        public boolean hasDynamicElements() {
            return false;
        }

        @Override
        public boolean supportsDynamicFetch() {
            return false;
        }

        @NonNull
        @Override
        public Map<Object, MapElementSpec> fetchMapElements(@NonNull LatLngBounds bounds) {
            return elements;
        }

        @Override
        public void close() {}
    }
}
