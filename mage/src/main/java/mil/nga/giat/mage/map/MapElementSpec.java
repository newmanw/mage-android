package mil.nga.giat.mage.map;

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

public abstract class MapElementSpec {

    public interface MapElementOwner {
        @UiThread
        default void addedToMap(MapCircleSpec spec, Circle x) {}
        @UiThread
        default void addedToMap(MapGroundOverlaySpec spec, GroundOverlay x) {}
        @UiThread
        default void addedToMap(MapMarkerSpec spec, Marker x) {}
        @UiThread
        default void addedToMap(MapPolygonSpec spec, Polygon x) {}
        @UiThread
        default void addedToMap(MapPolylineSpec spec, Polyline x) {}
        @UiThread
        default void addedToMap(MapTileOverlaySpec spec, TileOverlay x) {}
    }

    public interface MapElementSpecVisitor {
        default void visit(MapCircleSpec x) {};
        default void visit(MapGroundOverlaySpec x) {};
        default void visit(MapMarkerSpec x) {};
        default void visit(MapPolygonSpec x) {};
        default void visit(MapPolylineSpec x) {};
        default void visit(MapTileOverlaySpec x) {};
    }

    public final Object id;
    public final Object data;

    private MapElementSpec(Object id, Object data) {
        this.id = id;
        this.data = data;
    }

    @UiThread
    public abstract void createFor(MapElementOwner owner, GoogleMap map);

    public abstract void accept(MapElementSpecVisitor visitor);

    public static final class MapMarkerSpec extends MapElementSpec {

        public final MarkerOptions options;

        public MapMarkerSpec(Object id, Object data, MarkerOptions options) {
            super(id, data);
            this.options = options;
        }

        public void createFor(MapElementOwner owner, GoogleMap map) {
            owner.addedToMap(this, map.addMarker(options));
        }

        @Override
        public void accept(MapElementSpecVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class MapCircleSpec extends MapElementSpec {

        public final CircleOptions options;

        public MapCircleSpec(Object id, Object data, CircleOptions options) {
            super(id, data);
            this.options = options;
        }

        public void createFor(MapElementOwner owner, GoogleMap map) {
            owner.addedToMap(this, map.addCircle(options));
        }

        @Override
        public void accept(MapElementSpecVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class MapPolylineSpec extends MapElementSpec {

        public final PolylineOptions options;

        public MapPolylineSpec(Object id, Object data, PolylineOptions options) {
            super(id, data);
            this.options = options;
        }

        @Override
        public void createFor(MapElementOwner owner, GoogleMap map) {
            owner.addedToMap(this, map.addPolyline(options));
        }

        @Override
        public void accept(MapElementSpecVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class MapPolygonSpec extends MapElementSpec {

        public final PolygonOptions options;

        public MapPolygonSpec(Object id, Object data, PolygonOptions options) {
            super(id, data);
            this.options = options;
        }

        @Override
        public void createFor(MapElementOwner owner, GoogleMap map) {
            owner.addedToMap(this, map.addPolygon(options));
        }

        @Override
        public void accept(MapElementSpecVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class MapTileOverlaySpec extends MapElementSpec {

        public final TileOverlayOptions options;

        public MapTileOverlaySpec(Object id, Object data, TileOverlayOptions options) {
            super(id, data);
            this.options = options;
        }

        @Override
        public void createFor(MapElementOwner owner, GoogleMap map) {
            owner.addedToMap(this, map.addTileOverlay(options));
        }

        @Override
        public void accept(MapElementSpecVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class MapGroundOverlaySpec extends MapElementSpec {

        public final GroundOverlayOptions options;

        public MapGroundOverlaySpec(Object id, Object data, GroundOverlayOptions options) {
            super(id, data);
            this.options = options;
        }

        @Override
        public void createFor(MapElementOwner owner, GoogleMap map) {
            owner.addedToMap(this, map.addGroundOverlay(options));
        }

        @Override
        public void accept(MapElementSpecVisitor visitor) {
            visitor.visit(this);
        }
    }

    // TODO: is this useful? it violates the intention of interacting only with GoogleMap shapes on a one-to-one mapping
//    public static final class MapMultiElementSpec extends MapElementSpec {
//
//        public final Collection<? extends MapElementSpec> subSpecs;
//
//        public MapMultiElementSpec(Object id, Object data, Collection<? extends MapElementSpec> subSpecs) {
//            super(id, data);
//            this.subSpecs = subSpecs;
//        }
//
//        @Override
//        public void createFor(MapElementOwner owner, GoogleMap map) {
//            for (MapElementSpec subSpec : this.subSpecs) {
//                subSpec.createFor(owner, map);
//            }
//        }
//    }
}
