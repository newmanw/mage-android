package mil.nga.giat.mage.map

import android.support.annotation.UiThread

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.GroundOverlay
import com.google.android.gms.maps.model.GroundOverlayOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions

sealed class MapElementSpec(val id: Any, val data: Any?) {

    // TODO: move this and the createFor() method out to a separate visitor so there are no view-level linkages in this class
    interface MapElementOwner {

        @UiThread
        fun addedToMap(spec: MapCircleSpec, x: Circle) {
        }

        @UiThread
        fun addedToMap(spec: MapGroundOverlaySpec, x: GroundOverlay) {
        }

        @UiThread
        fun addedToMap(spec: MapMarkerSpec, x: Marker) {
        }

        @UiThread
        fun addedToMap(spec: MapPolygonSpec, x: Polygon) {
        }

        @UiThread
        fun addedToMap(spec: MapPolylineSpec, x: Polyline) {
        }

        @UiThread
        fun addedToMap(spec: MapTileOverlaySpec, x: TileOverlay) {
        }
    }

    interface MapElementSpecVisitor<out R> {

        fun visit(x: MapCircleSpec): R? {
            return null
        }

        fun visit(x: MapGroundOverlaySpec): R? {
            return null
        }

        fun visit(x: MapMarkerSpec): R? {
            return null
        }

        fun visit(x: MapPolygonSpec): R? {
            return null
        }

        fun visit(x: MapPolylineSpec): R? {
            return null
        }

        fun visit(x: MapTileOverlaySpec): R? {
            return null
        }
    }

    @UiThread
    abstract fun createFor(owner: MapElementOwner, map: GoogleMap)

    abstract fun <R> accept(visitor: MapElementSpecVisitor<R>): R?

    final override fun hashCode(): Int {
        return id.hashCode()
    }

    final override fun equals(other: Any?): Boolean {
        return javaClass == other?.javaClass && id == javaClass.cast(other).id
    }
}

class MapMarkerSpec(id: Any, data: Any?, val options: MarkerOptions) : MapElementSpec(id, data) {

    override fun createFor(owner: MapElementOwner, map: GoogleMap) {
        owner.addedToMap(this, map.addMarker(options))
    }

    override fun <R> accept(visitor: MapElementSpecVisitor<R>): R? {
        return visitor.visit(this)
    }
}

class MapCircleSpec(id: Any, data: Any?, val options: CircleOptions) : MapElementSpec(id, data) {

    override fun createFor(owner: MapElementOwner, map: GoogleMap) {
        owner.addedToMap(this, map.addCircle(options))
    }

    override fun <R> accept(visitor: MapElementSpecVisitor<R>): R? {
        return visitor.visit(this)
    }
}

class MapPolylineSpec(id: Any, data: Any?, val options: PolylineOptions) : MapElementSpec(id, data) {

    override fun createFor(owner: MapElementOwner, map: GoogleMap) {
        owner.addedToMap(this, map.addPolyline(options))
    }

    override fun <R> accept(visitor: MapElementSpecVisitor<R>): R? {
        return visitor.visit(this)
    }
}

class MapPolygonSpec(id: Any, data: Any?, val options: PolygonOptions) : MapElementSpec(id, data) {

    override fun createFor(owner: MapElementOwner, map: GoogleMap) {
        owner.addedToMap(this, map.addPolygon(options))
    }

    override fun <R> accept(visitor: MapElementSpecVisitor<R>): R? {
        return visitor.visit(this)
    }
}

class MapTileOverlaySpec(id: Any, data: Any?, val options: TileOverlayOptions) : MapElementSpec(id, data) {

    override fun createFor(owner: MapElementOwner, map: GoogleMap) {
        owner.addedToMap(this, map.addTileOverlay(options))
    }

    override fun <R> accept(visitor: MapElementSpecVisitor<R>): R? {
        return visitor.visit(this)
    }
}

class MapGroundOverlaySpec(id: Any, data: Any?, val options: GroundOverlayOptions) : MapElementSpec(id, data) {

    override fun createFor(owner: MapElementOwner, map: GoogleMap) {
        owner.addedToMap(this, map.addGroundOverlay(options))
    }

    override fun <R> accept(visitor: MapElementSpecVisitor<R>): R? {
        return visitor.visit(this)
    }
}
