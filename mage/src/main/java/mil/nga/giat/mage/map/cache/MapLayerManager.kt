package mil.nga.giat.mage.map.cache

import android.arch.lifecycle.Observer
import android.support.annotation.UiThread
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.data.Resource.Status
import mil.nga.giat.mage.map.*
import mil.nga.giat.mage.map.MapElementSpec.MapElementSpecVisitor
import mil.nga.giat.mage.map.view.MapLayersViewModel
import mil.nga.giat.mage.map.view.MapOwner
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReference

/**
 * A `MapLayerManager` binds [layer data][MapLayerDescriptor] from various
 * [sources][MapDataRepository] to visual objects on a [GoogleMap].
 */
class MapLayerManager(
    private val mapOwner: MapOwner,
    private val layersModel: MapLayersViewModel,
    mapDataManager: MapDataManager) :
    GoogleMap.OnCameraIdleListener,
    GoogleMap.OnMarkerClickListener,
    GoogleMap.OnMapClickListener,
    GoogleMap.OnCircleClickListener,
    GoogleMap.OnPolylineClickListener,
    GoogleMap.OnPolygonClickListener,
    GoogleMap.OnGroundOverlayClickListener,
    MapLayersViewModel.LayerListener {

    private val providers = mapDataManager.providers
    private val layersOnMap = HashMap<MapLayersViewModel.Layer, ViewLayer>()

    val map: GoogleMap
        get() = mapOwner.map

    /**
     * A `ViewLayer` is the visual representation on a [GoogleMap] of the data
     * a [MapLayerDescriptor] describes.  A [MapDataProvider] creates instances
     * of this class from the data it provides to be added to a map.  Instances of this
     * class comprise visual objects from the Google Maps API, such as
     * [tiles][TileProvider],
     * [markers][Marker],
     * [polygons][Polygon],
     * etc.
     */
    @UiThread
    private inner class ViewLayer(val adapter: MapDataProvider.LayerQuery) {

        val mapElements = BasicMapElementContainer()
        var isVisible = true
        var zIndex = 0
            set(z) {
                field = z
                mapElements.forEach(MapElementOperation.SetZIndex(z))
            }

        /**
         * Remove this layer's visible and hidden objects from the map.
         * Clear whatever resources this layer might hold such as data source
         * connections or large geometry collections and prepare for garbage collection.
         */
        fun removeFromMap() {
            mapElements.forEach(MapElementOperation.REMOVE)
            adapter.onLayerRemoved()
        }

        fun show() {
            mapElements.forEach(MapElementOperation.SHOW)
            isVisible = true
        }

        fun hide() {
            mapElements.forEach(MapElementOperation.HIDE)
            isVisible = false
        }

        fun ownsElement(x: Circle): Boolean {
            return mapElements.contains(x)
        }

        fun ownsElement(x: GroundOverlay): Boolean {
            return mapElements.contains(x)
        }

        fun ownsElement(x: Marker): Boolean {
            return mapElements.contains(x)
        }

        fun ownsElement(x: Polygon): Boolean {
            return mapElements.contains(x)
        }

        fun ownsElement(x: Polyline): Boolean {
            return mapElements.contains(x)
        }

        fun onMarkerClick(x: Marker) {
            val getInfo = mapElements.withElement<Callable<String>>(x) { x1, id -> adapter.onClick(x1, id) }
            // TODO: get the info on background thread and get it on the map popup
        }

        fun onCircleClick(x: Circle) {
            val getInfo = mapElements.withElement<Callable<String>>(x) { x1, id -> adapter.onClick(x1, id) }
            // TODO: get the info on background thread and get it on the map popup
        }

        fun onPolylineClick(x: Polyline) {
            val getInfo = mapElements.withElement<Callable<String>>(x) { x1, id -> adapter.onClick(x1, id) }
            // TODO: get the info on background thread and get it on the map popup
        }

        fun onPolygonClick(x: Polygon) {
            val getInfo = mapElements.withElement<Callable<String>>(x) { x1, id -> adapter.onClick(x1, id) }
            // TODO: get the info on background thread and get it on the map popup
        }

        fun onGroundOverlayClick(x: GroundOverlay) {
            val getInfo = mapElements.withElement<Callable<String>>(x) { x1, id -> adapter.onClick(x1, id) }
            // TODO: get the info on background thread and get it on the map popup
        }

        fun onMapClick(pos: LatLng, mapOwner: MapOwner) {
            adapter.onClick(pos, mapOwner)
        }
    }

    init {
        layersModel.layersInZOrder.observe(mapOwner, Observer<Resource<List<MapLayersViewModel.Layer>>> { this.onMapLayersChanged(it) })
        layersModel.layerEvents.listen(mapOwner, this)
    }

    override fun layerVisibilityChanged(layer: MapLayersViewModel.Layer, position: Int) {
        var viewLayer = layersOnMap[layer]
        if (layer.isVisible) {
            if (viewLayer == null) {
                // load layer elements for bounds
                viewLayer = ViewLayer(layer.adapter)
                layersOnMap[layer] = viewLayer
            }
            viewLayer.show()
        }
        else {
            viewLayer?.hide()
        }
    }

    override fun zOrderShift(range: IntRange) {
        for (z in range) {
            val modelLayer = layersModel.layerAt(z)
            val viewLayer = layersOnMap[modelLayer]
            viewLayer?.zIndex = modelLayer.zIndex
        }
    }

    override fun layerElementsChanged(layer: MapLayersViewModel.Layer, position: Int, removed: Map<Any, MapElementSpec>) {
        val viewLayer = layersOnMap[layer] ?: return
        if (Status.Loading == layer.elements.status) {

        }
        for (rm in removed.values) {

        }
        for (el in layer.elements.content!!.values) {
            TODO("create the elements if they don't exist - move MapElementSpec.createFor() somewhere else, maybe another spec visitor")
        }
    }

    override fun onCameraIdle() {
        layersModel.mapBoundsChanged(map.projection.visibleRegion.latLngBounds)
    }

    override fun onMapClick(pos: LatLng) {
        val modelLayers = layersModel.layersInZOrder.value?.content ?: return
        for (modelLayer in modelLayers) {
            val layer = layersOnMap[modelLayer]
            layer?.onMapClick(pos, mapOwner)
        }
    }

    override fun onCircleClick(x: Circle) {
        for (layer in layersOnMap.values) {
            if (layer.ownsElement(x)) {
                layer.onCircleClick(x)
                return
            }
        }
    }

    override fun onGroundOverlayClick(x: GroundOverlay) {
        for (layer in layersOnMap.values) {
            if (layer.ownsElement(x)) {
                layer.onGroundOverlayClick(x)
                return
            }
        }
    }

    override fun onMarkerClick(x: Marker): Boolean {
        for (layer in layersOnMap.values) {
            if (layer.ownsElement(x)) {
                layer.onMarkerClick(x)
                return true
            }
        }
        return false
    }

    override fun onPolygonClick(x: Polygon) {
        for (layer in layersOnMap.values) {
            if (layer.ownsElement(x)) {
                layer.onPolygonClick(x)
                return
            }
        }
    }

    override fun onPolylineClick(x: Polyline) {
        for (layer in layersOnMap.values) {
            if (layer.ownsElement(x)) {
                layer.onPolylineClick(x)
                return
            }
        }
    }

    private fun onMapLayersChanged(change: Resource<List<MapLayersViewModel.Layer>>?) {
        val modelLayers = change?.content?.associateBy { it } ?: emptyMap()
        val cursor = layersOnMap.keys.iterator()
        for (modelLayerOnMap in cursor) {
            if (!modelLayers.containsKey(modelLayerOnMap)) {
                val viewLayer = layersOnMap[modelLayerOnMap]!!
                viewLayer.removeFromMap()
                cursor.remove()
            }
        }
        // TODO: handle updated/chagned layers; use layerElementsChanged event?
    }

    fun dispose() {
        // TODO: remove and dispose all overlays/notify providers
        val cursor = layersOnMap.values.iterator()
        for (layer in cursor) {
            layer.removeFromMap()
            cursor.remove()
        }
    }

    private class RemoveElementForSpec(private val elements: MapElements) : MapElementSpecVisitor<Boolean> {

        override fun visit(x: MapCircleSpec): Boolean? {
            return elements.withElementForSpec(x, MapElementOperation.REMOVE)
        }

        override fun visit(x: MapGroundOverlaySpec): Boolean? {
            return elements.withElementForSpec(x, MapElementOperation.REMOVE)
        }

        override fun visit(x: MapMarkerSpec): Boolean? {
            return elements.withElementForSpec(x, MapElementOperation.REMOVE)
        }

        override fun visit(x: MapPolygonSpec): Boolean? {
            return elements.withElementForSpec(x, MapElementOperation.REMOVE)
        }

        override fun visit(x: MapPolylineSpec): Boolean? {
            return elements.withElementForSpec(x, MapElementOperation.REMOVE)
        }

        override fun visit(x: MapTileOverlaySpec): Boolean? {
            return elements.withElementForSpec(x, MapElementOperation.REMOVE)
        }
    }

    companion object {

        private val LOG_NAME = MapLayerManager::class.java.simpleName
        private val ANYWHERE = LatLngBounds(LatLng(-90.0, -180.0), LatLng(90.0, 180.0))
    }
}
