package mil.nga.giat.mage.map.cache;

import android.arch.lifecycle.LiveData;
import android.support.annotation.MainThread;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import mil.nga.giat.mage.data.Resource;

/**
 * A MapDataResository is a store of {@link MapDataResource resources} that {@link MapDataManager}
 * can potentially import to show data on a {@link com.google.android.gms.maps.GoogleMap map}.
 * An implementation of this interface can return a set of {@link MapDataResource#MapDataResource(URI, Class, long) unresolved}
 * resources, or may return fully {@link MapDataResource#MapDataResource(URI, Class, long, MapDataResource.Resolved) resolved}
 * resources with a {@link MapDataProvider type} and {@link MapLayerDescriptor layer information}.
 * In the former case, {@link MapDataManager} will attempt to apply the correct {@link MapDataProvider} to
 * import the resource.  In the latter case, the MapDataRepository and the {@link MapDataProvider type}
 * implementations might be one-and-the-same, for example, a WMS/WFS server implementation.
 */
// TODO: add api for telling a repository when unresolved resources have been resolved
public abstract class MapDataRepository extends LiveData<Set<MapDataResource>> implements Resource<Set<MapDataResource>> {

    @MainThread
    public abstract boolean ownsResource(URI resourceUri);

    // TODO: maybe necessary, maybe not, with the resolvedResources already being passed to refreshAvailableMapData()
//    @MainThread
//    public abstract void onResolved(Set<MapDataResource> resource);

    @MainThread
    public abstract void refreshAvailableMapData(Map<URI, MapDataResource> resolvedResources, Executor executor);
}
