package mil.nga.giat.mage.map.cache;

import java.net.URI;

public class MapDataImportException extends Exception {

    private final URI resourceUri;

    public MapDataImportException(URI resourceUri) {
        this(resourceUri, (Throwable) null);
    }

    public MapDataImportException(URI resourceUri, String message) {
        this(resourceUri, message, null);
    }

    public MapDataImportException(URI resourceUri, String message, Throwable cause) {
        super(message, cause);
        this.resourceUri = resourceUri;
    }

    public MapDataImportException(URI resourceUri, Throwable cause) {
        this(resourceUri, "failed to import resource " + resourceUri, cause);
    }

    public URI getResourceUri() {
        return resourceUri;
    }
}
