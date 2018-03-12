package mil.nga.giat.mage.map.cache;

import java.net.URI;

public class MapDataResolveException extends Exception {

    private final URI resourceUri;

    public MapDataResolveException(URI resourceUri) {
        this(resourceUri, (Throwable) null);
    }

    public MapDataResolveException(URI resourceUri, String message) {
        this(resourceUri, message, null);
    }

    public MapDataResolveException(URI resourceUri, String message, Throwable cause) {
        super(message, cause);
        this.resourceUri = resourceUri;
    }

    public MapDataResolveException(URI resourceUri, Throwable cause) {
        this(resourceUri, "failed to import resource " + resourceUri, cause);
    }

    public URI getResourceUri() {
        return resourceUri;
    }
}
