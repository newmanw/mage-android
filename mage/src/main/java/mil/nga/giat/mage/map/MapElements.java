package mil.nga.giat.mage.map;

import android.support.annotation.NonNull;
import android.support.annotation.UiThread;

import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.TileOverlay;

import org.jetbrains.annotations.NotNull;

import mil.nga.giat.mage.map.view.MapOwner;

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

    interface ComprehensiveMapElementVisitor<T> extends
        CircleVisitor<T>,
        GroundOverlayVisitor<T>,
        MarkerVisitor<T>,
        PolygonVisitor<T>,
        PolylineVisitor<T>,
        TileOverlayVisitor<T> {

        @Override
        default T visit(@NonNull Circle x, @NonNull Object id) { return null; }
        @Override
        default T visit(@NonNull GroundOverlay x, @NonNull  Object id) { return null; }
        @Override
        default T visit(@NonNull Marker x, @NonNull Object id) { return null; }
        @Override
        default T visit(@NonNull Polygon x, @NonNull Object id) { return null; }
        @Override
        default T visit(@NonNull Polyline x, @NonNull Object id) { return null; }
        @Override
        default T visit(@NonNull TileOverlay x, @NonNull Object id) { return null; }
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
    boolean contains(@NonNull MapElementSpec spec);

    <T> T withElement(Circle x, CircleVisitor<T> action);
    <T> T withElement(GroundOverlay x, GroundOverlayVisitor<T> action);
    <T> T withElement(Marker x, MarkerVisitor<T> action);
    <T> T withElement(Polygon x, PolygonVisitor<T> action);
    <T> T withElement(Polyline x, PolylineVisitor<T> action);
    <T> T withElement(TileOverlay x, TileOverlayVisitor<T> action);

    <T> T withElementForSpec(MapCircleSpec x, CircleVisitor<T> action);
    <T> T withElementForSpec(MapGroundOverlaySpec x, GroundOverlayVisitor<T> action);
    <T> T withElementForSpec(MapMarkerSpec x, MarkerVisitor<T> action);
    <T> T withElementForSpec(MapPolygonSpec x, PolygonVisitor<T> action);
    <T> T withElementForSpec(MapPolylineSpec x, PolylineVisitor<T> action);
    <T> T withElementForSpec(MapTileOverlaySpec x, TileOverlayVisitor<T> action);

    void remove(Circle x);
    void remove(GroundOverlay x);
    void remove(Marker x);
    void remove(Polygon x);
    void remove(Polyline x);
    void remove(TileOverlay x);

    void forEach(ComprehensiveMapElementVisitor<Boolean> v);

    int count();

    /**
     * Remove all elements from the map.
     */
    void clear();
}
