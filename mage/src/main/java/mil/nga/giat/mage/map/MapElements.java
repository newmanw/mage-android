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

    interface MapElementVisitor<E> {
        boolean visit(E element, Object id);
    }

    interface CircleVisitor extends MapElementVisitor<Circle> {
        boolean visit(Circle x, Object id);
    }

    interface GroundOverlayVisitor extends MapElementVisitor<GroundOverlay> {
        boolean visit(GroundOverlay x, Object id);
    }

    interface MarkerVisitor extends MapElementVisitor<Marker> {
        boolean visit(Marker x, Object id);
    }

    interface PolygonVisitor extends MapElementVisitor<Polygon> {
        boolean visit(Polygon x, Object id);
    }

    interface PolylineVisitor extends MapElementVisitor<Polyline> {
        boolean visit(Polyline x, Object id);
    }

    interface TileOverlayVisitor extends MapElementVisitor<TileOverlay> {
        boolean visit(TileOverlay x, Object id);
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

    void remove(Circle x);
    void remove(GroundOverlay x);
    void remove(Marker x);
    void remove(Polygon x);
    void remove(Polyline x);
    void remove(TileOverlay x);

    void forEach(ComprehensiveMapElementVisitor... v);

    int count();
}
