package mil.nga.giat.mage.map;

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

    public static class Remove implements MapElements.ComprehensiveMapElementVisitor {

        @Override
        public boolean visit(Circle x, Object id) {
            x.remove();
            return true;
        }

        @Override
        public boolean visit(GroundOverlay x, Object id) {
            x.remove();
            return true;
        }

        @Override
        public boolean visit(Marker x, Object id) {
            x.remove();
            return true;
        }

        @Override
        public boolean visit(Polygon x, Object id) {
            x.remove();
            return true;
        }

        @Override
        public boolean visit(Polyline x, Object id) {
            x.remove();
            return true;
        }

        @Override
        public boolean visit(TileOverlay x, Object id) {
            x.remove();
            return true;
        }
    }

    public static class SetVisibility implements MapElements.ComprehensiveMapElementVisitor {

        private final boolean visible;

        public SetVisibility(boolean visible) {
            this.visible = visible;
        }

        @Override
        public boolean visit(Circle x, Object id) {
            x.setVisible(visible);
            return true;
        }

        @Override
        public boolean visit(GroundOverlay x, Object id) {
            x.setVisible(visible);
            return true;
        }

        @Override
        public boolean visit(Marker x, Object id) {
            x.setVisible(visible);
            return true;
        }

        @Override
        public boolean visit(Polygon x, Object id) {
            x.setVisible(visible);
            return true;
        }

        @Override
        public boolean visit(Polyline x, Object id) {
            x.setVisible(visible);
            return true;
        }

        @Override
        public boolean visit(TileOverlay x, Object id) {
            x.setVisible(visible);
            return true;
        }
    }

    public static class SetZIndex implements MapElements.ComprehensiveMapElementVisitor {

        private final int z;

        public SetZIndex(int z) {
            this.z = z;
        }

        @Override
        public boolean visit(Circle x, Object id) {
            x.setZIndex(z);
            return true;
        }

        @Override
        public boolean visit(GroundOverlay x, Object id) {
            x.setZIndex(z);
            return true;
        }

        @Override
        public boolean visit(Marker x, Object id) {
            x.setZIndex(z);
            return true;
        }

        @Override
        public boolean visit(Polygon x, Object id) {
            x.setZIndex(z);
            return true;
        }

        @Override
        public boolean visit(Polyline x, Object id) {
            x.setZIndex(z);
            return true;
        }

        @Override
        public boolean visit(TileOverlay x, Object id) {
            x.setZIndex(z);
            return true;
        }
    }
}
