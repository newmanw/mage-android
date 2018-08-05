package mil.nga.giat.mage.map.view

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import mil.nga.giat.mage.map.cache.MapDataManager

class MapLayersViewModelFactory(private val mapDataManager: MapDataManager) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return MapLayersViewModel(mapDataManager) as T
    }
}