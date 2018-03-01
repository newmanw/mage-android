package mil.nga.giat.mage.map.cache;

import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapCacheTest {

    static final Set<MapLayerDescriptor> none = Collections.emptySet();

    @Test
    public void equalWhenNameAndTypeAreEqual() {
        MapCache c1 = new MapCache("c1", CacheProvider.class, null, none);
        MapCache c2 = new MapCache("c1", CacheProvider.class, null, none);

        assertTrue(c1.equals(c2));
    }

    public void equalWhenOverlaysAreDifferent() {
        MapCache c1 = new MapCache("c1", CacheProvider.class, null, none);
        MapCache c2 = new MapCache("c1", CacheProvider.class, null,
            Collections.<MapLayerDescriptor>singleton(new CacheOverlayTest.TestLayerDescriptor1("test", "c1", CacheProvider.class)));

        assertTrue(c1.equals(c2));
    }

    @Test
    public void notEqualWhenOtherIsNull() {
        MapCache c1 = new MapCache("c1", CacheProvider.class, null, none);

        assertFalse(c1.equals(null));
    }

    @Test
    public void notEqualWhenOtherIsNotMapCache() {
        MapCache c1 = new MapCache("c1", CacheProvider.class, null, none);

        assertFalse(c1.equals(new Object()));
    }

    @Test
    public void notEqualWhenNamesAreDifferent() {
        MapCache c1 = new MapCache("c1", CacheProvider.class, null, none);
        MapCache c2 = new MapCache("c2", CacheProvider.class, null, none);

        assertFalse(c1.equals(c2));
    }
}
