package mil.nga.giat.mage.map.cache;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.TileOverlay;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import mil.nga.giat.mage.map.BasicMapElementContainer;
import mil.nga.giat.mage.map.MapElementSpec;
import mil.nga.giat.mage.map.MapElements;
import mil.nga.giat.mage.test.AsyncTesting;
import mil.nga.giat.mage.test.TargetSuppliesPropertyValueMatcher;

import static java.util.Objects.requireNonNull;
import static mil.nga.giat.mage.test.AsyncTesting.waitForMainThreadToRun;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.everyItem;
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

@RunWith(Suite.class)
@Suite.SuiteClasses({MapLayerManagerTest.MainInteractions.class, MapLayerManagerTest.ZOrder.class, MapLayerManagerTest.MovingSingleOverlayZIndex.class})
public class MapLayerManagerTest {


    static class MockMapElements implements MapElementSpec.MapElementSpecVisitor {

        final GoogleMap mockMap;
        final Map<Object, Object> mockElements = new HashMap<>();

        MockMapElements(GoogleMap mockMap) {
            this.mockMap = mockMap;
        }

        @Override
        public void visit(MapElementSpec.MapCircleSpec x) {
            Circle y = mock(Circle.class);
            mockElements.put(x.id, y);
            when(mockMap.addCircle(x.options)).thenReturn(y);
        }

        @Override
        public void visit(MapElementSpec.MapGroundOverlaySpec x) {
            GroundOverlay y = mock(GroundOverlay.class);
            mockElements.put(x.id, y);
            when(mockMap.addGroundOverlay(x.options)).thenReturn(y);
        }

        @Override
        public void visit(MapElementSpec.MapMarkerSpec x) {
            Marker y = mock(Marker.class);
            mockElements.put(x.id, y);
            when(mockMap.addMarker(x.options)).thenReturn(y);
        }

        @Override
        public void visit(MapElementSpec.MapPolygonSpec x) {
            Polygon y = mock(Polygon.class);
            mockElements.put(x.id, y);
            when(mockMap.addPolygon(x.options)).thenReturn(y);
        }

        @Override
        public void visit(MapElementSpec.MapPolylineSpec x) {
            Polyline y = mock(Polyline.class);
            mockElements.put(x.id, y);
            when(mockMap.addPolyline(x.options)).thenReturn(y);
        }

        @Override
        public void visit(MapElementSpec.MapTileOverlaySpec x) {
            TileOverlay y = mock(TileOverlay.class);
            mockElements.put(x.id, y);
            when(mockMap.addTileOverlay(x.options)).thenReturn(y);
        }

        <T> T verify(Class<T> type, Object id) {
            return type.cast(Mockito.verify(mockElements.get(id)));
        }

        <T> T verify(Class<T> type, Object id, VerificationMode mode) {
            return type.cast(Mockito.verify(mockElements.get(id), mode));
        }
    }

    static class TestLayerAdapter implements MapLayerManager.MapLayerAdapter {

        final MapLayerDescriptor layerDesc;
        final MapDataProvider provider;
        final Map<Object, MapElementSpec> elementSpecs = new HashMap<>();
        final MapElements elements = new BasicMapElementContainer();
        final MockMapElements mockedElements;
        AtomicBoolean elementsQueried = new AtomicBoolean(false);
        AtomicBoolean removed = new AtomicBoolean(false);

        TestLayerAdapter(MapLayerDescriptor layerDesc, MapDataProvider provider, GoogleMap map, List<? extends MapElementSpec> elementSpecs) {
            this.layerDesc = layerDesc;
            this.provider = provider;
            mockedElements = new MockMapElements(map);
            for (MapElementSpec e : elementSpecs) {
                this.elementSpecs.put(e.id, e);
                e.accept(mockedElements);
            }
        }

        TestLayerAdapter(MapLayerDescriptor layerDesc, MapDataProvider provider, GoogleMap map, MapElementSpec... elementSpecs) {
            this(layerDesc, provider, map, Arrays.asList(elementSpecs));
        }

        @Override
        public void onLayerRemoved() {
            removed.set(true);
        }

        @Override
        public Iterator<? extends MapElementSpec> elementsInBounds(LatLngBounds bounds) {
            elementsQueried.set(true);
            return elementSpecs.values().iterator();
        }

