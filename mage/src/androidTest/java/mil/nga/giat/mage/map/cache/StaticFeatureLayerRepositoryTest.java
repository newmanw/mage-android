package mil.nga.giat.mage.map.cache;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LifecycleRegistry;
import android.arch.lifecycle.Observer;
import android.support.annotation.NonNull;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import mil.nga.giat.mage.data.Resource;
import mil.nga.giat.mage.sdk.datastore.layer.Layer;
import mil.nga.giat.mage.sdk.datastore.layer.LayerHelper;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeature;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeatureHelper;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeatureProperty;
import mil.nga.giat.mage.sdk.datastore.user.Event;
import mil.nga.giat.mage.sdk.datastore.user.EventHelper;
import mil.nga.giat.mage.sdk.exceptions.LayerException;
import mil.nga.giat.mage.sdk.exceptions.StaticFeatureException;
import mil.nga.giat.mage.sdk.http.resource.LayerResource;
import mil.nga.giat.mage.test.AsyncTesting;
import mil.nga.wkb.geom.Geometry;
import mil.nga.wkb.geom.Point;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static mil.nga.giat.mage.test.AsyncTesting.waitForMainThreadToRun;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(AndroidJUnit4.class)
@SmallTest
public class StaticFeatureLayerRepositoryTest {

    private static class TestLayer extends Layer {

        private Long id;

        TestLayer(String remoteId, String type, String name, Event event) {
            super(remoteId, type, name, event);
        }

        @Override
        public Long getId() {
            return id;
        }

        TestLayer setId(Long x) {
            id = x;
            return this;
        }
    }

    private static class TestStaticFeature extends StaticFeature {

        private Long id;

        TestStaticFeature(String remoteId, Geometry geom, Layer layer) {
            super(remoteId, geom, layer);
        }

        @Override
        public Long getId() {
            return id;
        }

        TestStaticFeature setId(Long id) {
            this.id = id;
            return this;
        }
    }

    private static long oneSecond() {
        return 300000;
    }

    @Rule
    public TestName testName = new TestName();
    @Rule
    public TemporaryFolder iconsDirRule = new TemporaryFolder();
    @Rule
    public AsyncTesting.MainLooperAssertion onMainThread = new AsyncTesting.MainLooperAssertion();

    @Mock
    private EventHelper eventHelper;
    @Mock
    private LayerHelper layerHelper;
    @Mock
    private StaticFeatureHelper featureHelper;
    @Mock
    private LayerResource layerService;
    @Mock
    private Observer<Set<MapDataResource>> observer;
    @Mock
    private StaticFeatureLayerRepository.NetworkCondition network;

    @Captor
    private ArgumentCaptor<Set<MapDataResource>> observed;

    private Event currentEvent;
    private File iconsDir;
    private StaticFeatureLayerRepository repo;
    private ThreadPoolExecutor executor;

    @Before
    public void setupRepo() throws IOException {
        MockitoAnnotations.initMocks(this);
        iconsDir = iconsDirRule.newFolder("icons");
        currentEvent = new Event(testName.getMethodName(), testName.getMethodName(), testName.getMethodName(), "", "");
        repo = new StaticFeatureLayerRepository(eventHelper, layerHelper, featureHelper, layerService, iconsDir, network);
        LifecycleOwner observerLifecycle = new LifecycleOwner() {
            private LifecycleRegistry lifecycle = new LifecycleRegistry(this);

            {
                lifecycle.markState(Lifecycle.State.RESUMED);
            }

            @NonNull
            public Lifecycle getLifecycle() {
                return lifecycle;
            }
        };
        repo.observe(observerLifecycle, observer);
        executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(16));

