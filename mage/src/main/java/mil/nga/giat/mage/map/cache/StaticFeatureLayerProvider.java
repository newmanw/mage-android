package mil.nga.giat.mage.map.cache;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import mil.nga.giat.mage.map.MapElementSpec;
import mil.nga.giat.mage.sdk.datastore.layer.Layer;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeature;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeatureHelper;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeatureProperty;
import mil.nga.giat.mage.sdk.exceptions.StaticFeatureException;
import mil.nga.wkb.geom.Geometry;
import mil.nga.wkb.geom.GeometryType;
import mil.nga.wkb.geom.LineString;
import mil.nga.wkb.geom.Point;

public class StaticFeatureLayerProvider implements MapDataProvider {

    private static final String LOG_NAME = StaticFeatureLayerProvider.class.getSimpleName();

    private final StaticFeatureHelper featureHelper;
    private final Application context;

    public StaticFeatureLayerProvider(StaticFeatureHelper featureHelper, Application context) {
        this.featureHelper = featureHelper;
        this.context = context;
    }

    @Override
    public boolean canHandleResource(MapDataResource resource) {
        return resource.getUri().equals(StaticFeatureLayerRepository.RESOURCE_URI);
    }

    @Override
    public MapDataResource resolveResource(MapDataResource resource) throws MapDataResolveException {
        throw new MapDataResolveException(resource.getUri(), "static feature layer resources should never need resolution");
    }

    @Override
    public Callable<? extends MapLayerManager.MapLayerAdapter> createMapLayerAdapter(MapLayerDescriptor layerDescriptor, GoogleMap map) {
        return () -> new StaticFeatureMapLayer((StaticFeatureLayerDescriptor) layerDescriptor, featureHelper);
    }

    static class StaticFeatureLayerDescriptor extends MapLayerDescriptor {

        private final Long layerId;

        StaticFeatureLayerDescriptor(Layer layer) {
            super(layer.getRemoteId(), StaticFeatureLayerRepository.RESOURCE_URI, StaticFeatureLayerProvider.class);
            setLayerTitle(layer.getName());
            this.layerId = layer.getId();
        }
    }

    public class StaticFeatureMapLayer implements MapLayerManager.MapLayerAdapter {

        private final StaticFeatureLayerDescriptor layerDescriptor;
        private final StaticFeatureHelper featureHelper;
        private final Map<String, String> infoForMapElementId = new HashMap<>();

        StaticFeatureMapLayer(StaticFeatureLayerDescriptor layerDescriptor, StaticFeatureHelper featureHelper) {
            this.layerDescriptor = layerDescriptor;
            this.featureHelper = featureHelper;
        }

        @Override
        public void addedToMap(MapElementSpec.MapMarkerSpec spec, Marker x) {
            infoForMapElementId.put(x.getId(), (String) spec.data);
        }

        @Override
        public void addedToMap(MapElementSpec.MapPolygonSpec spec, Polygon x) {
            infoForMapElementId.put(x.getId(), (String) spec.data);
        }

        @Override
        public void addedToMap(MapElementSpec.MapPolylineSpec spec, Polyline x) {
            infoForMapElementId.put(x.getId(), (String) spec.data);
        }

        @Override
        public Callable<String> onClick(Marker x, Object id) {
            return () -> infoForMapElementId.get(x.getId());
        }

        @Override
        public Callable<String> onClick(Polygon x, Object id) {
            return () -> infoForMapElementId.get(x.getId());
        }

        @Override
        public Callable<String> onClick(Polyline x, Object id) {
            return () -> infoForMapElementId.get(x.getId());
        }

        @Override
        public void onLayerRemoved() {
            infoForMapElementId.clear();
        }

