package mil.nga.giat.mage.ui.geojson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mil.nga.giat.mage.data.repository.layer.LayerRepository
import mil.nga.giat.mage.data.repository.map.MapRepository
import javax.inject.Inject

@HiltViewModel
class GeoJsonViewModel @Inject constructor(
   mapRepository: MapRepository,
   private val layerRepository: LayerRepository
): ViewModel() {

   val baseMap = mapRepository.baseMapType.asLiveData()

   private val keyFlow = MutableSharedFlow<GeoJsonFeatureKey>(replay = 1)
   fun setKey(key: GeoJsonFeatureKey) {
      viewModelScope.launch {
         keyFlow.emit(key)
      }
   }

   val feature = keyFlow.map { key ->
      layerRepository.getStaticFeature(key.layerId, key.featureId)
   }.asLiveData()
}