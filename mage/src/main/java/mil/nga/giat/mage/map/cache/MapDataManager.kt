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
    private val pendingChangeForRepository = HashMap<String, ResolveRepositoryChangeTask>()

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
        val oldResources = resourcesForRepo(source).toMutableMap()
        val newResources = resources.associateByTo(HashMap(), MapDataResource::uri)
        val toResolve = HashMap<URI, MapDataResource>()
        for (resource in resources) {
            if (resource.resolved == null) {
                toResolve[resource.uri] = resource
            }
        }
        if (toResolve.isEmpty()) {
            // TODO: dispatch immediately or go through the async flow?
            val change = ResolveRepositoryChangeResult(source, oldResources, newResources)
            change.resolved.putAll(newResources.mapKeys { it.value })
            finishPendingChangeForResolveResult(change)
            return
        }
        val resolveTask = ResolveRepositoryChangeTask(source, oldResources, newResources, toResolve)
        val replaced = pendingChangeForRepository.put(source.id, resolveTask)
        if (replaced != null) {
            throw Error("TODO: handle concurrent updates")
        }
        resolveTask.executeOnExecutor(executor)
    }

    private fun onResolveFinished(result: ResolveRepositoryChangeResult) {
        val resolveTask = pendingChangeForRepository.remove(result.repository.id)!!
        if (resolveTask.isCancelled) {
            // TODO something
            return
        }
        if (resolveTask.get() !== result) {
            throw Error("stored resolve task's result differs from result argument")
        }
        result.repository.onExternallyResloved(HashSet(result.resolved.values))
    }

    private fun finishPendingChangeForResolveResult(result: ResolveRepositoryChangeResult) {
        val added = HashMap<URI, MapDataResource>()
        val updated = HashMap<URI, MapDataResource>()
        val removed = result.oldResources
        for ((key, value) in result.resolved) {
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
            repository: MapDataRepository,
            oldResources: MutableMap<URI, MapDataResource>,
            newResources: MutableMap<URI, MapDataResource>,
            toResolve: MutableMap<URI, MapDataResource>) : AsyncTask<Void, ResolveRepositoryChangeProgress, ResolveRepositoryChangeResult>() {

        private val resolveQueue = ArrayDeque<MapDataResource>(toResolve.values)
        private val result = ResolveRepositoryChangeResult(repository, oldResources, newResources)

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
            throw MapDataResolveException(uri, "no cache provider could handle file $resource")
        }

        override fun doInBackground(vararg nothing: Void): ResolveRepositoryChangeResult {
            while (resolveQueue.size > 0 && !isCancelled) {
                val unresolved = resolveQueue.poll()
                try {
                    val resolved = resolveWithFirstCapableProvider(unresolved)
                    publishProgress(ResolveRepositoryChangeProgress(unresolved, resolved))
                }
                catch (e: MapDataResolveException) {
                    publishProgress(ResolveRepositoryChangeProgress(unresolved, e))
                }
            }
            return result
        }

        override fun onProgressUpdate(vararg values: ResolveRepositoryChangeProgress?) {
            val update = values[0]!!
            if (update.resolved != null) {
                result.resolved[update.unresolved] = update.resolved
            }
            else {
                result.failed[update.unresolved] = update.failure!!
            }
        }

        override fun onCancelled(result: ResolveRepositoryChangeResult) {
            while (resolveQueue.size > 0) {
                val resource = resolveQueue.poll()
                result.cancelled[resource] = resource
            }
            onResolveFinished(result)
        }

        override fun onPostExecute(result: ResolveRepositoryChangeResult) {
            onResolveFinished(result)
        }
    }

    private class ResolveRepositoryChangeProgress private constructor(val unresolved: MapDataResource, val resolved: MapDataResource?, val failure: MapDataResolveException?) {

        constructor(unresolved: MapDataResource, resolved: MapDataResource) : this(unresolved, resolved, null)
        constructor(unresolved: MapDataResource, failure: MapDataResolveException) : this(unresolved, null, failure)
    }

    private class ResolveRepositoryChangeResult internal constructor(
            val repository: MapDataRepository,
            val oldResources: MutableMap<URI, MapDataResource>,
            val newResources: MutableMap<URI, MapDataResource>) {

        val resolved = HashMap<MapDataResource, MapDataResource>()
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