        @Override
        public void addedToMap(MapElementSpec.MapCircleSpec spec, Circle x) {
            elements.add(x, spec.id);
        }

        @Override
        public void addedToMap(MapElementSpec.MapGroundOverlaySpec spec, GroundOverlay x) {
            elements.add(x, spec.id);
        }

        @Override
        public void addedToMap(MapElementSpec.MapMarkerSpec spec, Marker x) {
            elements.add(x, spec.id);
        }

        @Override
        public void addedToMap(MapElementSpec.MapPolygonSpec spec, Polygon x) {
            elements.add(x, spec.id);
        }

        @Override
        public void addedToMap(MapElementSpec.MapPolylineSpec spec, Polyline x) {
            elements.add(x, spec.id);
        }

        @Override
        public void addedToMap(MapElementSpec.MapTileOverlaySpec spec, TileOverlay x) {
            elements.add(x, spec.id);
        }

        boolean isOnMap() {
            return !removed.get() && elementsQueried.get() && elements.count() == elementSpecs.size();
        }

        boolean isRemoved() {
            return removed.get();
        }

        <T extends MapElementSpec> T elementSpecForId(Object id, Class<T> type) {
            return type.cast(elementSpecs.get(id));
        }
    }

    static MapElementSpec.MapMarkerSpec markerSpec(Object id) {
        double lon = Math.random() * 360 - 180;
        double lat = Math.random() * 180 - 90;
        MarkerOptions options = new MarkerOptions().position(new LatLng(lat, lon));
        return new MapElementSpec.MapMarkerSpec(id, null, options);
    }

