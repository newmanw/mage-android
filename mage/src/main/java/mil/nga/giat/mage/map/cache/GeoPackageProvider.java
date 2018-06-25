package mil.nga.giat.mage.map.cache;

import android.app.Application;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.view.View;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import mil.nga.geopackage.BoundingBox;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageCache;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.extension.link.FeatureTileTableLinker;
import mil.nga.geopackage.factory.GeoPackageFactory;
import mil.nga.geopackage.features.index.FeatureIndexManager;
import mil.nga.geopackage.features.user.FeatureCursor;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.geopackage.map.geom.GoogleMapShapeConverter;
import mil.nga.geopackage.map.tiles.overlay.BoundedOverlay;
import mil.nga.geopackage.map.tiles.overlay.FeatureOverlay;
import mil.nga.geopackage.map.tiles.overlay.FeatureOverlayQuery;
import mil.nga.geopackage.map.tiles.overlay.GeoPackageOverlayFactory;
import mil.nga.geopackage.projection.Projection;
import mil.nga.geopackage.tiles.features.DefaultFeatureTiles;
import mil.nga.geopackage.tiles.features.FeatureTiles;
import mil.nga.geopackage.tiles.features.custom.NumberFeaturesTile;
import mil.nga.geopackage.tiles.user.TileDao;
import mil.nga.geopackage.validate.GeoPackageValidate;
import mil.nga.giat.mage.R;
import mil.nga.giat.mage.map.MapElementSpec;
import mil.nga.giat.mage.map.WKBGeometryTransforms;
import mil.nga.giat.mage.sdk.utils.MediaUtility;
import mil.nga.wkb.geom.Geometry;
import mil.nga.wkb.geom.GeometryType;

public class GeoPackageProvider implements MapDataProvider {

    private static final String LOG_NAME = GeoPackageProvider.class.getName();
    private static final float Z_INDEX_TILE_TABLE = -2.0f;
    private static final float Z_INDEX_FEATURE_TABLE = -1.0f;

    /**
     * Get a cache name for the cache file
     */
    private static String makeUniqueCacheName(GeoPackageManager manager, File cacheFile) {
        String cacheName = MediaUtility.getFileNameWithoutExtension(cacheFile);
        final String baseCacheName = cacheName;
        int nameCount = 0;
        while (manager.exists(cacheName)) {
            cacheName = baseCacheName + "_" + (++nameCount);
        }
        return cacheName;
    }


    private final Application context;
    private final GeoPackageManager geoPackageManager;
    private final GeoPackageCache geoPackageCache;

    public GeoPackageProvider(Application context) {
        this.context = context;
        geoPackageManager = GeoPackageFactory.getManager(context);
        geoPackageCache = new GeoPackageCache(geoPackageManager);
    }

    @Override
    public boolean canHandleResource(MapDataResource resource) {
        return "file".equalsIgnoreCase(resource.getUri().getScheme()) &&
            GeoPackageValidate.hasGeoPackageExtension(new File(resource.getUri()));
    }

    @Override
    public MapDataResource resolveResource(MapDataResource resource) throws MapDataResolveException {
        File cacheFile = new File(resource.getUri());
        String resourceName = getOrImportGeoPackageDatabase(cacheFile);
        return createCache(resource, resourceName);
    }

    @Nullable
    @Override
    public Callable<? extends MapLayerManager.MapLayerAdapter> createMapLayerAdapter(MapLayerDescriptor layerDescriptor, GoogleMap map) {
        if (layerDescriptor instanceof GeoPackageTileTableDescriptor) {
            return () -> createTileTableAdapter((GeoPackageTileTableDescriptor) layerDescriptor, map);
        }
        else if (layerDescriptor instanceof GeoPackageFeatureTableDescriptor) {
            return () -> createFeatureTableAdapter((GeoPackageFeatureTableDescriptor) layerDescriptor, map);
        }
        throw new IllegalArgumentException(getClass().getSimpleName() +
            " does not support " + layerDescriptor + " of type " + layerDescriptor.getDataType() );
    }

