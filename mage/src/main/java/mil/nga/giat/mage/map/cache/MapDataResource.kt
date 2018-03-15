package mil.nga.giat.mage.map.cache

import com.google.android.gms.maps.model.LatLngBounds
import java.net.URI


class MapDataResource private constructor (val uri: URI, val repositoryId: String, val contentTimestamp: Long, val resolved: Resolved?) {

    /**
     * Create a [MapDataResource] with the given ID that has not been resolved
     * and so has no type information.
     */
    constructor(uri: URI, repository: MapDataRepository, contentTimestamp: Long = System.currentTimeMillis()) : this(uri, repository.id, contentTimestamp, null)

    /**
     * Create a [MapDataResource] with the given ID that has been resolved
     */
    constructor(uri: URI, repository: MapDataRepository, contentTimestamp: Long = System.currentTimeMillis(), resolved: Resolved) : this(uri, repository.id, contentTimestamp, resolved)

    /**
     * Create a [MapDataResource] with the same URI, repository, and content timestamp as the given source repository,
     * but with the new given resolved information.
     */
    constructor(source: MapDataResource, resolved: Resolved) : this(source.uri, source.repositoryId, source.contentTimestamp, resolved)

    data class Resolved(val name: String, val type: Class<out MapDataProvider>) {
        constructor(name: String, type: Class<out MapDataProvider>, layerDescriptors: Set<MapLayerDescriptor>)
                : this(name, type) {
            this.layerDescriptors = layerDescriptors.associateBy({it.layerName})
        }
        var layerDescriptors: Map<String, MapLayerDescriptor> = emptyMap()
            private set
    }

    /**
     * Return a map of [layer descriptors][MapLayerDescriptor] keyed by their [names][MapLayerDescriptor.layerName].
     * @return
     */
    val layers: Map<String, MapLayerDescriptor> = resolved?.layerDescriptors ?: emptyMap()
    var refreshTimestamp: Long = System.currentTimeMillis()
        private set
    var bounds: LatLngBounds? = null
        get() = null
        private set

    fun updateRefreshTimestamp(): MapDataResource {
        this.refreshTimestamp = System.currentTimeMillis()
        return this
    }

    /**
     * Return a [new][MapDataResource(MapDataResource,Resolved)]
     */
    fun resolve(resolved: Resolved): MapDataResource {
        return MapDataResource(this, resolved)
    }

    fun resolve(resolved: Resolved, contentTimestamp: Long): MapDataResource {
        return MapDataResource(uri, repositoryId, contentTimestamp, resolved);
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
