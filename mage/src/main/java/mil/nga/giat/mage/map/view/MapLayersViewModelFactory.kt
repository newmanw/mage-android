package mil.nga.giat.mage.map.view

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.os.AsyncTask
import mil.nga.giat.mage.map.cache.MapDataManager
import java.util.concurrent.Executor

class MapLayersViewModelFactory(private val mapDataManager: MapDataManager, private val executor: Executor) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return MapLayersViewModel(mapDataManager, executor) as T
    }
}