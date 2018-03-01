package mil.nga.giat.mage.map.cache;

import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapDataResourceTest {

    static final Set<MapLayerDescriptor> none = Collections.emptySet();

    @Test
    public void equalWhenNameAndTypeAreEqual() {
        MapDataResource c1 = new MapDataResource(null, "c1", MapDataProvider.class, none);
        MapDataResource c2 = new MapDataResource(null, "c1", MapDataProvider.class, none);

        assertTrue(c1.equals(c2));
    }

    public void equalWhenOverlaysAreDifferent() {
        MapDataResource c1 = new MapDataResource(null, "c1", MapDataProvider.class, none);
        MapDataResource c2 = new MapDataResource(null, "c1", MapDataProvider.class,
            Collections.<MapLayerDescriptor>singleton(new MapLayerDescriptorTest.TestLayerDescriptor1("test", "c1", MapDataProvider.class)));

        assertTrue(c1.equals(c2));
    }

    @Test
    public void notEqualWhenOtherIsNull() {
        MapDataResource c1 = new MapDataResource(null, "c1", MapDataProvider.class, none);

        assertFalse(c1.equals(null));
    }

    @Test
    public void notEqualWhenOtherIsNotMapCache() {
        MapDataResource c1 = new MapDataResource(null, "c1", MapDataProvider.class, none);

        assertFalse(c1.equals(new Object()));
    }

    @Test
    public void notEqualWhenNamesAreDifferent() {
        MapDataResource c1 = new MapDataResource(null, "c1", MapDataProvider.class, none);
        MapDataResource c2 = new MapDataResource(null, "c2", MapDataProvider.class, none);

        assertFalse(c1.equals(c2));
    }
}
