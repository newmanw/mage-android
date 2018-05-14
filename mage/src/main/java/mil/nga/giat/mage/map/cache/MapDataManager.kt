package mil.nga.giat.mage.map.cache

import android.annotation.SuppressLint
import android.app.Application
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LifecycleRegistry
import android.arch.lifecycle.Observer
import android.os.AsyncTask
import android.support.annotation.MainThread
import com.google.android.gms.maps.GoogleMap
import java.io.File
import java.net.URI
import java.util.*
import java.util.concurrent.Executor
import kotlin.collections.HashMap
import kotlin.collections.HashSet


@MainThread
class MapDataManager(config: Config) : LifecycleOwner {

    private val updatePermission: CreateUpdatePermission
    private val executor: Executor
    private val repositories: Array<out MapDataRepository>
    private val providers: Array<out MapDataProvider>
    private val listeners = ArrayList<MapDataListener>()
    private val lifecycle = LifecycleRegistry(this)
    private val mutableResources = HashMap<URI, MapDataResource>()
    private val nextChangeForRepository = HashMap<String, ResolveRepositoryChangeTask>()
    private val changeInProgressForRepository = HashMap<String, ResolveRepositoryChangeTask>()

    val resources: Map<URI, MapDataResource> = mutableResources
    val layers: Map<URI, MapLayerDescriptor> get() = mutableResources.values.flatMap({ it.layers.values }).associateBy({ it.layerUri })

    init {
        updatePermission = config.updatePermission!!
        repositories = config.repositories!!
        providers = config.providers!!
        executor = config.executor!!
        lifecycle.markState(Lifecycle.State.STARTED)
        for (repo in repositories) {
            repo.observe(this, RepositoryObserver(repo))
        }
        lifecycle.markState(Lifecycle.State.RESUMED)
    }

    fun addUpdateListener(listener: MapDataListener) {
        listeners.add(listener)
    }

    fun removeUpdateListener(listener: MapDataListener) {
        listeners.remove(listener)
    }

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
     * Asynchronous notifications to [listeners][.addUpdateListener]
     * will result, one notification per refresh, per listener.  Only one refresh can be active at any moment.
     */
    fun refreshMapData() {
        for (repo in repositories) {
            repo.refreshAvailableMapData(resourcesForRepo(repo), executor)
        }
    }

    fun createMapLayerManager(map: GoogleMap): MapLayerManager {
        return MapLayerManager(this, Arrays.asList(*providers), map)
    }

    fun destroy() {
        listeners.clear()
        lifecycle.markState(Lifecycle.State.DESTROYED)
    }

    private fun resourcesForRepo(repo: MapDataRepository): Map<URI, MapDataResource> {
        return mutableResources.filter { it.value.repositoryId == repo.id }
    }

    private fun onMapDataChanged(resources: Set<MapDataResource>, source: MapDataRepository) {
        if (lifecycle.currentState != Lifecycle.State.RESUMED) {
            return
        }
        nextChangeForRepository[source.id] = ResolveRepositoryChangeTask(source, resources)
        val changeInProgress = changeInProgressForRepository[source.id]
        if (changeInProgress == null) {
            beginNextChangeForRepository(source)
        }
        else {
            changeInProgress.cancel(false)
        }
    }

    private fun beginNextChangeForRepository(source: MapDataRepository, changeInProgress: ResolveRepositoryChangeTask? = null) {
        val change = nextChangeForRepository.remove(source.id)!!
        changeInProgressForRepository[source.id] = change
        if (changeInProgress != null) {
            change.updateProgressFromCancelledChange(changeInProgress)
        }
        if (change.toResolve.isEmpty()) {
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
            val result = change.get()
            result.repository.onExternallyResloved(HashSet(result.resolved.values))
        }
    }

    private fun finishChangeInProgressForRepository(repo: MapDataRepository) {
        val change = changeInProgressForRepository.remove(repo.id)!!
        val result = change.result
        val added = HashMap<URI, MapDataResource>()
        val updated = HashMap<URI, MapDataResource>()
        val removed = result.oldResources.toMutableMap()
        for (value in result.resolved.values) {
            val oldValue = removed.remove(value.uri)
            if (oldValue == null) {
                added[value.uri] = value
            }
            else if (value.contentTimestamp > oldValue.contentTimestamp) {
                updated[value.uri] = value
            }
        }
        mutableResources.putAll(result.resolved.mapKeys { it.key.uri })
        if (added.isEmpty() && updated.isEmpty() && removed.isEmpty()) {
            return
        }
        removed.keys.forEach({ mutableResources.remove(it) })
        val update = MapDataUpdate(updatePermission, added, updated, removed)
        for (listener in listeners) {
            listener.onMapDataUpdated(update)
        }
    }

