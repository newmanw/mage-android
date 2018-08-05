package mil.nga.giat.mage.map.view

import android.annotation.SuppressLint
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.os.AsyncTask
import android.support.annotation.UiThread
import com.google.android.gms.maps.model.LatLngBounds
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.data.Resource.Status.Loading
import mil.nga.giat.mage.map.*
import mil.nga.giat.mage.map.cache.MapDataManager
import mil.nga.giat.mage.map.cache.MapDataProvider
import mil.nga.giat.mage.map.cache.MapLayerDescriptor
import mil.nga.giat.mage.utils.EnumLiveEvents
import mil.nga.giat.mage.utils.LiveEvents
import java.util.*
import kotlin.Comparator
import kotlin.collections.HashMap
import kotlin.collections.HashSet


private val defaultLayerOrder = Comparator<MapLayersViewModel.Layer> { a, b ->
    if (a == b) {
        return@Comparator 0
    }
    if (a.desc.resourceUri == b.desc.resourceUri) {
        return@Comparator a.desc.layerTitle.compareTo(b.desc.layerTitle)
    }
    var resourceDiff = a.resourceName.compareTo(b.resourceName)
    if (resourceDiff == 0) {
       resourceDiff = a.desc.resourceUri.compareTo(b.desc.resourceUri)
    }
    resourceDiff
}

@UiThread
class MapLayersViewModel(private val mapDataManager: MapDataManager) : ViewModel() {

    private val layerOrder = ArrayList<Layer>()
    private val mediatedLayers = MediatorLiveData<Resource<List<Layer>>>()
    private val queryForLayer = HashMap<Layer, MapDataProvider.LayerQuery>()
    private val fetchForLayer = HashMap<Layer, FetchLayerElements>()
    private val elementsForLayer = HashMap<Layer, MutableLiveData<Resource<Map<Any, MapElementSpec>>>>()
    private val triggerLayerEvent = object: EnumLiveEvents<LayerEventType, LayerListener>(LayerEventType::class.java) {

        fun zOrderShift(range: IntRange) {
            super.trigger(LayerEventType.ZOrderShift, range)
        }

        fun layerVisibility(layer: Layer, position: Int) {
            super.trigger(LayerEventType.Visibility, Pair(layer, position))
        }

        fun elementsChanged(layer: Layer, elements: Map<Any, MapElementSpec>) {

        }
    }
    private var currentBounds: LatLngBounds? = null

    val layersInZOrder: LiveData<Resource<List<Layer>>> = mediatedLayers
    val layerEvents: LiveEvents<LayerListener> = triggerLayerEvent

    init {
        mediatedLayers.addSource(mapDataManager.mapData) { mapData ->
            if (mapData?.status == Loading) {
                mediatedLayers.value = Resource.loading(emptyList())
                return@addSource
            }
            val mutableLayers = mapDataManager.layers
            val cursor = layerOrder.iterator()
            while (cursor.hasNext()) {
                val layer = cursor.next()
                if (mutableLayers.remove(layer.desc.layerUri) == null) {
                    val elements = elementsForLayer.remove(layer)
                    elements?.value = null
                    cursor.remove()
                    // TODO: layer removed notification, or suffice to set elements live data value to null?
                }
            }
            var zIndex = layerOrder.size
            mutableLayers.values.asSequence()
                    .map { Layer(it, mapDataManager.resourceForLayer(it)!!.requireResolved().name, false) }
                    .sortedWith(defaultLayerOrder)
                    .forEach {
                        layerOrder.add(it)
                        elementsForLayer[it] = MutableLiveData()
                    }
            mediatedLayers.value = Resource.success(layerOrder)
        }
    }

    fun setLayerVisibility(layer: Layer, visible: Boolean) {
        if (layer.isVisible == visible) {
            return
        }
        if (!visible) {
            triggerLayerEvent.layerVisibility(layer, layerOrder.indexOf(layer))
            return
        }
        val pos = layerOrder.indexOf(layer)
        layerOrder[pos] = layerOrder[pos].copy(isVisible = true)
        var layerQuery = queryForLayer[layer]
        val provider = mapDataManager.providers[layer.desc.dataType]!!
        if (layerQuery == null) {
            layerQuery = provider.createQueryForLayer(layer.desc)
            if (layerQuery.hasDynamicElements()) {
                if (!layerQuery.supportsDynamicFetch()) {
                    // TODO: on bg thread get all the elements and add to an in-memory spatial index
                    // wrap in own layer query impl that will cull the full set of elements
                    // maybe also use some spatial LRU cache
                }
            }
            queryForLayer[layer] = layerQuery
            if (currentBounds != null) {
                FetchLayerElements(layer, layerQuery, currentBounds!!).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
            }
        }
        triggerLayerEvent.layerVisibility(layer, pos)
    }

