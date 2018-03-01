package mil.nga.giat.mage.map.cache;

import java.net.URI;

public class CacheImportException extends Exception {

    private final URI cacheFile;

    public CacheImportException(URI cacheFile) {
        this(cacheFile, "failed to import cache cacheFile " + cacheFile);
    }

    public CacheImportException(URI cacheFile, String message) {
        this(cacheFile, message, null);
    }

    public CacheImportException(URI cacheFile, String message, Throwable cause) {
        super(message, cause);
        this.cacheFile = cacheFile;
    }

    public CacheImportException(URI cacheFile, Throwable cause) {
        this(cacheFile, "failed to import cache cacheFile " + cacheFile, cause);
    }

    public URI getCacheFile() {
        return cacheFile;
    }
}