      // TODO: this is now in MapDataRepository - are we misisng anything moving it there?
      // the repository should know when its resource have changed and need to be resolved
      // again by the provider
//    public Set<MapDataResource> refreshResources(Map<URI, MapDataResource> existingResources) {
//        Set<MapDataResource> refreshed = new HashSet<>(existingResources.size());
//        for (MapDataResource cache : existingResources.values()) {
//            File dbFile = geoPackageManager.getFile(cache.getResolved().getName());
//            if (!dbFile.exists() || !dbFile.canRead()) {
//                cache = null;
//            }
//            else if (dbFile.lastModified() > cache.getRefreshTimestamp()) {
//                cache = createCache(cache, cache.getResolved().getName());
//            }
//            else {
//                cache.updateRefreshTimestamp();
//            }
//
//            if (cache != null) {
//                refreshed.add(cache);
//            }
//        }
//
//        return refreshed;

        // TODO: test getting rid of this in favor of above to keep records of
        // unavailable databases along with a persistent database name that
        // can be stored in preferences to persist z-order.  otherwise, there's
        // no guarantee that the database/cache name will be the same across
        // different imports because of makeUniqueCacheName() above
//        Set<MapLayerDescriptor> overlays = new HashSet<>();
//        geoPackageManager.deleteAllMissingExternal();
//        List<String> externalDatabases = geoPackageManager.externalDatabases();
//        for (String database : externalDatabases) {
//            GeoPackageCacheOverlay cacheOverlay = createCache(database);
//            if (cacheOverlay != null) {
//                overlays.add(cacheOverlay);
//            }
//        }
//        return overlays;
//    }

    @NonNull
    private String getOrImportGeoPackageDatabase(File cacheFile) throws MapDataResolveException {
        String databaseName = geoPackageManager.getDatabaseAtExternalFile(cacheFile);
        if (databaseName != null) {
            return databaseName;
        }

        databaseName = makeUniqueCacheName(geoPackageManager, cacheFile);
        MapDataResolveException fail;
        try {
            // import the GeoPackage as a linked file
            if (geoPackageManager.importGeoPackageAsExternalLink(cacheFile, databaseName)) {
                return databaseName;
            }
            fail = new MapDataResolveException(cacheFile.toURI(), "GeoPackage import failed: " + cacheFile.getName());
        }
        catch (Exception e) {
            Log.e(LOG_NAME, "Failed to import file as GeoPackage. path: " + cacheFile.getAbsolutePath() + ", name: " + databaseName + ", error: " + e.getMessage());
            fail = new MapDataResolveException(cacheFile.toURI(), "GeoPackage import threw exception", e);
        }

        if (cacheFile.canWrite()) {
            try {
                if (!cacheFile.delete()) {
                    throw new RuntimeException("delete returned non-excepion failure");
                }
            }
            catch (Exception deleteException) {
                Log.e(LOG_NAME, "failed to delete file: " + cacheFile.getAbsolutePath() + ", error: " + deleteException.getMessage());
            }
        }

        throw fail;
    }

