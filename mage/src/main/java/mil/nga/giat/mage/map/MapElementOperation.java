package mil.nga.giat.mage.map;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.TileOverlay;


public class MapElementOperation {

    public static final Remove REMOVE = new Remove();
    public static final SetVisibility SHOW = new SetVisibility(true);
    public static final SetVisibility HIDE = new SetVisibility(false);
    public static final MapElements.CircleVisitor<Circle> GET_CIRCLE = (Circle x, Object id) -> x;
    public static final MapElements.GroundOverlayVisitor<GroundOverlay> GET_GROUND_OVERLAY = (GroundOverlay x, Object id) -> x;
    public static final MapElements.MarkerVisitor<Marker> GET_MARKER = (Marker x, Object id) -> x;
    public static final MapElements.PolygonVisitor<Polygon> GET_POLYGON = (Polygon x, Object id) -> x;
    public static final MapElements.PolylineVisitor<Polyline> GET_POLYLINE = (Polyline x, Object id) -> x;
    public static final MapElements.TileOverlayVisitor<TileOverlay> GET_TILE_OVERLAY = (TileOverlay x, Object id) -> x;
    public static final MapElements.ComprehensiveMapElementVisitor<Void> NOOP = new MapElements.ComprehensiveMapElementVisitor<Void>() {};


    public static class Remove implements MapElements.ComprehensiveMapElementVisitor<Boolean> {

        @Override
        public Boolean visit(@NonNull Circle x, @NonNull Object id) {
            x.remove();
            return true;
        }

        @Override
        public Boolean visit(@NonNull GroundOverlay x, @NonNull Object id) {
            x.remove();
            return true;
        }

        @Override
        public Boolean visit(@NonNull Marker x, @NonNull Object id) {
            x.remove();
            return true;
        }

        @Override
        public Boolean visit(@NonNull Polygon x, @NonNull Object id) {
            x.remove();
            return true;
        }

        @Override
        public Boolean visit(@NonNull Polyline x, @NonNull Object id) {
            x.remove();
            return true;
        }

        @Override
        public Boolean visit(@NonNull TileOverlay x, @NonNull Object id) {
            x.remove();
            return true;
        }
    }

    public static class CreateElementOnMap implements MapElementSpec.MapElementSpecVisitor<Void> {

        private final GoogleMap map;
        private final MapElements.ComprehensiveMapElementVisitor<?> then;

        public CreateElementOnMap(@NonNull GoogleMap map, @Nullable MapElements.ComprehensiveMapElementVisitor<?> then) {
            this.map = map;
            this.then = then == null ? NOOP : then;
        }

        @Nullable
        @Override
        public Void visit(@NonNull MapCircleSpec x) {
            then.visit(map.addCircle(x.getOptions()), x.getId());
            return null;
        }

        @Nullable
        @Override
        public Void visit(@NonNull MapGroundOverlaySpec x) {
            then.visit(map.addGroundOverlay(x.getOptions()), x.getId());
            return null;
        }

        @Nullable
        @Override
        public Void visit(@NonNull MapMarkerSpec x) {
            then.visit(map.addMarker(x.getOptions()), x.getId());
            return null;
        }

        @Nullable
        @Override
        public Void visit(@NonNull MapPolygonSpec x) {
            then.visit(map.addPolygon(x.getOptions()), x.getId());
            return null;
        }

        @Nullable
        @Override
        public Void visit(@NonNull MapPolylineSpec x) {
            then.visit(map.addPolyline(x.getOptions()), x.getId());
            return null;
        }

        @Nullable
        @Override
        public Void visit(@NonNull MapTileOverlaySpec x) {
            then.visit(map.addTileOverlay(x.getOptions()), x.getId());
            return null;
        }
    }

    public static class SetVisibility implements MapElements.ComprehensiveMapElementVisitor<Boolean> {

        private final boolean visible;

        public SetVisibility(boolean visible) {
            this.visible = visible;
        }

        @Override
        public Boolean visit(@NonNull Circle x, @NonNull Object id) {
            x.setVisible(visible);
            return true;
        }

        @Override
        public Boolean visit(@NonNull GroundOverlay x, @NonNull Object id) {
            x.setVisible(visible);
            return true;
        }

        @Override
        public Boolean visit(@NonNull Marker x, @NonNull Object id) {
            x.setVisible(visible);
            return true;
        }

        @Override
        public Boolean visit(@NonNull Polygon x, @NonNull Object id) {
            x.setVisible(visible);
            return true;
        }

        @Override
        public Boolean visit(@NonNull Polyline x, @NonNull Object id) {
            x.setVisible(visible);
            return true;
        }

        @Override
        public Boolean visit(@NonNull TileOverlay x, @NonNull Object id) {
            x.setVisible(visible);
            return true;
        }
    }

    public static class SetZIndex implements MapElements.ComprehensiveMapElementVisitor<Boolean> {

        private final int z;

        public SetZIndex(int z) {
            this.z = z;
        }

        @Override
        public Boolean visit(@NonNull Circle x, @NonNull Object id) {
            x.setZIndex(z);
            return true;
        }

        @Override
        public Boolean visit(@NonNull GroundOverlay x, @NonNull Object id) {
            x.setZIndex(z);
            return true;
        }

        @Override
        public Boolean visit(@NonNull Marker x, @NonNull Object id) {
            x.setZIndex(z);
            return true;
        }

        @Override
        public Boolean visit(@NonNull Polygon x, @NonNull Object id) {
            x.setZIndex(z);
            return true;
        }

        @Override
        public Boolean visit(@NonNull Polyline x, @NonNull Object id) {
            x.setZIndex(z);
            return true;
        }

        @Override
        public Boolean visit(@NonNull TileOverlay x, @NonNull Object id) {
            x.setZIndex(z);
            return true;
        }
    }
}