        @Override
        public Iterator<? extends MapElementSpec> elementsInBounds(LatLngBounds bounds) {
            List<StaticFeature> features;
            try {
                features = featureHelper.readAll(layerDescriptor.layerId);
            }
            catch (StaticFeatureException e) {
                return Collections.emptyIterator();
            }
            List<MapElementSpec> featureSpecs = new ArrayList<>();
            for (StaticFeature feature : features) {
                Geometry geometry = feature.getGeometry();
                Map<String, StaticFeatureProperty> properties = feature.getPropertiesMap();
                StringBuilder content = new StringBuilder();
                if (properties.get("name") != null) {
                    content.append("<h5>").append(properties.get("name").getValue()).append("</h5>");
                }
                if (properties.get("description") != null) {
                    content.append("<div>").append(properties.get("description").getValue()).append("</div>");
                }
                GeometryType type = geometry.getGeometryType();
                if (type == GeometryType.POINT) {
                    Point point = (Point) geometry;
                    MarkerOptions options = new MarkerOptions().position(new LatLng(point.getY(), point.getX())).snippet(content.toString());
                    // check to see if there's an icon
                    String iconPath = feature.getLocalPath();
                    if (iconPath != null) {
                        File iconFile = new File(iconPath);
                        if (iconFile.exists()) {
                            BitmapFactory.Options o = new BitmapFactory.Options();
                            o.inDensity = 480;
                            o.inTargetDensity = context.getResources().getDisplayMetrics().densityDpi;
                            try {
                                Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(iconFile), null, o);
                                if (bitmap != null) {
                                    options.icon(BitmapDescriptorFactory.fromBitmap(bitmap));
                                }
                            } catch (FileNotFoundException fnfe) {
                                Log.e(LOG_NAME, "Could not set icon.", fnfe);
                            }
                        }
                    }
                    featureSpecs.add(new MapElementSpec.MapMarkerSpec(feature.getId(), content.toString(), options));
                }
                else if (type == GeometryType.LINESTRING) {
                    PolylineOptions options = new PolylineOptions();
                    StaticFeatureProperty property = properties.get("stylelinestylecolorrgb");
                    if (property != null) {
                        String color = property.getValue();
                        options.color(Color.parseColor(color));
                    }
                    LineString lineString = (LineString) geometry;
                    for(Point point: lineString.getPoints()){
                        options.add(new LatLng(point.getY(), point.getX()));
                    }
                    featureSpecs.add(new MapElementSpec.MapPolylineSpec(feature.getId(), content.toString(), options));
                }
                else if (type == GeometryType.POLYGON) {
                    PolygonOptions options = new PolygonOptions().clickable(true);
                    Integer color = null;
                    StaticFeatureProperty property = properties.get("stylelinestylecolorrgb");
                    if (property != null) {
                        String colorProperty = property.getValue();
                        color = Color.parseColor(colorProperty);
                        options.strokeColor(color);
                    }
                    else {
                        property = properties.get("stylepolystylecolorrgb");
                        if (property != null) {
                            String colorProperty = property.getValue();
                            color = Color.parseColor(colorProperty);
                            options.strokeColor(color);
                        }
                    }
                    property = properties.get("stylepolystylefill");
                    if (property != null) {
                        String fill = property.getValue();
                        if ("1".equals(fill) && color != null) {
                            options.fillColor(color);
                        }
                    }
                    mil.nga.wkb.geom.Polygon polygon = (mil.nga.wkb.geom.Polygon) geometry;
                    List<LineString> rings = polygon.getRings();
                    LineString polygonLineString = rings.get(0);
                    for (Point point : polygonLineString.getPoints()) {
                        LatLng latLng = new LatLng(point.getY(), point.getX());
                        options.add(latLng);
                    }
                    for (int i = 1; i < rings.size(); i++) {
                        LineString hole = rings.get(i);
                        List<LatLng> holeLatLngs = new ArrayList<>();
                        for (Point point : hole.getPoints()) {
                            LatLng latLng = new LatLng(point.getY(), point.getX());
                            holeLatLngs.add(latLng);
                        }
                        options.addHole(holeLatLngs);
                    }
                    featureSpecs.add(new MapElementSpec.MapPolygonSpec(feature.getId(), content.toString(), options));
                }
            }
            return featureSpecs.iterator();
        }
    }
}
