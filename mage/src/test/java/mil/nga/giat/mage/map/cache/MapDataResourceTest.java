package mil.nga.giat.mage.map.cache;

import org.junit.Test;

import java.net.URI;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapDataResourceTest {

    static final Set<MapLayerDescriptor> none = Collections.emptySet();

    private static abstract class Repo1 extends MapDataRepository {};
    private static abstract class Repo2 extends MapDataRepository {};
    private static abstract class Provider1 implements MapDataProvider {};
    private static abstract class Provider2 implements MapDataProvider {};

    @Test
    public void equalWhenUrisAreEqual() {
        MapDataResource r1 = new MapDataResource(URI.create("test:equals"), Repo1.class, 0);
        MapDataResource r2 = new MapDataResource(URI.create("test:equals"), Repo2.class, 1);

        assertTrue(r1.equals(r2));
    }

    @Test
    public void equalWhenUrisAreEqualAndOneIsResolved() {
        MapDataResource r1 = new MapDataResource(URI.create("test:equals"), Repo1.class, 0);
        MapDataResource r2 = new MapDataResource(URI.create("test:equals"), Repo1.class, 0,
            new MapDataResource.Resolved("Resolved", MapDataProvider.class));

        assertTrue(r1.equals(r2));
    }

    @Test
    public void equalWhenUrisAreEqualAndBothAreResolved() {
        MapDataResource r1 = new MapDataResource(URI.create("test:equals"), Repo1.class, 0,
            new MapDataResource.Resolved("Resolved A", Provider1.class));
        MapDataResource r2 = new MapDataResource(URI.create("test:equals"), Repo1.class, 0,
            new MapDataResource.Resolved("Resolved B", Provider2.class));

        assertTrue(r1.equals(r2));
    }

    @Test
    public void notEqualWhenOtherIsNull() {
        MapDataResource c1 = new MapDataResource(URI.create("test:equals:null"), Repo1.class, 0);

        assertFalse(c1.equals(null));
    }

    @Test
    public void notEqualWhenOtherIsNotResource() {
        MapDataResource c1 = new MapDataResource(URI.create("test:equals:wrong_type"), Repo1.class, 0);

        assertFalse(c1.equals(new Object()));
    }
}
