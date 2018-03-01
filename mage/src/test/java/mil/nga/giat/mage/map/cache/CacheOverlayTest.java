package mil.nga.giat.mage.map.cache;

import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CacheOverlayTest {

    static class TestCacheProvider1 implements CacheProvider {

        @Override
        public boolean isCacheFile(URI cacheFile) {
            return false;
        }

        @Override
        public MapCache importCacheFromFile(URI cacheFile) throws CacheImportException {
            return null;
        }

        @Override
        public Set<MapCache> refreshCaches(Set<MapCache> existingCaches) {
            return null;
        }

        @Override
        public OverlayOnMapManager.OverlayOnMap createOverlayOnMapFromCache(CacheOverlay cache, OverlayOnMapManager map) {
            return null;
        }
    }

    static class TestCacheProvider2 implements CacheProvider {

        @Override
        public boolean isCacheFile(URI cacheFile) {
            return false;
        }

        @Override
        public MapCache importCacheFromFile(URI cacheFile) throws CacheImportException {
            return null;
        }

        @Override
        public Set<MapCache> refreshCaches(Set<MapCache> existingCaches) {
            return null;
        }

        @Override
        public OverlayOnMapManager.OverlayOnMap createOverlayOnMapFromCache(CacheOverlay cache, OverlayOnMapManager map) {
            return null;
        }
    }

    static class TestCacheOverlay1 extends CacheOverlay {

        protected TestCacheOverlay1(String overlayName, String cacheName, Class<? extends CacheProvider> cacheType) {
            super(overlayName, cacheName, cacheType);
        }
    }

    static class TestCacheOverlay2 extends CacheOverlay {

        protected TestCacheOverlay2(String overlayName, String cacheName, Class<? extends CacheProvider> cacheType) {
            super(overlayName, cacheName, cacheType);
        }
    }

    @Test
    public void equalWhenNamesAndTypeEqualRegardlessOfClass() {
        TestCacheOverlay1 overlay1 = new TestCacheOverlay1("test1", "cache1", TestCacheProvider1.class);
        TestCacheOverlay1 overlay2 = new TestCacheOverlay1("test1", "cache1", TestCacheProvider1.class);
        TestCacheOverlay2 overlay3 = new TestCacheOverlay2("test1", "cache1", TestCacheProvider1.class);

        assertTrue(overlay1.equals(overlay2));
        assertTrue(overlay1.equals(overlay3));
    }

    @Test
    public void notEqualWhenOtherIsNull() {
        TestCacheOverlay1 overlay = new TestCacheOverlay1("testOverlay", "testCache", TestCacheProvider1.class);

        assertFalse(overlay.equals(null));
    }

    @Test
    public void notEqualWhenProviderTypesAreDifferent() {
        TestCacheOverlay1 overlay1 = new TestCacheOverlay1("test1", "cache1", TestCacheProvider1.class);
        TestCacheOverlay1 overlay2 = new TestCacheOverlay1(overlay1.getOverlayName(), overlay1.getCacheName(), TestCacheProvider2.class);
        TestCacheOverlay2 overlay3 = new TestCacheOverlay2(overlay1.getOverlayName(), overlay1.getCacheName(), TestCacheProvider2.class);

        assertFalse(overlay1.equals(overlay2));
        assertFalse(overlay1.equals(overlay3));
    }

    @Test
    public void notEqualWhenNamesAreDifferent() {
        TestCacheOverlay1 overlay1 = new TestCacheOverlay1("test1", "cache1", TestCacheProvider1.class);
        TestCacheOverlay1 overlay2 = new TestCacheOverlay1("test2", "cache1", TestCacheProvider1.class);
        TestCacheOverlay1 overlay3 = new TestCacheOverlay1("test1", "cache2", TestCacheProvider1.class);

        assertFalse(overlay1.equals(overlay2));
        assertFalse(overlay1.equals(overlay3));
    }

    @Test
    public void notEqualWhenNamesAndProvidersAreDifferent() {
        TestCacheOverlay1 overlay1 = new TestCacheOverlay1("test1", "cache1", TestCacheProvider1.class);
        TestCacheOverlay1 overlay2 = new TestCacheOverlay1("test2", "cache1", TestCacheProvider2.class);
        TestCacheOverlay2 overlay3 = new TestCacheOverlay2("test1", "cache2", TestCacheProvider2.class);
        TestCacheOverlay1 overlay4 = new TestCacheOverlay1("test2", "cache2", TestCacheProvider2.class);

        assertFalse(overlay1.equals(overlay2));
        assertFalse(overlay1.equals(overlay3));
        assertFalse(overlay1.equals(overlay4));
    }
}
