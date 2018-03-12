package mil.nga.giat.mage.map.cache

import com.google.android.gms.maps.model.LatLngBounds

import java.net.URI


class MapDataResource(val uri: URI, val source: Class<out MapDataRepository>, val contentTimestamp: Long, val resolved: Resolved?) {

    data class Resolved(val name: String, val type: Class<out MapDataProvider>) {
        constructor(name: String, type: Class<out MapDataProvider>, layerDescriptors: Set<MapLayerDescriptor>)
                : this(name, type) {
            this.layerDescriptors = layerDescriptors.associateBy({it.layerName})
        }
        var layerDescriptors: Map<String, MapLayerDescriptor> = emptyMap()
            private set
    }

    /**
     * Create a new [MapDataResource] from the given URI that has not been resolved
     * and so has no type information.
     */
    constructor(uri: URI, source: Class<out MapDataRepository>, contentTimestamp: Long = System.currentTimeMillis()) : this(uri, source, contentTimestamp, null)

    /**
     * Return a map of [layer descriptors][MapLayerDescriptor] keyed by their [names][MapLayerDescriptor.layerName].
     * @return
     */
    val layers: Map<String, MapLayerDescriptor> = resolved?.layerDescriptors ?: emptyMap()
    var refreshTimestamp: Long = 0
        private set
    var bounds: LatLngBounds? = null
        get() = null
        private set

    init {
        updateRefreshTimestamp()
    }

    fun updateRefreshTimestamp() {
        this.refreshTimestamp = System.currentTimeMillis()
    }

    /**
     * Two [MapDataResource] objects are equal if and only if their [URIs][.uri] are equal.
     * @param other
     * @return
     */
    override fun equals(other: Any?): Boolean {
        if (other is MapDataResource) {
            return uri == other.uri
        }
        return false
    }

    override fun hashCode(): Int {
        return uri.hashCode()
    }

    override fun toString(): String {
        if (resolved != null) {
            return "${resolved.name}: ${resolved.type}"
        }
        return uri.toString()
    }
}
