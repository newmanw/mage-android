package mil.nga.giat.mage.map;

import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

import mil.nga.geopackage.map.geom.GoogleMapShapeConverter;
import mil.nga.wkb.geom.CompoundCurve;
import mil.nga.wkb.geom.CurvePolygon;
import mil.nga.wkb.geom.Geometry;
import mil.nga.wkb.geom.GeometryCollection;
import mil.nga.wkb.geom.GeometryType;
import mil.nga.wkb.geom.LineString;
import mil.nga.wkb.geom.MultiLineString;
import mil.nga.wkb.geom.MultiPoint;
import mil.nga.wkb.geom.MultiPolygon;
import mil.nga.wkb.geom.Point;
import mil.nga.wkb.geom.Polygon;
import mil.nga.wkb.geom.PolyhedralSurface;

public final class WKBGeometryTransforms {

    private final GoogleMapShapeConverter converter;

    public WKBGeometryTransforms() {
        this(new GoogleMapShapeConverter());
    }

    public WKBGeometryTransforms(GoogleMapShapeConverter converter) {
        this.converter = converter;
    }

    @SuppressWarnings("unchecked")
    public Collection<? extends MapElementSpec> transform(Geometry geom, Object id, Object data) {
        WKBGeometryTransform tx = transformForGeometryType.get(geom.getGeometryType());
        return tx.toElementSpecs(geom, id, data);
    }

    public MapElementSpec.MapMarkerSpec transform(Point geom, Object id, Object data) {
        return new MapElementSpec.MapMarkerSpec(id, data, new MarkerOptions().position(converter.toLatLng(geom)));
    }

    public MapElementSpec.MapPolylineSpec transform(LineString geom, Object id, Object data) {
        PolylineOptions options = converter.toPolyline(geom);
        return new MapElementSpec.MapPolylineSpec(id, data, options);
    }

    public MapElementSpec.MapPolygonSpec transform(CurvePolygon geom, Object id, Object data) {
        PolygonOptions options = converter.toCurvePolygon(geom);
        return new MapElementSpec.MapPolygonSpec(id, data, options);
    }

    public MapElementSpec.MapPolygonSpec transform(Polygon geom, Object id, Object data) {
        PolygonOptions options = converter.toPolygon(geom);
        return new MapElementSpec.MapPolygonSpec(id, data, options);
    }

    public Collection<MapElementSpec.MapMarkerSpec> transform(MultiPoint geom, Object id, Object data) {
        List<MapElementSpec.MapMarkerSpec> specs = new ArrayList<>(geom.numGeometries());
        for (Point x : geom.getGeometries()) {
            specs.add(transform(x, id, data));
        }
        return specs;
    }

    public Collection<MapElementSpec.MapPolylineSpec> transform(MultiLineString geom, Object id, Object data) {
        List<MapElementSpec.MapPolylineSpec> specs = new ArrayList<>(geom.numGeometries());
        for (LineString x : geom.getGeometries()) {
            specs.add(transform(x, id, data));
        }
        return specs;
    }

    public Collection<MapElementSpec.MapPolygonSpec> transform(MultiPolygon geom, Object id, Object data) {
        List<MapElementSpec.MapPolygonSpec> specs = new ArrayList<>(geom.numGeometries());
        for (Polygon x : geom.getGeometries()) {
            specs.add(transform(x, id, data));
        }
        return specs;
    }

    public Collection<MapElementSpec.MapPolygonSpec> transform(PolyhedralSurface geom, Object id, Object data) {
        List<MapElementSpec.MapPolygonSpec> specs = new ArrayList<>(geom.numPolygons());
        for (PolygonOptions options : converter.toPolygons(geom).getPolygonOptions()) {
            specs.add(new MapElementSpec.MapPolygonSpec(id, data, options));
        }
        return specs;
    }

    public Collection<MapElementSpec.MapPolylineSpec> transform(CompoundCurve geom, Object id, Object data) {
        List<MapElementSpec.MapPolylineSpec> specs = new ArrayList<>(geom.numLineStrings());
        for (LineString x : geom.getLineStrings()) {
            specs.add(transform(x, id, data));
        }
        return specs;
    }

    public final WKBPointTransform TRANSFORM_POINT = new WKBPointTransform();
    public final WKBLineStringTransform TRANSFORM_LINE_STRING = new WKBLineStringTransform();
    public final WKBPolygonTransform TRANSFORM_POLYGON = new WKBPolygonTransform();
    public final WKBMultiPointTransform TRANSFORM_MULTI_POINT = new WKBMultiPointTransform();
    public final WKBMultiLineStringTransform TRANSFORM_MULTI_LINE_STRING = new WKBMultiLineStringTransform();
    public final WKBMultiPolygonTransform TRANSFORM_MULTI_POLYGON = new WKBMultiPolygonTransform();
    public final WKBCompoundCurveTransform TRANSFORM_COMPOUND_CURVE = new WKBCompoundCurveTransform();
    public final WKBCurvePolygonTransform TRANSFORM_CURVE_POLYGON = new WKBCurvePolygonTransform();
    public final WKBPolyhedralSurfaceTransform TRANSFORM_POLYHEDRAL_SURFACE = new WKBPolyhedralSurfaceTransform();
    public final WKBGeometryCollectionTransform TRANSFORM_GEOMETRY_COLLECTION = new WKBGeometryCollectionTransform();

