package mil.nga.giat.mage.map.cache;

import android.support.annotation.WorkerThread;

/**
 * A MapDataProvider represents a specific cache data format that can put overlays on a map.
 *
 * TODO: thread-safety coniderations - {@link MapDataManager} for now only invokes these methods serially
 * across all providers, but could be otherwise
 */
@WorkerThread
public interface MapDataProvider {

    /**
     * Does this provider recognize the given file as its type of cache?
     *
     * @param resource
     * @return
     *
     * TODO: add extra known information about the uri content, e.g., from a HEAD request
     */
    boolean canHandleResource(MapDataResource resource);

    /**
     * Attempt to import the given resource's content as this provider's data type and return the
     * {@link mil.nga.giat.mage.map.cache.MapDataResource.Resolved resolved} resource.
     *
     * @param resource a {@link MapDataResource} to resolve
     * @return a resolved {@link MapDataResource resource}
     * @throws MapDataResolveException if there is an error resolving the resource
     *
     * TODO: an argument for the owning repository may be necessary at some point
     */
    MapDataResource resolveResource(MapDataResource resource) throws MapDataResolveException;

    MapLayerManager.MapLayer createMapLayerFromDescriptor(MapLayerDescriptor layerDescriptor, MapLayerManager map);
}
