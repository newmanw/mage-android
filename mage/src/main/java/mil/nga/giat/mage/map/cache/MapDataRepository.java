package mil.nga.giat.mage.map.cache;

import java.io.File;
import java.util.List;

/**
 * Dynamically provide a list of standard locations to search for available caches.
 * This is primarily intended to support changing external SD cards and refreshing
 * caches from there, but the concept could be extended to remote URLs as well or
 * other file sources.
 */
public interface MapDataRepository {
    List<File> getLocalSearchDirs();
}