    private MapDataResource createCache(MapDataResource resource, String database) {

        GeoPackage geoPackage = null;

        // Add the GeoPackage overlay
        try {
            geoPackage = geoPackageManager.open(database);
            Set<MapLayerDescriptor> tables = new HashSet<>();

            // GeoPackage tile tables, build a mapping between table name and the created cache overlays
            Map<String, GeoPackageTileTableDescriptor> tileCacheOverlays = new HashMap<>();
            List<String> tileTables = geoPackage.getTileTables();
            for (String tableName : tileTables) {
                TileDao tileDao = geoPackage.getTileDao(tableName);
                int count = tileDao.count();
                int minZoom = (int) tileDao.getMinZoom();
                int maxZoom = (int) tileDao.getMaxZoom();
                GeoPackageTileTableDescriptor tableCache = new GeoPackageTileTableDescriptor(resource.getUri(), database, tableName, count, minZoom, maxZoom);
                tileCacheOverlays.put(tableName, tableCache);
            }

            // Get a linker to find tile tables linked to features
            FeatureTileTableLinker linker = new FeatureTileTableLinker(geoPackage);
            Map<String, GeoPackageTileTableDescriptor> linkedTileCacheOverlays = new HashMap<>();

            // GeoPackage feature tables
            List<String> featureTables = geoPackage.getFeatureTables();
            for (String tableName : featureTables) {
                FeatureDao featureDao = geoPackage.getFeatureDao(tableName);
                int count = featureDao.count();
                FeatureIndexManager indexer = new FeatureIndexManager(context, geoPackage, featureDao);
                boolean indexed = indexer.isIndexed();
                List<GeoPackageTileTableDescriptor> linkedTileTableCaches = new ArrayList<>();
                int minZoom = 0;
                if (indexed) {
                    minZoom = featureDao.getZoomLevel() + context.getResources().getInteger(R.integer.geopackage_feature_tiles_min_zoom_offset);
                    minZoom = Math.max(minZoom, 0);
                    minZoom = Math.min(minZoom, GeoPackageFeatureTableDescriptor.MAX_ZOOM);
                    List<String> linkedTileTables = linker.getTileTablesForFeatureTable(tableName);
                    for (String linkedTileTable : linkedTileTables) {
                        // Get the tile table cache overlay
                        GeoPackageTileTableDescriptor tileCacheOverlay = tileCacheOverlays.get(linkedTileTable);
                        if (tileCacheOverlay != null) {
                            // Remove from tile cache overlays so the tile table is not added as stand alone, and add to the linked overlays
                            tileCacheOverlays.remove(linkedTileTable);
                            linkedTileCacheOverlays.put(linkedTileTable, tileCacheOverlay);
                        }
                        else {
                            // Another feature table may already be linked to this table, so check the linked overlays
                            tileCacheOverlay = linkedTileCacheOverlays.get(linkedTileTable);
                        }

                        if (tileCacheOverlay != null) {
                            linkedTileTableCaches.add(tileCacheOverlay);
                        }
                    }
                }

                GeoPackageFeatureTableDescriptor tableCache = new GeoPackageFeatureTableDescriptor(
                    resource.getUri(), database, tableName, count, minZoom, indexed, linkedTileTableCaches);
                tables.add(tableCache);
            }

            // Add stand alone tile tables that were not linked to feature tables
            tables.addAll(tileCacheOverlays.values());

            return resource.resolve(new MapDataResource.Resolved(database, this.getClass(), tables));
        }
        catch (Exception e) {
            Log.e(LOG_NAME, "error creating GeoPackage cache", e);
        }
        finally {
            if (geoPackage != null) {
                geoPackage.close();
            }
        }

        return null;
    }

    @WorkerThread
    private TileTableLayer createTileTableAdapter(GeoPackageTileTableDescriptor layerDesc, GoogleMap map) {
        GeoPackage geoPackage = geoPackageCache.getOrOpen(layerDesc.getGeoPackage());
        TileDao tileDao = geoPackage.getTileDao(layerDesc.getTableName());
        BoundedOverlay geoPackageTileProvider = GeoPackageOverlayFactory.getBoundedOverlay(tileDao);
        TileOverlayOptions overlayOptions = new TileOverlayOptions()
            .tileProvider(geoPackageTileProvider)
            .zIndex(Z_INDEX_TILE_TABLE);
        // Check for linked feature tables
        FeatureTileTableLinker linker = new FeatureTileTableLinker(geoPackage);
        List<FeatureOverlayQuery> queries = new ArrayList<>();
        List<FeatureDao> featureDaos = linker.getFeatureDaosForTileTable(tileDao.getTableName());
        for (FeatureDao featureDao : featureDaos) {
            FeatureTiles featureTiles = new DefaultFeatureTiles(context, featureDao);
            FeatureIndexManager indexer = new FeatureIndexManager(context, geoPackage, featureDao);
            featureTiles.setIndexManager(indexer);
            FeatureOverlayQuery featureOverlayQuery = new FeatureOverlayQuery(context, geoPackageTileProvider, featureTiles);
            queries.add(featureOverlayQuery);
        }
        return new TileTableLayer(new MapElementSpec.MapTileOverlaySpec(layerDesc, null, overlayOptions), queries, map);
    }

