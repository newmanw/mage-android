package mil.nga.giat.mage.map.cache;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Return a list of map data resources, for instance, from a local storage directory, or a server.
 */
public interface MapDataRepository {

    Set<MapCache> retrieveMapDataResources();
}
