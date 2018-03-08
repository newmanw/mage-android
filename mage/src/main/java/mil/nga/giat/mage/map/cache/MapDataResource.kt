package mil.nga.giat.mage.map.cache

import com.google.android.gms.maps.model.LatLngBounds

import java.net.URI


class MapDataResource(val uri: URI, var resolved: Resolved?) {

    data class Resolved(val name: String, val source: Class<out MapDataRepository>, val type: Class<out MapDataProvider>) {
        constructor(name: String, source: Class<out MapDataRepository>, type: Class<out MapDataProvider>, layerDescriptors: Set<MapLayerDescriptor>)
                : this(name, source, type) {
            this.layerDescriptors = layerDescriptors
        }
        var layerDescriptors: Set<MapLayerDescriptor> = emptySet()
            private set
    }

    /**
     * Create a new [MapDataResource] from the given URI that has not been resolved
     * and so has no type information.
     */
    constructor(uri: URI) : this(uri, null)

    /**
     * Return a map of [layer descriptors][MapLayerDescriptor] keyed by their [names][MapLayerDescriptor.getLayerName].
     * @return
     */
    val layers: Map<String, MapLayerDescriptor> = resolved?.layerDescriptors?.associateBy({it.layerName}) ?: emptyMap()
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
     * Two [MapDataResource] objects are equal if and only if their [URIs][.uri]
     * @param other
     * @return
     */
    override fun equals(other: Any?): Boolean {
        if (other is MapDataResource) {
            return uri == other.uri && resolved == other.resolved
        }
        return false
    }

    override fun hashCode(): Int {
        return resolved?.name?.hashCode() ?: 0
    }

    override fun toString(): String {
        if (resolved != null) {
            return "${resolved?.name}: ${resolved?.type}"
        }
        return uri.toString()
    }
}