    private  final EnumMap<GeometryType, WKBGeometryTransform<? extends Geometry, ? extends MapElementSpec>> transformForGeometryType;
    {
        transformForGeometryType = new EnumMap<>(GeometryType.class);
        transformForGeometryType.put(GeometryType.POINT, TRANSFORM_POINT);
        transformForGeometryType.put(GeometryType.LINESTRING, TRANSFORM_LINE_STRING);
        transformForGeometryType.put(GeometryType.POLYGON, TRANSFORM_POLYGON);
        transformForGeometryType.put(GeometryType.MULTIPOINT, TRANSFORM_MULTI_POINT);
        transformForGeometryType.put(GeometryType.MULTILINESTRING, TRANSFORM_MULTI_LINE_STRING);
        transformForGeometryType.put(GeometryType.MULTIPOLYGON, TRANSFORM_MULTI_POLYGON);
        transformForGeometryType.put(GeometryType.CIRCULARSTRING, TRANSFORM_LINE_STRING);
        transformForGeometryType.put(GeometryType.POLYHEDRALSURFACE, TRANSFORM_POLYHEDRAL_SURFACE);
        transformForGeometryType.put(GeometryType.TIN, TRANSFORM_POLYHEDRAL_SURFACE);
        transformForGeometryType.put(GeometryType.COMPOUNDCURVE, TRANSFORM_COMPOUND_CURVE);
        transformForGeometryType.put(GeometryType.CURVEPOLYGON, TRANSFORM_CURVE_POLYGON);
        transformForGeometryType.put(GeometryType.TRIANGLE, TRANSFORM_POLYGON);
        transformForGeometryType.put(GeometryType.GEOMETRYCOLLECTION, TRANSFORM_GEOMETRY_COLLECTION);
    }

    public interface WKBGeometryTransform<G extends Geometry, S extends MapElementSpec> {
        Collection<? extends S> toElementSpecs(G geom, Object id, Object data);
    }

    public class WKBPointTransform implements WKBGeometryTransform<Point, MapElementSpec.MapMarkerSpec> {

        @Override
        public Collection<MapElementSpec.MapMarkerSpec> toElementSpecs(Point geom, Object id, Object data) {
            return Collections.singleton(transform(geom, id, data));
        }
    }

    public class WKBLineStringTransform implements WKBGeometryTransform<LineString, MapElementSpec.MapPolylineSpec> {

        @Override
        public Collection<MapElementSpec.MapPolylineSpec> toElementSpecs(LineString geom, Object id, Object data) {
            return Collections.singleton(transform(geom, id, data));
        }
    }

    public class WKBPolygonTransform implements WKBGeometryTransform<Polygon, MapElementSpec.MapPolygonSpec> {

        @Override
        public Collection<MapElementSpec.MapPolygonSpec> toElementSpecs(Polygon geom, Object id, Object data) {
            return Collections.singleton(transform(geom, id, data));
        }
    }


    public class WKBMultiPointTransform implements WKBGeometryTransform<MultiPoint, MapElementSpec.MapMarkerSpec> {

        @Override
        public Collection<MapElementSpec.MapMarkerSpec> toElementSpecs(MultiPoint geom, Object id, Object data) {
            return transform(geom, id, data);
        }
    }


    public class WKBMultiLineStringTransform implements WKBGeometryTransform<MultiLineString, MapElementSpec.MapPolylineSpec> {

        @Override
        public Collection<MapElementSpec.MapPolylineSpec> toElementSpecs(MultiLineString geom, Object id, Object data) {
            return transform(geom, id, data);
        }
    }

    public class WKBMultiPolygonTransform implements WKBGeometryTransform<MultiPolygon, MapElementSpec.MapPolygonSpec> {

        @Override
        public Collection<MapElementSpec.MapPolygonSpec> toElementSpecs(MultiPolygon geom, Object id, Object data) {
            return transform(geom, id, data);
        }
    }

    public class WKBCompoundCurveTransform implements WKBGeometryTransform<CompoundCurve, MapElementSpec.MapPolylineSpec> {

        @Override
        public Collection<MapElementSpec.MapPolylineSpec> toElementSpecs(CompoundCurve geom, Object id, Object data) {
            return transform(geom, id, data);
        }
    }

    public class WKBCurvePolygonTransform implements WKBGeometryTransform<CurvePolygon, MapElementSpec.MapPolygonSpec> {

        @Override
        public Collection<MapElementSpec.MapPolygonSpec> toElementSpecs(CurvePolygon geom, Object id, Object data) {
            return Collections.singleton(transform(geom, id, data));
        }
    }

    public class WKBPolyhedralSurfaceTransform implements WKBGeometryTransform<PolyhedralSurface, MapElementSpec.MapPolygonSpec> {

        @Override
        public Collection<MapElementSpec.MapPolygonSpec> toElementSpecs(PolyhedralSurface geom, Object id, Object data) {
            return transform(geom, id, data);
        }
    }

    public class WKBGeometryCollectionTransform implements WKBGeometryTransform<GeometryCollection<? extends Geometry>, MapElementSpec> {

        @Override
        public Collection<MapElementSpec> toElementSpecs(GeometryCollection<? extends Geometry> geom, Object id, Object data) {
            List<MapElementSpec> specs = new ArrayList<>(geom.numGeometries());
            for (Geometry member : geom.getGeometries()) {
                Collection<? extends MapElementSpec> memberSpecs = transform(member, id, data);
                specs.addAll(memberSpecs);
            }
            return specs;
        }
    }
}
