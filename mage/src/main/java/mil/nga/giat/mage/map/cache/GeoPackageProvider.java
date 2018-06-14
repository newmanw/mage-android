package mil.nga.giat.mage.map.cache;

import android.app.Application;
import android.support.annotation.NonNull;
import android.support.v4.util.LongSparseArray;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
import mil.nga.geopackage.map.geom.GoogleMapShape;
import mil.nga.geopackage.map.geom.GoogleMapShapeConverter;
import mil.nga.geopackage.map.geom.MultiMarker;
import mil.nga.geopackage.map.geom.MultiPolygon;
import mil.nga.geopackage.map.geom.MultiPolyline;
import mil.nga.geopackage.map.geom.PolylineMarkers;
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
import mil.nga.giat.mage.map.MapDisplayObject;
import mil.nga.giat.mage.sdk.utils.MediaUtility;
import mil.nga.wkb.geom.Geometry;
import mil.nga.wkb.geom.GeometryType;

public class GeoPackageProvider implements MapDataProvider {

    private static final String LOG_NAME = GeoPackageProvider.class.getName();
    private static final float Z_INDEX_TILE_TABLE = -2.0f;
    private static final float Z_INDEX_FEATURE_TABLE = -1.0f;

    /**
     * Get a cache name for the cache file
     *
     * @param manager
     * @param cacheFile
     * @return cache name
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

    private static void setZIndexOfShape(GoogleMapShape shape, int zIndex) {
        switch(shape.getShapeType()) {
            case MARKER:
                ((Marker)shape.getShape()).setZIndex(zIndex);
                break;
            case POLYGON:
                ((Polygon)shape.getShape()).setZIndex(zIndex);
                break;
            case POLYLINE:
                ((Polyline)shape.getShape()).setZIndex(zIndex);
                break;
            case MULTI_MARKER:
                for (Marker x : ((MultiMarker)shape.getShape()).getMarkers()) {
                    x.setZIndex(zIndex);
                }
                break;
            case MULTI_POLYLINE:
                for (Polyline x : ((MultiPolyline)shape.getShape()).getPolylines()) {
                    x.setZIndex(zIndex);
                }
                break;
            case MULTI_POLYGON:
                for (Polygon x : ((MultiPolygon)shape.getShape()).getPolygons()) {
                    x.setZIndex(zIndex);
                }
                break;
            case POLYLINE_MARKERS:
                ((PolylineMarkers) shape.getShape()).getPolyline().setZIndex(zIndex);
                for (Marker x : ((PolylineMarkers) shape.getShape()).getMarkers()) {
                    x.setZIndex(zIndex);
                }
                break;
            case POLYGON_MARKERS:
                break;
            case MULTI_POLYLINE_MARKERS:
                break;
            case MULTI_POLYGON_MARKERS:
                break;
            case COLLECTION:
                break;
        }
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
        if (!"file".equalsIgnoreCase(resource.getUri().getScheme())) {
            return false;
        }
        return GeoPackageValidate.hasGeoPackageExtension(new File(resource.getUri()));
    }

    @Override
    public MapDataResource resolveResource(MapDataResource resource) throws MapDataResolveException {
        File cacheFile = new File(resource.getUri());
        String resourceName = getOrImportGeoPackageDatabase(cacheFile);
        return createCache(resource, resourceName);
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

    /**
     * Import the GeoPackage file as an external link if it does not exist
     *
     * @param cacheFile
     * @return cache name when imported, null when not imported
     */
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
                cacheFile.delete();
            }
            catch (Exception deleteException) {
                Log.e(LOG_NAME, "Failed to delete file: " + cacheFile.getAbsolutePath() + ", error: " + deleteException.getMessage());
            }
        }

        throw fail;
    }

    /**
     * Get the GeoPackage database as a cache overlay
     *
     * @param database
     * @return cache overlay
     */
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

    static class TileTableLayer extends MapLayerManager.MapLayer {

        private final GoogleMap map;
        /**
         * Used to query the backing feature tables
         */
        private final List<FeatureOverlayQuery> featureOverlayQueries;
        private TileOverlay tileOverlay;


        private TileTableLayer(MapLayerManager manager, List<FeatureOverlayQuery> queries) {
            manager.super();
            this.map = manager.getMap();
            this.featureOverlayQueries = queries;
        }

        @Override
        public void removeFromMap() {
            if (tileOverlay != null) {
                tileOverlay.remove();
                tileOverlay = null;
            }
        }

        @Override
        public void zoomMapToBoundingBox() {
        }

        @Override
        public void show() {
            if (tileOverlay != null) {
                tileOverlay.setVisible(true);
            }
        }

        @Override
        public void hide() {
            if (tileOverlay != null) {
                tileOverlay.setVisible(false);
            }
        }

        @Override
        protected void setZIndex(int z) {
            if (tileOverlay != null) {
                tileOverlay.setZIndex(z);
            }
        }

        @Override
        public boolean isVisible() {
            return tileOverlay != null && tileOverlay.isVisible();
        }

        @Override
        public String onMapClick(LatLng latLng, MapView mapView) {
            StringBuilder message = new StringBuilder();
            for(FeatureOverlayQuery featureOverlayQuery : featureOverlayQueries) {
                String overlayMessage = featureOverlayQuery.buildMapClickMessage(latLng, mapView, map);
                if (overlayMessage != null) {
                    if (message.length() > 0) {
                        message.append("\n\n");
                    }
                    message.append(overlayMessage);
                }
            }
            return message.length() > 0 ? message.toString() : null;
        }

        void clearFeatureOverlayQueries(){
            Iterator<FeatureOverlayQuery> queryIter = featureOverlayQueries.iterator();
            while (queryIter.hasNext()) {
                FeatureOverlayQuery query = queryIter.next();
                queryIter.remove();
                query.close();
            }
        }

        @Override
        protected void dispose() {
            removeFromMap();
            clearFeatureOverlayQueries();
        }
    }

    static class FeatureTableLayer extends MapLayerManager.MapLayer {

        private final GoogleMap map;
        private final Map<String, TileTableLayer> linkedTiles;
        private final FeatureOverlayQuery query;
        /**
         * keys are feature IDs from GeoPackage table
         */
        private LongSparseArray<GoogleMapShape> shapesOnMap = new LongSparseArray<>(0);
        private TileOverlay indexedFeaturesTileOverlay;
        private boolean visible;
        private int zIndex;

        FeatureTableLayer(MapLayerManager manager, Map<String, TileTableLayer> linkedTiles, FeatureOverlayQuery query) {
            manager.super();
            this.map = manager.getMap();
            this.linkedTiles = linkedTiles;
            this.query = query;
        }

        FeatureTableLayer(MapLayerManager manager, Map<String, TileTableLayer> linkedTiles) {
            manager.super();
            this.map = manager.getMap();
            this.linkedTiles = linkedTiles;
            query = null;
        }

        @Override
        protected void removeFromMap() {
            removeShapes();
            if (indexedFeaturesTileOverlay != null) {
                indexedFeaturesTileOverlay.remove();
                indexedFeaturesTileOverlay = null;
            }
            for (TileTableLayer linkedTileTable : linkedTiles.values()){
                linkedTileTable.removeFromMap();
            }
        }

        @Override
        protected void zoomMapToBoundingBox() {
            // TODO
        }

        @Override
        protected void show() {
            if (visible) {
                return;
            }
            for (TileTableLayer linkedTileTable : linkedTiles.values()) {
                linkedTileTable.show();
            }
            if (indexedFeaturesTileOverlay != null) {
                indexedFeaturesTileOverlay.setVisible(true);
            }
            else {
                for (int i = 0; i < shapesOnMap.size(); i++) {
                    GoogleMapShape shape = shapesOnMap.valueAt(i);
                    shape.setVisible(true);
                }
            }
            visible = true;
        }

        @Override
        protected void hide() {
            if (!visible) {
                return;
            }
            for (TileTableLayer linkedTileTable : linkedTiles.values()) {
                linkedTileTable.hide();
            }
            if (indexedFeaturesTileOverlay != null) {
                indexedFeaturesTileOverlay.setVisible(false);
            }
            else {
                for (int i = 0; i < shapesOnMap.size(); i++) {
                    GoogleMapShape shape = shapesOnMap.valueAt(i);
                    shape.setVisible(false);
                }
            }
            visible = false;
        }

        @Override
        protected void setZIndex(int z) {
            zIndex = z;
            for (TileTableLayer linkedTileTable : linkedTiles.values()) {
                linkedTileTable.setZIndex(z);
            }
            if (indexedFeaturesTileOverlay != null) {
                indexedFeaturesTileOverlay.setZIndex(z);
            }
            // TODO: GoogleMapShape needs z-index support
            for (int i = 0; i < shapesOnMap.size(); i++) {
                GoogleMapShape shape = shapesOnMap.valueAt(i);
                setZIndexOfShape(shape, z);
            }
        }

        @Override
        protected String onMapClick(LatLng latLng, MapView mapView) {
            String message = null;
            if (query != null) {
                message = query.buildMapClickMessage(latLng, mapView, map);
            }
            return message;
        }

        @Override
        protected boolean isVisible() {
            return visible;
        }

        @Override
        protected void dispose() {
            removeFromMap();
            for (TileTableLayer tilesOnMap : linkedTiles.values()) {
                tilesOnMap.dispose();
            }
            if (query != null) {
                query.close();
            }
        }

        private void removeShapes() {
            for (int i = 0; i < shapesOnMap.size(); i++) {
                GoogleMapShape shape = shapesOnMap.valueAt(i);
                shape.remove();
            }
            shapesOnMap.clear();
        }
    }

    @Override
    public MapLayerManager.LoadLayerMapObjects createMapLayerFromDescriptor(MapLayerDescriptor layerDescriptor, MapLayerManager mapManager) {
        if (layerDescriptor instanceof GeoPackageTileTableDescriptor) {
            return new CreateTileTableLayer((GeoPackageTileTableDescriptor) layerDescriptor, mapManager, geoPackageCache, context);
        }
        else if (layerDescriptor instanceof GeoPackageFeatureTableDescriptor) {
            return new CreateFeatureTableLayer((GeoPackageFeatureTableDescriptor) layerDescriptor, mapManager, geoPackageCache, context);
        }

        throw new IllegalArgumentException(getClass().getSimpleName() + " does not support " + layerDescriptor + " of type " + layerDescriptor.getDataType() );
    }

    private static class CreateTileTableLayer extends MapLayerManager.LoadLayerMapObjects<GeoPackageTileTableDescriptor, TileTableLayer> {

        private final Application context;
        private final GeoPackageCache geoPackageCache;
        private TileOverlay tileOverlay;

        private CreateTileTableLayer(GeoPackageTileTableDescriptor descriptor, MapLayerManager mapLayerManager, GeoPackageCache geoPackageCache, Application context) {
            super(descriptor, mapLayerManager);
            this.geoPackageCache = geoPackageCache;
            this.context = context;
        }

        @Override
        protected TileTableLayer doInBackground(Void... nothing) {
            TileTableLayerComponents layerComponents = createTileTableLayerComponents(layerDescriptor, mapLayerManager, geoPackageCache, context);
            publishProgress(new MapDisplayObject.MapTileOverlay(null, layerComponents.options));
            return new TileTableLayer(mapLayerManager, layerComponents.queries);
        }

        @Override
        public void addedToMap(MapDisplayObject object, TileOverlay overlay) {
            tileOverlay = overlay;
        }

        @Override
        protected MapLayerManager.MapLayer prepareLayer(TileTableLayer layer) {
            layer.tileOverlay = tileOverlay;
            return layer;
        }
    }

    private static class TileTableLayerComponents {

        private final TileOverlayOptions options;
        private final List<FeatureOverlayQuery> queries;

        private TileTableLayerComponents(TileOverlayOptions options, List<FeatureOverlayQuery> queries) {
            this.options = options;
            this.queries = queries;
        }
    }

    private static TileTableLayerComponents createTileTableLayerComponents(GeoPackageTileTableDescriptor tableCache, MapLayerManager mapManager, GeoPackageCache geoPackageCache, Application context) {
        GeoPackage geoPackage = geoPackageCache.getOrOpen(tableCache.getGeoPackage());
        TileDao tileDao = geoPackage.getTileDao(tableCache.getTableName());
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
        return new TileTableLayerComponents(overlayOptions, queries);
    }

    private static class CreateFeatureTableLayer extends MapLayerManager.LoadLayerMapObjects<GeoPackageFeatureTableDescriptor, FeatureTableLayer> implements GoogleMapShapeOwner {

        private final GeoPackageCache geoPackageCache;
        private final Application context;
        private final Map<String, TileOverlay> linkedTileOverlays = new HashMap<>();
        private final LongSparseArray<GoogleMapShape> shapes = new LongSparseArray<>();
        private TileOverlay indexedFeaturesTileOverlay;

        CreateFeatureTableLayer(GeoPackageFeatureTableDescriptor layerDescriptor, MapLayerManager mapLayerManager, GeoPackageCache geoPackageCache, Application context) {
            super(layerDescriptor, mapLayerManager);
            this.geoPackageCache = geoPackageCache;
            this.context = context;
        }

        @Override
        protected FeatureTableLayer doInBackground(Void... voids) {
            Map<String, TileTableLayer> linkedTiles = new TreeMap<>();
            for (GeoPackageTileTableDescriptor linkedTileTable : layerDescriptor.getLinkedTileTables()) {
                TileTableLayerComponents tileComponents = createTileTableLayerComponents(linkedTileTable, mapLayerManager, geoPackageCache, context);
                linkedTiles.put(linkedTileTable.getTableName(), new TileTableLayer(mapLayerManager, tileComponents.queries));
                publishProgress(new MapDisplayObject.MapTileOverlay(linkedTileTable.getLayerName(), tileComponents.options));
            }
            GeoPackage geoPackage = geoPackageCache.getOrOpen(layerDescriptor.getGeoPackage());
            // Add the features to the map
            FeatureDao featureDao = geoPackage.getFeatureDao(layerDescriptor.getTableName());
            // If indexed, add as a tile overlay
            if (layerDescriptor.isIndexed()) {
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
                tileProvider.setMinZoom(layerDescriptor.getMinZoom());
                FeatureTileTableLinker linker = new FeatureTileTableLinker(geoPackage);
                List<TileDao> tileDaos = linker.getTileDaosForFeatureTable(featureDao.getTableName());
                tileProvider.ignoreTileDaos(tileDaos);
                TileOverlayOptions overlayOptions = new TileOverlayOptions()
                    .zIndex(Z_INDEX_FEATURE_TABLE)
                    .tileProvider(tileProvider);
                FeatureOverlayQuery featureQuery = new FeatureOverlayQuery(context, tileProvider);
                publishProgress(new MapDisplayObject.MapTileOverlay(null, overlayOptions));
                return new FeatureTableLayer(mapLayerManager, linkedTiles, featureQuery);
            }
            // Not indexed, add the features to the map
            else {
                int maxFeaturesPerTable;
                if (featureDao.getGeometryType() == GeometryType.POINT) {
                    maxFeaturesPerTable = context.getResources().getInteger(R.integer.geopackage_features_max_points_per_table);
                }
                else {
                    maxFeaturesPerTable = context.getResources().getInteger(R.integer.geopackage_features_max_features_per_table);
                }
                Projection projection = featureDao.getProjection();
                GoogleMapShapeConverter shapeConverter = new GoogleMapShapeConverter(projection);
                FeatureCursor featureCursor = featureDao.queryForAll();
                int featuresAdded = 0;
                final int numFeaturesInTable;
                try {
                    numFeaturesInTable = featureCursor.getCount();
                    while (featureCursor.moveToNext() && featuresAdded++ < maxFeaturesPerTable) {
                        FeatureRow featureRow = featureCursor.getRow();
                        GeoPackageGeometryData geometryData = featureRow.getGeometry();
                        if (geometryData != null && !geometryData.isEmpty()) {
                            Geometry geometry = geometryData.getGeometry();
                            if (geometry != null) {
                                GoogleMapShape shape = shapeConverter.toShape(geometry);
                                // Set the Shape Marker, PolylineOptions, and PolygonOptions here if needed to change color and style
                                publishProgress(new GoogleMapShapeDisplayObject(featureRow.getId(), shape));
                            }
                        }
                    }
                }
                finally {
                    featureCursor.close();
                }

                // TODO: don't really like doing any UI stuff here
                // and i don't think one can call this method on a background thread anyway
//                if (shapes.size() < numFeaturesInTable) {
//                    Toast.makeText(context, featureTableCache.getTableName()
//                        + "- added " + shapes.size() + " of " + numFeaturesInTable, Toast.LENGTH_LONG).show();
//                }

                return new FeatureTableLayer(mapLayerManager, linkedTiles);
            }
        }

        @Override
        public void addedToMap(MapDisplayObject object, TileOverlay overlay) {
            if (object.id != null) {
                linkedTileOverlays.put((String) object.id, overlay);
            }
            else {
                indexedFeaturesTileOverlay = overlay;
            }
        }

        @Override
        public void addedToMap(MapDisplayObject object, GoogleMapShape shape) {
            shapes.put((Long) object.id, shape);
        }

        @Override
        protected MapLayerManager.MapLayer prepareLayer(FeatureTableLayer layer) {
            layer.indexedFeaturesTileOverlay = indexedFeaturesTileOverlay;
            layer.shapesOnMap = shapes;
            for (Map.Entry<String, TileOverlay> linkedOverlay : linkedTileOverlays.entrySet()) {
                layer.linkedTiles.get(linkedOverlay.getKey()).tileOverlay = linkedOverlay.getValue();
            }
            return layer;
        }
    }

    /**
     * Delete the GeoPackage cache overlay
     *
     * TODO: this was originally in TileOverlayPreferenceActivity to handle deleting on long press
     * this logic to go searching through directories to delete the cache file should be reworked
     */
    private void deleteGeoPackageCacheOverlay(MapDataResource cache) {

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
    }

    private interface GoogleMapShapeOwner extends MapDisplayObject.MapOwner {
        void addedToMap(MapDisplayObject object, GoogleMapShape shape);
    }

    private static class GoogleMapShapeDisplayObject extends MapDisplayObject {

        private final GoogleMapShape shape;

        private GoogleMapShapeDisplayObject(Object id, GoogleMapShape shape) {
            super(id);
            this.shape = shape;
        }

        @Override
        public void createFor(MapOwner owner) {
            ((GoogleMapShapeOwner) owner).addedToMap(this, GoogleMapShapeConverter.addShapeToMap(owner.getMap(), shape));
        }
    }
}
