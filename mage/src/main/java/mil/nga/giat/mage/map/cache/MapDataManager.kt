package mil.nga.giat.mage.map.cache

import android.app.Application
import android.arch.lifecycle.*
import android.arch.lifecycle.Observer
import android.os.AsyncTask
import android.support.annotation.MainThread
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.data.Resource.Status.Loading
import mil.nga.giat.mage.data.Resource.Status.Success
import mil.nga.giat.mage.data.UniqueAsyncTaskManager
import java.io.File
import java.net.URI
import java.util.*
import java.util.concurrent.Executor
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap


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
class MapDataManager(config: Config) : LifecycleOwner {

    val providers: Map<Class<out MapDataProvider>, MapDataProvider>
    // TODO: this will probably become public eventually
    private val repositories: List<MapDataRepository>
    private val executor: Executor
    private val lifecycle = LifecycleRegistry(this)
    private val mutableMapData = MutableLiveData<Resource<Map<URI, MapDataResource>>>()
    private val repositoryChangeTasks: UniqueAsyncTaskManager<String, Pair<MapDataResource, MapDataResolveException?>, ResolveRepositoryChangeResult>
    private val repositoryChangeListener = object : UniqueAsyncTaskManager.TaskListener<String, Pair<MapDataResource, MapDataResolveException?>, ResolveRepositoryChangeResult> {

        override fun taskProgress(key: String, task: UniqueAsyncTaskManager.Task<Pair<MapDataResource, MapDataResolveException?>, ResolveRepositoryChangeResult>, progress: Pair<MapDataResource, MapDataResolveException?>) {
            val change = task as ResolveRepositoryChangeTask
            change.unresolvedRemaining.remove(progress.first)
            if (progress.second == null) {
                change.result.resolved[progress.first.uri] = progress.first
            }
            else {
                change.result.failed[progress.first] = progress.second!!
            }
        }

        override fun taskCancelled(key: String, task: UniqueAsyncTaskManager.Task<Pair<MapDataResource, MapDataResolveException?>, ResolveRepositoryChangeResult>, result: ResolveRepositoryChangeResult?) {
            onResolveChangeCancelled(task as ResolveRepositoryChangeTask)
        }

        override fun taskFinished(key: String, task: UniqueAsyncTaskManager.Task<Pair<MapDataResource, MapDataResolveException?>, ResolveRepositoryChangeResult>, result: ResolveRepositoryChangeResult?) {
            onResolveFinished(task as ResolveRepositoryChangeTask, result!!)
        }
    }


    val mapData: LiveData<Resource<Map<URI, MapDataResource>>> = mutableMapData

    fun requireMapData(): Resource<Map<URI, MapDataResource>> { return mutableMapData.value!! }
    /**
     * Return a non-null map of resources, keyed by their [URIs][MapDataResource.uri].
     */
    var resources: Map<URI, MapDataResource> = emptyMap()
        private set
    /**
     * Return a non-null, mutable map of all layers from every [resource][resources], keyed by [layer URI][MapLayerDescriptor.layerUri].
     * Changes to the returned mutable map have no effect on this [MapDataManager]'s layers and resources.
     */
    val layers: MutableMap<URI, MapLayerDescriptor> get() = resources.values.flatMap { it.layers.values }.associateBy { it.layerUri }.toMutableMap()

