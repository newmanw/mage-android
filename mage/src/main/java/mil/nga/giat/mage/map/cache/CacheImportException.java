package mil.nga.giat.mage.map.cache;

import java.io.File;

public class CacheImportException extends Exception {

    private final File cacheFile;

    public CacheImportException(File cacheFile) {
        this(cacheFile, "failed to import cache cacheFile " + cacheFile.getName());
    }

    public CacheImportException(File cacheFile, String message) {
        this(cacheFile, message, null);
    }

    public CacheImportException(File cacheFile, String message, Throwable cause) {
        super(message, cause);
        this.cacheFile = cacheFile;
    }

    public CacheImportException(File cacheFile, Throwable cause) {
        this(cacheFile, "failed to import cache cacheFile " + cacheFile.getName(), cause);
    }

    public File getCacheFile() {
        return cacheFile;
    }
}
