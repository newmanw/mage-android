package mil.nga.giat.mage.map.cache

import android.annotation.SuppressLint
import android.app.Application
import android.arch.lifecycle.*
import android.arch.lifecycle.Observer
import android.os.AsyncTask
import android.support.annotation.MainThread
import com.google.android.gms.maps.GoogleMap
import mil.nga.giat.mage.data.BasicResource
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.data.Resource.Status.Loading
import mil.nga.giat.mage.data.Resource.Status.Success
import java.io.File
import java.net.URI
import java.util.*
import java.util.concurrent.Executor
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet


/**
 * Return a new Map excluding the pairs from this Map for which the given lambda returns true.  Return this
 * Map if the given lambda returns false for all of this Map's pairs.
 */
fun <K, V> Map<K, V>.removeIf(predicate: (K, V) -> Boolean, removeTo: MutableMap<K, V>? = null): Map<K, V> {
    val result = HashMap<K, V>()
    forEach { key, value ->
        if (!predicate.invoke(key, value)) {
            result[key] = value
        }
        else {
            removeTo?.put(key, value)
        }
    }
    if (result.size == size) {
        return this
    }
    return result
}


@MainThread
class MapDataManager(config: Config) : LifecycleOwner, LiveData<Resource<Map<URI, MapDataResource>>>() {

    private val executor: Executor
    private val repositories: Array<out MapDataRepository>
    private val providers: Array<out MapDataProvider>
    private val lifecycle = LifecycleRegistry(this)
    private val nextChangeForRepository = HashMap<String, ResolveRepositoryChangeTask>()
    private val changeInProgressForRepository = HashMap<String, ResolveRepositoryChangeTask>()

    fun requireValue(): Resource<Map<URI, MapDataResource>> { return value!! }
    /**
     * Return a non-null map of resources, keyed by their [URIs][MapDataResource.uri].
     */
    var resources: Map<URI, MapDataResource> = emptyMap()
        private set
    /**
     * Return a non-null, mutable map of all layers from every [resource][resources], keyed by [layer URI][MapLayerDescriptor.layerUri].
     * Changes to the returned mutable map have no effect on this [MapDataManager]'s layers and resources.
     */
    val layers: MutableMap<URI, MapLayerDescriptor> get() = resources.values.flatMap({ it.layers.values }).associateBy({ it.layerUri }).toMutableMap()

    init {
        repositories = config.repositories!!
        providers = config.providers!!
        executor = config.executor!!
        lifecycle.markState(Lifecycle.State.RESUMED)
        value = BasicResource.success(emptyMap())
        for (repo in repositories) {
            repo.observe(this, RepositoryObserver(repo))
        }
    }

    fun resourceForLayer(layer: MapLayerDescriptor): MapDataResource? {
        return resources[layer.resourceUri]
    }

    /**
     * Attempt to import the data the given resource URI references.  If a potential path to importing the data
     * exists, begin the asynchronous import process.
     *
     * @return true if an async import operation will begin, false if the resource cannot be imported
     */
    fun tryImportResource(resourceUri: URI): Boolean {
        for (repo in repositories) {
            if (repo.ownsResource(resourceUri)) {
                repo.refreshAvailableMapData(resourcesForRepo(repo), executor)
                return true
            }
        }
        return false
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycle
    }

    /**
     * Discover new resources available in known [locations][MapDataRepository], then remove defunct resources.
     * One or more asynchronous notifications to [observers][observe] will result as data from the various
     * [repositories][MapDataRepository] loads and [resolves][MapDataProvider.resolveResource].
     */
    fun refreshMapData() {
        for (repo in repositories) {
            if (repo.value?.status != Loading && !changeInProgressForRepository.containsKey(repo.id)) {
                repo.refreshAvailableMapData(resourcesForRepo(repo), executor)
            }
        }
    }

    fun createMapLayerManager(map: GoogleMap): MapLayerManager {
        return MapLayerManager(this, Arrays.asList(*providers), map)
    }

    private fun resourcesForRepo(repo: MapDataRepository): Map<URI, MapDataResource> {
        return resources.filter { it.value.repositoryId == repo.id }
    }

