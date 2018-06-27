package mil.nga.giat.mage.map.cache;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MapLayerDescriptorTest {

    static abstract class TestMapDataProvider1 implements MapDataProvider {}

    static abstract class TestMapDataProvider2 implements MapDataProvider {}

    static class TestLayerDescriptor1 extends MapLayerDescriptor {

        protected TestLayerDescriptor1(String overlayName, URI resourceUri, Class<? extends MapDataProvider> cacheType) {
            super(overlayName, resourceUri, cacheType);
        }
    }

    static class TestLayerDescriptor2 extends MapLayerDescriptor {

        protected TestLayerDescriptor2(String overlayName, URI resourceUri, Class<? extends MapDataProvider> cacheType) {
            super(overlayName, resourceUri, cacheType);
        }
    }

    @Rule
    public TestName testName = new TestName();

    private URI makeUri() {
        try {
            return new URI(MapLayerDescriptorTest.class.getSimpleName(), testName.getMethodName(), "/" + UUID.randomUUID().toString(), null);
        }
        catch (URISyntaxException e) {
            throw new Error(e);
        }
    }

    @Test
    public void equalWhenNamesAndTypeEqualRegardlessOfClass() {
        TestLayerDescriptor1 overlay1 = new TestLayerDescriptor1("test1", makeUri(), TestMapDataProvider1.class);
        TestLayerDescriptor1 overlay2 = new TestLayerDescriptor1("test1", overlay1.getResourceUri(), TestMapDataProvider1.class);
        TestLayerDescriptor2 overlay3 = new TestLayerDescriptor2("test1", overlay1.getResourceUri(), TestMapDataProvider1.class);

        assertTrue(overlay1.equals(overlay2));
        assertTrue(overlay1.equals(overlay3));
    }

    @Test
    public void notEqualWhenOtherIsNull() {
        TestLayerDescriptor1 overlay = new TestLayerDescriptor1("testOverlay", makeUri(), TestMapDataProvider1.class);

        assertFalse(overlay.equals(null));
    }

    @Test
    public void equalWhenProviderTypesAreDifferent() {
        TestLayerDescriptor1 overlay1 = new TestLayerDescriptor1("test1", makeUri(), TestMapDataProvider1.class);
        TestLayerDescriptor1 overlay2 = new TestLayerDescriptor1(overlay1.getLayerName(), overlay1.getResourceUri(), TestMapDataProvider2.class);
        TestLayerDescriptor2 overlay3 = new TestLayerDescriptor2(overlay1.getLayerName(), overlay1.getResourceUri(), TestMapDataProvider2.class);

        assertTrue(overlay1.equals(overlay2));
        assertTrue(overlay1.equals(overlay3));
    }

    @Test
    public void notEqualWhenNamesAreDifferent() {
        TestLayerDescriptor1 overlay1 = new TestLayerDescriptor1("test1", makeUri(), TestMapDataProvider1.class);
        TestLayerDescriptor1 overlay2 = new TestLayerDescriptor1("test2", overlay1.getResourceUri(), TestMapDataProvider1.class);
        TestLayerDescriptor1 overlay3 = new TestLayerDescriptor1("test1", makeUri(), TestMapDataProvider1.class);

        assertFalse(overlay1.equals(overlay2));
        assertFalse(overlay1.equals(overlay3));
    }

    @Test
    public void notEqualWhenNamesAndProvidersAreDifferent() {
        TestLayerDescriptor1 overlay1 = new TestLayerDescriptor1("test1", makeUri(), TestMapDataProvider1.class);
        TestLayerDescriptor1 overlay2 = new TestLayerDescriptor1("test2", overlay1.getResourceUri(), TestMapDataProvider2.class);
        TestLayerDescriptor2 overlay3 = new TestLayerDescriptor2("test1", makeUri(), TestMapDataProvider2.class);
        TestLayerDescriptor1 overlay4 = new TestLayerDescriptor1("test2", overlay3.getResourceUri(), TestMapDataProvider2.class);

        assertFalse(overlay1.equals(overlay2));
        assertFalse(overlay1.equals(overlay3));
        assertFalse(overlay1.equals(overlay4));
    }

    @Test
    public void createsHierarchicalLayerUriWhenResourceUriPathHasTrailingSlash() throws URISyntaxException {
        URI resourceUri = new URI("mage", "test", "/trailing/slash/", null);
        TestLayerDescriptor1 desc = new TestLayerDescriptor1("testLayer", resourceUri, TestMapDataProvider1.class);

        assertThat(desc.getLayerUri(), is(new URI("mage", "test", "/trailing/slash/testLayer", null)));
    }

    @Test
    public void createsHierarchicalLayerUriWhenResourceUriPathHasNoTrailingSlash() throws URISyntaxException {
        URI resourceUri = new URI("mage", "test", "/no/trailing/slash", null);
        TestLayerDescriptor1 desc = new TestLayerDescriptor1("testLayer", resourceUri, TestMapDataProvider1.class);

        assertThat(desc.getLayerUri(), is(new URI("mage", "test", "/no/trailing/slash/testLayer", null)));
    }
}
