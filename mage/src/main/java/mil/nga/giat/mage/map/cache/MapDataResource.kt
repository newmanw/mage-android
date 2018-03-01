package mil.nga.giat.mage.map.cache

import com.google.android.gms.maps.model.LatLngBounds

import java.net.URI


class MapDataResource(val uri: URI, val name: String, val type: Class<out MapDataProvider>?, layerDescriptors: Set<MapLayerDescriptor>) {

    /**
     * Create a new [MapDataResource] from the given URI that has not been resolved
     * and so has no type information.
     */
    constructor(uri: URI) : this(uri, "", null, emptySet())

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
     * Two [MapDataResource] objects are equal if and only if their [types][.getType]
     * are non-null and equal, and [names][.getName] are equal.  A [MapDataResource]
     * with a null type will never be equal to another [MapDataResource].
     * @param other
     * @return
     */
    override fun equals(other: Any?): Boolean {
        when (other) { is MapDataResource -> return type != null && type == other.type && name == other.name }
        return false
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return "$name:$type.simpleName"
    }
}
