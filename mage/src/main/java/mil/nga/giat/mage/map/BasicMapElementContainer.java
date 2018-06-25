package mil.nga.giat.mage.map;

import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.TileOverlay;

import java.util.HashMap;
import java.util.Map;

public class BasicMapElementContainer implements MapElements {

    private static Object safeIdOfElement(Circle x, Object id) {
        return id == null ? x.getId() : id;
    }

    private static Object safeIdOfElement(GroundOverlay x, Object id) {
        return id == null ? x.getId() : id;
    }

    private static Object safeIdOfElement(Marker x, Object id) {
        return id == null ? x.getId() : id;
    }

    private static Object safeIdOfElement(Polygon x, Object id) {
        return id == null ? x.getId() : id;
    }

    private static Object safeIdOfElement(Polyline x, Object id) {
        return id == null ? x.getId() : id;
    }

    private static Object safeIdOfElement(TileOverlay x, Object id) {
        return id == null ? x.getId() : id;
    }

    private final Map<Circle, Object> circles = new HashMap<>();
    private final Map<GroundOverlay, Object> groundOverlays = new HashMap<>();
    private final Map<Marker, Object> markers = new HashMap<>();
    private final Map<Polygon, Object> polygons = new HashMap<>();
    private final Map<Polyline, Object> polylines = new HashMap<>();
    private final Map<TileOverlay, Object> tileOverlays = new HashMap<>();
    private final Map<Object, Circle> circleForId = new HashMap<>();
    private final Map<Object, GroundOverlay> groundOverlayForId = new HashMap<>();
    private final Map<Object, Marker> markerForId = new HashMap<>();
    private final Map<Object, Polygon> polygonForId = new HashMap<>();
    private final Map<Object, Polyline> polylineForId = new HashMap<>();
    private final Map<Object, TileOverlay> tileOverlayForId = new HashMap<>();

    @Override
    public MapElements add(Circle x, Object id) {
        circles.put(x, safeIdOfElement(x, id));
        circleForId.put(safeIdOfElement(x, id), x);
        return this;
    }

    @Override
    public MapElements add(GroundOverlay x, Object id) {
        groundOverlays.put(x, safeIdOfElement(x, id));
        groundOverlayForId.put(safeIdOfElement(x, id), x);
        return this;
    }

    @Override
    public MapElements add(Marker x, Object id) {
        markers.put(x, safeIdOfElement(x, id));
        markerForId.put(safeIdOfElement(x, id), x);
        return this;
    }

    @Override
    public MapElements add(Polygon x, Object id) {
        polygons.put(x, safeIdOfElement(x, id));
        polygonForId.put(safeIdOfElement(x, id), x);
        return this;
    }

    @Override
    public MapElements add(Polyline x, Object id) {
        polylines.put(x, safeIdOfElement(x, id));
        polylineForId.put(safeIdOfElement(x, id), x);
        return this;
    }

    @Override
    public MapElements add(TileOverlay x, Object id) {
        tileOverlays.put(x, safeIdOfElement(x, id));
        tileOverlayForId.put(safeIdOfElement(x, id), x);
        return this;
    }

    @Override
    public boolean contains(Circle x) {
        return circles.containsKey(x);
    }

    @Override
    public boolean contains(GroundOverlay x) {
        return groundOverlays.containsKey(x);
    }

    @Override
    public boolean contains(Marker x) {
        return markers.containsKey(x);
    }

    @Override
    public boolean contains(Polygon x) {
        return polygons.containsKey(x);
    }

    @Override
    public boolean contains(Polyline x) {
        return polylines.containsKey(x);
    }

    @Override
    public boolean contains(TileOverlay x) {
        return tileOverlays.containsKey(x);
    }

    @Override
    public <R> R withElement(Circle x, CircleVisitor<R> action) {
        return action.visit(x, circles.get(x));
    }

    @Override
    public <R> R withElement(GroundOverlay x, GroundOverlayVisitor<R> action) {
        return action.visit(x, groundOverlays.get(x));
    }

