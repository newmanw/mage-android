package mil.nga.giat.mage.map.cache;

import java.util.Set;

/**
 * A MapDataResository is a store {@link MapDataResource resources} that {@link MapDataManager}
 * can potentially import to show data on a {@link com.google.android.gms.maps.GoogleMap map}.
 * An implementation of this interface can return a set of {@link MapDataResource#MapDataResource(java.net.URI) unresolved}
 * resources, or may return fully {@link MapDataResource#MapDataResource(java.net.URI, String, Class, Set) resolved}
 * resources with a {@link MapDataProvider type} and {@link MapLayerDescriptor layer information}.
 * In the former case, {@link MapDataManager} will attempt to apply the correct {@link MapDataProvider} to
 * import the resource.  In the latter case, the MapDataRepository and the {@link MapDataProvider type}
 * implementations might be one-and-the-same, for example, a WMS/WFS server implementation.
 */
public interface MapDataRepository {

    Set<MapDataResource> retrieveMapDataResources();
}