    @WorkerThread
    private FeatureTableLayer createFeatureTableAdapter(GeoPackageFeatureTableDescriptor layerDesc, GoogleMap map) {
        Map<GeoPackageTileTableDescriptor, TileTableLayer> linkedTiles = new HashMap<>(layerDesc.getLinkedTileTables().size());
        for (GeoPackageTileTableDescriptor linkedTileTable : layerDesc.getLinkedTileTables()) {
            TileTableLayer tileAdapter = createTileTableAdapter(linkedTileTable, map);
            linkedTiles.put(linkedTileTable, tileAdapter);
        }
        GeoPackage geoPackage = geoPackageCache.getOrOpen(layerDesc.getGeoPackage());
        // Add the features to the map
        FeatureDao featureDao = geoPackage.getFeatureDao(layerDesc.getTableName());
        // If indexed, add as a tile overlay
        if (layerDesc.isIndexed()) {
            FeatureTiles featureTiles = new DefaultFeatureTiles(context, featureDao);
            Integer maxFeaturesPerTile;
            if (featureDao.getGeometryType() == GeometryType.POINT) {
                maxFeaturesPerTile = context.getResources().getInteger(R.integer.geopackage_feature_tiles_max_points_per_tile);
            }
            else {
                maxFeaturesPerTile = context.getResources().getInteger(R.integer.geopackage_feature_tiles_max_features_per_tile);
            }
            featureTiles.setMaxFeaturesPerTile(maxFeaturesPerTile);
            NumberFeaturesTile numberFeaturesTile = new NumberFeaturesTile(context);
            // Adjust the max features number tile draw paint attributes here as needed to
            // change how tiles are drawn when more than the max features exist in a tile
            featureTiles.setMaxFeaturesTileDraw(numberFeaturesTile);
            featureTiles.setIndexManager(new FeatureIndexManager(context, geoPackage, featureDao));
            // Adjust the feature tiles draw paint attributes here as needed to change how
            // features are drawn on tiles
            FeatureOverlay tileProvider = new FeatureOverlay(featureTiles);
            tileProvider.setMinZoom(layerDesc.getMinZoom());
            FeatureTileTableLinker linker = new FeatureTileTableLinker(geoPackage);
            List<TileDao> tileDaos = linker.getTileDaosForFeatureTable(featureDao.getTableName());
            tileProvider.ignoreTileDaos(tileDaos);
            TileOverlayOptions overlayOptions = new TileOverlayOptions()
                .zIndex(Z_INDEX_FEATURE_TABLE)
                .tileProvider(tileProvider);
            FeatureOverlayQuery featureQuery = new FeatureOverlayQuery(context, tileProvider);
            return new FeatureTableLayer(new MapElementSpec.MapTileOverlaySpec(null, null, overlayOptions), featureQuery, linkedTiles, map);
        }
        return new FeatureTableLayer(featureDao, linkedTiles, map);
    }

    private class TileTableLayer implements MapLayerManager.MapLayerAdapter {

        private final MapElementSpec.MapTileOverlaySpec tileOverlaySpec;
        private final List<FeatureOverlayQuery> queries;
        private final GoogleMap map;

        private TileTableLayer(MapElementSpec.MapTileOverlaySpec tileOverlaySpec, List<FeatureOverlayQuery> queries, GoogleMap map) {
            this.tileOverlaySpec = tileOverlaySpec;
            this.queries = queries;
            this.map = map;
        }

