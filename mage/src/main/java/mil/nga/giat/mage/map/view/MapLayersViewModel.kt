package mil.nga.giat.mage.map.view

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import mil.nga.giat.mage.data.ListResource
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.map.cache.MapDataManager
import mil.nga.giat.mage.map.cache.MapLayerDescriptor

class MapLayersViewModel(private val mapDataManager: MapDataManager) : ViewModel() {

    data class LayerItem(override val content: MapLayerDescriptor, val resourceName: String, val visible: Boolean) : Resource<MapLayerDescriptor> {
        override val status: Resource.Status
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        override val statusCode: Int
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        override val statusMessage: String?
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    }

    private val layers = MutableLiveData<ListResource<LayerItem>>()

    val layersInZOrder: LiveData<ListResource<LayerItem>> = layers

    fun setLayerVisible(layer: LayerItem, visible: Boolean): Boolean {
        val layers = layersInZOrder.value?.content ?:
            throw IllegalStateException("cannot show layers before layers have been loaded")
        when (layer.status) {
            Resource.Status.Loading -> return false
            Resource.Status.Success -> {

            }
            Resource.Status.Error -> {
                // TODO try reloading the layer
            }
            else -> return false
        }
        return false
    }

    fun moveZIndex(from: Int, to: Int): Boolean {
        return false
    }

    fun refreshLayers() {
        mapDataManager.refreshMapData()
    }
}