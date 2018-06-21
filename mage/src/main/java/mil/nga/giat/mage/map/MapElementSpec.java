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

import java.util.Collection;

public abstract class MapElementSpec {

    @UiThread
    public interface MapElementOwner {
        default void addedToMap(MapCircleSpec spec, Circle x) {}
        default void addedToMap(MapGroundOverlaySpec spec, GroundOverlay x) {}
        default void addedToMap(MapMarkerSpec spec, Marker x) {}
        default void addedToMap(MapPolygonSpec spec, Polygon x) {}
        default void addedToMap(MapPolylineSpec spec, Polyline x) {}
        default void addedToMap(MapTileOverlaySpec spec, TileOverlay x) {}
    }

    public final Object id;
    public final Object data;

    protected MapElementSpec(Object id, Object data) {
        this.id = id;
        this.data = data;
    }

    @UiThread
    public abstract void createFor(MapElementOwner owner, GoogleMap map);

    public static final class MapMarkerSpec extends MapElementSpec {

        private final MarkerOptions options;

        public MapMarkerSpec(Object id, Object data, MarkerOptions options) {
            super(id, data);
            this.options = options;
        }

        public void createFor(MapElementOwner owner, GoogleMap map) {
            owner.addedToMap(this, map.addMarker(options));
        }
    }

    public static final class MapCircleSpec extends MapElementSpec {

        private final CircleOptions options;

        public MapCircleSpec(Object id, Object data, CircleOptions options) {
            super(id, data);
            this.options = options;
        }

        public void createFor(MapElementOwner owner, GoogleMap map) {
            owner.addedToMap(this, map.addCircle(options));
        }
    }

    public static final class MapPolylineSpec extends MapElementSpec {

        private final PolylineOptions options;

        public MapPolylineSpec(Object id, Object data, PolylineOptions options) {
            super(id, data);
            this.options = options;
        }

        @Override
        public void createFor(MapElementOwner owner, GoogleMap map) {
            owner.addedToMap(this, map.addPolyline(options));
        }
    }

    public static final class MapPolygonSpec extends MapElementSpec {

        private final PolygonOptions options;

        public MapPolygonSpec(Object id, Object data, PolygonOptions options) {
            super(id, data);
            this.options = options;
        }

        @Override
        public void createFor(MapElementOwner owner, GoogleMap map) {
            owner.addedToMap(this, map.addPolygon(options));
        }
    }

    public static final class MapTileOverlaySpec extends MapElementSpec {

        private final TileOverlayOptions options;

        public MapTileOverlaySpec(Object id, Object data, TileOverlayOptions options) {
            super(id, data);
            this.options = options;
        }

        @Override
        public void createFor(MapElementOwner owner, GoogleMap map) {
            owner.addedToMap(this, map.addTileOverlay(options));
        }
    }

    public static final class MapGroundOverlaySpec extends MapElementSpec {

        private final GroundOverlayOptions options;

        public MapGroundOverlaySpec(Object id, Object data, GroundOverlayOptions options) {
            super(id, data);
            this.options = options;
        }

        @Override
        public void createFor(MapElementOwner owner, GoogleMap map) {
            owner.addedToMap(this, map.addGroundOverlay(options));
        }
    }

    public static final class MapMultiElementSpec extends MapElementSpec {

        private final Collection<? extends MapElementSpec> subSpecs;

        public MapMultiElementSpec(Object id, Object data, Collection<? extends MapElementSpec> subSpecs) {
            super(id, data);
            this.subSpecs = subSpecs;
        }

        @Override
        public void createFor(MapElementOwner owner, GoogleMap map) {
            for (MapElementSpec subSpec : this.subSpecs) {
                subSpec.createFor(owner, map);
            }
        }
    }
}