    static Set<MapLayerDescriptor> setOf(MapLayerDescriptor... things) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(things)));
    }

    static Map<URI, MapDataResource> mapOf(MapDataResource... things) {
        Map<URI, MapDataResource> map = new HashMap<>(things.length);
        for (MapDataResource resource : things) {
            map.put(resource.getUri(), resource);
        }
        return map;
    }

    static long oneSecond() {
        return 1000L;
    }

    static class BaseConfig implements MapDataManager.CreateUpdatePermission {

        MapDataManager mapDataManager;
        MapDataRepository repo1;
        MapLayerDescriptorTest.TestMapDataProvider1 provider1;
        MapLayerDescriptorTest.TestMapDataProvider2 provider2;
        List<MapDataProvider> providers;
        GoogleMap map;

        @Rule
        TestName testName = new TestName();

        @Rule
        AsyncTesting.MainLooperAssertion onMainThread = new AsyncTesting.MainLooperAssertion();

        URI makeUri() {
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
            map = mock(GoogleMap.class);
        }

        MapDataProvider providerForLayerDesc(MapLayerDescriptor desc) {
            if (desc.getDataType() == provider1.getClass()) {
                return provider1;
            }
            if (desc.getDataType() == provider2.getClass()) {
                return provider2;
            }
            throw new Error("no provider for data type " + desc.getDataType().getName() + " on layer descriptor " + desc);
        }

        TestLayerAdapter prepareLayerAdapterStubs(MapLayerManager manager, MapLayerDescriptor layerDesc, MapElementSpec... layerElements) {
            MockMapElements mockMapElements = new MockMapElements(manager.getMap());
            for (MapElementSpec s : layerElements) {
                s.accept(mockMapElements);
            }
            MapDataProvider provider = providerForLayerDesc(layerDesc);
            TestLayerAdapter adapter = new TestLayerAdapter(layerDesc, provider, manager.getMap(), layerElements);
            Callable<TestLayerAdapter> createAdapter = () -> adapter;
            when(provider.createMapLayerAdapter(same(layerDesc), same(manager.getMap()))).then(invoc -> createAdapter);
            return adapter;
        }

        TestLayerAdapter showAndWaitForLayerOnMap(MapLayerManager manager, MapLayerDescriptor layerDesc, MapElementSpec... layerElements) throws InterruptedException {
            TestLayerAdapter adapter = prepareLayerAdapterStubs(manager, layerDesc, layerElements);
            waitForMainThreadToRun(() -> {
                manager.showLayer(layerDesc);
                Mockito.verify(adapter.provider).createMapLayerAdapter(same(layerDesc), same(manager.getMap()));
            });
            onMainThread.assertThatWithin(oneSecond(), adapter::isOnMap, is(true));
            return adapter;
        }
    }

    @RunWith(AndroidJUnit4.class)
    @SmallTest
    static class MainInteractions extends BaseConfig {

        @Test
        public void listensToCacheManagerUpdates() {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

            Mockito.verify(mapDataManager).addUpdateListener(overlayManager);
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
                new MapDataResource.Resolved(requireNonNull(mapDataResource.getResolved()).getName(), mapDataResource.getResolved().getType(), setOf(overlay2)));
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
                new MapDataResource.Resolved(requireNonNull(cache.getResolved()).getName(), cache.getResolved().getType(), setOf(overlay1, overlay2)));
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
                new MapDataResource.Resolved(requireNonNull(cache.getResolved()).getName(), cache.getResolved().getType(), setOf(overlay2)));
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
                new MapDataResource.Resolved(requireNonNull(cache.getResolved()).getName(), cache.getResolved().getType(), setOf(overlay1Updated)));
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
            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, map);

            waitForMainThreadToRun(() -> overlayManager.onMapDataUpdated(update));

            assertThat(overlayManager.getLayersInZOrder().size(), is(1));
            assertThat(overlayManager.getLayersInZOrder(), hasItems(overlay1));
            assertFalse(overlayManager.isLayerVisible(overlay1));
            Mockito.verify(provider1, never()).createMapLayerAdapter(any(), any());

            TestLayerAdapter layer1 = showAndWaitForLayerOnMap(overlayManager, overlay1, markerSpec("m1"));

            assertTrue(overlayManager.isLayerVisible(overlay1));
            Mockito.verify(map).addMarker(layer1.elementSpecForId("m1", MapElementSpec.MapMarkerSpec.class).options);
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

            TestLayerAdapter layer1 = showAndWaitForLayerOnMap(overlayManager, layerDesc1, markerSpec("m1"));

            MapLayerDescriptor layerDesc2 = new MapLayerDescriptorTest.TestLayerDescriptor1(layerDesc1.getLayerName(), layerDesc1.getResourceUri(), layerDesc1.getDataType());
            mapData = new MapDataResource(layerDesc2.getResourceUri(), repo1, 0,
                new MapDataResource.Resolved(requireNonNull(mapData.getResolved()).getName(), mapData.getResolved().getType(), setOf(layerDesc2)));
            MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.emptyMap(), mapOf(mapData), Collections.emptyMap());
            TestLayerAdapter layer2 = prepareLayerAdapterStubs(overlayManager, layerDesc2, markerSpec("m2"));

            waitForMainThreadToRun(() -> {
                overlayManager.onMapDataUpdated(update);
                Mockito.verify(provider1).createMapLayerAdapter(same(layerDesc2), same(map));
            });

            onMainThread.assertThatWithin(oneSecond(), layer2::isOnMap, is(true));

            assertTrue(overlayManager.isLayerVisible(layerDesc2));
            assertTrue(layer1.isRemoved());
            assertTrue(layer2.isOnMap());
            layer1.mockedElements.verify(Marker.class, "m1").remove();
            Mockito.verify(map).addMarker(layer2.elementSpecForId("m2", MapElementSpec.MapMarkerSpec.class).options);
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

            TestLayerAdapter layer1 = showAndWaitForLayerOnMap(overlayManager, layerDesc1, markerSpec("m1"), markerSpec("m2"));

            MapLayerDescriptor layerDesc2 = new MapLayerDescriptorTest.TestLayerDescriptor1(layerDesc1.getLayerName(), layerDesc1.getResourceUri(), layerDesc1.getDataType());
            mapData = new MapDataResource(layerDesc2.getResourceUri(), repo1, 0,
                new MapDataResource.Resolved(requireNonNull(mapData.getResolved()).getName(), mapData.getResolved().getType(), setOf(layerDesc2)));
            MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.emptyMap(), mapOf(mapData), Collections.emptyMap());

            TestLayerAdapter layerUpdated = prepareLayerAdapterStubs(overlayManager, layerDesc2, markerSpec("m2"));

            waitForMainThreadToRun(() -> {
                overlayManager.onMapDataUpdated(update);
                Mockito.verify(provider1, never()).createMapLayerAdapter(same(layerDesc2), same(map));
                layer1.mockedElements.verify(Marker.class, "m1").remove();
                layer1.mockedElements.verify(Marker.class, "m2").remove();
            });

            assertTrue(layer1.isRemoved());
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

            TestLayerAdapter layer1 = showAndWaitForLayerOnMap(overlayManager, layerDesc1, markerSpec("m1"));

            MapLayerDescriptor layerDesc2 = new MapLayerDescriptorTest.TestLayerDescriptor1(
                "overlay 2", mapData.getUri(), requireNonNull(mapData.getResolved()).getType());
            mapData = mapData.resolve(new MapDataResource.Resolved(mapData.getResolved().getName(), mapData.getResolved().getType(), setOf(layerDesc1, layerDesc2)));
            MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.emptyMap(), mapOf(mapData), Collections.emptyMap());

            prepareLayerAdapterStubs(overlayManager, layerDesc2, markerSpec("m2"));

            waitForMainThreadToRun(() -> overlayManager.onMapDataUpdated(update));

            assertTrue(layer1.isOnMap());
            assertTrue(overlayManager.isLayerVisible(layerDesc1));
            verify(provider1, times(1)).createMapLayerAdapter(same(layerDesc1), same(map));
            layer1.mockedElements.verify(Marker.class, "m1", never()).remove();
            verify(provider1, never()).createMapLayerAdapter(same(layerDesc2), any());
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

            TestLayerAdapter layer1 =  showAndWaitForLayerOnMap(overlayManager, overlay1, markerSpec("m1"), markerSpec("m2"));

            mapData = mapData.resolve(new MapDataResource.Resolved(requireNonNull(mapData.getResolved()).getName(), mapData.getResolved().getType(), setOf(overlay2)));
            MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.emptyMap(), mapOf(mapData), Collections.emptyMap());

            waitForMainThreadToRun(() -> {
                overlayManager.onMapDataUpdated(update);
                assertThat(overlayManager.getLayersInZOrder(), not(hasItem(overlay1)));
                assertFalse(layer1.isOnMap());
                assertTrue(layer1.isRemoved());
                layer1.mockedElements.verify(Marker.class, "m1").remove();
                layer1.mockedElements.verify(Marker.class, "m2").remove();
            });

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

            TestLayerAdapter layer1 = showAndWaitForLayerOnMap(overlayManager, overlay1, markerSpec("m1"));
            TestLayerAdapter layer2 = showAndWaitForLayerOnMap(overlayManager, overlay2, markerSpec("m2"));

            MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.emptyMap(), Collections.emptyMap(), mapOf(cache));

            waitForMainThreadToRun(() -> {
                overlayManager.onMapDataUpdated(update);
                assertTrue(layer1.isRemoved());
                assertTrue(layer2.isRemoved());
                assertTrue(overlayManager.getLayersInZOrder().isEmpty());
                layer1.mockedElements.verify(Marker.class, "m1").remove();
                layer2.mockedElements.verify(Marker.class, "m2").remove();
            });
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

            TestLayerAdapter onMap1 = prepareLayerAdapterStubs(overlayManager, overlay1, markerSpec("m1"));
            TestLayerAdapter onMap2 = prepareLayerAdapterStubs(overlayManager, overlay2, markerSpec("m2"));

            waitForMainThreadToRun(() -> {
                overlayManager.showLayer(overlay1);
                verify(provider1).createMapLayerAdapter(same(overlay1), same(overlayManager.getMap()));
                verify(provider1, never()).createMapLayerAdapter(same(overlay2), any());
            });

            onMainThread.assertThatWithin(oneSecond(), onMap1::isOnMap, is(true));

            waitForMainThreadToRun(() -> {
                overlayManager.showLayer(overlay2);
                verify(provider1, times(1)).createMapLayerAdapter(same(overlay1), same(overlayManager.getMap()));
                verify(provider1).createMapLayerAdapter(same(overlay2), same(overlayManager.getMap()));
            });

            onMainThread.assertThatWithin(oneSecond(), onMap2::isOnMap, is(true));

            assertTrue(onMap1.isOnMap());
            assertTrue(onMap2.isOnMap());

            waitForMainThreadToRun(() -> {
                overlayManager.hideLayer(overlay2);
                assertTrue(overlayManager.isLayerVisible(overlay1));
                assertFalse(overlayManager.isLayerVisible(overlay2));
                onMap1.mockedElements.verify(Marker.class, "m1", never()).setVisible(false);
                onMap2.mockedElements.verify(Marker.class, "m2").setVisible(true);
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

            Mockito.verify(mapDataManager).removeUpdateListener(overlayManager);
        }

        @Test
        public void disposeRemovesAllOverlays() throws InterruptedException {

            MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay1", makeUri(), provider1.getClass());
            MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay2", overlay1.getResourceUri(), provider1.getClass());
            MapLayerDescriptor overlay3 = new MapLayerDescriptorTest.TestLayerDescriptor2("overlay3", makeUri(), provider2.getClass());
            MapDataResource cache1 = new MapDataResource(overlay1.getResourceUri(), repo1, 0,
                new MapDataResource.Resolved("cache1", provider1.getClass(), setOf(overlay1, overlay2)));
            MapDataResource cache2 = new MapDataResource(overlay3.getResourceUri(), repo1, 0,
                new MapDataResource.Resolved("cache1", provider2.getClass(), setOf(overlay3)));

            when(mapDataManager.getResources()).thenReturn(mapOf(cache1, cache2));

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, null);

            TestLayerAdapter layer1 = prepareLayerAdapterStubs(overlayManager, overlay1, markerSpec("1.1"), markerSpec("1.2"));
            TestLayerAdapter layer2 = prepareLayerAdapterStubs(overlayManager, overlay2, markerSpec("2.1"));
            TestLayerAdapter layer3 = prepareLayerAdapterStubs(overlayManager, overlay3, markerSpec("3.1"), markerSpec("3.2"));

            waitForMainThreadToRun(() -> {
                overlayManager.showLayer(overlay1);
                overlayManager.showLayer(overlay2);
                overlayManager.showLayer(overlay3);
            });

            onMainThread.assertThatWithin(oneSecond(), () -> layer1.isOnMap() && layer2.isOnMap() && layer3.isOnMap(), is(true));

            waitForMainThreadToRun(() -> {
                overlayManager.dispose();

                assertTrue(layer1.isRemoved());
                assertTrue(layer2.isRemoved());
                assertTrue(layer3.isRemoved());
                layer1.mockedElements.verify(Marker.class, "1.1").remove();
                layer1.mockedElements.verify(Marker.class, "1.2").remove();
                layer2.mockedElements.verify(Marker.class, "2.1").remove();
                layer3.mockedElements.verify(Marker.class, "3.1").remove();
                layer3.mockedElements.verify(Marker.class, "3.2").remove();
            });
        }

        @Test
        public void hidesAllElementsOfALayer() {
            fail("unimplemented");
        }

        @Test
        public void showsAllElementsOfALayer() {
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
    }


    static class BaseZOrderConfig extends BaseConfig {

        URI c1Uri;
        URI c2Uri;
        MapLayerDescriptor c1o1;
        MapLayerDescriptor c1o2;
        MapLayerDescriptor c1o3;
        MapLayerDescriptor c2o1;
        MapLayerDescriptor c2o2;
        MapDataResource cache1;
        MapDataResource cache2;

        @Before
        public void setupMapData() throws URISyntaxException {

            c1Uri = new URI("test", "z-order", "c1", null);
            c2Uri = new URI("test", "z-order", "c2", null);

            c1o1 = new MapLayerDescriptorTest.TestLayerDescriptor1("c1.1", c1Uri, provider1.getClass());
            c1o2 = new MapLayerDescriptorTest.TestLayerDescriptor1("c1.2", c1Uri, provider1.getClass());
            c1o3 = new MapLayerDescriptorTest.TestLayerDescriptor1("c1.3", c1Uri, provider1.getClass());
            cache1 = new MapDataResource(makeUri(), repo1, 0,
                new MapDataResource.Resolved("c1", provider1.getClass(), MapLayerManagerTest.setOf(c1o1, c1o2, c1o3)));

            c2o1 = new MapLayerDescriptorTest.TestLayerDescriptor2("c2.1", c2Uri, provider2.getClass());
            c2o2 = new MapLayerDescriptorTest.TestLayerDescriptor2("c2.2", c2Uri, provider2.getClass());
            cache2 = new MapDataResource(makeUri(), repo1, 0,
                new MapDataResource.Resolved("c2", provider2.getClass(), MapLayerManagerTest.setOf(c2o2, c2o1)));

            when(mapDataManager.getResources()).thenReturn(MapLayerManagerTest.mapOf(cache1, cache2));
        }
    }


    @RunWith(AndroidJUnit4.class)
    @SmallTest
    public static class ZOrder extends BaseZOrderConfig {

        @Test
        public void returnsModifiableCopyOfOverlayZOrder() {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, map);
            List<MapLayerDescriptor> orderModified = overlayManager.getLayersInZOrder();
            Collections.reverse(orderModified);
            List<MapLayerDescriptor> orderUnmodified = overlayManager.getLayersInZOrder();

            assertThat(orderUnmodified, not(sameInstance(orderModified)));
            assertThat(orderUnmodified, not(contains(orderModified.toArray())));
            assertThat(orderUnmodified.get(0), sameInstance(orderModified.get(orderModified.size() - 1)));
        }

        @Test
        public void initializesOverlaysOnMapWithProperZOrder() throws InterruptedException {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, map);
            List<MapLayerDescriptor> order = overlayManager.getLayersInZOrder();
            int c1o2z = order.indexOf(c1o2);
            int c2o1z = order.indexOf(c2o1);

            TestLayerAdapter c1o2OnMap = prepareLayerAdapterStubs(overlayManager, c1o2, markerSpec("c1o2.1"));
            TestLayerAdapter c2o1OnMap = prepareLayerAdapterStubs(overlayManager, c2o1, markerSpec("c2o1.1"));

            when(map.addMarker(c1o2OnMap.elementSpecForId("c1o2.1", MapElementSpec.MapMarkerSpec.class).options)).then(invoc -> {
                MarkerOptions options = invoc.getArgument(0);
                assertThat(options.getZIndex(), is(c1o2z));
                return mock(Marker.class);
            });
            when(map.addMarker(c2o1OnMap.elementSpecForId("c2o1.1", MapElementSpec.MapMarkerSpec.class).options)).then(invoc -> {
                MarkerOptions options = invoc.getArgument(0);
                assertThat(options.getZIndex(), is(c2o1z));
                return mock(Marker.class);
            });

            waitForMainThreadToRun(() -> {
                overlayManager.showLayer(c1o1);
                overlayManager.showLayer(c2o1);
            });

            onMainThread.assertThatWithin(oneSecond(), () -> c1o2OnMap.isOnMap() && c2o1OnMap.isOnMap(), is(true));

            verify(map).addMarker(same(c1o2OnMap.elementSpecForId("c1o2.1", MapElementSpec.MapMarkerSpec.class).options));
            verify(map).addMarker(same(c2o1OnMap.elementSpecForId("c2o1.1", MapElementSpec.MapMarkerSpec.class).options));
        }

        @Test
        public void setsComprehensiveZOrderFromList() {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, map);
            List<MapLayerDescriptor> order = overlayManager.getLayersInZOrder();
            Collections.reverse(order);

            waitForMainThreadToRun(() -> {
                assertTrue(overlayManager.setZOrder(order));
                List<MapLayerDescriptor> orderMod = overlayManager.getLayersInZOrder();

                assertThat(orderMod, equalTo(order));
            });
        }

        @Test
        @SuppressWarnings("UnnecessaryLocalVariable")
        public void setsZOrderOfOverlaysOnMapFromComprehensiveUpdate() throws InterruptedException {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, map);
            List<MapLayerDescriptor> order = overlayManager.getLayersInZOrder();
            int c1o1z = order.indexOf(c1o1);
            int c2o1z = order.indexOf(c2o1);
            int c2o2z = order.indexOf(c2o2);
            TestLayerAdapter c1o1OnMap = prepareLayerAdapterStubs(overlayManager, c1o1, markerSpec("c1o1.1"));
            TestLayerAdapter c2o1OnMap = prepareLayerAdapterStubs(overlayManager, c2o1, markerSpec("c2o1.1"), markerSpec("c2o1.2"));
            TestLayerAdapter c2o2OnMap = prepareLayerAdapterStubs(overlayManager, c2o2);

            waitForMainThreadToRun(() -> {
                overlayManager.showLayer(c1o1);
                overlayManager.showLayer(c2o1);
                overlayManager.showLayer(c2o2);
            });

            onMainThread.assertThatWithin(oneSecond(), () -> c1o1OnMap.isOnMap() && c2o1OnMap.isOnMap() && c2o2OnMap.isOnMap(), is(true));

            int c1o1zMod = c2o1z;
            int c2o1zMod = c2o2z;
            int c2o2zMod = c1o1z;
            order.set(c1o1zMod, c1o1);
            order.set(c2o1zMod, c2o1);
            order.set(c2o2zMod, c2o2);

            waitForMainThreadToRun(() -> {
                assertTrue(overlayManager.setZOrder(order));

                List<MapLayerDescriptor> orderMod = overlayManager.getLayersInZOrder();

                assertThat(orderMod, equalTo(order));
                assertThat(orderMod.indexOf(c1o1), is(c1o1zMod));
                assertThat(orderMod.indexOf(c2o1), is(c2o1zMod));
                assertThat(orderMod.indexOf(c2o2), is(c2o2zMod));
                assertThat(c1o1OnMap.elements.withElementForId("c1o1.1", (Marker x, Object id) -> x.getZIndex()), is(c1o1zMod));
                assertThat(c2o1OnMap.elements.withElementForId("c2o1.1", (Marker x, Object id) -> x.getZIndex()), is(c2o1zMod));
                assertThat(c2o1OnMap.elements.withElementForId("c2o1.2", (Marker x, Object id) -> x.getZIndex()), is(c2o2zMod));
            });
        }

        @Test
        public void setsZOrderOfHiddenOverlayOnMapFromComprehensiveUpdate() throws InterruptedException {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, map);
            List<MapLayerDescriptor> order = overlayManager.getLayersInZOrder();
            int c1o1z = order.indexOf(c1o1);
            int c2o1z = order.indexOf(c2o1);
            TestLayerAdapter c1o1OnMap = prepareLayerAdapterStubs(overlayManager, c1o1, markerSpec("c1o1.1"));
            TestLayerAdapter c2o1OnMap = prepareLayerAdapterStubs(overlayManager, c2o1, markerSpec("c2o1.1"));

            waitForMainThreadToRun(() -> {
                overlayManager.showLayer(c1o1);
                overlayManager.showLayer(c2o1);
            });

            onMainThread.assertThatWithin(oneSecond(), () -> c1o1OnMap.isOnMap() && c2o1OnMap.isOnMap(), is(true));

            waitForMainThreadToRun(() -> overlayManager.hideLayer(c1o1));

            Collections.swap(order, c1o1z, c2o1z);

            waitForMainThreadToRun(() -> {
                assertTrue(overlayManager.setZOrder(order));

                List<MapLayerDescriptor> orderMod = overlayManager.getLayersInZOrder();

                assertThat(orderMod, equalTo(order));
                assertThat(orderMod.indexOf(c1o1), is(c2o1z));
                assertThat(orderMod.indexOf(c2o1), is(c1o1z));
                c1o1OnMap.mockedElements.verify(Marker.class, "c1o1.1").setZIndex(c2o1z);
                c2o1OnMap.mockedElements.verify(Marker.class, "c2o1.1").setZIndex(c1o1z);
            });
        }

        @Test
        public void doesNotSetZOrderIfNewOrderHasDifferingElements() throws InterruptedException {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, map);
            List<MapLayerDescriptor> invalidOrder = overlayManager.getLayersInZOrder();
            MapLayerDescriptor first = invalidOrder.get(0);

            TestLayerAdapter firstOnMap = showAndWaitForLayerOnMap(overlayManager, first, markerSpec("m1"));

            invalidOrder.set(1, new MapLayerDescriptorTest.TestLayerDescriptor1("c1.1.tainted", c1Uri, provider1.getClass()));

            waitForMainThreadToRun(() -> {
                assertFalse(overlayManager.setZOrder(invalidOrder));

                List<MapLayerDescriptor> unchangedOrder = overlayManager.getLayersInZOrder();

                assertThat(unchangedOrder, not(equalTo(invalidOrder)));
                assertThat(unchangedOrder, not(hasItem(invalidOrder.get(1))));
                firstOnMap.mockedElements.verify(Marker.class, "m1", never()).setZIndex(any());
            });
        }

        @Test
        public void doesNotSetZOrderIfNewOrderHasDifferentSize() throws InterruptedException {

            MapLayerManager overlayManager = new MapLayerManager(mapDataManager, providers, map);
            List<MapLayerDescriptor> invalidOrder = overlayManager.getLayersInZOrder();
            int lastZIndex = invalidOrder.size() - 1;
            MapLayerDescriptor last = invalidOrder.get(lastZIndex);

            TestLayerAdapter lastOnMap = showAndWaitForLayerOnMap(overlayManager, last, markerSpec("m1"));

            invalidOrder.remove(0);

            waitForMainThreadToRun(() -> {
                assertFalse(overlayManager.setZOrder(invalidOrder));

                List<MapLayerDescriptor> unchangedOrder = overlayManager.getLayersInZOrder();

                assertThat(unchangedOrder, not(equalTo(invalidOrder)));
                assertThat(unchangedOrder.size(), is(invalidOrder.size() + 1));
                lastOnMap.mockedElements.verify(Marker.class, "m1", never()).setZIndex(any());
            });
        }

        @Test
        public void setsProperZOrderIfZOrderChangesWhileLayerIsLoading() {
            fail("unimplemented");
        }
    }

    @RunWith(AndroidJUnit4.class)
    @SmallTest
    public static class MovingSingleOverlayZIndex extends BaseZOrderConfig {

        private String qnameOf(MapLayerDescriptor overlay) {
            return overlay.getResourceUri() + ":" + overlay.getLayerName();
        }

        MapLayerManager overlayManager;
        Map<MapLayerDescriptor, TestLayerAdapter> overlaysOnMap;

        @Before
        public void addAllOverlaysToMap() throws InterruptedException {
            overlayManager = new MapLayerManager(mapDataManager, providers, null);
            overlaysOnMap = new HashMap<>();
            List<MapLayerDescriptor> orderByName = overlayManager.getLayersInZOrder();
            Collections.sort(orderByName, (o1, o2) -> qnameOf(o1).compareTo(qnameOf(o2)));

            assertTrue(overlayManager.setZOrder(orderByName));

            for (MapLayerDescriptor overlay : orderByName) {
                TestLayerAdapter onMap = prepareLayerAdapterStubs(overlayManager, overlay, markerSpec(overlay.getLayerName() + ".m1"));
                overlaysOnMap.put(overlay, onMap);
            }
            waitForMainThreadToRun(() -> {
                for (MapLayerDescriptor layerDesc : overlaysOnMap.keySet()) {
                    overlayManager.showLayer(layerDesc);
                }
            });
            onMainThread.assertThatWithin(oneSecond(), overlaysOnMap::values,
                everyItem(TargetSuppliesPropertyValueMatcher.withValueSuppliedBy(TestLayerAdapter::isOnMap, is(true))));
        }

        private void assertZIndexMove(int from, int to) {
            List<MapLayerDescriptor> order = overlayManager.getLayersInZOrder();
            List<MapLayerDescriptor> expectedOrder = new ArrayList<>(order);
            MapLayerDescriptor target = expectedOrder.remove(from);
            expectedOrder.add(to, target);

            waitForMainThreadToRun(() -> assertTrue(overlayManager.moveZIndex(from, to)));

            order = overlayManager.getLayersInZOrder();
            assertThat(String.format("%d to %d", from, to), order, equalTo(expectedOrder));
            for (int zIndex = 0; zIndex < expectedOrder.size(); zIndex++) {
                MapLayerDescriptor overlay = expectedOrder.get(zIndex);
                TestLayerAdapter onMap = overlaysOnMap.get(overlay);
                onMap.mockedElements.verify(Marker.class, overlay.getLayerName() + ".m1").setZIndex(zIndex);
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
