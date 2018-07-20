package mil.nga.giat.mage.map.view

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.ViewModel
import mil.nga.giat.mage.data.BasicResource
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.data.Resource.Status
import mil.nga.giat.mage.data.Resource.Status.*
import mil.nga.giat.mage.map.cache.MapDataManager
import mil.nga.giat.mage.map.cache.MapLayerDescriptor
import java.util.*

class MapLayersViewModel(private val mapDataManager: MapDataManager) : ViewModel() {

    data class LayerItem(override val content: MapLayerDescriptor, val resourceName: String, val visible: Boolean = false) : Resource<MapLayerDescriptor> {
        override val status: Status
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        override val statusCode: Int
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        override val statusMessage: String?
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    }

    private val layerOrder = ArrayList<LayerItem>()
    private val mediatedLayers = MediatorLiveData<Resource<List<LayerItem>>>()

    init {
        mediatedLayers.addSource(mapDataManager) { mapData ->
            if (mapData?.status == Loading) {
                mediatedLayers.value = BasicResource.loading(emptyList())
            }
            val mutableLayers = mapDataManager.layers
            val cursor = layerOrder.iterator()
            while (cursor.hasNext()) {
                if (mutableLayers.remove(cursor.next().content.layerUri) == null) {
                    cursor.remove()
                }
            }
            layerOrder.addAll(mutableLayers.values.asSequence().map { LayerItem(it, mapDataManager.resourceForLayer(it)!!.requireResolved().name) })
            mediatedLayers.value = BasicResource.success(layerOrder)
        }
    }

    val layersInZOrder: LiveData<Resource<List<LayerItem>>> = mediatedLayers

    fun setLayerVisible(layer: LayerItem, visible: Boolean): Boolean {
        val layers = layersInZOrder.value?.content ?:
            throw IllegalStateException("cannot show layers before layers have been loaded")
        when (layer.status) {
            Loading -> return false
            Success -> {

            }
            Error -> {
                // TODO try reloading the layer
            }
        }
        return false
    }

    fun moveZIndex(from: Int, to: Int): Boolean {
        Collections.swap(layerOrder, from, to)
        return true
    }

    fun refreshLayers() {
        mapDataManager.refreshMapData()
    }
}