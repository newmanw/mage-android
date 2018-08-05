package mil.nga.giat.mage.map.cache;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;

import mil.nga.giat.mage.map.FileSystemTileProvider;
import mil.nga.giat.mage.map.MapCircleSpec;
import mil.nga.giat.mage.map.MapElementSpec;
import mil.nga.giat.mage.map.MapGroundOverlaySpec;
import mil.nga.giat.mage.map.MapMarkerSpec;
import mil.nga.giat.mage.map.MapPolygonSpec;
import mil.nga.giat.mage.map.MapPolylineSpec;
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

    @Nullable
    @Override
    public Callable<? extends MapLayerManager.MapLayerAdapter> createMapLayerAdapter(@NonNull MapLayerDescriptor layerDescriptor, @NonNull MapOwner mapOwner) {
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
        public void close() {

        }
    }

    static class LayerAdapter implements MapLayerManager.MapLayerAdapter {

        private final MapTileOverlaySpec tilesSpec;

        LayerAdapter(XYZDirectoryLayerDescriptor cache) {
            TileOverlayOptions overlayOptions = new TileOverlayOptions();
            overlayOptions.tileProvider(new FileSystemTileProvider(256, 256, cache.getDirectory().getAbsolutePath()));
            tilesSpec = new MapTileOverlaySpec(cache.getLayerUri(), null, overlayOptions);
        }

        @Override
        public void onLayerRemoved() {}

        @Override
        public Iterator<? extends MapElementSpec> elementsInBounds(LatLngBounds bounds) {
            return Collections.singleton(tilesSpec).iterator();
        }

        @Override
        public void addedToMap(@NotNull MapCircleSpec spec, @NotNull Circle x) {

        }

        @Override
        public void addedToMap(@NotNull MapGroundOverlaySpec spec, @NotNull GroundOverlay x) {

        }

        @Override
        public void addedToMap(@NotNull MapMarkerSpec spec, @NotNull Marker x) {

        }

        @Override
        public void addedToMap(@NotNull MapPolygonSpec spec, @NotNull Polygon x) {

        }

        @Override
        public void addedToMap(@NotNull MapPolylineSpec spec, @NotNull Polyline x) {

        }

        @Override
        public void addedToMap(@NotNull MapTileOverlaySpec spec, @NotNull TileOverlay x) {

        }
    }
}
