package mil.nga.giat.mage.map.cache

import android.support.annotation.UiThread
import android.support.annotation.WorkerThread
import com.google.android.gms.maps.model.*
import mil.nga.giat.mage.map.*
import mil.nga.giat.mage.map.view.MapOwner
import java.io.Closeable
import java.util.concurrent.Callable

/**
 * A MapDataProvider represents a specific data format that can put overlays on a map.
 *
 * TODO: eventually this should change to async calls from the main thread to resolveResource() and
 * createMapLayerAdapter() so a provider can use its own configured background
 * executor based on what kind of work it does to resolve resources and create map
 * objects, rather than whatever executor [MapDataManager] uses.
 * CompletableFuture would be nice but requires min SDK 24 or higher.  an option could
 * be to use https://github.com/retrostreams/android-retrofuture, or maybe rxjava/rxandroid or
 * some such thing.
 */
interface MapDataProvider {

    /**
     * Does this provider recognize the given resource as its data type?
     *
     * @param resource
     * @return
     *
     * TODO: add extra known information about the uri content, e.g., from a HEAD request
     */
    @WorkerThread
    fun canHandleResource(resource: MapDataResource): Boolean

    /**
     * Attempt to import the given resource's content as this provider's data type and return the
     * [resolved][mil.nga.giat.mage.map.cache.MapDataResource.Resolved] resource.
     *
     * @param resource a [MapDataResource] to resolve
     * @return a resolved [resource][MapDataResource]
     * @throws MapDataResolveException if there is an error resolving the resource
     *
     * TODO: an argument for the owning repository may be necessary at some point
     */
    @WorkerThread
    @Throws(MapDataResolveException::class)
    fun resolveResource(resource: MapDataResource): MapDataResource

    @WorkerThread
    fun createAdapterForLayer(layer: MapLayerDescriptor): LayerAdapter

    interface LayerAdapter : Closeable {

        /**
         * Return true if this layer query returns elements bound to a particulary location, which is really
         * anything other than a [com.google.android.gms.maps.model.TileOverlay]
         * [spec][mil.nga.giat.mage.map.MapTileOverlaySpec].
         */
        @UiThread
        fun hasDynamicElements(): Boolean

        /**
         * Return true if this layer query supports parameterized element fetches, meaning fetches efficiently
         * constrain results to a given bounding box and possible other parameters.  Examples are a SQLite
         * database with a spatial index, or an OGC service.  If this layer query does not support dynamic fetch,
         * the expectation is that a fetch always returns all the elements for the subject [layer][MapLayerDescriptor].
         */
        @UiThread
        fun supportsDynamicFetch(): Boolean

        /**
         * Fetch the map elements in the given bounds for the [layer][mil.nga.giat.mage.map.cache.MapLayerDescriptor] bound to this query.
         * @param bounds the bounding box area of interest
         * @return a map of [map elements][MapElementSpec] keyed by provider-specific consistent/persistent IDs
         * @todo add a z-index parameter to return elements with a given z-index, and optimistically assume the z-index will not change between the time the query starts and the elements are drawn on the map
         */
        @WorkerThread
        fun fetchMapElements(bounds: LatLngBounds): Map<Any, MapElementSpec>

        @UiThread
        @JvmDefault
        fun addedToMap(spec: MapCircleSpec, elmt: Circle) {}

        @UiThread
        @JvmDefault
        fun addedToMap(spec: MapGroundOverlaySpec, elmt: GroundOverlay) {}

        @UiThread
        @JvmDefault
        fun addedToMap(spec: MapMarkerSpec, elmt: Marker) {}

        @UiThread
        @JvmDefault
        fun addedToMap(spec: MapPolygonSpec, elmt: Polygon) {}

        @UiThread
        @JvmDefault
        fun addedToMap(spec: MapPolylineSpec, elmt: Polyline) {}

        @UiThread
        @JvmDefault
        fun addedToMap(spec: MapTileOverlaySpec, elmt: TileOverlay) {}

        @UiThread
        @JvmDefault
        fun removedFromMap(elmt: Circle, id: Any) {}

        @UiThread
        @JvmDefault
        fun removedFromMap(elmt: GroundOverlay, id: Any) {}

        @UiThread
        @JvmDefault
        fun removedFromMap(elmt: Marker, id: Any) {}

        @UiThread
        @JvmDefault
        fun removedFromMap(elmt: Polygon, id: Any) {}

        @UiThread
        @JvmDefault
        fun removedFromMap(elmt: Polyline, id: Any) {}

        @UiThread
        @JvmDefault
        fun removedFromMap(elmt: TileOverlay, id: Any) {}

        @UiThread
        @JvmDefault
        fun onClick(x: Circle, id: Any): Callable<String>? {
            return null
        }

        @UiThread
        @JvmDefault
        fun onClick(x: GroundOverlay, id: Any): Callable<String>? {
            return null
        }

        @UiThread
        @JvmDefault
        fun onClick(x: Marker, id: Any): Callable<String>? {
            return null
        }

        @UiThread
        @JvmDefault
        fun onClick(x: Polygon, id: Any): Callable<String>? {
            return null
        }

        @UiThread
        @JvmDefault
        fun onClick(x: Polyline, id: Any): Callable<String>? {
            return null
        }

        @UiThread
        @JvmDefault
        fun onClick(pos: LatLng, mapOwner: MapOwner): Callable<String>? {
            return null
        }
    }
}