    fun moveZIndex(from: Int, to: Int): Boolean {
        val layer = layerOrder.removeAt(from)
        layerOrder.add(to, layer)
        val low = Math.min(from, to)
        val shiftRange = low..(from + to - low)
        for (z in shiftRange) {
            val layer = layerOrder[z]
            layerOrder[z] = layer.copy(zIndex = z)
        }
        triggerLayerEvent.zOrderShift(low..(from + to - low))
        return true
    }

    fun mapBoundsChanged(bounds: LatLngBounds) {
        layerOrder.asSequence().filter(Layer::isVisible).forEach { layer ->
            val layerQuery = queryForLayer[layer]!!
            if (layerQuery.hasDynamicElements()) {
                // TODO: create async task to reconcile new results

            }
            // else don't bother updating
        }
    }

    fun refreshLayers() {
        mapDataManager.refreshMapData()
    }

    fun layerAt(pos: Int): Layer {
        return layerOrder[pos]
    }

    fun elementsForLayer(layer: Layer): LiveData<Resource<Map<Any, MapElementSpec>>> {
        return elementsForLayer[layer]!!
    }

    override fun onCleared() {
        val fetchLayers= ArrayList(fetchForLayer.keys)
        for (layer in fetchLayers) {
            fetchForLayer.remove(layer)!!.cancel(false)
        }
    }

    private fun onLayerFetchFinished(fetch: FetchLayerElements, elements: Map<Any, MapElementSpec>? = null, removed: Map<Any, MapElementSpec>? = null) {
        val expectedFetch = fetchForLayer[fetch.layer]
        if (fetch === expectedFetch) {
            fetchForLayer.remove(fetch.layer)
            if (fetch.isCancelled) {
                return
            }
        }
        elementsForLayer[fetch.layer]
    }

    data class Layer(
            val desc: MapLayerDescriptor,
            val resourceName: String,
            val isVisible: Boolean = false,
            val zIndex: Int = 0
    ) {

        override fun hashCode(): Int {
            return desc.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return if (other is Layer) desc == other.desc else false
        }
    }

    interface LayerListener {

        fun zOrderShift(range: IntRange)
        fun layerVisibilityChanged(layer: Layer, position: Int)
        fun layerElementsChanged(layer: Layer, added: Map<Any, MapElementSpec>, removed: Map<Any, MapElementSpec>)
    }

    @Suppress("UNCHECKED_CAST")
    private enum class LayerEventType : EnumLiveEvents.EnumEventType<LayerListener> {

        ZOrderShift {
            override fun deliver(listener: LayerListener, data: Any?) {
                listener.zOrderShift(data as IntRange)
            }
        },
        Visibility {
            override fun deliver(listener: LayerListener, pair: Any?) {
                val item = pair as Pair<Layer, Int>
                listener.layerVisibilityChanged(pair.first, pair.second)
            }
        },
        ElementsChanged {
            override fun deliver(listener: LayerListener, data: Any?) {
                val triple = data as Triple<Layer, Map<Any, MapElementSpec>, Map<Any, MapElementSpec>>
                listener.layerElementsChanged(triple.first, triple.second, triple.third)
            }
        },
    }

    @SuppressLint("StaticFieldLeak")
    private inner class FetchLayerElements(val layer: Layer, val layerQuery: MapDataProvider.LayerQuery, val bounds: LatLngBounds) : AsyncTask<Void?, Void?, Map<Any, MapElementSpec>>() {

        override fun doInBackground(vararg params: Void?): Map<Any, MapElementSpec> {
            return layerQuery.fetchMapElements(bounds)
        }

        override fun onPostExecute(result: Map<Any, MapElementSpec>?) {
            onLayerFetchFinished(this, result)
        }

        override fun onCancelled() {
            onLayerFetchFinished(this)
        }
    }

    private inner class PendingLayerQuery : MapDataProvider.LayerQuery {

        override fun hasDynamicElements(): Boolean {
            return false
        }

        override fun supportsDynamicFetch(): Boolean {
            return false
        }

        override fun fetchMapElements(bounds: LatLngBounds): Map<Any, MapElementSpec> {
            return emptyMap()
        }

        override fun close() {
        }
    }

    private class SetSpecZIndex private constructor(private val zIndex: Int) : MapElementSpec.MapElementSpecVisitor<Void> {

        override fun visit(x: MapCircleSpec): Void? {
            x.options.zIndex(zIndex.toFloat())
            return null
        }

        override fun visit(x: MapGroundOverlaySpec): Void? {
            x.options.zIndex(zIndex.toFloat())
            return null
        }

        override fun visit(x: MapMarkerSpec): Void? {
            x.options.zIndex(zIndex.toFloat())
            return null
        }

        override fun visit(x: MapPolygonSpec): Void? {
            x.options.zIndex(zIndex.toFloat())
            return null
        }

        override fun visit(x: MapPolylineSpec): Void? {
            x.options.zIndex(zIndex.toFloat())
            return null
        }

        override fun visit(x: MapTileOverlaySpec): Void? {
            x.options.zIndex(zIndex.toFloat())
            return null
        }
    }
}