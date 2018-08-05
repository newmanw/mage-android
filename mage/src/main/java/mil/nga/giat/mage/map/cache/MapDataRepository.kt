package mil.nga.giat.mage.map.cache

import android.arch.lifecycle.LiveData
import android.support.annotation.MainThread
import mil.nga.giat.mage.data.Resource
import java.net.URI
import java.util.concurrent.Executor

/**
 * A MapDataResository is a store of [resources][MapDataResource] that [MapDataManager]
 * can potentially import to show data on a [map][com.google.android.gms.maps.GoogleMap].
 * An implementation of this interface can return a set of [unresolved][MapDataResource.resolved]
 * resources, or may return fully [resolved][MapDataResource.resolved]
 * resources with a [type][MapDataProvider] and [layer information][MapLayerDescriptor].
 * In the former case, [MapDataManager] will attempt to apply the correct [MapDataProvider] to
 * import the resource.  In the latter case, the MapDataRepository and the [type][MapDataProvider]
 * implementations might be one-and-the-same, for example, a WMS/WFS server implementation.
 */
abstract class MapDataRepository : LiveData<Resource<Set<MapDataResource>>>() {

    /**
     * Return a unique, persistent ID string for this repository.  This ID should be
     * consistent across separate process lifecycles of the host application.  This
     * default implementation returns the [canonical][Class.getCanonicalName]
     * class name.  Be aware the canonical class name is null for local and anonymous
     * classes.
     */
    open val id: String
        get() = javaClass.canonicalName

    /**
     * Notify this repository that resources have been [resolved][MapDataProvider.resolveResource].
     * This default implementation will retain only the resources from the given set that are present in this
     * repository's [current][getValue] resource set.  This will [asynchronously set][postValue]
     * a new value for this repository's resource set resulting in notifications to [observers][observe].
     * @param resources a set of resolved resources
     */
    @MainThread
    fun onExternallyResolved(resources: Set<MapDataResource>) {
        val content: Set<MapDataResource> = value?.content ?: emptySet()
        val merged = HashSet(resources)
        merged.retainAll(content)
        merged.addAll(content)
        postValue(Resource(merged, Resource.Status.Success))
    }

    @MainThread
    abstract fun ownsResource(resourceUri: URI): Boolean

    @MainThread
    abstract fun refreshAvailableMapData(resolvedResources: Map<URI, MapDataResource>, executor: Executor)
}
