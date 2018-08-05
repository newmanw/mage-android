package mil.nga.giat.mage.map.cache

import android.arch.lifecycle.LiveData
import com.google.android.gms.maps.model.CameraPosition
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.map.MapElementSpec

abstract class MapLayerElements(val subject: MapLayerDescriptor) : LiveData<Resource<Map<Any, MapElementSpec>>>() {

    abstract fun loadElementsAt(pos: CameraPosition)
}