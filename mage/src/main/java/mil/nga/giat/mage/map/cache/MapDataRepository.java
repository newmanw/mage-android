package mil.nga.giat.mage.map.cache;

import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import mil.nga.giat.mage.data.Resource;

/**
 * A MapDataResository is a store of {@link MapDataResource resources} that {@link MapDataManager}
 * can potentially import to show data on a {@link com.google.android.gms.maps.GoogleMap map}.
 * An implementation of this interface can return a set of {@link MapDataResource#MapDataResource(URI, MapDataRepository, long) unresolved}
 * resources, or may return fully {@link MapDataResource#MapDataResource(URI, MapDataRepository, long, MapDataResource.Resolved) resolved}
 * resources with a {@link MapDataProvider type} and {@link MapLayerDescriptor layer information}.
 * In the former case, {@link MapDataManager} will attempt to apply the correct {@link MapDataProvider} to
 * import the resource.  In the latter case, the MapDataRepository and the {@link MapDataProvider type}
 * implementations might be one-and-the-same, for example, a WMS/WFS server implementation.
 */
public abstract class MapDataRepository extends LiveData<Set<MapDataResource>> implements Resource<Set<MapDataResource>> {

    /**
     * Return a unique, persistent ID string for this repository.  This ID should be
     * consistent across separate process lifecycles of the host application.  This
     * default implementation returns the {@link Class#getCanonicalName() canonical}
     * class name.  Be aware the canonical class name is null for local and anonymous
     * classes.
     */
    @NonNull
    public String getId() {
        return getClass().getCanonicalName();
    }

    @Nullable
    @Override
    // does not compile without this override for some reason - guessing something with
    // Kotlin and LiveData implementing the getValue() method of the Resource interface
    public Set<MapDataResource> getValue() {
        return super.getValue();
    }

    /**
     * Notify this repository that resources have been {@link MapDataProvider#resolveResource(MapDataResource) resolved}.
     * This default implementation will retain only the resources from the given set that are present in this
     * repository's {@link #getValue() current} resource set.  This will {@link #postValue(Object) asynchronously set}
     * a new value for this repository's resource set resulting in notifications to {@link #observe(LifecycleOwner, Observer) observers}.
     * @param resources
     */
    @MainThread
    public final void onExternallyResolved(Set<MapDataResource> resources) {
        Set<MapDataResource> existing = getValue();
        if (existing == null) {
            existing = Collections.emptySet();
        }
        Set<MapDataResource> merged = new HashSet<>(resources);
        merged.retainAll(existing);
        merged.addAll(existing);
        postValue(Collections.unmodifiableSet(merged));
    }

    @MainThread
    public abstract boolean ownsResource(URI resourceUri);

    @MainThread
    public abstract void refreshAvailableMapData(Map<URI, MapDataResource> resolvedResources, Executor executor);
}