    @Override
    public <R> R withElement(Marker x, MarkerVisitor<R> action) {
        return action.visit(x, markers.get(x));
    }

    @Override
    public <R> R withElement(Polygon x, PolygonVisitor<R> action) {
        return action.visit(x, polygons.get(x));
    }

    @Override
    public <R> R withElement(Polyline x, PolylineVisitor<R> action) {
        return action.visit(x, polylines.get(x));
    }

    @Override
    public <R> R withElement(TileOverlay x, TileOverlayVisitor<R> action) {
        return action.visit(x, tileOverlays.get(x));
    }

    @Override
    public <R> R withElementForId(Object id, CircleVisitor<R> action) {
        return action.visit(circleForId.get(id), id);
    }

    @Override
    public <R> R withElementForId(Object id, GroundOverlayVisitor<R> action) {
        return action.visit(groundOverlayForId.get(id), id);
    }

    @Override
    public <R> R withElementForId(Object id, MarkerVisitor<R> action) {
        return action.visit(markerForId.get(id), id);
    }

    @Override
    public <R> R withElementForId(Object id, PolygonVisitor<R> action) {
        return action.visit(polygonForId.get(id), id);
    }

    @Override
    public <R> R withElementForId(Object id, PolylineVisitor<R> action) {
        return action.visit(polylineForId.get(id), id);
    }

    @Override
    public <R> R withElementForId(Object id, TileOverlayVisitor<R> action) {
        return action.visit(tileOverlayForId.get(id), id);
    }

    @Override
    public void remove(Circle x) {
        circleForId.remove(circles.remove(x));
    }

    @Override
    public void remove(GroundOverlay x) {
        groundOverlayForId.remove(groundOverlays.remove(x));
    }

    @Override
    public void remove(Marker x) {
        markerForId.remove(markers.remove(x));
    }

    @Override
    public void remove(Polygon x) {
        polygonForId.remove(polygons.remove(x));
    }

    @Override
    public void remove(Polyline x) {
        polylineForId.remove(polylines.remove(x));
    }

    @Override
    public void remove(TileOverlay x) {
        tileOverlayForId.remove(tileOverlays.remove(x));
    }

    @Override
    public void forEach(ComprehensiveMapElementVisitor... visitors) {
        for (Map.Entry<Marker, Object> e : markers.entrySet()) {
            for (ComprehensiveMapElementVisitor v : visitors) {
                if (!v.visit(e.getKey(), e.getValue())) {
                    return;
                }
            }
        }
        for (Map.Entry<Circle, Object> e : circles.entrySet()) {
            for (ComprehensiveMapElementVisitor v : visitors) {
                if (!v.visit(e.getKey(), e.getValue())) {
                    return;
                }
            }
        }
        for (Map.Entry<Polygon, Object> e : polygons.entrySet()) {
            for (ComprehensiveMapElementVisitor v : visitors) {
                if (!v.visit(e.getKey(), e.getValue())) {
                    return;
                }
            }
        }
        for (Map.Entry<Polyline, Object> e : polylines.entrySet()) {
            for (ComprehensiveMapElementVisitor v : visitors) {
                if (!v.visit(e.getKey(), e.getValue())) {
                    return;
                }
            }
        }
        for (Map.Entry<GroundOverlay, Object> e : groundOverlays.entrySet()) {
            for (ComprehensiveMapElementVisitor v : visitors) {
                if (!v.visit(e.getKey(), e.getValue())) {
                    return;
                }
            }
        }
        for (Map.Entry<TileOverlay, Object> e : tileOverlays.entrySet()) {
            for (ComprehensiveMapElementVisitor v : visitors) {
                if (!v.visit(e.getKey(), e.getValue())) {
                    return;
                }
            }
        }
    }

    @Override
    public int count() {
        return circles.size() + groundOverlays.size() + markers.size() + polygons.size() + polylines.size() + tileOverlays.size();
    }
}
