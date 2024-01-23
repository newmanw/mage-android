package mil.nga.giat.mage.ui.observation.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import mil.nga.giat.mage.data.repository.map.MapRepository
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
   mapRepository: MapRepository,
): ViewModel() {
   val baseMap = mapRepository.baseMapType.asLiveData()
}