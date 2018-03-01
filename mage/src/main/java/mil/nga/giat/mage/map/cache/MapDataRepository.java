package mil.nga.giat.mage.map.cache;

import java.util.Set;

/**
 * Return a list of map data resources, for instance, from a local storage directory, or a server.
 */
public interface MapDataRepository {

    Set<MapDataResource> retrieveMapDataResources();
}
