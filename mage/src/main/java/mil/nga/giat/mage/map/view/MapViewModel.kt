package mil.nga.giat.mage.map.view

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLngBounds
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.map.MapElementSpec

class MapViewModel : ViewModel() {

    val mapElements: LiveData<Resource<List<MapElementSpec>>> = MutableLiveData()

    fun boundsChanged(bounds: LatLngBounds) {

    }


}