    init {
        providers = Collections.unmodifiableMap(config.providers!!.associateByTo(LinkedHashMap()) { it.javaClass })
        repositories = config.repositories!!.asList()
        executor = config.executor!!
        lifecycle.markState(Lifecycle.State.RESUMED)
        mutableMapData.value = Resource.success(emptyMap())
        repositoryChangeTasks = UniqueAsyncTaskManager(repositoryChangeListener, executor)
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
     * One or more asynchronous notifications to [observers][mapData] will result as data from the various
     * [repositories][MapDataRepository] loads and [resolves][MapDataProvider.resolveResource].
     */
    fun refreshMapData() {
        for (repo in repositories) {
            if (repo.value?.status != Loading && !repositoryChangeTasks.isRunningTaskForKey(repo.id)) {
                repo.refreshAvailableMapData(resourcesForRepo(repo), executor)
            }
        }
    }

    private fun resourcesForRepo(repo: MapDataRepository): Map<URI, MapDataResource> {
        return resources.filter { it.value.repositoryId == repo.id }
    }

    private fun onMapDataChanged(resource: Resource<Set<MapDataResource>>?, source: MapDataRepository) {
        val preChangeSize = requireMapData().content!!.size
        val preChangeStatus = requireMapData().status
        if (resource?.status == Loading) {
            resources = resources.removeIf({ _, res ->
                res.repositoryId == source.id
            })
            if (resources.size < preChangeSize || preChangeStatus != Loading) {
                mutableMapData.value = Resource.loading(resources)
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
        when {
            unresolved.isNotEmpty() -> {
                nextStatus = Loading
                repositoryChangeTasks.execute(source.id, ResolveRepositoryChangeTask(source, unresolved, resourcesForRepo(source), ArrayList(providers.values)))
            }
            repositoryChangeTasks.isRunningTaskForKey(source.id) -> {
                if (repositoryChangeTasks.pendingTaskForKey(source.id) != null) {
                    nextStatus = Loading
                }
                repositoryChangeTasks.cancelTasksForKey(source.id)
            }
            repositories.any { it.value?.status == Loading } -> nextStatus = Loading
        }

        if (nextStatus == preChangeStatus && resolved.isEmpty() && remove.isEmpty()) {
            return
        }

        resources -= remove.keys
        resources += resolved

        mutableMapData.value = Resource(resources, nextStatus)
    }

    private fun onResolveFinished(change: ResolveRepositoryChangeTask, result: ResolveRepositoryChangeResult) {
        change.repository.onExternallyResolved(HashSet(result.resolved.values))
    }

    private fun onResolveChangeCancelled(cancelled: ResolveRepositoryChangeTask) {
        val pending = repositoryChangeTasks.pendingTaskForKey(cancelled.repository.id) as ResolveRepositoryChangeTask?
//        if (pending == null) {
//            onMapDataChanged(cancelled.repository.value, cancelled.repository)
//        }
//        else {
            pending?.updateProgressFromCancelledChange(cancelled)
//        }
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

    private class ResolveRepositoryChangeTask(
            val repository: MapDataRepository,
            unresolved: Map<URI, MapDataResource>,
            private val existingResolved: Map<URI, MapDataResource>,
            private val providers: List<MapDataProvider>)
        : UniqueAsyncTaskManager.Task<Pair<MapDataResource, MapDataResolveException?>, ResolveRepositoryChangeResult> {

        val unresolvedRemaining: SortedSet<MapDataResource> = TreeSet { a, b -> a.uri.compareTo(b.uri) }
        val result = ResolveRepositoryChangeResult()
        val stringValue: String
        var backgroundUnresolved: Array<MapDataResource>

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
                    return provider.resolveResource(resource)
                }
            }
            throw MapDataResolveException(uri, "no cache provider could handle resource $resource")
        }

        override fun run(support: UniqueAsyncTaskManager.TaskSupport<Pair<MapDataResource, MapDataResolveException?>>): ResolveRepositoryChangeResult? {
            backgroundUnresolved.forEach { unresolved ->
                if (support.isCancelled()) {
                    return@forEach
                }
                try {
                    val resolved = resolveWithFirstCapableProvider(unresolved)
                    // if cancelled while this resolve happens, AsyncTask will not publish the progress,
                    // so capture it anyway to avoid potentially doing unnecessary work again in the next
                    // resolve for the associated repository
                    support.reportProgressToMainThread(Pair(resolved, null))
                }
                catch (e: MapDataResolveException) {
                    support.reportProgressToMainThread(Pair(unresolved, e))
                }
            }
            return result
        }

        @MainThread
        fun updateProgressFromCancelledChange(cancelledChange: MapDataManager.ResolveRepositoryChangeTask) {
            val cursor = unresolvedRemaining.iterator()
            while (cursor.hasNext()) {
                val unresolvedResource = cursor.next()
                val resolvedResource = cancelledChange.result.resolved[unresolvedResource.uri]
                if (resolvedResource != null && resolvedResource.contentTimestamp >= unresolvedResource.contentTimestamp) {
                    result.resolved[unresolvedResource.uri] = unresolvedResource.resolve(resolvedResource.resolved!!)
                    cursor.remove()
                }
            }
            backgroundUnresolved = unresolvedRemaining.toTypedArray()
        }

        override fun toString(): String {
            return stringValue
        }
    }


    private class ResolveRepositoryChangeResult {

        val resolved = HashMap<URI, MapDataResource>()
        // TODO: propagate failed imports to user somehow
        val failed = HashMap<MapDataResource, MapDataResolveException>()
    }

    companion object {

        @JvmStatic
        var instance: MapDataManager? = null
            private set

        @JvmStatic
        @Synchronized
        fun initialize(config: Config) {
            if (MapDataManager.instance != null) {
                throw Error("attempt to initialize " + MapDataManager::class.java + " singleton more than once")
            }
            instance = MapDataManager(config)
        }
    }
}
