package mil.nga.giat.mage.map;

import android.support.annotation.UiThread;

import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.TileOverlay;

@UiThread
public interface MapElements {

    @FunctionalInterface
    interface CircleVisitor<R> {
        R visit(Circle x, Object id);
    }

    @FunctionalInterface
    interface GroundOverlayVisitor<R> {
        R visit(GroundOverlay x, Object id);
    }

    @FunctionalInterface
    interface MarkerVisitor<R> {
        R visit(Marker x, Object id);
    }

    @FunctionalInterface
    interface PolygonVisitor<R> {
        R visit(Polygon x, Object id);
    }

    @FunctionalInterface
    interface PolylineVisitor<R> {
        R visit(Polyline x, Object id);
    }

    @FunctionalInterface
    interface TileOverlayVisitor<R> {
        R visit(TileOverlay x, Object id);
    }

    interface ComprehensiveMapElementVisitor {
        default boolean visit(Circle x, Object id) { return true; }
        default boolean visit(GroundOverlay x, Object id) { return true; }
        default boolean visit(Marker x, Object id) { return true; }
        default boolean visit(Polygon x, Object id) { return true; }
        default boolean visit(Polyline x, Object id) { return true; }
        default boolean visit(TileOverlay x, Object id) { return true; }
    }

    MapElements add(Circle x, Object id);
    MapElements add(GroundOverlay x, Object id);
    MapElements add(Marker x, Object id);
    MapElements add(Polygon x, Object id);
    MapElements add(Polyline x, Object id);
    MapElements add(TileOverlay x, Object id);

    boolean contains(Circle x);
    boolean contains(GroundOverlay x);
    boolean contains(Marker x);
    boolean contains(Polygon x);
    boolean contains(Polyline x);
    boolean contains(TileOverlay x);

    <T> T withElement(Circle x, CircleVisitor<T> action);
    <T> T withElement(GroundOverlay x, GroundOverlayVisitor<T> action);
    <T> T withElement(Marker x, MarkerVisitor<T> action);
    <T> T withElement(Polygon x, PolygonVisitor<T> action);
    <T> T withElement(Polyline x, PolylineVisitor<T> action);
    <T> T withElement(TileOverlay x, TileOverlayVisitor<T> action);

    <T> T withElementForId(Object id, CircleVisitor<T> action);
    <T> T withElementForId(Object id, GroundOverlayVisitor<T> action);
    <T> T withElementForId(Object id, MarkerVisitor<T> action);
    <T> T withElementForId(Object id, PolygonVisitor<T> action);
    <T> T withElementForId(Object id, PolylineVisitor<T> action);
    <T> T withElementForId(Object id, TileOverlayVisitor<T> action);

    void remove(Circle x);
    void remove(GroundOverlay x);
    void remove(Marker x);
    void remove(Polygon x);
    void remove(Polyline x);
    void remove(TileOverlay x);

    void forEach(ComprehensiveMapElementVisitor... v);

    int count();
}
