package mil.nga.giat.mage.map.cache;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.bechte.junit.runners.context.HierarchicalContextRunner;
import mil.nga.giat.mage.test.AsyncTesting;

import static mil.nga.giat.mage.test.AsyncTesting.waitForMainThreadToRun;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MapLayerManagerTest implements MapDataManager.CreateUpdatePermission {

    static class TestMapLayer extends MapLayerManager.MapLayer {

        boolean onMap = false;
        boolean visible;
        boolean disposed;
        int zIndex;

        TestMapLayer(MapLayerManager manager) {
            manager.super();
        }

        @Override
        protected void removeFromMap() {
            onMap = visible = false;
        }

        @Override
        protected void show() {
            visible = true;
        }

        @Override
        protected void hide() {
            visible = false;
        }

        @Override
        protected void setZIndex(int z) {
            zIndex = z;
        }

        int getZIndex() {
            return zIndex;
        }

        @Override
        protected void zoomMapToBoundingBox() {

        }

        @Override
        protected boolean isVisible() {
            return visible;
        }

        @Override
        protected String onMapClick(LatLng latLng, MapView mapView) {
            return null;
        }

        @Override
        protected void dispose() {
            disposed = true;
        }

        TestMapLayer visible(boolean x) {
            if (x) {
                show();
            }
            else {
                hide();
            }
            return this;
        }

        TestMapLayer onMap(boolean x) {
            onMap = x;
            return this;
        }

        private boolean isOnMap() {
            return onMap;
        }
    }

    private static class TestLoadLayer extends MapLayerManager.LoadLayerMapObjects<MapLayerDescriptor, TestMapLayer> {

        private TestMapLayer layer;
        private TestMapLayer preparedLayer;

        protected TestLoadLayer(MapLayerDescriptor layerDescriptor, MapLayerManager mapLayerManager) {
            super(layerDescriptor, mapLayerManager);
        }

        private TestLoadLayer withResultLayer(TestMapLayer layer) {
            this.layer = layer;
            return this;
        }

        private TestLoadLayer withPreparedLayer(TestMapLayer layer) {
            preparedLayer = layer;
            return this;
        }

        @Override
        protected MapLayerManager.MapLayer prepareLayer(TestMapLayer layer) {
            if (preparedLayer == null) {
                return layer;
            }
            return preparedLayer.onMap(true).visible(true);
        }

        @Override
        protected TestMapLayer doInBackground(Void... nothing) {
            return layer;
        }
    }

    private static MapLayerManager.MapLayer mockMapLayer(MapLayerManager overlayManager) {
        return mock(MapLayerManager.MapLayer.class, withSettings().useConstructor(overlayManager));
    }

    private static Set<MapLayerDescriptor> setOf(MapLayerDescriptor... things) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(things)));
    }

    private static Set<MapDataResource> setOf(MapDataResource... things) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(things)));
    }

    private static Map<URI, MapDataResource> mapOf(MapDataResource... things) {
        Map<URI, MapDataResource> map = new HashMap<>(things.length);
        for (MapDataResource resource : things) {
            map.put(resource.getUri(), resource);
        }
        return map;
    }

    private static long oneSecond() {
        return 1000L;
    }

    private MapDataManager mapDataManager;
    private MapDataRepository repo1;
    private MapLayerDescriptorTest.TestMapDataProvider1 provider1;
    private MapLayerDescriptorTest.TestMapDataProvider2 provider2;
    private List<MapDataProvider> providers;

    @Rule
    private TestName testName = new TestName();

    @Rule
    private AsyncTesting.MainLooperAssertion onMainThread = new AsyncTesting.MainLooperAssertion();

    private URI makeUri() {
        try {
            return new URI("test", MapLayerManagerTest.class.getSimpleName(), "/" + testName.getMethodName() + "/" + UUID.randomUUID().toString(), null);
        }
        catch (URISyntaxException e) {
            throw new Error(e);
        }
    }

    @Before
    public void setup() {

        repo1 = mock(MapDataRepository.class);
        provider1 = mock(MapLayerDescriptorTest.TestMapDataProvider1.class);
        provider2 = mock(MapLayerDescriptorTest.TestMapDataProvider2.class);
        mapDataManager = mock(MapDataManager.class, withSettings().useConstructor(new MapDataManager.Config().updatePermission(this)));
        providers = Arrays.asList(provider1, provider2);
    }

    @Test
    public void listensToCacheManagerUpdates() {

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        verify(mapDataManager).addUpdateListener(overlayManager);
    }

    @Test
    public void addsOverlaysFromAddedCaches() {

        MapLayerManager manager = new MapLayerManager(mapDataManager, providers, null);
        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 1", makeUri(), provider1.getClass());
        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 2", overlay1.getResourceUri(), provider1.getClass());
        MapDataResource mapDataResource = new MapDataResource(makeUri(), repo1, System.currentTimeMillis(),
            new MapDataResource.Resolved("test cache", provider1.getClass(), setOf(overlay1, overlay2)));
        Map<URI, MapDataResource> added = mapOf(mapDataResource);
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, added, Collections.emptyMap(), Collections.emptyMap());

        manager.onMapDataUpdated(update);

        assertThat(manager.getLayersInZOrder().size(), is(2));
        assertThat(manager.getLayersInZOrder(), hasItems(overlay1, overlay2));
    }

    @Test
    public void removesOverlaysFromRemovedCaches() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 1", makeUri(), provider1.getClass());
        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 2", overlay1.getResourceUri(), provider1.getClass());
        MapDataResource mapDataResource = new MapDataResource(overlay1.getResourceUri(), repo1, System.currentTimeMillis(),
            new MapDataResource.Resolved("test cache", provider1.getClass(), setOf(overlay1, overlay2)));

        when(mapDataManager.getResources()).thenReturn(mapOf(mapDataResource));

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        assertThat(overlayManager.getLayersInZOrder().size(), is(2));
        assertThat(overlayManager.getLayersInZOrder(), hasItems(overlay1, overlay2));

        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.emptyMap(), Collections.emptyMap(), mapOf(mapDataResource));
        overlayManager.onMapDataUpdated(update);

        assertTrue(overlayManager.getLayersInZOrder().isEmpty());
    }

    @Test
    public void removesOverlaysFromUpdatedCaches() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 1", makeUri(), provider1.getClass());
        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 2", overlay1.getResourceUri(), provider1.getClass());
        MapDataResource mapDataResource = new MapDataResource(makeUri(), repo1, 0,
            new MapDataResource.Resolved("test cache", provider1.getClass(), setOf(overlay1, overlay2)));

        when(mapDataManager.getResources()).thenReturn(mapOf(mapDataResource));

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        List<MapLayerDescriptor> overlays = overlayManager.getLayersInZOrder();
        assertThat(overlays.size(), is(2));
        assertThat(overlays, hasItems(overlay1, overlay2));

        overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1(overlay2.getLayerName(), overlay2.getResourceUri(), overlay2.getDataType());
        mapDataResource = new MapDataResource(mapDataResource.getUri(), repo1, 0,
            new MapDataResource.Resolved(mapDataResource.getResolved().getName(), mapDataResource.getResolved().getType(), setOf(overlay2)));
        Map<URI, MapDataResource> updated = mapOf(mapDataResource);
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this,
            Collections.emptyMap(), updated, Collections.emptyMap());

        overlayManager.onMapDataUpdated(update);

        overlays = overlayManager.getLayersInZOrder();
        assertThat(overlays.size(), is(1));
        assertThat(overlays, hasItem(overlay2));
    }

    @Test
    public void addsOverlaysFromUpdatedCaches() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 1", makeUri(), provider1.getClass());
        MapDataResource cache = new MapDataResource(overlay1.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("test cache", provider1.getClass(), setOf(overlay1)));

        when(mapDataManager.getResources()).thenReturn(mapOf(cache));

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        assertThat(overlayManager.getLayersInZOrder().size(), is(1));
        assertThat(overlayManager.getLayersInZOrder(), hasItem(overlay1));


        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 2", overlay1.getResourceUri(), provider1.getClass());
        cache = new MapDataResource(makeUri(), repo1, 0,
            new MapDataResource.Resolved(cache.getResolved().getName(), cache.getResolved().getType(), setOf(overlay1, overlay2)));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(
            this, Collections.emptyMap(), mapOf(cache), Collections.emptyMap());
        overlayManager.onMapDataUpdated(update);

        assertThat(overlayManager.getLayersInZOrder().size(), is(2));
        assertThat(overlayManager.getLayersInZOrder(), hasItems(overlay1, overlay2));
    }

    @Test
    public void addsAndRemovesOverlaysFromUpdatedCachesWhenOverlayCountIsUnchanged() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", makeUri(), provider1.getClass());
        MapDataResource cache = new MapDataResource(overlay1.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("test cache", provider1.getClass(), setOf(overlay1)));

        when(mapDataManager.getResources()).thenReturn(mapOf(cache));

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        assertThat(overlayManager.getLayersInZOrder().size(), is(1));
        assertThat(overlayManager.getLayersInZOrder(), hasItem(overlay1));

        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 2", overlay1.getResourceUri(), provider1.getClass());
        cache = new MapDataResource(makeUri(), repo1, 0,
            new MapDataResource.Resolved(cache.getResolved().getName(), cache.getResolved().getType(), setOf(overlay2)));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(
            this, Collections.emptyMap(), mapOf(cache), Collections.emptyMap());
        overlayManager.onMapDataUpdated(update);

        assertThat(overlayManager.getLayersInZOrder().size(), is(1));
        assertThat(overlayManager.getLayersInZOrder(), hasItems(overlay2));
    }

    @Test
    public void replacesLikeOverlaysFromUpdatedCaches() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", makeUri(), provider1.getClass());
        MapDataResource cache = new MapDataResource(overlay1.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("test cache", provider1.getClass(), setOf(overlay1)));

        when(mapDataManager.getResources()).thenReturn(mapOf(cache));

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        assertThat(overlayManager.getLayersInZOrder().size(), is(1));
        assertThat(overlayManager.getLayersInZOrder(), hasItem(overlay1));

        MapLayerDescriptor overlay1Updated = new MapLayerDescriptorTest.TestLayerDescriptor1(overlay1.getLayerName(), overlay1.getResourceUri(), overlay1.getDataType());
        cache = new MapDataResource(cache.getUri(), repo1, 0,
            new MapDataResource.Resolved(cache.getResolved().getName(), cache.getResolved().getType(), setOf(overlay1Updated)));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(
            this, Collections.emptyMap(), mapOf(cache), Collections.emptyMap());
        overlayManager.onMapDataUpdated(update);

        assertThat(overlayManager.getLayersInZOrder().size(), is(1));
        assertThat(overlayManager.getLayersInZOrder(), hasItem(overlay1));
        assertThat(overlayManager.getLayersInZOrder(), not(hasItem(sameInstance(overlay1))));
        assertThat(overlayManager.getLayersInZOrder(), hasItem(sameInstance(overlay1Updated)));
    }

    @Test
    public void createsMapLayersLazily() throws InterruptedException {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", makeUri(), provider1.getClass());
        MapDataResource mapDataResource = new MapDataResource(overlay1.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("test cache", provider1.getClass(), setOf(overlay1)));
        Map<URI, MapDataResource> added = mapOf(mapDataResource);
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, added, Collections.emptyMap(), Collections.emptyMap());
        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        waitForMainThreadToRun(() -> overlayManager.onMapDataUpdated(update));

        assertThat(overlayManager.getLayersInZOrder().size(), is(1));
        assertThat(overlayManager.getLayersInZOrder(), hasItems(overlay1));
        assertFalse(overlayManager.isLayerVisible(overlay1));
        verify(provider1, never()).createMapLayerFromDescriptor(any(MapLayerDescriptor.class), same(overlayManager));

        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(
            new TestLoadLayer(overlay1, overlayManager).withResultLayer(new TestMapLayer(overlayManager)));

        waitForMainThreadToRun(() -> {
            overlayManager.showLayer(overlay1);
            verify(provider1).createMapLayerFromDescriptor(overlay1, overlayManager);
        });

        onMainThread.assertThatWithin(oneSecond(), () -> overlayManager.isLayerVisible(overlay1), is(true));
    }


    @Test
    public void refreshesVisibleOverlayOnMapWhenUpdated() throws InterruptedException {

        MapLayerDescriptor layerDesc1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", makeUri(), provider1.getClass());
        MapDataResource mapData = new MapDataResource(layerDesc1.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("test cache", provider1.getClass(), setOf(layerDesc1)));

        when(mapDataManager.getResources()).thenReturn(mapOf(mapData));

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        assertThat(overlayManager.getLayersInZOrder().size(), is(1));
        assertThat(overlayManager.getLayersInZOrder(), hasItem(layerDesc1));
        assertFalse(overlayManager.isLayerVisible(layerDesc1));

        TestMapLayer layer1 = new TestMapLayer(overlayManager);
        when(provider1.createMapLayerFromDescriptor(layerDesc1, overlayManager))
            .thenReturn(new TestLoadLayer(layerDesc1, overlayManager).withResultLayer(layer1));

        waitForMainThreadToRun(() -> {
            overlayManager.showLayer(layerDesc1);
            verify(provider1).createMapLayerFromDescriptor(layerDesc1, overlayManager);
        });

        onMainThread.assertThatWithin(oneSecond(), layer1::isOnMap, is(true));

        MapLayerDescriptor layerDesc2 = new MapLayerDescriptorTest.TestLayerDescriptor1(layerDesc1.getLayerName(), layerDesc1.getResourceUri(), layerDesc1.getDataType());
        mapData = new MapDataResource(layerDesc2.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved(mapData.getResolved().getName(), mapData.getResolved().getType(), setOf(layerDesc2)));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.emptyMap(), mapOf(mapData), Collections.emptyMap());

        TestMapLayer layer2 = new TestMapLayer(overlayManager);
        when(provider1.createMapLayerFromDescriptor(layerDesc2, overlayManager))
            .thenReturn(new TestLoadLayer(layerDesc2, overlayManager).withResultLayer(layer2));

        waitForMainThreadToRun(() -> {
            overlayManager.onMapDataUpdated(update);
            verify(provider1).createMapLayerFromDescriptor(same(layerDesc2), same(overlayManager));
        });

        onMainThread.assertThatWithin(oneSecond(), layer2::isOnMap, is(true));

        assertTrue(layer2.isVisible());
        assertTrue(overlayManager.isLayerVisible(layerDesc2));
        assertFalse(layer1.isOnMap());
    }

    @Test
    public void doesNotRefreshButOnlyRemovesHiddenLayersWhenUpdated() throws InterruptedException {

        MapLayerDescriptor layerDesc1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", makeUri(), provider1.getClass());
        MapDataResource mapData = new MapDataResource(layerDesc1.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("test cache", provider1.getClass(), setOf(layerDesc1)));

        when(mapDataManager.getResources()).thenReturn(mapOf(mapData));

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        assertThat(overlayManager.getLayersInZOrder().size(), is(1));
        assertThat(overlayManager.getLayersInZOrder(), hasItem(layerDesc1));
        assertFalse(overlayManager.isLayerVisible(layerDesc1));

        TestMapLayer layer = new TestMapLayer(overlayManager);
        when(provider1.createMapLayerFromDescriptor(layerDesc1, overlayManager)).thenReturn(new TestLoadLayer(layerDesc1, overlayManager));

        waitForMainThreadToRun(() -> overlayManager.showLayer(layerDesc1));

        onMainThread.assertThatWithin(oneSecond(), layer::isOnMap, is(true));

        verify(provider1).createMapLayerFromDescriptor(layerDesc1, overlayManager);
        assertTrue(layer.isVisible());

        MapLayerDescriptor layerDesc2 = new MapLayerDescriptorTest.TestLayerDescriptor1(layerDesc1.getLayerName(), layerDesc1.getResourceUri(), layerDesc1.getDataType());
        mapData = new MapDataResource(layerDesc2.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved(mapData.getResolved().getName(), mapData.getResolved().getType(), setOf(layerDesc2)));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.emptyMap(), mapOf(mapData), Collections.emptyMap());

        TestMapLayer layerUpdated = new TestMapLayer(overlayManager);
        when(provider1.createMapLayerFromDescriptor(layerDesc2, overlayManager)).thenReturn(new TestLoadLayer(layerDesc2, overlayManager).withResultLayer(layerUpdated));

        waitForMainThreadToRun(() -> {
            overlayManager.onMapDataUpdated(update);
            verify(provider1, never()).createMapLayerFromDescriptor(same(layerDesc1), same(overlayManager));
        });

        assertFalse(layer.isOnMap());
        assertFalse(layerUpdated.isOnMap());
    }

    @Test
    public void doesNotRefreshUnchangedVisibleLayersFromUpdatedResources() throws InterruptedException {

        MapLayerDescriptor layerDesc1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", makeUri(), provider1.getClass());
        MapDataResource mapData = new MapDataResource(layerDesc1.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("test cache", provider1.getClass(), setOf(layerDesc1)));

        when(mapDataManager.getResources()).thenReturn(mapOf(mapData));

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        assertThat(overlayManager.getLayersInZOrder().size(), is(1));
        assertThat(overlayManager.getLayersInZOrder(), hasItem(layerDesc1));
        assertFalse(overlayManager.isLayerVisible(layerDesc1));

        TestMapLayer layer = new TestMapLayer(overlayManager);
        when(provider1.createMapLayerFromDescriptor(layerDesc1, overlayManager))
            .thenReturn(new TestLoadLayer(layerDesc1, overlayManager).withResultLayer(layer));

        waitForMainThreadToRun(() -> overlayManager.showLayer(layerDesc1));

        onMainThread.assertThatWithin(oneSecond(), layer::isOnMap, is(true));

        MapLayerDescriptor layerDesc2 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 2", mapData.getUri(), mapData.getResolved().getType());
        mapData = mapData.resolve(new MapDataResource.Resolved(mapData.getResolved().getName(), mapData.getResolved().getType(), setOf(layerDesc1, layerDesc2)));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.emptyMap(), mapOf(mapData), Collections.emptyMap());

        waitForMainThreadToRun(() -> overlayManager.onMapDataUpdated(update));

        assertTrue(layer.isOnMap());
        assertTrue(layer.isVisible());
        verify(provider1, times(1)).createMapLayerFromDescriptor(same(layerDesc1), same(overlayManager));
        verify(provider1, never()).createMapLayerFromDescriptor(same(layerDesc2), same(overlayManager));
    }

    @Test
    public void removesMapLayerWhenLayerDescriptorIsRemovedFromResource() throws InterruptedException {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", makeUri(), provider1.getClass());
        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 2", overlay1.getResourceUri(), provider1.getClass());
        MapDataResource mapData = new MapDataResource(overlay1.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("test cache", provider1.getClass(), setOf(overlay1, overlay2)));

        when(mapDataManager.getResources()).thenReturn(mapOf(mapData));

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        assertThat(overlayManager.getLayersInZOrder().size(), is(2));
        assertThat(overlayManager.getLayersInZOrder(), hasItems(overlay1, overlay2));

        TestMapLayer layer1 =  new TestMapLayer(overlayManager);
        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager))
            .thenReturn(new TestLoadLayer(overlay1, overlayManager).withResultLayer(layer1));

        waitForMainThreadToRun(() -> overlayManager.showLayer(overlay1));

        onMainThread.assertThatWithin(oneSecond(), layer1::isOnMap, is(true));

        mapData = mapData.resolve(new MapDataResource.Resolved(mapData.getResolved().getName(), mapData.getResolved().getType(), setOf(overlay2)));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.emptyMap(), mapOf(mapData), Collections.emptyMap());

        waitForMainThreadToRun(() -> overlayManager.onMapDataUpdated(update));

        assertFalse(layer1.isOnMap());
    }

    @Test
    public void removesMapLayerWhenResourceIsRemoved() throws InterruptedException {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", makeUri(), provider1.getClass());
        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 2", overlay1.getResourceUri(), provider1.getClass());
        MapDataResource cache = new MapDataResource(overlay1.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("test cache", provider1.getClass(), setOf(overlay1, overlay2)));

        when(mapDataManager.getResources()).thenReturn(mapOf(cache));

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        assertThat(overlayManager.getLayersInZOrder().size(), is(2));
        assertThat(overlayManager.getLayersInZOrder(), hasItems(overlay1, overlay2));

        TestMapLayer layer1 = new TestMapLayer(overlayManager);
        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager))
            .thenReturn(new TestLoadLayer(overlay1, overlayManager).withResultLayer(layer1));

        waitForMainThreadToRun(() -> overlayManager.showLayer(overlay1));

        onMainThread.assertThatWithin(oneSecond(), layer1::isOnMap, is(true));

        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.emptyMap(), Collections.emptyMap(), mapOf(cache));

        waitForMainThreadToRun(() -> {
            overlayManager.onMapDataUpdated(update);
            assertFalse(layer1.isOnMap());
            assertTrue(overlayManager.getLayersInZOrder().isEmpty());
        });
    }

    @Test
    public void showsOverlayTheFirstTimeOverlayIsAdded() throws InterruptedException {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", makeUri(), provider1.getClass());
        MapDataResource mapData = new MapDataResource(overlay1.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("test cache", provider1.getClass(), setOf(overlay1)));

        when(mapDataManager.getResources()).thenReturn(mapOf(mapData));

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        assertFalse(overlayManager.isLayerVisible(overlay1));

        TestMapLayer layer =  new TestMapLayer(overlayManager);
        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager))
            .thenReturn(new TestLoadLayer(overlay1, overlayManager).withResultLayer(layer));

        waitForMainThreadToRun(() -> overlayManager.showLayer(overlay1));

        onMainThread.assertThatWithin(oneSecond(), layer::isOnMap, is(true));

        assertTrue(overlayManager.isLayerVisible(overlay1));
        assertTrue(layer.isOnMap());
        assertTrue(layer.isVisible());
    }

    @Test
    public void behavesWhenTwoCachesHaveOverlaysWithTheSameName() throws InterruptedException {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay1", makeUri(), provider1.getClass());
        MapDataResource cache1 = new MapDataResource(overlay1.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("cache1", provider1.getClass(), setOf(overlay1)));

        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor2("overlay1", makeUri(), provider1.getClass());
        MapDataResource cache2 = new MapDataResource(overlay2.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("cache2", provider1.getClass(), setOf(overlay2)));

        when(mapDataManager.getResources()).thenReturn(mapOf(cache1, cache2));

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        assertThat(overlayManager.getLayersInZOrder().size(), is(2));
        assertThat(overlayManager.getLayersInZOrder(), hasItems(overlay1, overlay2));

        TestMapLayer onMap1 = new TestMapLayer(overlayManager);
        TestMapLayer onMap2 = new TestMapLayer(overlayManager);
        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(new TestLoadLayer(overlay1, overlayManager).withResultLayer(onMap1));
        when(provider1.createMapLayerFromDescriptor(overlay2, overlayManager)).thenReturn(new TestLoadLayer(overlay2, overlayManager).withResultLayer(onMap2));

        waitForMainThreadToRun(() -> {
            overlayManager.showLayer(overlay1);
            verify(provider1).createMapLayerFromDescriptor(overlay1, overlayManager);
            verify(provider1, never()).createMapLayerFromDescriptor(overlay2, overlayManager);
        });

        onMainThread.assertThatWithin(oneSecond(), onMap1::isOnMap, is(true));

        waitForMainThreadToRun(() -> {
            overlayManager.showLayer(overlay2);
            verify(provider1).createMapLayerFromDescriptor(overlay1, overlayManager);
            verify(provider1).createMapLayerFromDescriptor(overlay2, overlayManager);
        });

        onMainThread.assertThatWithin(oneSecond(), onMap2::isOnMap, is(true));

        assertTrue(onMap1.isOnMap());
        assertTrue(onMap2.isOnMap());

        waitForMainThreadToRun(() -> {
            overlayManager.hideLayer(overlay2);
            assertTrue(onMap1.isVisible());
            assertFalse(onMap2.isVisible());
        });
    }

    @Test
    public void behavesWhenTwoOverlaysAndTheirCachesHaveTheSameNames() {

        fail("unimplemented");
    }

    @Test
    public void maintainsOrderOfUpdatedCacheOverlays() {

        fail("unimplemented");
    }

    @Test
    public void forwardsMapClicksToOverlaysInZOrder() {

        fail("unimplemented");
    }

    @Test
    public void notifiesListenersWhenOverlaysUpdate() {

        fail("unimplemented");
    }

    @Test
    public void notifiesListenersWhenZOrderChanges() {

        fail("unimplemented");
    }

    @Test
    public void disposeStopsListeningToCacheManager() {

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);
        overlayManager.dispose();

        verify(mapDataManager).removeUpdateListener(overlayManager);
    }

    @Test
    public void disposeRemovesAndDisposesAllOverlays() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay1", makeUri(), provider1.getClass());
        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay2", overlay1.getResourceUri(), provider1.getClass());
        MapLayerDescriptor overlay3 = new MapLayerDescriptorTest.TestLayerDescriptor2("overlay3", makeUri(), provider2.getClass());
        MapDataResource cache1 = new MapDataResource(overlay1.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("cache1", provider1.getClass(), setOf(overlay1, overlay2)));
        MapDataResource cache2 = new MapDataResource(overlay3.getResourceUri(), repo1, 0,
            new MapDataResource.Resolved("cache1", provider2.getClass(), setOf(overlay3)));

        when(mapDataManager.getResources()).thenReturn(mapOf(cache1, cache2));

        MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

        MapLayerManager.MapLayer onMap1 = mockMapLayer(overlayManager);
        MapLayerManager.MapLayer onMap2 = mockMapLayer(overlayManager);
        MapLayerManager.MapLayer onMap3 = mockMapLayer(overlayManager);

        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(onMap1);
        when(provider1.createMapLayerFromDescriptor(overlay2, overlayManager)).thenReturn(onMap2);
        when(provider2.createMapLayerFromDescriptor(overlay3, overlayManager)).thenReturn(onMap3);

        overlayManager.showLayer(overlay1);
        overlayManager.showLayer(overlay2);
        overlayManager.showLayer(overlay3);

        when(onMap1.isOnMap()).thenReturn(true);
        when(onMap2.isOnMap()).thenReturn(true);
        when(onMap3.isOnMap()).thenReturn(true);

        overlayManager.dispose();

        verify(onMap1).removeFromMap();
        verify(onMap1).dispose();
        verify(onMap2).removeFromMap();
        verify(onMap2).dispose();
        verify(onMap3).removeFromMap();
        verify(onMap3).dispose();
    }

    public class ZOrderTests {

        private URI c1Uri;
        private URI c2Uri;
        private MapLayerDescriptor c1o1;
        private MapLayerDescriptor c1o2;
        private MapLayerDescriptor c1o3;
        private MapLayerDescriptor c2o1;
        private MapLayerDescriptor c2o2;
        private MapDataResource cache1;
        private MapDataResource cache2;

        @Before
        public void setup() throws URISyntaxException {

            c1Uri = new URI("test", "z-order", "c1", null);
            c2Uri = new URI("test", "z-order", "c2", null);

            c1o1 = new MapLayerDescriptorTest.TestLayerDescriptor1("c1.1", c1Uri, provider1.getClass());
            c1o2 = new MapLayerDescriptorTest.TestLayerDescriptor1("c1.2", c1Uri, provider1.getClass());
            c1o3 = new MapLayerDescriptorTest.TestLayerDescriptor1("c1.3", c1Uri, provider1.getClass());
            cache1 = new MapDataResource(makeUri(), repo1, 0,
                new MapDataResource.Resolved("c1", provider1.getClass(), setOf(c1o1, c1o2, c1o3)));

            c2o1 = new MapLayerDescriptorTest.TestLayerDescriptor2("c2.1", c2Uri, provider2.getClass());
            c2o2 = new MapLayerDescriptorTest.TestLayerDescriptor2("c2.2", c2Uri, provider2.getClass());
            cache2 = new MapDataResource(makeUri(), repo1, 0,
                new MapDataResource.Resolved("c2", provider2.getClass(), setOf(c2o2, c2o1)));

            when(mapDataManager.getResources()).thenReturn(mapOf(cache1, cache2));
        }

        @Test
        public void returnsModifiableCopyOfOverlayZOrder() {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> orderModified = overlayManager.getLayersInZOrder();
            Collections.reverse(orderModified);
            List<MapLayerDescriptor> orderUnmodified = overlayManager.getLayersInZOrder();

            assertThat(orderUnmodified, not(sameInstance(orderModified)));
            assertThat(orderUnmodified, not(contains(orderModified.toArray())));
            assertThat(orderUnmodified.get(0), sameInstance(orderModified.get(orderModified.size() - 1)));
        }

        @Test
        public void initializesOverlaysOnMapWithProperZOrder() {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> order = overlayManager.getLayersInZOrder();
            int c1o1z = order.indexOf(c1o1);
            int c2o1z = order.indexOf(c2o1);

            TestMapLayer c1o1OnMap = new TestMapLayer(overlayManager);
            TestMapLayer c2o1OnMap = new TestMapLayer(overlayManager);
            when(provider1.createMapLayerFromDescriptor(c1o1, overlayManager)).thenReturn(c1o1OnMap);
            when(provider2.createMapLayerFromDescriptor(c2o1, overlayManager)).thenReturn(c2o1OnMap);

            overlayManager.showLayer(c1o1);
            overlayManager.showLayer(c2o1);

            assertThat(c1o1OnMap.getZIndex(), is(c1o1z));
            assertThat(c2o1OnMap.getZIndex(), is(c2o1z));
        }

        @Test
        public void setsComprehensiveZOrderFromList() {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> order = overlayManager.getLayersInZOrder();
            Collections.reverse(order);

            assertTrue(overlayManager.setZOrder(order));

            List<MapLayerDescriptor> orderMod = overlayManager.getLayersInZOrder();

            assertThat(orderMod, equalTo(order));
        }

        @Test
        public void setsZOrderOfOverlaysOnMapFromComprehensiveUpdate() {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> order = overlayManager.getLayersInZOrder();
            int c1o1z = order.indexOf(c1o1);
            int c2o1z = order.indexOf(c2o1);
            int c2o2z = order.indexOf(c2o2);
            TestMapLayer c1o1OnMap = new TestMapLayer(overlayManager);
            TestMapLayer c2o1OnMap = new TestMapLayer(overlayManager);
            TestMapLayer c2o2OnMap = new TestMapLayer(overlayManager);

            when(provider1.createMapLayerFromDescriptor(c1o1, overlayManager)).thenReturn(c1o1OnMap);
            when(provider2.createMapLayerFromDescriptor(c2o1, overlayManager)).thenReturn(c2o1OnMap);
            when(provider2.createMapLayerFromDescriptor(c2o2, overlayManager)).thenReturn(c2o2OnMap);

            overlayManager.showLayer(c1o1);
            overlayManager.showLayer(c2o1);
            overlayManager.showLayer(c2o2);

            assertThat(c1o1OnMap.getZIndex(), is(c1o1z));
            assertThat(c2o1OnMap.getZIndex(), is(c2o1z));
            assertThat(c2o2OnMap.getZIndex(), is(c2o2z));

            int c1o1zMod = c2o1z;
            int c2o1zMod = c2o2z;
            int c2o2zMod = c1o1z;
            order.set(c1o1zMod, c1o1);
            order.set(c2o1zMod, c2o1);
            order.set(c2o2zMod, c2o2);

            assertTrue(overlayManager.setZOrder(order));

            List<MapLayerDescriptor> orderMod = overlayManager.getLayersInZOrder();

            assertThat(orderMod, equalTo(order));
            assertThat(orderMod.indexOf(c1o1), is(c1o1zMod));
            assertThat(orderMod.indexOf(c2o1), is(c2o1zMod));
            assertThat(orderMod.indexOf(c2o2), is(c2o2zMod));
            assertThat(c1o1OnMap.getZIndex(), is(c1o1zMod));
            assertThat(c2o1OnMap.getZIndex(), is(c2o1zMod));
            assertThat(c2o2OnMap.getZIndex(), is(c2o2zMod));
        }

        @Test
        public void setsZOrderOfHiddenOverlayOnMapFromComprehensiveUpdate() {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> order = overlayManager.getLayersInZOrder();
            int c1o1z = order.indexOf(c1o1);
            int c2o1z = order.indexOf(c2o1);
            TestMapLayer c1o1OnMap = new TestMapLayer(overlayManager);
            TestMapLayer c2o1OnMap = new TestMapLayer(overlayManager);

            when(provider1.createMapLayerFromDescriptor(c1o1, overlayManager)).thenReturn(c1o1OnMap);
            when(provider2.createMapLayerFromDescriptor(c2o1, overlayManager)).thenReturn(c2o1OnMap);

            overlayManager.showLayer(c1o1);
            overlayManager.showLayer(c2o1);

            assertThat(c1o1OnMap.getZIndex(), is(c1o1z));
            assertThat(c2o1OnMap.getZIndex(), is(c2o1z));

            overlayManager.hideLayer(c1o1);
            Collections.swap(order, c1o1z, c2o1z);

            assertTrue(overlayManager.setZOrder(order));

            List<MapLayerDescriptor> orderMod = overlayManager.getLayersInZOrder();

            assertThat(orderMod, equalTo(order));
            assertThat(orderMod.indexOf(c1o1), is(c2o1z));
            assertThat(orderMod.indexOf(c2o1), is(c1o1z));
            assertTrue(c1o1OnMap.isOnMap());
            assertFalse(c1o1OnMap.isVisible());
            assertTrue(c2o1OnMap.isOnMap());
            assertTrue(c2o1OnMap.isVisible());
        }

        @Test
        public void doesNotSetZOrderIfNewOrderHasDifferingElements() {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> invalidOrder = overlayManager.getLayersInZOrder();
            TestMapLayer firstOnMap = new TestMapLayer(overlayManager);
            MapLayerDescriptor first = invalidOrder.get(0);

            when(provider1.createMapLayerFromDescriptor(first, overlayManager)).thenReturn(firstOnMap);
            when(provider2.createMapLayerFromDescriptor(first, overlayManager)).thenReturn(firstOnMap);

            overlayManager.showLayer(first);

            assertThat(firstOnMap.getZIndex(), is(0));

            invalidOrder.set(1, new MapLayerDescriptorTest.TestLayerDescriptor1("c1.1.tainted", c1Uri, provider1.getClass()));

            assertFalse(overlayManager.setZOrder(invalidOrder));

            List<MapLayerDescriptor> unchangedOrder = overlayManager.getLayersInZOrder();

            assertThat(unchangedOrder, not(equalTo(invalidOrder)));
            assertThat(unchangedOrder, not(hasItem(invalidOrder.get(1))));
            assertThat(firstOnMap.getZIndex(), is(0));
        }

        @Test
        public void doesNotSetZOrderIfNewOrderHasDifferentSize() {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> invalidOrder = overlayManager.getLayersInZOrder();
            TestMapLayer lastOnMap = new TestMapLayer(overlayManager);
            int lastZIndex = invalidOrder.size() - 1;
            MapLayerDescriptor last = invalidOrder.get(lastZIndex);

            when(provider1.createMapLayerFromDescriptor(last, overlayManager)).thenReturn(lastOnMap);
            when(provider2.createMapLayerFromDescriptor(last, overlayManager)).thenReturn(lastOnMap);

            overlayManager.showLayer(last);

            assertThat(lastOnMap.getZIndex(), is(lastZIndex));

            invalidOrder.remove(0);

            assertFalse(overlayManager.setZOrder(invalidOrder));

            List<MapLayerDescriptor> unchangedOrder = overlayManager.getLayersInZOrder();

            assertThat(unchangedOrder, not(equalTo(invalidOrder)));
            assertThat(unchangedOrder.size(), is(invalidOrder.size() + 1));
            assertThat(lastOnMap.getZIndex(), is(lastZIndex));
        }

        @Test
        public void setsProperZOrderIfZOrderChangesWhileLayerIsLoading() {
            fail("unimplemented");
        }

        @Test
        public void hidesLayerIfHiddenWhileLayerIsLoading() {
            fail("unimplemented");
        }

        @Test
        public void showsLayerIfShownWhileLayerIsLoading() {
            fail("unimplemented");
        }

        public class MovingSingleOverlayZIndex {

            private String qnameOf(MapLayerDescriptor overlay) {
                return overlay.getResourceUri() + ":" + overlay.getLayerName();
            }

            MapLayerManager overlayManager;
            Map<MapLayerDescriptor, TestMapLayer> overlaysOnMap;

            @Before
            public void addAllOverlaysToMap() {
                overlayManager = new MapLayerManager(mapDataManager, providers, null);
                overlaysOnMap = new HashMap<>();
                List<MapLayerDescriptor> orderByName = overlayManager.getLayersInZOrder();
                Collections.sort(orderByName, new Comparator<MapLayerDescriptor>() {
                    @Override
                    public int compare(MapLayerDescriptor o1, MapLayerDescriptor o2) {
                        return qnameOf(o1).compareTo(qnameOf(o2));
                    }
                });

                assertTrue(overlayManager.setZOrder(orderByName));

                for (MapLayerDescriptor overlay : orderByName) {
                    TestMapLayer onMap = new TestMapLayer(overlayManager);
                    overlaysOnMap.put(overlay, onMap);
                    if (overlay.getDataType() == provider1.getClass()) {
                        when(provider1.createMapLayerFromDescriptor(overlay, overlayManager)).thenReturn(onMap);
                    }
                    else if (overlay.getDataType() == provider2.getClass()) {
                        when(provider2.createMapLayerFromDescriptor(overlay, overlayManager)).thenReturn(onMap);
                    }
                    overlayManager.showLayer(overlay);
                }
            }

            private void assertZIndexMove(int from, int to) {
                List<MapLayerDescriptor> order = overlayManager.getLayersInZOrder();
                List<MapLayerDescriptor> expectedOrder = new ArrayList<>(order);
                MapLayerDescriptor target = expectedOrder.remove(from);
                expectedOrder.add(to, target);

                assertTrue(overlayManager.moveZIndex(from, to));

                order = overlayManager.getLayersInZOrder();
                assertThat(String.format("%d to %d", from, to), order, equalTo(expectedOrder));
                for (int zIndex = 0; zIndex < expectedOrder.size(); zIndex++) {
                    MapLayerDescriptor overlay = expectedOrder.get(zIndex);
                    TestMapLayer onMap = overlaysOnMap.get(overlay);
                    assertThat(qnameOf(overlay) + " on map", onMap.getZIndex(), is(zIndex));
                }
            }

            @Test
            public void movesTopToLowerZIndex() {
                assertZIndexMove(4, 2);
            }

            @Test
            public void movesTopToBottomZIndex() {
                assertZIndexMove(4, 0);
            }

            @Test
            public void movesBottomToTopZIndex() {
                assertZIndexMove(0, 4);
            }

            @Test
            public void movesBottomToHigherZIndex() {
                assertZIndexMove(0, 3);
            }

            @Test
            public void movesMiddleToLowerZIndex() {
                assertZIndexMove(2, 1);
            }

            @Test
            public void movesMiddleToHigherZIndex() {
                assertZIndexMove(2, 3);
            }
        }
    }
}
