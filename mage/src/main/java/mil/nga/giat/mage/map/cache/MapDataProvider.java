package mil.nga.giat.mage.map.cache;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import com.google.android.gms.maps.GoogleMap;

/**
 * A MapDataProvider represents a specific data format that can put overlays on a map.
 *
 * TODO: eventually this should change to async calls from the main thread to resolveResource() and
 * createMapLayerAdapter() so a provider can use its own configured background
 * executor based on what kind of work it does to resolve resources and create map
 * objects, rather than whatever executor {@link MapDataManager} uses.
 * CompletableFuture would be nice but requires min SDK 24 or higher.  an option could
 * be to use https://github.com/retrostreams/android-retrofuture, or maybe rxjava/rxandroid or
 * some such thing.
 */
public interface MapDataProvider {

    /**
     * Does this provider recognize the given resource as its data type?
     *
     * @param resource
     * @return
     *
     * TODO: add extra known information about the uri content, e.g., from a HEAD request
     */
    @WorkerThread
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
    @Nullable
    @WorkerThread
    MapDataResource resolveResource(MapDataResource resource) throws MapDataResolveException;

    @Nullable
    @UiThread
    MapLayerManager.MapLayerAdapter createMapLayerAdapter(MapLayerDescriptor layerDescriptor, GoogleMap map);
}
