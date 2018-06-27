package mil.nga.giat.mage.map.cache;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import mil.nga.giat.mage.data.Resource;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class MapDataResourceTest {

    static final Set<MapLayerDescriptor> none = Collections.emptySet();

    private static class TestRepo extends MapDataRepository {

        private final String id;

        private TestRepo(String id) {
            this.id = id;
        }

        @NonNull
        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean ownsResource(URI resourceUri) {
            return false;
        }

        @Override
        public void refreshAvailableMapData(Map<URI, MapDataResource> resolvedResources, Executor executor) {

        }

        @NotNull
        @Override
        public Resource.Status getStatus() {
            return null;
        }

        @Override
        public int getStatusCode() {
            return 0;
        }

        @Nullable
        @Override
        public String getStatusMessage() {
            return null;
        }
    };

    private TestRepo repo1 = new TestRepo("repo1");
    private TestRepo repo2 = new TestRepo("repo2");
    private static abstract class Provider1 implements MapDataProvider {};
    private static abstract class Provider2 implements MapDataProvider {};

    @Test
    public void equalWhenUrisAreEqual() {
        MapDataResource r1 = new MapDataResource(URI.create("test:/equals"), repo1, 0);
        MapDataResource r2 = new MapDataResource(URI.create("test:/equals"), repo2, 1);

        assertTrue(r1.equals(r2));
    }

    @Test
    public void equalWhenUrisAreEqualAndOneIsResolved() {
        MapDataResource r1 = new MapDataResource(URI.create("test:/equals"), repo1, 0);
        MapDataResource r2 = new MapDataResource(URI.create("test:/equals"), repo1, 0,
            new MapDataResource.Resolved("Resolved", MapDataProvider.class));

        assertTrue(r1.equals(r2));
    }

    @Test
    public void equalWhenUrisAreEqualAndBothAreResolved() {
        MapDataResource r1 = new MapDataResource(URI.create("test:/equals"), repo1, 0,
            new MapDataResource.Resolved("Resolved A", Provider1.class));
        MapDataResource r2 = new MapDataResource(URI.create("test:/equals"), repo1, 0,
            new MapDataResource.Resolved("Resolved B", Provider2.class));

        assertTrue(r1.equals(r2));
    }

    @Test
    public void notEqualWhenOtherIsNull() {
        MapDataResource c1 = new MapDataResource(URI.create("test:/equals:null"), repo1, 0);

        assertFalse(c1.equals(null));
    }

    @Test
    public void notEqualWhenOtherIsNotResource() {
        MapDataResource c1 = new MapDataResource(URI.create("test:/equals:wrong_type"), repo1, 0);

        assertFalse(c1.equals(new Object()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void validatesUriIsNotOpaque() throws URISyntaxException {

        URI uri = new URI("test", "no_leading_slash", "mystery");

        assertTrue(uri.isOpaque());
        assertTrue(uri.isAbsolute());

        assertFalse(MapDataResource.validateUri(uri));

        new MapDataResource(uri, repo1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validatesUriIsAbsolute() throws URISyntaxException {

        URI uri = new URI(null, "/scheme_specific_part", null);

        assertFalse(uri.isOpaque());
        assertFalse(uri.isAbsolute());

        assertFalse(MapDataResource.validateUri(uri));

        new MapDataResource(uri, repo1, 0);
    }
}