        @Override
        public Callable<String> onClick(LatLng latLng, View mapView) {
            StringBuilder message = new StringBuilder();
            for(FeatureOverlayQuery featureOverlayQuery : queries) {
                String overlayMessage = featureOverlayQuery.buildMapClickMessage(latLng, mapView, map);
                if (overlayMessage != null) {
                    if (message.length() > 0) {
                        message.append("\n\n");
                    }
                    message.append(overlayMessage);
                }
            }
            return message.length() > 0 ? message::toString : null;
        }

        void clearFeatureOverlayQueries(){
            Iterator<FeatureOverlayQuery> queryIter = queries.iterator();
            while (queryIter.hasNext()) {
                FeatureOverlayQuery query = queryIter.next();
                queryIter.remove();
                query.close();
            }
        }

        @Override
        public void onLayerRemoved() {
            clearFeatureOverlayQueries();
        }

        @Override
        public Iterator<? extends MapElementSpec> elementsInBounds(LatLngBounds bounds) {
            return Collections.singleton(tileOverlaySpec).iterator();
        }
    }

    private class FeatureTableLayer implements MapLayerManager.MapLayerAdapter {

        private final MapElementSpec.MapTileOverlaySpec indexedFeatureTiles;
        private final FeatureOverlayQuery indexedFeatureQuery;
        private final FeatureDao featureDao;
        private final Map<GeoPackageTileTableDescriptor, TileTableLayer> linkedTiles;
        private final GoogleMap map;

        private FeatureTableLayer(MapElementSpec.MapTileOverlaySpec indexedFeatureTiles, FeatureOverlayQuery indexedFeatureQuery, Map<GeoPackageTileTableDescriptor, TileTableLayer> linkedTiles, GoogleMap map) {
            this.indexedFeatureTiles = indexedFeatureTiles;
            this.indexedFeatureQuery = indexedFeatureQuery;
            this.linkedTiles = linkedTiles;
            this.map = map;
            featureDao = null;
        }

        private FeatureTableLayer(FeatureDao featureDao, Map<GeoPackageTileTableDescriptor, TileTableLayer> linkedTiles, GoogleMap map) {
            this.featureDao = featureDao;
            this.linkedTiles = linkedTiles;
            this.map = map;
            indexedFeatureTiles = null;
            indexedFeatureQuery = null;
        }

        @Override
        public void addedToMap(MapElementSpec.MapTileOverlaySpec spec, TileOverlay x) {
            if (spec.id instanceof GeoPackageTileTableDescriptor) {
                GeoPackageTileTableDescriptor tileDesc = (GeoPackageTileTableDescriptor) spec.id;
                TileTableLayer tileLayer = linkedTiles.get(tileDesc);
                tileLayer.addedToMap(spec, x);
            }
        }

        // TODO: implement click handlers

        @Override
        public Callable<String> onClick(Circle x, Object id) {
            return null;
        }

        @Override
        public Callable<String> onClick(GroundOverlay x, Object id) {
            return null;
        }

        @Override
        public Callable<String> onClick(Marker x, Object id) {
            return null;
        }

        @Override
        public Callable<String> onClick(Polygon x, Object id) {
            return null;
        }

        @Override
        public Callable<String> onClick(Polyline x, Object id) {
            return null;
        }

        @Override
        public Callable<String> onClick(LatLng latLng, View mapView) {
            if (indexedFeatureQuery == null) {
                return null;
            }
            float zoom = map.getCameraPosition().zoom;
            LatLngBounds mapBounds = map.getProjection().getVisibleRegion().latLngBounds;
            BoundingBox bbox = new BoundingBox(
                mapBounds.southwest.longitude, mapBounds.northeast.longitude,
                mapBounds.southwest.latitude, mapBounds.northeast.latitude);
            return () -> indexedFeatureQuery.buildMapClickMessageWithMapBounds(latLng, zoom, bbox);
        }

