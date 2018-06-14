package mil.nga.giat.mage.map;

import android.support.annotation.NonNull;
import android.support.annotation.UiThread;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;

public abstract class MapDisplayObject {

    protected MapDisplayObject(Object id) {
        this.id = id;
    }

    @UiThread
    public interface MapOwner {

        @NonNull
        GoogleMap getMap();
        default void addedToMap(MapDisplayObject object, Marker marker) {}
        default void addedToMap(MapDisplayObject object, Circle circle) {}
        default void addedToMap(MapDisplayObject object, Polyline polyline) {}
        default void addedToMap(MapDisplayObject object, Polygon polygon) {}
        default void addedToMap(MapDisplayObject object, TileOverlay overlay) {}
        default void addedToMap(MapDisplayObject object, GroundOverlay overlay) {}
    }

    public final Object id;

    public abstract void createFor(MapOwner owner);

    public static final class MapMarker extends MapDisplayObject {

        private final MarkerOptions options;

        public MapMarker(Object id, MarkerOptions options) {
            super(id);
            this.options = options;
        }

        public void createFor(MapOwner owner) {
            owner.addedToMap(this, owner.getMap().addMarker(options));
        }
    }

    public static final class MapCircle extends MapDisplayObject {

        private final CircleOptions options;

        public MapCircle(Object id, CircleOptions options) {
            super(id);
            this.options = options;
        }

        public void createFor(MapOwner owner) {
            owner.addedToMap(this, owner.getMap().addCircle(options));
        }
    }

    public static final class MapPolyline extends MapDisplayObject {

        private final PolylineOptions options;

        public MapPolyline(Object id, PolylineOptions options) {
            super(id);
            this.options = options;
        }

        @Override
        public void createFor(MapOwner owner) {
            owner.addedToMap(this, owner.getMap().addPolyline(options));
        }
    }

    public static final class MapPolygon extends MapDisplayObject {

        private final PolygonOptions options;

        public MapPolygon(Object id, PolygonOptions options) {
            super(id);
            this.options = options;
        }

        @Override
        public void createFor(MapOwner owner) {
            owner.addedToMap(this, owner.getMap().addPolygon(options));
        }
    }

    public static final class MapTileOverlay extends MapDisplayObject {

        private final TileOverlayOptions options;

        public MapTileOverlay(Object id, TileOverlayOptions options) {
            super(id);
            this.options = options;
        }

        @Override
        public void createFor(MapOwner owner) {
            owner.addedToMap(this, owner.getMap().addTileOverlay(options));
        }
    }

    public static final class MapGroundOverlay extends MapDisplayObject {

        private final GroundOverlayOptions options;

        public MapGroundOverlay(Object id, GroundOverlayOptions options) {
            super(id);
            this.options = options;
        }

        @Override
        public void createFor(MapOwner owner) {
            owner.addedToMap(this, owner.getMap().addGroundOverlay(options));
        }
    }
}
