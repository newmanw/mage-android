package mil.nga.giat.mage.map.view

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.ViewModel
import android.support.annotation.UiThread
import com.google.android.gms.maps.model.LatLngBounds
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.data.Resource.Status.Loading
import mil.nga.giat.mage.data.UniqueAsyncTaskManager
import mil.nga.giat.mage.map.*
import mil.nga.giat.mage.map.cache.MapDataManager
import mil.nga.giat.mage.map.cache.MapDataProvider
import mil.nga.giat.mage.map.cache.MapDataProvider.LayerQuery
import mil.nga.giat.mage.map.cache.MapLayerDescriptor
import mil.nga.giat.mage.utils.EnumLiveEvents
import mil.nga.giat.mage.utils.LiveEvents
import java.util.concurrent.Executor
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


typealias MapElementSpecs = Map<Any, MapElementSpec>

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

    private var layers = ArrayList<Layer>()
    private val mediatedLayers = MediatorLiveData<Resource<List<Layer>>>()
    private val queryForLayer = HashMap<Layer, LayerQuery>()
    private val boundsForLayer = HashMap<Layer, LatLngBounds>()
    private val elementLoadTasks = UniqueAsyncTaskManager(object : UniqueAsyncTaskManager.TaskListener<Layer, Void, LoadLayerElementsResult> {
        override fun taskFinished(key: Layer, task: UniqueAsyncTaskManager.Task<Void, LoadLayerElementsResult>, result: LoadLayerElementsResult?) {
            var pos = posOfZIndex(key.zIndex)
            if (pos < 0 || pos >= layers.size || layers[pos] != key) {
                pos = layers.indexOf(key)
            }
            if (pos < 0) {
                // layer was removed when map data changed
                return
            }
            queryForLayer[key] = result!!.query
            boundsForLayer[key] = result.bounds
            val target = layers[pos]
            val elements = result.elements ?: emptyMap()
            val prevElements = target.elements.content ?: emptyMap()
            val removed = HashMap<Any, MapElementSpec>(prevElements.size)
            for ((prevKey, prev) in prevElements) {
                if (!elements.containsKey(prevKey)) {
                    removed[prevKey] = prev
                }
            }
            val updatedLayer = target.copy(elements = Resource.success(elements))
            layers[pos] = updatedLayer
            triggerLayerEvent.elementsChanged(updatedLayer, pos, removed)
        }
    }, executor)
    private val triggerLayerEvent = object : EnumLiveEvents<LayerEventType, LayerListener>(LayerEventType::class.java) {

        fun zOrderShift(range: IntRange) {
            super.trigger(LayerEventType.ZOrderShift, range)
        }

        fun layerVisibility(layer: Layer, position: Int) {
            super.trigger(LayerEventType.Visibility, Pair(layer, position))
        }

        fun elementsChanged(layer: Layer, position: Int, removed: MapElementSpecs) {
            super.trigger(LayerEventType.ElementsChanged, Triple(layer, position, removed))
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
                mediatedLayers.value = Resource.loading(layers)
                return@addSource
            }
            val fresh = ArrayList<Layer>()
            val layerDescs = mapDataManager.layers
            val layerCount = layerDescs.size
            // TODO: track z-indexes and emit events so visible layers get updated
            layers.forEachIndexed { index, layer ->
                val layerDesc = layerDescs.remove(layer.desc.layerUri)
                if (layerDesc != null) {
                    fresh.add(layer.copy(zIndex = zIndexOfPos(index, layerCount)))
                    // TODO: handle updated layers that might need to reload elements
                }
            }
            var layerPos = layers.size
            layerDescs.values.asSequence()
                    .map { Layer(it, mapDataManager.resourceForLayer(it)!!.requireResolved().name, false, zIndexOfPos(layerPos++, layerCount)) }
                    .sortedWith(defaultLayerOrder)
                    .forEach { fresh.add(it) }
            layers = fresh
            mediatedLayers.value = Resource.success(layers)
        }
    }

    fun setLayerVisible(layer: Layer, visible: Boolean) {
        val pos = layers.indexOf(layer)
        val target = layers[pos]
        if (target.isVisible == visible) {
            return
        }
        if (!visible) {
            layers[pos] = target.copy(isVisible = false)
            triggerLayerEvent.layerVisibility(layers[pos], pos)
            return
        }
        if (currentBounds == null) {
            layers[pos] = target.copy(isVisible = true)
        }
        else if (currentBounds != boundsForLayer[layer]) {
            layers[pos] = target.copy(isVisible = true, elements = target.elements.copy(status = Loading))
            elementLoadTasks.execute(layers[pos], LoadLayerElements(
                layers[pos], currentBounds!!, queryForLayer[layer], mapDataManager.providers[layer.desc.dataType]!!))
        }
        triggerLayerEvent.layerVisibility(layers[pos], pos)
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
        val layer = layers.removeAt(from)
        layers.add(to, layer)
        for (pos in shiftRange) {
            val shiftedLayer = layers[pos]
            layers[pos] = shiftedLayer.copy(zIndex = zIndexOfPos(pos))
        }
        triggerLayerEvent.zOrderShift(shiftRange)
        return true
    }

    fun mapBoundsChanged(bounds: LatLngBounds) {
        currentBounds = bounds
        val cursor = layers.listIterator()
        for (layer in cursor) {
            if (layer.isVisible) {
                val layerQuery = queryForLayer[layer]
                if ((layerQuery != null && layerQuery.hasDynamicElements() && layerQuery.supportsDynamicFetch()) || layerQuery == null) {
                    val loadingLayer = layer.copy(elements = layer.elements.copy(status = Loading))
                    cursor.set(loadingLayer)
                    triggerLayerEvent.elementsChanged(loadingLayer, cursor.previousIndex(), emptyMap())
                    elementLoadTasks.execute(layer, LoadLayerElements(loadingLayer, bounds, layerQuery, mapDataManager.providers[layer.desc.dataType]!!))
                }
            }
        }
    }

    fun refreshLayers() {
        mapDataManager.refreshMapData()
    }

    /**
     * Return the layer at the given index position in the [layer list][layersInZOrder].
     */
    fun layerAt(pos: Int): Layer {
        return layers[pos]
    }

    override fun onCleared() {
        elementLoadTasks.dispose()
    }

    private fun posOfZIndex(zIndex: Int, layerCount: Int = layers.size): Int {
        return layerCount - zIndex
    }

    private fun zIndexOfPos(pos: Int, layerCount: Int = layers.size): Int {
        return layerCount - pos
    }


    data class Layer(
            val desc: MapLayerDescriptor,
            val resourceName: String,
            val isVisible: Boolean = false,
            val zIndex: Int = 0,
            val elements: Resource<MapElementSpecs> = Resource.success()
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

        /**
         * The [visibility][Layer.isVisible] [changed][setLayerVisible] for the given layer.  This event
         * also implies a change to the layer's [elements][Layer.elements], but without actually firing
         * [that event][layerElementsChanged].
         */
        fun layerVisibilityChanged(layer: Layer, position: Int)

        /**
         * The [elements][Layer.elements] for the given layer changed.  This occurs as a result of a change
         * to the [map bounds][mapBoundsChanged].
         */
        fun layerElementsChanged(layer: Layer, position: Int, removed: MapElementSpecs)
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
                val triple = data as Triple<Layer, Int, MapElementSpecs>
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
            var query: LayerQuery?,
            var provider: MapDataProvider)
        : UniqueAsyncTaskManager.Task<Void, LoadLayerElementsResult> {

        override fun run(support: UniqueAsyncTaskManager.TaskSupport<Void>): LoadLayerElementsResult {
            if (query == null) {
                query = provider.createQueryForLayer(layer.desc)
            }
            if (support.isCancelled()) {
                return LoadLayerElementsResult(query!!, bounds)
            }
            val elements = query!!.fetchMapElements(bounds)
            return LoadLayerElementsResult(query!!, bounds, elements)
        }
    }

    private data class LoadLayerElementsResult(val query: LayerQuery, val bounds: LatLngBounds, val elements: MapElementSpecs? = null)
}