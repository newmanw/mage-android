package mil.nga.giat.mage.map;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.TileOverlay;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BasicMapElementContainer implements MapElements {

    private final Map<Circle, Object> circles = new HashMap<>();
    private final Map<GroundOverlay, Object> groundOverlays = new HashMap<>();
    private final Map<Marker, Object> markers = new HashMap<>();
    private final Map<Polygon, Object> polygons = new HashMap<>();
    private final Map<Polyline, Object> polylines = new HashMap<>();
    private final Map<TileOverlay, Object> tileOverlays = new HashMap<>();
    private final Set<Object> allElements = new HashSet<>();
//    private final Map<String, Circle> circleForMapId = new HashMap<>();
//    private final Map<String, GroundOverlay> groundOverlayForMapId = new HashMap<>();
//    private final Map<String, Marker> markerForMapId = new HashMap<>();
//    private final Map<String, Polygon> polygonForMapId = new HashMap<>();
//    private final Map<String, Polyline> polylineForMapId = new HashMap<>();
//    private final Map<String, TileOverlay> tileOverlayForMapId = new HashMap<>();

    @Override
    public MapElements add(Circle x, Object id) {
        circles.put(x, id);
        allElements.add(x);
//        circleForMapId.put(x.getId(), x);
        return this;
    }

    @Override
    public MapElements add(GroundOverlay x, Object id) {
        groundOverlays.put(x, id);
        allElements.add(x);
//        groundOverlayForMapId.put(x.getId(), x);
        return this;
    }

    @Override
    public MapElements add(Marker x, Object id) {
        markers.put(x, id);
        allElements.add(x);
//        markerForMapId.put(x.getId(), x);
        return this;
    }

    @Override
    public MapElements add(Polygon x, Object id) {
        polygons.put(x, id);
        allElements.add(x);
//        polygonForMapId.put(x.getId(), x);
        return this;
    }

    @Override
    public MapElements add(Polyline x, Object id) {
        polylines.put(x, id);
        allElements.add(x);
//        polylineForMapId.put(x.getId(), x);
        return this;
    }

    @Override
    public MapElements add(TileOverlay x, Object id) {
        tileOverlays.put(x, id);
        allElements.add(x);
//        tileOverlayForMapId.put(x.getId(), x);
        return this;
    }

    @Override
    public boolean contains(Circle x) {
        return allElements.contains(x);
//        return circleForMapId.get(x.getId()) != null;
    }

    @Override
    public boolean contains(GroundOverlay x) {
        return allElements.contains(x);
//        return groundOverlayForMapId.get(x.getId()) != null;
    }

    @Override
    public boolean contains(Marker x) {
        return allElements.contains(x);
//        return markerForMapId.get(x.getId()) != null;
    }

    @Override
    public boolean contains(Polygon x) {
        return allElements.contains(x);
//        return polygonForMapId.get(x.getId()) != null;
    }

    @Override
    public boolean contains(Polyline x) {
        return allElements.contains(x);
//        return polylineForMapId.get(x.getId()) != null;
    }

    @Override
    public boolean contains(TileOverlay x) {
        return allElements.contains(x);
//        return tileOverlayForMapId.get(x.getId()) != null;
    }

    @Override
    public void remove(Circle x) {
        circles.remove(x);
        allElements.remove(x);
    }

    @Override
    public void remove(GroundOverlay x) {
        groundOverlays.remove(x);
        allElements.remove(x);
    }

    @Override
    public void remove(Marker x) {
        markers.remove(x);
        allElements.remove(x);
    }

    @Override
    public void remove(Polygon x) {
        polygons.remove(x);
        allElements.remove(x);
    }

    @Override
    public void remove(Polyline x) {
        polylines.remove(x);
        allElements.remove(x);
    }

    @Override
    public void remove(TileOverlay x) {
        tileOverlays.remove(x);
        allElements.remove(x);
    }

    @Override
    public void forEach(ComprehensiveMapElementVisitor v) {
        for (Map.Entry<Marker, Object> e : markers.entrySet()) {
            if (!v.visit(e.getKey(), e.getValue())) {
                return;
            }
        }
        for (Map.Entry<Circle, Object> e : circles.entrySet()) {
            if (!v.visit(e.getKey(), e.getValue())) {
                return;
            }
        }
        for (Map.Entry<Polygon, Object> e : polygons.entrySet()) {
            if (!v.visit(e.getKey(), e.getValue())) {
                return;
            }
        }
        for (Map.Entry<Polyline, Object> e : polylines.entrySet()) {
            if (!v.visit(e.getKey(), e.getValue())) {
                return;
            }
        }
        for (Map.Entry<GroundOverlay, Object> e : groundOverlays.entrySet()) {
            if (!v.visit(e.getKey(), e.getValue())) {
                return;
            }
        }
        for (Map.Entry<TileOverlay, Object> e : tileOverlays.entrySet()) {
            if (!v.visit(e.getKey(), e.getValue())) {
                return;
            }
        }
    }

    @Override
    public int count() {
        return allElements.size();
    }
}
