package mil.nga.giat.mage.map;

import android.support.annotation.UiThread;

import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.TileOverlay;

import mil.nga.giat.mage.R;

@UiThread
public interface MapElements {

    @FunctionalInterface
    interface MapElementVisitor<E, R> {
        R visit(E element, Object id);
    }

    @FunctionalInterface
    interface CircleVisitor<R> extends MapElementVisitor<Circle, R> {
        R visit(Circle x, Object id);
    }

    @FunctionalInterface
    interface GroundOverlayVisitor<R> extends MapElementVisitor<GroundOverlay, R> {
        R visit(GroundOverlay x, Object id);
    }

    @FunctionalInterface
    interface MarkerVisitor<R> extends MapElementVisitor<Marker, R> {
        R visit(Marker x, Object id);
    }

    @FunctionalInterface
    interface PolygonVisitor<R> extends MapElementVisitor<Polygon, R> {
        R visit(Polygon x, Object id);
    }

    @FunctionalInterface
    interface PolylineVisitor<R> extends MapElementVisitor<Polyline, R> {
        R visit(Polyline x, Object id);
    }

    @FunctionalInterface
    interface TileOverlayVisitor<R> extends MapElementVisitor<TileOverlay, R> {
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

    <R> R withElement(Circle x, CircleVisitor<R> action);
    <R> R withElement(GroundOverlay x, GroundOverlayVisitor<R> action);
    <R> R withElement(Marker x, MarkerVisitor<R> action);
    <R> R withElement(Polygon x, PolygonVisitor<R> action);
    <R> R withElement(Polyline x, PolylineVisitor<R> action);
    <R> R withElement(TileOverlay x, TileOverlayVisitor<R> action);

    <R> R withElementForId(Object id, CircleVisitor<R> action);
    <R> R withElementForId(Object id, GroundOverlayVisitor<R> action);
    <R> R withElementForId(Object id, MarkerVisitor<R> action);
    <R> R withElementForId(Object id, PolygonVisitor<R> action);
    <R> R withElementForId(Object id, PolylineVisitor<R> action);
    <R> R withElementForId(Object id, TileOverlayVisitor<R> action);

    void remove(Circle x);
    void remove(GroundOverlay x);
    void remove(Marker x);
    void remove(Polygon x);
    void remove(Polyline x);
    void remove(TileOverlay x);

    void forEach(ComprehensiveMapElementVisitor... v);

    int count();
}
