package mil.nga.giat.mage.map.cache

import com.google.android.gms.maps.model.LatLngBounds

import java.net.URI


class MapCache(val name: String, val type: Class<out CacheProvider>, val sourceFile: URI, layerDescriptors: Set<MapLayerDescriptor>) {

    /**
     * Return a map of [layer descriptors][MapLayerDescriptor] keyed by their [names][MapLayerDescriptor.getOverlayName].
     * @return
     */
    val layers: Map<String, MapLayerDescriptor> = layerDescriptors.associateBy({it.overlayName})
    var refreshTimestamp: Long = 0
        private set

    val bounds: LatLngBounds?
        get() = null


    init {
        updateRefreshTimestamp()
    }

    fun updateRefreshTimestamp() {
        this.refreshTimestamp = System.currentTimeMillis()
    }

    /**
     * Two [MapCache] objects are equal if and only if their [types][.getType]
     * ane [names][.getName] are equal.
     * @param other
     * @return
     */
    override fun equals(other: Any?): Boolean {
        when (other) { is MapCache -> return type == other.type && name == other.name }
        return false
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return "$name:$type.simpleName"
    }
}
