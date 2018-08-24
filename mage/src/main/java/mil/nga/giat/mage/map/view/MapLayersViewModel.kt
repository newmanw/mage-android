package mil.nga.giat.mage.map.view

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.support.annotation.UiThread
import com.google.android.gms.maps.model.LatLngBounds
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.data.Resource.Status.Loading
import mil.nga.giat.mage.data.UniqueAsyncTaskManager
import mil.nga.giat.mage.map.*
import mil.nga.giat.mage.map.cache.MapDataManager
import mil.nga.giat.mage.map.cache.MapDataProvider
import mil.nga.giat.mage.map.cache.MapLayerDescriptor
import mil.nga.giat.mage.utils.EnumLiveEvents
import mil.nga.giat.mage.utils.LiveEvents
import java.util.*
import java.util.concurrent.Executor
import kotlin.Comparator
import kotlin.collections.HashMap


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
class MapLayersViewModel(private val mapDataManager: MapDataManager, executor: Executor) : ViewModel() {

    private val layerOrder = ArrayList<Layer>()
    private val mediatedLayers = MediatorLiveData<Resource<List<Layer>>>()
    private val queryForLayer = HashMap<Layer, MapDataProvider.LayerQuery>()
    private val elementsForLayer = HashMap<Layer, MutableLiveData<Resource<Map<Any, MapElementSpec>>>>()
    private val elementLoadTasks = UniqueAsyncTaskManager(object : UniqueAsyncTaskManager.TaskListener<Layer, Void, Map<Any, MapElementSpec>> {
        override fun taskFinished(key: Layer, task: UniqueAsyncTaskManager.Task<Void, Map<Any, MapElementSpec>>, result: Map<Any, MapElementSpec>?) {
            elementsForLayer[key]!!.value = Resource.success(result!!)
        }
    }, executor)
    private val triggerLayerEvent = object : EnumLiveEvents<LayerEventType, LayerListener>(LayerEventType::class.java) {

        fun zOrderShift(range: IntRange) {
            super.trigger(LayerEventType.ZOrderShift, range)
        }

        fun layerVisibility(layer: Layer, position: Int) {
            super.trigger(LayerEventType.Visibility, Pair(layer, position))
        }
    }

    private var currentBounds: LatLngBounds? = null

    /**
     * The order of this list is by descending z-order, the first item, index 0, has z-index [size][List.size],
     * index 1 has z-index [size][List.size] - 1, ... , index n has z-index [size][List.size] - n.  The last
     * layer in the list, index [size][List.size] - 1, has the lowest z-index, 1.  This allows the default view
     * of this model to easily show the top-most layer on the map at the first position of the layer list view,
     * and so on.
     */
    val layersInZOrder: LiveData<Resource<List<Layer>>> = mediatedLayers
    val layerEvents: LiveEvents<LayerListener> = triggerLayerEvent

    init {
        mediatedLayers.addSource(mapDataManager.mapData) { mapData ->
            if (mapData?.status == Loading) {
                mediatedLayers.value = Resource.loading(emptyList())
                return@addSource
            }
            val mutableLayers = mapDataManager.layers
            var cursor = layerOrder.listIterator()
            // TODO: track z-indexes and emit events so visible layers get updated
            var firstZIndexChange = -1
            var cursorPos = 0
            while (cursor.hasNext()) {
                val layer = cursor.next()
                if (mutableLayers.remove(layer.desc.layerUri) == null) {
                    val elements = elementsForLayer.remove(layer)
                    // TODO: post value or keep serial here, collect and do all at once in another loop below?
                    elements?.value = null
                    cursor.remove()
                    if (firstZIndexChange < 0) {
                        firstZIndexChange = cursorPos
                    }
                    // TODO: layer removed notification, or suffice to set elements live data value to null?
                }
                else {
                    // TODO: handle updated layers that might need to reload elements
                    cursorPos++
                }
            }
            if (firstZIndexChange < 0) {
                firstZIndexChange = layerOrder.size
            }
            // TODO: z-index shift event or just the live data change?
            cursorPos = firstZIndexChange
            cursor = layerOrder.listIterator(cursorPos)
            while (cursor.hasNext()) {
                val layer = cursor.next()
                cursor.set(layer.copy(zIndex = cursorPos++))
            }
            val layerCount = mutableLayers.size
            mutableLayers.values.asSequence()
                    .map { Layer(it, mapDataManager.resourceForLayer(it)!!.requireResolved().name, false, layerCount - cursorPos++) }
                    .sortedWith(defaultLayerOrder)
                    .forEach {
                        layerOrder.add(it)
                        val elements = MutableLiveData<Resource<Map<Any, MapElementSpec>>>()
                        elements.value = Resource.success(emptyMap())
                        elementsForLayer[it] = elements
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
        triggerLayerEvent.layerVisibility(layer, pos)
        if (currentBounds == null) {
            return
        }
        val elements = elementsForLayer[layer]!!
        elements.value = Resource.loading(elements.value!!.content!!)
        elementLoadTasks.execute(layer, LoadLayerElements(layer, currentBounds!!, queryForLayer[layer], mapDataManager.providers[layer.desc.dataType]!!))
    }

    /**
     * Move the layer at the [given position][from] to the the given [destination][to] position,  Layers at positions
     * greater than or equal to the desitination position will shift one position higher, similar to [MutableList.add].
     * Both given positions must be valid list indexes in the range \[0, size).
     */
    fun moveZIndex(from: Int, to: Int): Boolean {
        if (from == to) {
            return false
        }
        val low = Math.min(from, to)
        val shiftRange = low..(from + to - low)
        val layer = layerOrder.removeAt(from)
        layerOrder.add(to, layer)
        val layerCount = layerOrder.size
        for (pos in shiftRange) {
            val shiftedLayer = layerOrder[pos]
            layerOrder[pos] = shiftedLayer.copy(zIndex = layerCount - pos)
        }
        triggerLayerEvent.zOrderShift(shiftRange)
        return true
    }

    fun mapBoundsChanged(bounds: LatLngBounds) {
        currentBounds = bounds
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
        elementLoadTasks.dispose()
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

        /**
         * The [layers][layersInZOrder] in the given range, closed/inclusive, changed z-indexes.
         */
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
            override fun deliver(listener: LayerListener, data: Any?) {
                val pair = data as Pair<Layer, Int>
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

    private class LoadLayerElements
        constructor(
            val layer: Layer,
            val bounds: LatLngBounds,
            var query: MapDataProvider.LayerQuery?,
            var provider: MapDataProvider)
        : UniqueAsyncTaskManager.Task<Void, Map<Any, MapElementSpec>> {

        override fun run(support: UniqueAsyncTaskManager.TaskSupport<Void>): Map<Any, MapElementSpec> {
            if (query == null) {
                query = provider.createQueryForLayer(layer.desc)
            }
            return query!!.fetchMapElements(bounds)
        }
    }
}