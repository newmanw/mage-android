package mil.nga.giat.mage.ui.observation.edit.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import mil.nga.gars.GARS
import mil.nga.giat.mage.coordinate.DMS
import mil.nga.giat.mage.data.repository.map.MapRepository
import mil.nga.mgrs.MGRS
import javax.inject.Inject

data class LatLngState(
   val latitude: String,
   val longitude: String
) {
   companion object {
      fun fromLatLng(latLng: LatLng): LatLngState {
         return LatLngState(
            latitude = "%.6f".format(latLng.latitude),
            longitude = "%.6f".format(latLng.longitude)
         )
      }
   }
}

data class DmsState(
   val latitude: String,
   val longitude: String
) {
   companion object {
      fun fromLatLng(latLng: LatLng) : DmsState {
         val dms = DMS.from(latLng)
         return DmsState(
            latitude = dms.latitude.format(),
            longitude = dms.longitude.format()
         )
      }
   }
}

data class MgrsState(
   val mgrs: String
) {
   companion object {
      fun fromLatLng(latLng: LatLng) : MgrsState {
         return MgrsState(
            mgrs = MGRS.from(latLng.longitude, latLng.latitude).coordinate()
         )
      }
   }
}

data class GarsState(
   val gars: String
) {
   companion object {
      fun fromLatLng(latLng: LatLng) : GarsState {
         return GarsState(
            gars = GARS.from(latLng.longitude, latLng.latitude).coordinate()
         )
      }
   }
}

@HiltViewModel
open class GeometryFieldDialogViewModel @Inject constructor(
   mapRepository: MapRepository
) : ViewModel() {

   val baseMap = mapRepository.baseMapType.asLiveData()

   private val latLngStateFlow = MutableSharedFlow<LatLngState>(replay = 1)
   val latLngState = latLngStateFlow.asLiveData()
   fun setLatLng(latitude: String, longitude: String) {
      viewModelScope.launch {
         val latLng = LatLng(latitude.toDouble(), longitude.toDouble())
         mapLocationFlow.emit(latLng)
         latLngStateFlow.emit(LatLngState.fromLatLng(latLng))
         dmsStateFlow.emit(DmsState.fromLatLng(latLng))
         mgrsStateFlow.emit(MgrsState.fromLatLng(latLng))
         garsStateFlow.emit(GarsState.fromLatLng(latLng))
      }
   }

   private val dmsStateFlow = MutableSharedFlow<DmsState>(replay = 1)
   val dmsState = dmsStateFlow.asLiveData()
   fun setDms(latitude: String, longitude: String) {
      viewModelScope.launch {
         val dmsState = DMS.from(latitude, longitude)?.let {
            DmsState(it.latitude.format(), it.longitude.format())
         } ?: DmsState(latitude, longitude)

         dmsStateFlow.emit(dmsState)
         DMS.from(latitude, longitude)?.let { dms ->
            val latLng = dms.toLatLng()
            mapLocationFlow.emit(latLng)
            latLngStateFlow.emit(LatLngState.fromLatLng(latLng))
            mgrsStateFlow.emit(MgrsState.fromLatLng(latLng))
            garsStateFlow.emit(GarsState.fromLatLng(latLng))
         }
      }
   }

   private val mgrsStateFlow = MutableSharedFlow<MgrsState>(replay = 1)
   val mgrsState = mgrsStateFlow.asLiveData()
   fun setMgrs(mgrs: String) {
      viewModelScope.launch {
         mgrsStateFlow.emit(MgrsState(mgrs))
         try {
            val point = MGRS.parse(mgrs).toPoint()
            val latLng = LatLng(point.latitude, point.longitude)
            mapLocationFlow.emit(latLng)
            latLngStateFlow.emit(LatLngState.fromLatLng(latLng))
            dmsStateFlow.emit(DmsState.fromLatLng(latLng))
            garsStateFlow.emit(GarsState.fromLatLng(latLng))
         } catch (_: Exception) { }
      }
   }

   private val garsStateFlow = MutableSharedFlow<GarsState>(replay = 1)
   val garsState = garsStateFlow.asLiveData()
   fun setGars(gars: String) {
      viewModelScope.launch {
         garsStateFlow.emit(GarsState(gars))
         try {
            val point = GARS.parse(gars).toPoint()
            val latLng = LatLng(point.latitude, point.longitude)
            mapLocationFlow.emit(latLng)
            latLngStateFlow.emit(LatLngState.fromLatLng(latLng))
            dmsStateFlow.emit(DmsState.fromLatLng(latLng))
            mgrsStateFlow.emit(MgrsState.fromLatLng(latLng))
         } catch (_: Exception) { }
      }
   }

   private val tabFlow = MutableSharedFlow<Int>(replay = 1)
   val tab = tabFlow.asLiveData()
   fun setTab(tab: Int) {
      viewModelScope.launch {
         tabFlow.emit(tab)
      }
   }

   private val mapLocationFlow = MutableSharedFlow<LatLng>(replay = 1)
   val mapLocation = mapLocationFlow.asLiveData()
   fun setMapLocation(latLng: LatLng) {
      viewModelScope.launch {
         latLngStateFlow.emit(LatLngState.fromLatLng(latLng))
         dmsStateFlow.emit(DmsState.fromLatLng(latLng))
         mgrsStateFlow.emit(MgrsState.fromLatLng(latLng))
         garsStateFlow.emit(GarsState.fromLatLng(latLng))
      }
   }
}