    private fun onMapDataChanged(resource: Resource<Set<MapDataResource>>?, source: MapDataRepository) {
        val preChangeSize = requireValue().content!!.size
        val preChangeStatus = requireValue().status
        if (resource?.status == Loading) {
            resources = resources.removeIf({ _, res ->
                res.repositoryId == source.id
            })
            if (resources.size < preChangeSize || preChangeStatus != Loading) {
                value = BasicResource.loading(resources)
            }
            return
        }

        val change = resource?.content ?: emptySet()
        val unchanged = HashSet<URI>()
        val resolved = HashMap<URI, MapDataResource>(change.size)
        val unresolved = HashMap<URI, MapDataResource>(change.size)
        for (changeRes in change) {
            when {
                changeRes.resolved == null -> unresolved[changeRes.uri] = changeRes
                resources[changeRes.uri] !== changeRes -> resolved[changeRes.uri] = changeRes
                else -> unchanged.add(changeRes.uri)
            }
        }
        val remove = HashMap<URI, MapDataResource>()
        for ((uri, existing) in resources) {
            if (existing.repositoryId == source.id && !resolved.containsKey(uri) && !unchanged.contains(uri)) {
                remove[uri] = existing
            }
        }

        var nextStatus = Success
        if (unresolved.isNotEmpty()) {
            nextStatus = Loading
            nextChangeForRepository[source.id] = ResolveRepositoryChangeTask(source, unresolved)
            val changeInProgress = changeInProgressForRepository[source.id]
            if (changeInProgress == null) {
                beginNextChangeForRepository(source)
            }
            else {
                changeInProgress.cancel(false)
            }
        }
        else if (repositories.any { it.value?.status == Loading }) {
            nextStatus = Loading
        }

        if (nextStatus == preChangeStatus && resolved.isEmpty() && remove.isEmpty()) {
            return
        }

        resources -= remove.keys
        resources += resolved

        value = BasicResource(resources, nextStatus)
    }

    private fun beginNextChangeForRepository(source: MapDataRepository, changeInProgress: ResolveRepositoryChangeTask? = null) {
        val change = nextChangeForRepository.remove(source.id)!!
        changeInProgressForRepository[source.id] = change
        if (changeInProgress != null) {
            change.updateProgressFromCancelledChange(changeInProgress)
        }
        if (change.unresolvedRemaining.isEmpty()) {
            finishChangeInProgressForRepository(source)
        }
        else {
            change.executeOnExecutor(executor)
        }
    }

    private fun onResolveFinished(change: ResolveRepositoryChangeTask) {
        val changeInProgress = changeInProgressForRepository.remove(change.repository.id)
        if (change !== changeInProgress) {
            throw IllegalStateException("finished repository change mismatch: expected\n  $changeInProgress\n  but finishing change is\n  $change")
        }
        if (change.isCancelled) {
            beginNextChangeForRepository(change.repository, change)
        }
        else {
            val result = change.result
            change.repository.onExternallyResolved(HashSet(result.resolved.values))
        }
    }

    private fun finishChangeInProgressForRepository(repo: MapDataRepository) {
        val change = changeInProgressForRepository.remove(repo.id)!!
        val result = change.result
        resources += result.resolved
        val status = if (repositories.any({ it.value?.status == Loading })) Loading else Success
        // TODO: check failed and error state
        value = BasicResource(resources, status)
    }

    class Config {

        var context: Application? = null
            private set
        var repositories: Array<out MapDataRepository>? = emptyArray()
            private set
        var providers: Array<out MapDataProvider>? = emptyArray()
            private set
        var executor: Executor? = AsyncTask.THREAD_POOL_EXECUTOR
            private set

        fun context(x: Application): Config {
            context = x
            return this
        }

        fun repositories(vararg x: MapDataRepository): Config {
            repositories = x
            return this
        }

        fun providers(vararg x: MapDataProvider): Config {
            providers = x
            return this
        }

        fun executor(x: Executor): Config {
            executor = x
            return this
        }
    }

