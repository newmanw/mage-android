package mil.nga.giat.mage.map.cache

import android.annotation.SuppressLint
import android.app.Application
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LifecycleRegistry
import android.arch.lifecycle.Observer
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.support.annotation.MainThread
import com.google.android.gms.maps.GoogleMap
import java.io.File
import java.net.URI
import java.util.*
import java.util.concurrent.Executor
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap


@MainThread
class MapDataManager(config: Config) : LifecycleOwner {

    private val updatePermission: CreateUpdatePermission
    private val executor: Executor
    private val repositories: Array<out MapDataRepository>
    private val providers: Array<out MapDataProvider>
    private val listeners = ArrayList<MapDataListener>()
    private val lifecycle = LifecycleRegistry(this)
    private val repositoryResources = HashMap<String, Set<MapDataResource>>()
    private val pendingUpdates = HashMap<String, PendingUpdate>()
    private var pendingResolve = LinkedHashMap<URI, MapDataResource>()

    val resources: Map<URI, MapDataResource>
        get() = repositoryResources.flatMap({ it.value }).associateBy({ it.uri })

    var layers: Map<URI, MapLayerDescriptor> = emptyMap()
        get() = repositoryResources.asSequence().flatMap({ it.value.asSequence() }).flatMap({ it.layers.asSequence() }).map({ it.value }).associateBy({ it.layerUri })
        private set

    /**
     * Implement this interface and [register][.addUpdateListener]
     * an instance to receive [notifications][.onMapDataUpdated] when the set of resources changes.
     */
    interface MapDataListener {
        fun onMapDataUpdated(update: MapDataUpdate)
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

    private class PendingUpdate(
            val repository: MapDataRepository,
            val oldResources: HashMap<URI, MapDataResource>,
            var newResources: HashMap<URI, MapDataResource>,
            val toResolve: HashMap<URI, MapDataResource>) {
        val isReady: Boolean get() = toResolve.isEmpty()
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

    init {
        updatePermission = config.updatePermission!!
        repositories = config.repositories!!
        providers = config.providers!!
        executor = config.executor!!
        lifecycle.markState(Lifecycle.State.RESUMED)
        for (repo in repositories) {
            val resources = repo.value ?: HashSet()
            repositoryResources[repo.id] = resources
            repo.observe(this, RepositoryObserver(repo))
        }
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
                repo.refreshAvailableMapData(resolvedResourcesForRepo(repo), executor)
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
            repo.refreshAvailableMapData(resolvedResourcesForRepo(repo), executor)
        }
    }

    fun createMapLayerManager(map: GoogleMap): MapLayerManager {
        return MapLayerManager(this, Arrays.asList(*providers), map)
    }

    fun destroy() {
        listeners.clear()
        lifecycle.markState(Lifecycle.State.DESTROYED)
    }

    private fun resolvedResourcesForRepo(repo: MapDataRepository): Map<URI, MapDataResource>? {
        return repositoryResources[repo.id]?.associateBy({ it.uri })
    }

    private fun onMapDataChanged(resources: Set<MapDataResource>, source: MapDataRepository) {
        val oldResources = (repositoryResources[source.id] ?: emptySet()).associateByTo(HashMap<URI, MapDataResource>(), MapDataResource::uri)
        val newResources = resources.associateByTo(HashMap(), MapDataResource::uri)
        val toResolve = HashMap<URI, MapDataResource>()
        for (resource in resources) {
            if (resource.resolved == null) {
                toResolve[resource.uri] = resource
            }
        }
        if (toResolve.isEmpty()) {
            repositoryResources[source.id] = resources
            val added = HashMap<URI, MapDataResource>()
            val updated = HashMap<URI, MapDataResource>()
            for ((key, value) in newResources) {
                val oldValue = oldResources.remove(key)
                if (oldValue == null) {
                    added[key] = value
                }
                else if (value.contentTimestamp > oldValue.contentTimestamp) {
                    updated[key] = value
                }
            }
            if (added.isEmpty() && updated.isEmpty() && oldResources.isEmpty()) {
                return
            }
            val update = MapDataUpdate(updatePermission, added, updated, oldResources)
            for (listener in listeners) {
                listener.onMapDataUpdated(update)
            }
            return
        }
        var pendingUpdate = pendingUpdates[source.id]
        // TODO: need to consider resources that were already resolved in the pending update,
        // but that may have been removed in this change
        if (pendingUpdate == null) {
            pendingUpdate = PendingUpdate(source, oldResources, newResources, toResolve)
            pendingUpdates[source.id] = pendingUpdate
        }
        else {
            pendingUpdate.newResources = resources.associateByTo(HashMap(), MapDataResource::uri)
        }
        for (unresolved in toResolve.values) {
            val t: AsyncTask<Void,Void,Void>? = null
        }
    }

    private fun onResolveFinished(result: ResolveResult) {

    }

    private inner class RepositoryObserver internal constructor(private val repo: MapDataRepository) : Observer<Set<MapDataResource>> {

        override fun onChanged(data: Set<MapDataResource>?) {
            onMapDataChanged(data ?: emptySet(), repo)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class ResolveResourcesTask
    internal constructor(unresolved: Set<MapDataResource>, private val repo: MapDataRepository) : AsyncTask<Void, Void, ResolveResult>() {

        private val unresolved: Array<MapDataResource> = unresolved.toTypedArray()

        @Throws(MapDataResolveException::class)
        private fun importFromFirstCapableProvider(resource: MapDataResource): MapDataResource {
            val uri = resource.uri
            for (provider in providers) {
                if (uri.scheme.equals("file", ignoreCase = true)) {
                    val cacheFile = File(uri)
                    if (!cacheFile.canRead()) {
                        throw MapDataResolveException(uri, "cache file is not readable or does not exist: " + cacheFile.name)
                    }
                }
                if (provider.canHandleResource(resource)) {
                    return provider.resolveResource(resource)
                }
            }
            throw MapDataResolveException(uri, "no cache provider could handle file $resource")
        }

        override fun doInBackground(vararg nothing: Void): ResolveResult {
            val resolved = HashMap<MapDataResource, MapDataResource>(unresolved.size)
            val fails = HashMap<MapDataResource, MapDataResolveException>(unresolved.size)
            for (resource in unresolved) {
                val imported: MapDataResource
                try {
                    imported = importFromFirstCapableProvider(resource)
                    resolved[imported] = imported
                } catch (e: MapDataResolveException) {
                    fails[resource] = e
                }
            }
            return ResolveResult(resolved, fails, repo)
        }

        override fun onPostExecute(result: ResolveResult) {
            val h = object: Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message?) {
                    super.handleMessage(msg)
                }
            }
            onResolveFinished(result)
        }
    }

    private class ResolveResult internal constructor(
            private val resolved: Map<MapDataResource, MapDataResource>,
            // TODO: propagate failed imports to user somehow
            private val failed: Map<MapDataResource, MapDataResolveException>,
            private val repo: MapDataRepository)

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
