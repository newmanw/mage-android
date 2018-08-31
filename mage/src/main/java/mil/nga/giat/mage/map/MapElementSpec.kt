package mil.nga.giat.mage.map

import com.google.android.gms.maps.model.*

sealed class MapElementSpec(val id: Any, val data: Any?) {

    interface MapElementSpecVisitor<out R> {

        @JvmDefault
        fun visit(x: MapCircleSpec): R? {
            return null
        }

        @JvmDefault
        fun visit(x: MapGroundOverlaySpec): R? {
            return null
        }

        @JvmDefault
        fun visit(x: MapMarkerSpec): R? {
            return null
        }

        @JvmDefault
        fun visit(x: MapPolygonSpec): R? {
            return null
        }

        @JvmDefault
        fun visit(x: MapPolylineSpec): R? {
            return null
        }

        @JvmDefault
        fun visit(x: MapTileOverlaySpec): R? {
            return null
        }
    }

    abstract fun <R> accept(visitor: MapElementSpecVisitor<R>): R?

    final override fun hashCode(): Int {
        return id.hashCode()
    }

    final override fun equals(other: Any?): Boolean {
        return javaClass == other?.javaClass && id == javaClass.cast(other).id
    }
}

class MapMarkerSpec(id: Any, data: Any?, val options: MarkerOptions) : MapElementSpec(id, data) {

    override fun <R> accept(visitor: MapElementSpecVisitor<R>): R? {
        return visitor.visit(this)
    }
}

class MapCircleSpec(id: Any, data: Any?, val options: CircleOptions) : MapElementSpec(id, data) {

    override fun <R> accept(visitor: MapElementSpecVisitor<R>): R? {
        return visitor.visit(this)
    }
}

class MapPolylineSpec(id: Any, data: Any?, val options: PolylineOptions) : MapElementSpec(id, data) {

    override fun <R> accept(visitor: MapElementSpecVisitor<R>): R? {
        return visitor.visit(this)
    }
}

class MapPolygonSpec(id: Any, data: Any?, val options: PolygonOptions) : MapElementSpec(id, data) {

    override fun <R> accept(visitor: MapElementSpecVisitor<R>): R? {
        return visitor.visit(this)
    }
}

class MapTileOverlaySpec(id: Any, data: Any?, val options: TileOverlayOptions) : MapElementSpec(id, data) {

    override fun <R> accept(visitor: MapElementSpecVisitor<R>): R? {
        return visitor.visit(this)
    }
}

class MapGroundOverlaySpec(id: Any, data: Any?, val options: GroundOverlayOptions) : MapElementSpec(id, data) {

    override fun <R> accept(visitor: MapElementSpecVisitor<R>): R? {
        return visitor.visit(this)
    }
}
