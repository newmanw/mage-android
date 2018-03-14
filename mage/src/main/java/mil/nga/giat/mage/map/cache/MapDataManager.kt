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
import mil.nga.giat.mage.map.cache.MapDataManager.Companion.instance
import java.io.File
import java.net.URI
import java.util.*
import java.util.concurrent.Executor
import kotlin.collections.HashSet


@MainThread
class MapDataManager(config: Config) : LifecycleOwner {

    private val updatePermission: CreateUpdatePermission
    private val executor: Executor
    private val repositories: Array<out MapDataRepository>
    private val providers: Array<out MapDataProvider>
    private val listeners = ArrayList<MapDataListener>()
    private val lifecycle = LifecycleRegistry(this)
    private val mapDataForRepository = HashMap<Class<out MapDataRepository>, Set<MapDataResource>>()
    private val importResourcesForRefreshTask: ResolveResourcesTask? = null

    val mapData: Set<MapDataResource> get() {
        return mapDataForRepository.values.flatMapTo(HashSet(), { return it })
    }

    /**
     * Implement this interface and [register][.addUpdateListener]
     * an instance to receive [notifications][.onMapDataUpdated] when the set of mapData changes.
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
     *
     * <pre>
     * new MapDataManager(new MapDataManager.Config()<br></br>
     * .updatePermission(new MapDataManager.CreateUpdatePermission(){})
     * // other config items
     * );
    </pre> *
     *
     * This prevents the programmer error of creating update objects outside of the
     * `MapDataManager` instance to [deliver][MapDataListener.onMapDataUpdated]
     * to listeners.
     */
    interface CreateUpdatePermission

    inner class MapDataUpdate(updatePermission: CreateUpdatePermission, val added: Set<MapDataResource>, val updated: Set<MapDataResource>, val removed: Set<MapDataResource>) {
        val source = this@MapDataManager

        init {
            if (updatePermission !== source.updatePermission) {
                throw Error("erroneous attempt to create update from cache manager instance " + this@MapDataManager)
            }
        }
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
        executor = config.executor!!
        repositories = config.repositories!!
        providers = config.providers!!
        lifecycle.markState(Lifecycle.State.CREATED)
        for (repo in repositories) {
            mapDataForRepository[repo::class.java] = HashSet()
            repo.observe(this, RepositoryObserver(repo))
        }
    }

    fun addUpdateListener(listener: MapDataListener) {
        listeners.add(listener)
    }

    fun removeUpdateListener(listener: MapDataListener) {
        listeners.remove(listener)
    }

    fun tryImportResource(resourceUri: URI) {
        for (repo in repositories) {
            if (repo.ownsResource(resourceUri)) {
                repo.refreshAvailableMapData(resolvedResourcesForRepo(repo), executor)
            }
        }
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycle
    }

    /**
     * Discover new mapData available in standard [locations][MapDataRepository], then remove defunct mapData.
     * Asynchronous notifications to [listeners][.addUpdateListener]
     * will result, one notification per refresh, per listener.  Only one refresh can be active at any moment.
     */
    fun refreshAvailableCaches() {
        for (repo in repositories) {
            repo.refreshAvailableMapData(resolvedResourcesForRepo(repo), executor)
        }
    }

    fun createMapLayerManager(map: GoogleMap): MapLayerManager {
        return MapLayerManager(this, Arrays.asList(*providers), map)
    }

    private fun resolvedResourcesForRepo(repo: MapDataRepository): Map<URI, MapDataResource>? {
        return mapDataForRepository[repo.javaClass]?.associateBy({ it.uri })
    }

    private fun onMapDataChanged(resources: Set<MapDataResource>?, source: MapDataRepository) {
        val toResolve = HashSet<MapDataResource>()
        for (resource in resources!!) {
            if (resource.resolved == null) {
                toResolve.add(resource)
            }
        }
        if (toResolve.isEmpty()) {
            return
        }
        val resolveTask = ResolveResourcesTask(toResolve, source)
        resolveTask.executeOnExecutor(executor)
    }

    private fun onResolveFinished(result: ResolveResult) {

    }

    private inner class RepositoryObserver internal constructor(private val repo: MapDataRepository) : Observer<Set<MapDataResource>> {

        override fun onChanged(data: Set<MapDataResource>?) {
            onMapDataChanged(data, repo)
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