    /**
     * The create update permission is an opaque interface that enforces only holders of
     * the the permission instance have the ability to create a [MapDataUpdate]
     * associated with a given instance of [MapDataManager].  This can simply be an
     * anonymous implementation created at the call site of the [configuration][Config.updatePermission].
     * For example:
     *
     * <pre>
     * new MapDataManager(new MapDataManager.Config()<br></br>
     * .updatePermission(new MapDataManager.CreateUpdatePermission(){})
     * // other config items
     * );
     * </pre>
     *
     * This prevents the programmer error of creating update objects outside of the
     * `MapDataManager` instance to [deliver][MapDataListener.onMapDataUpdated]
     * to listeners.
     */
    interface CreateUpdatePermission

    /**
     * Implement this interface and [register][.addUpdateListener]
     * an instance to receive [notifications][.onMapDataUpdated] when the set of resources changes.
     */
    interface MapDataListener {
        fun onMapDataUpdated(update: MapDataUpdate)
    }

    class Config {
        var context: Application? = null
            private set
        var updatePermission: CreateUpdatePermission? = null
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

        fun updatePermission(x: CreateUpdatePermission): Config {
            updatePermission = x
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

    inner class MapDataUpdate(
            updatePermission: CreateUpdatePermission,
            val added: Map<URI, MapDataResource>,
            val updated: Map<URI, MapDataResource>,
            val removed: Map<URI, MapDataResource>) {

        val source = this@MapDataManager

        init {
            if (updatePermission !== source.updatePermission) {
                throw Error("erroneous attempt to create update from cache manager instance " + this@MapDataManager)
            }
        }
    }

    private inner class RepositoryObserver internal constructor(private val repo: MapDataRepository) : Observer<Set<MapDataResource>> {

        override fun onChanged(data: Set<MapDataResource>?) {
            onMapDataChanged(data ?: emptySet(), repo)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class ResolveRepositoryChangeTask internal constructor(
            val repository: MapDataRepository,
            changedResources: Set<MapDataResource>)
        : AsyncTask<Void, Void, ResolveRepositoryChangeResult>() {

        internal val toResolve: MutableMap<URI, MapDataResource> = HashMap()
        internal var result: ResolveRepositoryChangeResult
        internal val stringValue: String

        init {
            val oldResources = resourcesForRepo(repository)
            val newResources = HashMap<URI, MapDataResource>()
            val resolved = HashMap<MapDataResource, MapDataResource>()
            for (resource in changedResources) {
                newResources[resource.uri] = resource
                if (resource.resolved == null) {
                    toResolve[resource.uri] = resource
                }
                else {
                    resolved[resource] = resource
                }
            }
            result = ResolveRepositoryChangeResult(repository, oldResources, newResources, resolved)
            stringValue = "${javaClass.simpleName} repo ${repository.id} resolving\n  ${toResolve.keys}"
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
            val oldResource = result.oldResources[uri]
            if (oldResource != null && oldResource.contentTimestamp >= resource.contentTimestamp) {
                return resource.resolve(oldResource.resolved!!)
            }
            for (provider in providers) {
                if (provider.canHandleResource(resource)) {
                    return provider.resolveResource(resource)
                }
            }
            throw MapDataResolveException(uri, "no cache provider could handle resource $resource")
        }

        override fun doInBackground(vararg nothing: Void): ResolveRepositoryChangeResult {
            toResolve.values.iterator().run {
                while (hasNext() && !isCancelled) {
                    val unresolved = next()
                    try {
                        val resolved = resolveWithFirstCapableProvider(unresolved)
                        result!!.resolved[unresolved] = resolved
                    }
                    catch (e: MapDataResolveException) {
                        result!!.failed[unresolved] = e
                    }
                    remove()
                }
                return result!!
            }
        }

        override fun onCancelled(cancelledResult: ResolveRepositoryChangeResult?) {
            toResolve.values.iterator().run {
                while (hasNext()) {
                    val resource = next()
                    result.cancelled[resource] = resource
                    remove()
                }
            }
            onResolveFinished(this)
        }

        override fun onPostExecute(result: ResolveRepositoryChangeResult) {
            onResolveFinished(this)
        }

        @MainThread
        fun updateProgressFromCancelledChange(cancelledChange: MapDataManager.ResolveRepositoryChangeTask) {
            if (status != Status.PENDING) {
                throw IllegalStateException("attempt to initialize progress from cancelled change after this change already began")
            }
            cancelledChange.result.resolved.values.forEach({
                val unresolved = toResolve[it.uri]
                if (unresolved != null && unresolved.contentTimestamp <= it.contentTimestamp) {
                    result.resolved[unresolved] = it
                    toResolve.remove(it.uri)
                }
            })
        }

        override fun toString(): String {
            return stringValue
        }
    }



    private class ResolveRepositoryChangeResult internal constructor(
            val repository: MapDataRepository,
            val oldResources: Map<URI, MapDataResource>,
            val newResources: Map<URI, MapDataResource>,
            val resolved: MutableMap<MapDataResource, MapDataResource>) {

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