        when(network.isConnected()).thenReturn(true);
        when(eventHelper.getCurrentEvent()).thenReturn(currentEvent);
    }

    @After
    public void awaitThreadPoolTermination() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(oneSecond(), TimeUnit.MILLISECONDS)) {
            fail("timed out waiting for thread pool to shutdown");
        }
    }

    @Test
    public void ownsTheMageStaticFeatureLayersUri() {
        URI uri = URI.create("mage:/current_event/layers");
        assertTrue(repo.ownsResource(uri));
    }

    @Test
    public void doesNotOwnOtherUris() {
        for (String uri : Arrays.asList("mage:/layers", "mage:/current_event/layer", "mage:/current-event/layers", "nage:/current_event/layers", "http://mage/current_event/layers")) {
            assertFalse(uri, repo.ownsResource(URI.create(uri)));
        }
    }

    @Test
    public void initialStatusIsSuccess() {
        assertThat(repo.getStatus(), is(Resource.Status.Success));
        assertThat(repo.getStatusCode(), is(Resource.Status.Success.ordinal()));
        assertThat(repo.getStatusMessage(), is(Resource.Status.Success.toString()));
    }

    @Test
    public void initialDataIsNull() {
        waitForMainThreadToRun(() -> {
            assertThat(repo.getValue(), nullValue());
        });
    }

    @Test
    public void fetchesCurrentEventLayersFromServer() throws IOException, InterruptedException {

        when(layerService.getLayers(currentEvent)).thenReturn(Collections.emptySet());

        waitForMainThreadToRun(() -> {
            repo.refreshAvailableMapData(emptyMap(), executor);
            assertThat(repo.getStatus(), is(Resource.Status.Loading));
        });

        onMainThread.assertThatWithin(oneSecond(), repo::getStatus, is(Resource.Status.Success));

        verify(layerService).getLayers(currentEvent);
    }

    @Test
    public void fetchesFeaturesForFetchedLayers() throws IOException, InterruptedException {

        Layer layer = new Layer("1", "test", "test1", currentEvent);
        when(layerService.getLayers(currentEvent)).thenReturn(Collections.singleton(layer));

        waitForMainThreadToRun(() -> {
            repo.refreshAvailableMapData(emptyMap(), executor);
            assertThat(repo.getStatus(), is(Resource.Status.Loading));
        });

        onMainThread.assertThatWithin(oneSecond(), repo::getStatus, is(Resource.Status.Success));

        awaitThreadPoolTermination();

        InOrder fetchOrder = inOrder(layerService);
        fetchOrder.verify(layerService).getLayers(currentEvent);
        fetchOrder.verify(layerService).getFeatures(layer);
    }

    @Test
    public void savesFetchedLayersAndFeaturesAfterFetchingFeatures() throws IOException, InterruptedException, LayerException, StaticFeatureException {

        TestLayer layer = new TestLayer("1", "test", "test1", currentEvent);
        when(layerService.getLayers(currentEvent)).thenReturn(Collections.singleton(layer));
        Collection<StaticFeature> features = Collections.singleton(
            new StaticFeature("1.1", new Point(1, 2), layer));
        when(layerService.getFeatures(layer)).thenReturn(features);
        when(layerHelper.create(layer)).thenReturn(layer);
        when(featureHelper.createAll(features, layer)).then((invocation) -> {
            layer.setLoaded(true);
            return features;
        });

        waitForMainThreadToRun(() -> {
            repo.refreshAvailableMapData(emptyMap(), executor);
            assertThat(repo.getStatus(), is(Resource.Status.Loading));
        });

        onMainThread.assertThatWithin(oneSecond(), repo::getStatus, is(Resource.Status.Success));

        awaitThreadPoolTermination();

        InOrder saveOrder = inOrder(featureHelper, layerHelper);
        saveOrder.verify(layerHelper).create(layer);
        saveOrder.verify(featureHelper).createAll(features, layer);
        assertTrue(layer.isLoaded());
    }

    @Test
    public void fetchesAndSavesIconFilesForFeaturesAfterSavingFetchedFeatures() throws Exception {

        Layer layer = new Layer("1", "test", "test1", currentEvent);
        Layer createdLayer = new TestLayer("1", "test", "test1", currentEvent).setId(1234L);
        TestStaticFeature feature = new TestStaticFeature("1.1", new Point(1, 2), layer);
        StaticFeature createdFeature = new TestStaticFeature("1.1", new Point(1, 2), layer).setId(1L);
        String iconUrl = "http://test.mage/icons/test/point.png";
        StaticFeatureProperty iconProperty = new StaticFeatureProperty(StaticFeatureLayerRepository.PROP_ICON_URL, iconUrl);
        feature.getProperties().add(iconProperty);
        List<StaticFeature> features = Collections.singletonList(feature);
        ByteArrayInputStream iconBytes = new ByteArrayInputStream("test icon".getBytes());

        when(layerService.getLayers(currentEvent)).thenReturn(Collections.singleton(layer));
        when(layerService.getFeatures(layer)).thenReturn(features);
        when(layerService.getFeatureIcon(iconUrl)).thenReturn(iconBytes);
        when(layerHelper.create(layer)).thenReturn(createdLayer);
        when(featureHelper.createAll(features, createdLayer)).then(invocation -> {
            feature.setId(321L);
            return features;
        });
        when(featureHelper.readAll(createdLayer.getId())).thenReturn(features);
        when(featureHelper.read(321L)).thenReturn(feature);
        when(featureHelper.update(feature)).thenReturn(feature);

        waitForMainThreadToRun(() -> {
            repo.refreshAvailableMapData(emptyMap(), executor);
            assertThat(repo.getStatus(), is(Resource.Status.Loading));
        });

        onMainThread.assertThatWithin(oneSecond(), repo::getStatus, is(Resource.Status.Success));

        awaitThreadPoolTermination();

        verify(layerHelper, never()).delete(any());
        InOrder order = inOrder(layerHelper, featureHelper, layerService);
        order.verify(layerHelper).create(layer);
        order.verify(featureHelper).createAll(features, createdLayer);
        order.verify(layerService).getFeatureIcon(iconUrl);
        order.verify(featureHelper).read(321L);
        order.verify(featureHelper).update(feature);
        File iconFile = new File(iconsDir, "icons/test/point.png");
        assertThat(feature.getLocalPath(), is(iconFile.getAbsolutePath()));
        assertTrue(iconFile.exists());
        FileReader reader = new FileReader(iconFile);
        char[] content = new char[9];
        reader.read(content);
        assertThat(reader.read(), is(-1));
        assertThat(String.valueOf(content), is("test icon"));
    }

    @Test
    public void doesNotFetchSameIconUrlAgainAfterSavingIcon() throws IOException, LayerException, StaticFeatureException, InterruptedException {

        Layer layer1 = new Layer("1", "test", "test1", currentEvent);
        Layer layer2 = new Layer("2", "test", "test2", currentEvent);
        Layer createdLayer1 = new TestLayer("1", "test", "test1", currentEvent).setId(1234L);
        Layer createdLayer2 = new TestLayer("2", "test", "test2", currentEvent).setId(4567L);
        StaticFeature feature1 = new StaticFeature("1.1", new Point(1, 1), layer1);
        StaticFeature feature2 = new StaticFeature("1.2", new Point(1, 2), layer1);
        StaticFeature feature3 = new StaticFeature("2.1", new Point(2, 1), layer2);
        String iconUrl = "http://test.mage/icons/test/point.png";
        feature1.getProperties().add(new StaticFeatureProperty(StaticFeatureLayerRepository.PROP_ICON_URL, iconUrl));
        feature2.getProperties().add(new StaticFeatureProperty(StaticFeatureLayerRepository.PROP_ICON_URL, iconUrl));
        feature3.getProperties().add(new StaticFeatureProperty(StaticFeatureLayerRepository.PROP_ICON_URL, iconUrl));
        ByteArrayInputStream iconBytes = new ByteArrayInputStream("test icon".getBytes());
        List<StaticFeature> features1 = Arrays.asList(feature1, feature2);
        List<StaticFeature> features2 = Collections.singletonList(feature3);

        when(layerService.getLayers(currentEvent)).thenReturn(Arrays.asList(layer1, layer2));
        when(layerService.getFeatures(layer1)).thenReturn(features1);
        when(layerService.getFeatures(layer2)).thenReturn(features2);
        when(layerService.getFeatureIcon(iconUrl)).thenReturn(iconBytes);
        when(layerHelper.create(layer1)).thenReturn(createdLayer1);
        when(layerHelper.create(layer2)).thenReturn(createdLayer2);
        when(featureHelper.createAll(features1, createdLayer1)).thenReturn(features1);
        when(featureHelper.createAll(features2, createdLayer2)).thenReturn(features2);
        when(featureHelper.readAll(createdLayer1.getId())).thenReturn(features1);
        when(featureHelper.readAll(createdLayer2.getId())).thenReturn(features2);

        waitForMainThreadToRun(() -> {
            repo.refreshAvailableMapData(emptyMap(), executor);
            assertThat(repo.getStatus(), is(Resource.Status.Loading));
        });

        onMainThread.assertThatWithin(oneSecond(), repo::getStatus, is(Resource.Status.Success));

        awaitThreadPoolTermination();

        verify(layerService).getFeatures(layer1);
        verify(layerService).getFeatures(layer2);
        verify(layerService, times(1)).getFeatureIcon(iconUrl);
        File iconFile = new File(iconsDir, "icons/test/point.png");
        assertTrue(iconFile.exists());
        FileReader reader = new FileReader(iconFile);
        char[] content = new char[9];
        reader.read(content);
        assertThat(reader.read(), is(-1));
        assertThat(String.valueOf(content), is("test icon"));
    }

    @Test
    public void deletesLayersThatExistedInTheDatabaseButNotInTheFetchedLayers() throws LayerException, IOException, InterruptedException {

        List<Layer> localLayers = Arrays.asList(
            new TestLayer("layer1", "test", "layer1", currentEvent).setId(100L),
            new TestLayer("layer2", "test", "layer1", currentEvent).setId(200L));
        List<Layer> remoteLayers = Arrays.asList(
            new Layer("layer1", "test", "layer1", currentEvent),
            new Layer("layer3", "test", "layer3", currentEvent));
        when(layerHelper.readByEvent(currentEvent)).thenReturn(localLayers);
        when(layerService.getLayers(currentEvent)).thenReturn(remoteLayers);

        waitForMainThreadToRun(() -> {
            repo.refreshAvailableMapData(emptyMap(), executor);
            assertThat(repo.getStatus(), is(Resource.Status.Loading));
        });

        onMainThread.assertThatWithin(oneSecond(), repo::getStatus, is(Resource.Status.Success));

        verify(layerHelper).delete(200L);
    }

    @Test
    public void deletesLayersThatExistedInTheDatabaseWhenLayerFetchReturnsNoLayers() throws IOException, InterruptedException, LayerException {

        when(layerService.getLayers(currentEvent)).thenReturn(emptySet());
        when(layerHelper.readByEvent(currentEvent)).thenReturn(emptySet());

        waitForMainThreadToRun(() -> {
            repo.refreshAvailableMapData(emptyMap(), executor);
            assertThat(repo.getStatus(), is(Resource.Status.Loading));
        });

        onMainThread.assertThatWithin(oneSecond(), repo::getStatus, is(Resource.Status.Success));

        verify(layerHelper).deleteByEvent(currentEvent);
    }

    @Test
    public void setsTheLiveDataValueAfterTheRefreshFinishes() throws InterruptedException {

        waitForMainThreadToRun(() -> {
            repo.refreshAvailableMapData(emptyMap(), executor);
            assertThat(repo.getStatus(), is(Resource.Status.Loading));
        });

        onMainThread.assertThatWithin(oneSecond(), repo::getStatus, is(Resource.Status.Success));

        verify(observer).onChanged(observed.capture());
        @SuppressWarnings("unchecked")
        Set<MapDataResource> resources = observed.getValue();
        assertThat(resources, hasSize(1));
        assertThat(repo.getValue(), sameInstance(resources));
        MapDataResource res = (MapDataResource) resources.toArray()[0];
        assertThat(res.getResolved(), notNullValue());
        assertThat(res.getResolved().getLayerDescriptors(), is(emptyMap()));
    }

    @Test
    public void doesNotAttemptToFetchWhenOffline() {
        fail("unimplemented");
    }

    @Test
    public void usesLocalDataForFirstRefreshWhenOffline() {
        fail("unimplemented");
    }

    @Test
    public void deletesLayersAfterFeatureFetch() throws Exception {

        TestLayer layer = new TestLayer("abcd", "test", "layer 1", currentEvent);
        when(layerService.getLayers(currentEvent)).thenReturn(Collections.singleton(layer));
        when(layerHelper.read("abcd")).then(invoc -> layer.setId(123L));
        when(layerHelper.create(layer)).then(invoc -> layer.setId(1234L));
        when(layerService.getFeatures(layer)).thenReturn(Collections.emptySet());

        waitForMainThreadToRun(() -> {
            repo.refreshAvailableMapData(emptyMap(), executor);
            assertThat(repo.getStatus(), is(Resource.Status.Loading));
        });

        onMainThread.assertThatWithin(oneSecond(), repo::getStatus, is(Resource.Status.Success));

        verify(layerHelper).read("abcd");
        verify(layerHelper).delete(123L);
    }

    @Test
    public void doesNotDeleteLayersIfLayerFetchFailed() {
        fail("unimplemented");
    }

    @Test
    public void doesNotDeleteLayersIfOffline() {
        fail("unimplemented");
    }

    @Test
    public void finishesSyncInProgressIfCurrentEventChanges() {
        fail("unimplemented");
    }

    @Test
    public void handlesRejectedExecutionOnExecutor() {
        fail("unimplemented");
    }

    @Test
    public void finishesProperlyWhenTheLastLayerSyncFinishesButIsStillResolvingIconsForPreviousLayer() {
        fail("unimplemented");
    }

    @Test
    public void handlesLayerFetchFailure() {
        fail("unimplemented");
    }

    @Test
    public void handlesFeatureFetchFailure() {
        fail("unimplemented");
    }

    @Test
    public void handlesIconFetchFailure() {
        fail("unimplemented");
    }

    @Test
    public void handlesDatabaseErrors() {
        fail("unimplemented");
    }
}