    private inner class RepositoryObserver internal constructor(private val repo: MapDataRepository) : Observer<Resource<Set<MapDataResource>>> {

        override fun onChanged(data: Resource<Set<MapDataResource>>?) {
            onMapDataChanged(data, repo)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class ResolveRepositoryChangeTask internal constructor(
            val repository: MapDataRepository,
            unresolved: Map<URI, MapDataResource>
    ): AsyncTask<Void, Pair<MapDataResource, MapDataResolveException?>, ResolveRepositoryChangeResult>() {

        val existingResolved = resourcesForRepo(repository)
        val unresolvedRemaining: SortedSet<MapDataResource> = TreeSet({ a, b -> a.uri.compareTo(b.uri) })
        val backgroundUnresolved: Array<MapDataResource>
        val result = ResolveRepositoryChangeResult()
        val stringValue: String

        init {
            for (resource in unresolved.values) {
                unresolvedRemaining.add(resource)
            }
            backgroundUnresolved = unresolvedRemaining.toTypedArray()
            stringValue = "${javaClass.simpleName} repo ${repository.id} resolving\n  ${unresolvedRemaining.map { it.uri }}"
        }


        @Throws(MapDataResolveException::class)
        private fun resolveWithFirstCapableProvider(resource: MapDataResource): MapDataResource {
            val uri = resource.uri
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val resourceFile = File(uri)
                if (!resourceFile.canRead()) {
                    throw MapDataResolveException(uri, "resource file is not readable or does not exist: ${resourceFile.name}")
                }
            }
            val existing = existingResolved[uri]
            if (existing != null && existing.contentTimestamp >= resource.contentTimestamp) {
                return resource.resolve(existing.resolved!!)
            }
            for (provider in providers) {
                if (provider.canHandleResource(resource)) {
                    val resolvedResource = provider.resolveResource(resource)
                    if (resolvedResource != null) {
                        return resolvedResource
                    }
                }
            }
            throw MapDataResolveException(uri, "no cache provider could handle resource $resource")
        }

        override fun doInBackground(vararg nothing: Void): ResolveRepositoryChangeResult {
            for (unresolved in backgroundUnresolved) {
                try {
                    val resolved = resolveWithFirstCapableProvider(unresolved)
                    publishProgress(Pair(resolved, null))
                }
                catch (e: MapDataResolveException) {
                    publishProgress(Pair(unresolved, e))
                }
            }
            return result
        }

        override fun onCancelled(cancelledResult: ResolveRepositoryChangeResult?) {
            unresolvedRemaining.iterator().run {
                while (hasNext()) {
                    val resource = next()
                    result.cancelled[resource] = resource
                    remove()
                }
            }
            onResolveFinished(this)
        }

        override fun onProgressUpdate(vararg values: Pair<MapDataResource, MapDataResolveException?>?) {
            val progress: Pair<MapDataResource, MapDataResolveException?> = values[0]!!
            unresolvedRemaining.remove(progress.first)
            if (progress.second == null) {
                result.resolved[progress.first.uri] = progress.first
            }
            else {
                result.failed[progress.first] = progress.second!!
            }
        }

        override fun onPostExecute(result: ResolveRepositoryChangeResult) {
            onResolveFinished(this)
        }

        @MainThread
        fun updateProgressFromCancelledChange(cancelledChange: MapDataManager.ResolveRepositoryChangeTask) {
            if (status != Status.PENDING) {
                throw IllegalStateException("attempt to initialize progress from cancelled change after this change already began")
            }
            unresolvedRemaining.iterator().run {
                while (hasNext()) {
                    val unresolvedResource = next()
                    val resolvedResource = cancelledChange.result.resolved[unresolvedResource.uri]
                    if (resolvedResource != null && resolvedResource.contentTimestamp >= unresolvedResource.contentTimestamp) {
                        result.resolved[unresolvedResource.uri] = unresolvedResource.resolve(resolvedResource.resolved!!)
                        remove()
                    }
                }
            }
        }

        override fun toString(): String {
            return stringValue
        }
    }



    private class ResolveRepositoryChangeResult {

        val resolved = HashMap<URI, MapDataResource>()
        val cancelled = HashMap<MapDataResource, MapDataResource>()
        // TODO: propagate failed imports to user somehow
        val failed = HashMap<MapDataResource, MapDataResolveException>()
    }

    companion object {

        @JvmStatic
        var instance: MapDataManager? = null
            private set

        @Synchronized
        @JvmStatic
        fun initialize(config: Config) {
            if (MapDataManager.instance != null) {
                throw Error("attempt to initialize " + MapDataManager::class.java + " singleton more than once")
            }
            instance = MapDataManager(config)
        }
    }
}