        @Override
        public void onLayerRemoved() {
            for (TileTableLayer tilesOnMap : linkedTiles.values()) {
                tilesOnMap.onLayerRemoved();
            }
            if (indexedFeatureQuery != null) {
                indexedFeatureQuery.close();
            }
        }

        @Override
        public Iterator<? extends MapElementSpec> elementsInBounds(LatLngBounds bounds) {
            List<MapElementSpec> specs = new ArrayList<>();
            for (TileTableLayer tileAdapter : linkedTiles.values()) {
                specs.add(tileAdapter.tileOverlaySpec);
            }
            if (indexedFeatureTiles != null) {
                specs.add(indexedFeatureTiles);
                return specs.iterator();
            }
            int maxFeaturesPerTable;
            if (featureDao.getGeometryType() == GeometryType.POINT) {
                maxFeaturesPerTable = context.getResources().getInteger(R.integer.geopackage_features_max_points_per_table);
            }
            else {
                maxFeaturesPerTable = context.getResources().getInteger(R.integer.geopackage_features_max_features_per_table);
            }
            Projection projection = featureDao.getProjection();
            GoogleMapShapeConverter converter = new GoogleMapShapeConverter(projection);
            WKBGeometryTransforms geometryTransforms = new WKBGeometryTransforms(converter);
            // TODO: query within bounds parameter
            int featuresAdded = 0;
            final int numFeaturesInTable;
            try (FeatureCursor featureCursor = featureDao.queryForAll()) {
                numFeaturesInTable = featureCursor.getCount();
                while (featureCursor.moveToNext() && featuresAdded++ < maxFeaturesPerTable) {
                    FeatureRow featureRow = featureCursor.getRow();
                    GeoPackageGeometryData geometryData = featureRow.getGeometry();
                    if (geometryData != null && !geometryData.isEmpty()) {
                        Geometry geometry = geometryData.getGeometry();
                        if (geometry != null) {
                            // Set the Shape Marker, PolylineOptions, and PolygonOptions here if needed to change color and style
                            specs.addAll(geometryTransforms.transform(geometry, featureRow.getId(), null));
                        }
                    }
                }
            }
            return specs.iterator();
        }
    }

    /*
     * Delete the GeoPackage cache overlay
     *
     * TODO: this was originally in TileOverlayPreferenceActivity to handle deleting on long press
     * this logic to go searching through directories to delete the cache file should be reworked
     */
//    private void deleteGeoPackageCacheOverlay(MapDataResource cache) {
//
//        String database = cache.getName();
//
//        // Get the GeoPackage file
//        GeoPackageManager manager = GeoPackageFactory.getManager(context);
//        File path = manager.getFile(database);
//
//        // Delete the cache from the GeoPackage manager
//        manager.delete(database);
//
//        // Attempt to delete the cache file if it is in the cache directory
//        File pathDirectory = path.getParentFile();
//        if (path.canWrite() && pathDirectory != null) {
//            // TODO: this should be in MapDataManager
//            Map<StorageUtility.StorageType, File> storageLocations = StorageUtility.getWritableStorageLocations();
//            for (File storageLocation : storageLocations.values()) {
//                File root = new File(storageLocation, context.getString(R.string.overlay_cache_directory));
//                if (root.equals(pathDirectory)) {
//                    path.delete();
//                    break;
//                }
//            }
//        }
//
//        // Check internal/external application storage
//        File applicationCacheDirectory = LocalStorageMapDataRepository.getApplicationCacheDirectory(context);
//        if (applicationCacheDirectory != null && applicationCacheDirectory.exists()) {
//            for (File cacheFile : applicationCacheDirectory.listFiles()) {
//                if (cacheFile.equals(path)) {
//                    path.delete();
//                    break;
//                }
//            }
//        }
//    }
}
