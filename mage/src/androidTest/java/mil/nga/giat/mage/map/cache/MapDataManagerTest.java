package mil.nga.giat.mage.map.cache;


import android.app.Application;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LifecycleRegistry;
import android.arch.lifecycle.Observer;
import android.support.annotation.NonNull;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationWithTimeout;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import mil.nga.giat.mage.data.BasicResource;
import mil.nga.giat.mage.data.Resource;
import mil.nga.giat.mage.test.AsyncTesting;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static mil.nga.giat.mage.data.Resource.Status.*;
import static mil.nga.giat.mage.test.AsyncTesting.waitForMainThreadToRun;
import static mil.nga.giat.mage.test.TargetSuppliesPropertyValueMatcher.withValueSuppliedBy;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MapDataManagerTest implements LifecycleOwner {

    // had to do this to make Mockito generate a different class for
    // two mock providers, because it uses the same class for two
    // separate mock instances of MapDataProvider directly, which is
    // a collision for MapLayerDescriptor.getDataType()
    static abstract class CatProvider implements MapDataProvider {}
    static abstract class DogProvider implements MapDataProvider {}

    private static class TestRepository extends MapDataRepository {

        private final URI base;
        private int refreshCount = 0;
        private Map<URI, MapDataResource> lastRefreshSeed;

        TestRepository(String base) {
            try {
                this.base = new URI(getClass().getSimpleName(), base, "/", null);
            }
            catch (URISyntaxException e) {
                throw new Error(e);
            }
        }

        @NonNull
        @Override
        public String getId() {
            return base.toString();
        }

        @Override
        public boolean ownsResource(@NonNull URI resourceUri) {
            return false;
        }

        @Override
        public void refreshAvailableMapData(@NonNull Map<URI, MapDataResource> existingResolved, @NonNull Executor executor) {
            lastRefreshSeed = existingResolved;
            refreshCount++;
            setValue(BasicResource.loading());
        }

        @Override
        protected void setValue(Resource<Set<? extends MapDataResource>> value) {
            super.setValue(value);
        }

        @Override
        protected void postValue(Resource<Set<? extends MapDataResource>> value) {
            super.postValue(value);
        }

        @NonNull
        Resource<? extends Set<? extends MapDataResource>> requireValue() {
            return requireNonNull(getValue());
        }

        private ResourceBuilder buildResource(String name, MapDataProvider provider) {
            return new ResourceBuilder(name, provider == null ? null : provider.getClass());
        }

        private MapDataResource updateContentTimestampOfResource(MapDataResource resource) {
            return new MapDataResource(resource.getUri(), this, resource.getContentTimestamp() + 1, resource.getResolved());
        }

        private MapDataResource resolveResource(MapDataResource unresolved, MapDataProvider provider, String... layerNames) {
            if (!getId().equals(unresolved.getRepositoryId())) {
                throw new Error("cannot resolve resource from another repository: " + unresolved);
            }
            String name = base.relativize(unresolved.getUri()).getPath();
            return new ResourceBuilder(name, provider.getClass()).layers(layerNames).finish();
        }

        private MapDataResource updateContentTimestampAndLayers(MapDataResource resource, String... layerNames) {
            return updateContentTimestampOfResource(
                new ResourceBuilder(resource.requireResolved().getName(), resource.requireResolved().getType())
                    .layers(layerNames).finish());
        }

        private class ResourceBuilder {
            private final String name;
            private final Class<? extends MapDataProvider> type;
            private final Set<String> layerNames = new HashSet<>();

            private ResourceBuilder(String name, Class<? extends MapDataProvider> type) {
                this.name = name;
                this.type = type;
            }

            private ResourceBuilder layers(String... names) {
                layerNames.addAll(Arrays.asList(names));
                return this;
            }

            private MapDataResource finish() {
                URI uri;
                try {
                    uri = base.resolve(new URI(null, null, name, null));
                }
                catch (URISyntaxException e) {
                    throw new Error(e);
                }
                if (type == null) {
                    return new MapDataResource(uri, TestRepository.this, System.currentTimeMillis());
                }
                Set<MapLayerDescriptor> layers = new HashSet<>();
                for (String layerName : layerNames) {
                    layers.add(new TestLayerDescriptor(layerName, uri, type));
                }
                return new MapDataResource(uri, TestRepository.this, System.currentTimeMillis(),
                    new MapDataResource.Resolved(name, type, Collections.unmodifiableSet(layers)));
            }
        }
    }

    static class TestLayerDescriptor extends MapLayerDescriptor {

        TestLayerDescriptor(String overlayName, URI resourceUri, Class<? extends MapDataProvider> type) {
            super(overlayName, resourceUri, type);
        }

        @NonNull
        @Override
        public String toString() {
            return getLayerUri().toString();
        }
    }

    private static Resource<Set<? extends MapDataResource>> resourceOf(MapDataResource... items) {
        return new BasicResource<>(setOf(items), Success);
    }

    private static Resource<Set<? extends MapDataResource>> resourceOf(Collection<MapDataResource> items) {
        return resourceOf(new ArrayList<>(items).toArray(new MapDataResource[items.size()]));
    }

    private static Set<MapDataResource> setOf(MapDataResource... items) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(items)));
    }

    private static Map<URI, MapDataResource> mapOf(MapDataResource... resources) {
        Map<URI, MapDataResource> map = new HashMap<>(resources.length);
        for (MapDataResource res : resources) {
            map.put(res.getUri(), res);
        }
        return map;
    }

    private static Map<URI, MapLayerDescriptor> mapOfLayers(MapDataResource... resources) {
        Map<URI, MapLayerDescriptor> map = new HashMap<>();
        for (MapDataResource res : resources) {
            map.putAll(res.getLayers());
        }
        return map;
    }

    private static long timeout() {
        return 1000;
    }

    private static VerificationWithTimeout beforeTimeout() {
        return Mockito.timeout(timeout());
    }

    @Rule
    public TestName testName = new TestName();

    @Rule
    public AsyncTesting.MainLooperAssertion onMainLooper = new AsyncTesting.MainLooperAssertion();

    @Mock
    private Executor executor;
    @Mock
    private CatProvider catProvider;
    @Mock
    private DogProvider dogProvider;
    @Mock
    private Observer<Resource<Map<URI, ? extends MapDataResource>>> observer;
    @Mock
    private Application context;
    @Captor
    private ArgumentCaptor<Resource<Map<URI, ? extends MapDataResource>>> changeCaptor;

    private MapDataManager.Config config;
    private MapDataManager manager;
    private ThreadPoolExecutor realExecutor;
    private TestRepository repo1;
    private TestRepository repo2;
    private LifecycleRegistry lifecycle;

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    @Before
    public void configureManager() {

        MockitoAnnotations.initMocks(this);

        repo1 = new TestRepository("repo1");
        repo2 = new TestRepository("repo2");

        when(catProvider.canHandleResource(any(MapDataResource.class))).thenAnswer(invocationOnMock -> {
            MapDataResource res = invocationOnMock.getArgument(0);
            return res.getUri().getPath().toLowerCase().endsWith(".cat");
        });
        when(dogProvider.canHandleResource(any(MapDataResource.class))).thenAnswer(invocationOnMock -> {
            MapDataResource res = invocationOnMock.getArgument(0);
            return res.getUri().getPath().toLowerCase().endsWith(".dog");
        });

        lifecycle = new LifecycleRegistry(this);
        lifecycle.markState(Lifecycle.State.RESUMED);

        config = new MapDataManager.Config()
            .context(context)
            .executor(executor)
            .repositories(repo1, repo2)
            .providers(catProvider, dogProvider);
        initializeManager(config);
    }

    private void initializeManager(MapDataManager.Config config) {
        waitForMainThreadToRun(() -> {
            manager = new MapDataManager(config);
            manager.observe(this, observer);
            // clear the initial change event that LiveData fires after adding the observer
            //noinspection unchecked
            Mockito.reset(observer);
        });
    }

    private void initializeManager() {
        initializeManager(config);
    }

    @After
    public void deactivateExecutorAndWait() {
        if (realExecutor == null) {
            return;
        }
        final String testMethodName = this.testName.getMethodName();
        final ThreadPoolExecutor localRef = realExecutor;
        realExecutor = null;
        localRef.shutdown();
        localRef.setRejectedExecutionHandler((Runnable rejected, ThreadPoolExecutor executor) -> {
            throw new RejectedExecutionException(testMethodName + " tried to execute more background tasks after shutdown");
        });
        try {
            if (!localRef.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new Error(testMethodName + " timed out waiting for thread pool termination");
            }
        }
        catch (InterruptedException e) {
            throw new Error(testMethodName + " interrupted waiting for thread pool termination");
        }
    }

    private void activateExecutor() {
        realExecutor = new ThreadPoolExecutor(2, 4, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        doAnswer(invocationOnMock -> {
            Runnable task = invocationOnMock.getArgument(0);
            realExecutor.execute(task);
            return null;
        }).when(executor).execute(any(Runnable.class));
    }


    @Test
    @UiThreadTest
    public void isMockable() {

        mock(MapDataManager.class, withSettings()
            .useConstructor(new MapDataManager.Config()));
    }

    @Test
    public void resumesAfterCreated() {

        assertThat(manager.getLifecycle().getCurrentState(), is(Lifecycle.State.RESUMED));
    }

    @Test
    @UiThreadTest
    public void doesNotRefreshOnCreation() {

        MapDataRepository repo1 = mock(MapDataRepository.class);
        MapDataRepository repo2 = mock(MapDataRepository.class);
        initializeManager();

        verify(repo1, never()).refreshAvailableMapData(any(), any());
        verify(repo2, never()).refreshAvailableMapData(any(), any());
        verify(executor, never()).execute(any());
        verify(observer, never()).onChanged(any());
    }

    @Test
    @UiThreadTest
    public void initialValueIsEmptyNonNullWhenRepositoriesHaveNoValues() {

        assertThat(manager.getValue(), notNullValue());
        assertThat(manager.requireValue().getContent(), is(emptyMap()));
        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.getResources(), is(emptyMap()));
        assertThat(manager.getLayers(), is(emptyMap()));

        manager.removeObserver(observer);
        manager.observe(this, observer);

        verify(observer).onChanged(changeCaptor.capture());

        Resource<Map<URI, ? extends MapDataResource>> data = changeCaptor.getValue();

        assertThat(data, notNullValue());
        assertThat(data.getContent(), is(emptyMap()));
        assertThat(data.getStatus(), is(Success));
    }

    @Test
    @UiThreadTest
    public void initialDataHasDataFromRepository() {

        MapDataResource resource1 = repo1.buildResource("test.dog", dogProvider).layers("layer1").finish();
        MapDataResource resource2 = repo2.buildResource("test.cat", catProvider).layers("layer2").finish();
        MapDataResource resource3 = repo2.buildResource("other.dog", dogProvider).layers("layer3", "layer4").finish();
        repo1.setValue(BasicResource.success(setOf(resource1)));
        repo2.setValue(BasicResource.success(setOf(resource2, resource3)));
        initializeManager();

        assertThat(manager.requireValue().getContent(), is(mapOf(resource1, resource2, resource3)));
        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.getResources(), is(mapOf(resource1, resource2, resource3)));
        assertThat(manager.getLayers(), is(mapOfLayers(resource1, resource2, resource3)));
    }

    @Test
    @UiThreadTest
    public void initialStatusIsLoadingIfARepositoryIsLoading() {

        MapDataResource resource = repo1.buildResource("test.dog", dogProvider).layers("layer1").finish();
        repo1.setValue(BasicResource.loading(setOf(resource)));
        initializeManager();

        assertThat(manager.requireValue().getContent(), is(emptyMap()));
        assertThat(manager.requireValue().getStatus(), is(Loading));
        assertThat(manager.getResources(), is(emptyMap()));
        assertThat(manager.getLayers(), is(emptyMap()));
    }

    @Test
    @UiThreadTest
    public void changesValueWhenRepositoryAddsResolvedResources() {

        MapDataResource res1 = repo1.buildResource("test1.dog", dogProvider).layers("layer1", "layer2").finish();
        MapDataResource res2 = repo1.buildResource("test2.dog", dogProvider).layers("layer1").finish();
        repo1.setValue(BasicResource.success(setOf(res1, res2)));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(mapOf(res1, res2)));
        assertThat(manager.getResources(), is(mapOf(res1, res2)));
        assertThat(manager.getLayers(), is(mapOfLayers(res1, res2)));
        verify(observer).onChanged(manager.getValue());

        MapDataResource res3 = repo2.buildResource("test3.cat", catProvider).finish();
        repo2.setValue(BasicResource.success(setOf(res3)));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(mapOf(res1, res2, res3)));
        assertThat(manager.getResources(), is(mapOf(res1, res2, res3)));
        assertThat(manager.getLayers(), is(mapOfLayers(res1, res2, res3)));

        verify(observer, times(2)).onChanged(changeCaptor.capture());
        assertThat(changeCaptor.getAllValues().get(0).getContent(), is(mapOf(res1, res2)));
        assertThat(changeCaptor.getAllValues().get(1).getContent(), is(mapOf(res1, res2, res3)));
    }

    @Test
    @UiThreadTest
    public void changesValueWhenRepositoryUpdatesResolvedResources() {

        MapDataResource res1 = repo1.buildResource("test1.dog", dogProvider).layers("layer1", "layer2").finish();
        MapDataResource res2 = repo1.buildResource("test2.dog", dogProvider).layers("layer1").finish();
        repo1.setValue(BasicResource.success(setOf(res1, res2)));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(mapOf(res1, res2)));
        assertThat(manager.getResources(), is(mapOf(res1, res2)));
        assertThat(manager.getLayers(), is(mapOfLayers(res1, res2)));
        verify(observer).onChanged(manager.getValue());

        MapDataResource res1Updated = repo1.updateContentTimestampAndLayers(res1, "layer1", "layer2", "layer3");
        repo1.setValue(BasicResource.success(setOf(res1Updated, res2)));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), allOf(
            is(mapOf(res1, res2)),
            hasEntry(is(res1.getUri()), sameInstance(res1Updated))));
        assertThat(manager.getLayers(), is(mapOfLayers(res1Updated, res2)));
        verify(observer, times(2)).onChanged(changeCaptor.capture());
        assertThat(changeCaptor.getValue().getContent(), allOf(
            is(mapOf(res1, res2)),
            hasEntry(is(res1.getUri()), sameInstance(res1Updated))));
    }

    @Test
    @UiThreadTest
    public void changesValueWhenRepositoryRemovesResolvedResources() {

        MapDataResource res1 = repo1.buildResource("test1.dog", dogProvider).layers("layer1", "layer2").finish();
        MapDataResource res2 = repo1.buildResource("test2.dog", dogProvider).layers("layer1").finish();
        repo1.setValue(BasicResource.success(setOf(res1, res2)));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(mapOf(res1, res2)));
        assertThat(manager.getResources(), is(mapOf(res1, res2)));
        assertThat(manager.getLayers(), is(mapOfLayers(res1, res2)));
        verify(observer).onChanged(manager.getValue());

        repo1.setValue(BasicResource.success(setOf(res2)));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(mapOf(res2)));
        assertThat(manager.getLayers(), is(mapOfLayers(res2)));
        verify(observer, times(2)).onChanged(changeCaptor.capture());
        assertThat(changeCaptor.getValue().getContent(), is(mapOf(res2)));
    }

    @Test
    @UiThreadTest
    public void changesValueWhenRepositoryRemovesAllResolvedResourcesWithNullChange() {

        MapDataResource res1 = repo1.buildResource("test1.dog", dogProvider).layers("layer1", "layer2").finish();
        MapDataResource res2 = repo1.buildResource("test2.dog", dogProvider).layers("layer1").finish();
        repo1.setValue(BasicResource.success(setOf(res1, res2)));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(mapOf(res1, res2)));
        assertThat(manager.getResources(), is(mapOf(res1, res2)));
        assertThat(manager.getLayers(), is(mapOfLayers(res1, res2)));
        verify(observer).onChanged(manager.getValue());

        repo1.setValue(null);

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(emptyMap()));
        assertThat(manager.getLayers(), is(emptyMap()));
        verify(observer, times(2)).onChanged(changeCaptor.capture());
        assertThat(changeCaptor.getValue().getContent(), is(emptyMap()));
    }

    @Test
    @UiThreadTest
    public void changesValueWhenRepositoryRemovesAllResolvedResourcesWithNullContent() {

        MapDataResource res1 = repo1.buildResource("test1.dog", dogProvider).layers("layer1", "layer2").finish();
        MapDataResource res2 = repo1.buildResource("test2.dog", dogProvider).layers("layer1").finish();
        repo1.setValue(BasicResource.success(setOf(res1, res2)));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(mapOf(res1, res2)));
        assertThat(manager.getResources(), is(mapOf(res1, res2)));
        assertThat(manager.getLayers(), is(mapOfLayers(res1, res2)));
        verify(observer).onChanged(manager.getValue());

        repo1.setValue(BasicResource.success());

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(emptyMap()));
        assertThat(manager.getLayers(), is(emptyMap()));
        verify(observer, times(2)).onChanged(changeCaptor.capture());
        assertThat(changeCaptor.getValue().getContent(), is(emptyMap()));
    }

    @Test
    @UiThreadTest
    public void changesValueWhenRepositoryRemovesAllResolvedResourcesWithEmptyContent() {

        MapDataResource res1 = repo1.buildResource("test1.dog", dogProvider).layers("layer1", "layer2").finish();
        MapDataResource res2 = repo1.buildResource("test2.dog", dogProvider).layers("layer1").finish();
        repo1.setValue(BasicResource.success(setOf(res1, res2)));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(mapOf(res1, res2)));
        assertThat(manager.getResources(), is(mapOf(res1, res2)));
        assertThat(manager.getLayers(), is(mapOfLayers(res1, res2)));
        verify(observer).onChanged(manager.getValue());

        repo1.setValue(BasicResource.success(emptySet()));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(emptyMap()));
        assertThat(manager.getLayers(), is(emptyMap()));
        verify(observer, times(2)).onChanged(changeCaptor.capture());
        assertThat(changeCaptor.getValue().getContent(), is(emptyMap()));
    }

    @Test
    @UiThreadTest
    public void changesValueWhenRepositoryChangesWithSameResolvedDataButDifferentInstances() {

        MapDataResource res1 = repo1.buildResource("test1.dog", dogProvider).layers("layer1", "layer2").finish();
        MapDataResource res2 = repo1.buildResource("test2.dog", dogProvider).layers("layer1").finish();
        repo1.setValue(BasicResource.success(setOf(res1, res2)));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(mapOf(res1, res2)));
        assertThat(manager.getResources(), is(mapOf(res1, res2)));
        assertThat(manager.getLayers(), is(mapOfLayers(res1, res2)));
        verify(observer).onChanged(manager.getValue());

        MapDataResource res1Copy = new MapDataResource(res1.getUri(), repo1, res1.getContentTimestamp(), res1.requireResolved());
        repo1.setValue(BasicResource.success(setOf(res1Copy, res2)));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), allOf(
            is(mapOf(res1, res2)),
            hasEntry(is(res1.getUri()), sameInstance(res1Copy))));
        assertThat(manager.getLayers(), is(mapOfLayers(res1Copy, res2)));
        verify(observer, times(2)).onChanged(changeCaptor.capture());
        assertThat(changeCaptor.getValue().getContent(), allOf(
            is(mapOf(res1, res2)),
            hasEntry(is(res1.getUri()), sameInstance(res1Copy))));
    }

    @Test
    @UiThreadTest
    public void doesNotChangeValueIfRepositoryChangesValueWithSameResolvedDataAndSameInstances() {

        MapDataResource res1 = repo1.buildResource("test1.dog", dogProvider).layers("layer1", "layer2").finish();
        MapDataResource res2 = repo1.buildResource("test2.dog", dogProvider).layers("layer1").finish();
        repo1.setValue(BasicResource.success(setOf(res1, res2)));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(mapOf(res1, res2)));
        assertThat(manager.getResources(), is(mapOf(res1, res2)));
        assertThat(manager.getLayers(), is(mapOfLayers(res1, res2)));
        verify(observer).onChanged(manager.getValue());

        repo1.setValue(BasicResource.success(setOf(res1, res2)));

        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.requireValue().getContent(), is(mapOf(res1, res2)));
        assertThat(manager.getLayers(), is(mapOfLayers(res1, res2)));
        verify(observer, times(1)).onChanged(changeCaptor.capture());
        assertThat(changeCaptor.getValue().getContent(), is(mapOf(res1, res2)));
    }

    @Test
    @UiThreadTest
    public void doesNotChangeValueIfRepositoryChangesFromNullToEmptyData() {

        repo1.setValue(null);
        repo1.setValue(BasicResource.success());
        repo1.setValue(BasicResource.success(emptySet()));

        verify(observer, never()).onChanged(any());
    }

    @Test
    @UiThreadTest
    public void statusChangesToLoadingWhenRepositoryChangesToLoading() {

        MapDataResource resource = repo1.buildResource("test.dog", dogProvider).layers("layer1").finish();
        repo1.setValue(BasicResource.success(setOf(resource)));

        assertThat(manager.requireValue().getStatus(), is(Success));

        repo1.setValue(BasicResource.loading(setOf(resource)));

        assertThat(manager.requireValue().getStatus(), is(Loading));
    }

    @Test
    @UiThreadTest
    public void removesResourcesForRepositoryWhenStatusChangesToLoading() {

        MapDataResource resource = repo1.buildResource("test.dog", dogProvider).layers("layer1").finish();
        repo1.setValue(BasicResource.success(setOf(resource)));

        assertThat(manager.requireValue().getContent(), is(mapOf(resource)));
        assertThat(manager.requireValue().getStatus(), is(Success));
        assertThat(manager.getResources(), is(mapOf(resource)));
        assertThat(manager.getLayers(), is(resource.getLayers()));

        repo1.setValue(BasicResource.loading(setOf(resource)));

        assertThat(manager.requireValue().getContent(), is(emptyMap()));
        assertThat(manager.requireValue().getStatus(), is(Loading));
        assertThat(manager.getResources(), is(emptyMap()));
        assertThat(manager.getLayers(), is(emptyMap()));
    }

    @Test
    @UiThreadTest
    public void statusRemainsLoadingWhenRepositoryChangesToSuccessButOthersAreStillLoading() {

        repo1.setValue(BasicResource.loading());
        repo2.setValue(BasicResource.loading());

        assertThat(manager.requireValue().getStatus(), is(Loading));

        repo1.setValue(BasicResource.success());

        assertThat(manager.requireValue().getStatus(), is(Loading));

        repo2.setValue(BasicResource.success());

        assertThat(manager.requireValue().getStatus(), is(Success));
    }

    @Test
    @UiThreadTest
    public void statusRemainsLoadingWhenRepositoriesAreSuccessButStillResolvingResources() {

        MapDataResource res1 = repo1.buildResource("1.dog", null).finish();
        MapDataResource res2 = repo2.buildResource("2.dog", null).finish();

        repo1.setValue(BasicResource.loading());
        repo2.setValue(BasicResource.loading());

        assertThat(manager.requireValue().getStatus(), is(Loading));

        repo1.setValue(BasicResource.success(setOf(res1)));

        assertThat(manager.requireValue().getStatus(), is(Loading));

        repo2.setValue(BasicResource.success(setOf(res2)));

        assertThat(manager.requireValue().getStatus(), is(Loading));
    }

    @Test
    public void doesNotBeginResolvingResourcesUntilRepositoryStatusIsSuccess() {
        fail("unimplemented");
    }

    @Test
    public void doesNotChangeValueUntilResourcesAreResolvedForRepositoryChange() {
        /*
        MapDataManager should resolve resources, then call MapDataRepository.onExternallyResolved(),
        and wait for the resulting LiveData change with no unresolved resources before changing its
        own LiveData value
         */
        fail("unimplemented");
    }

    @Test
    public void refreshChangesValueToLoadingOnlyOnceInsteadOfOneForEachRepositoryChange() {
        fail("unimplemented");
    }

    @Test
    public void whatToDoWhenOneOrMoreRepositoriesChangeToErrorStatus() {
        // TODO: remove resources for repository, or just take repository's resolved resources as successful
        // but add the repo status message to its own status message and change to error status?
        fail("unimplemented");
    }

    @Test
    public void doesNotResolveOnConstruction() throws MapDataResolveException {

        // TODO: this test will be invalid if repository has inintial observed value with unresolved resources

        waitForMainThreadToRun(() -> {
            repo1.setValue(resourceOf(repo1.buildResource("do_not_resolve_yet.dog", null).finish()));

            reset(executor);

            initializeManager();

            verify(executor, never()).execute(any());
            verify(observer, never()).onChanged(any());
        });

        waitForMainThreadToRun(() -> {});

        verify(catProvider, never()).resolveResource(any());
        verify(dogProvider, never()).resolveResource(any());
    }

    @Test
    @UiThreadTest
    public void refreshSeriallyBeginsRefreshOnRepositories() {
        MapDataRepository repo1 = mock(MapDataRepository.class);
        when(repo1.getId()).thenReturn("repo1");
        MapDataRepository repo2 = mock(MapDataRepository.class);
        when(repo2.getId()).thenReturn("repo2");
        initializeManager(config.repositories(repo1, repo2));
        manager.refreshMapData();

        verify(repo1).refreshAvailableMapData(anyMap(), same(executor));
        verify(repo2).refreshAvailableMapData(anyMap(), same(executor));
        verify(executor, never()).execute(any());
        verify(observer, never()).onChanged(any());
    }

    @Test
    @UiThreadTest
    public void providesResolvedResourcesToRepositoryForRefresh() throws InterruptedException {
        MapDataResource res1 = repo1.buildResource("res1.dog", dogProvider).finish();
        MapDataResource res2 = repo1.buildResource("res2.cat", catProvider).finish();
        MapDataResource res3 = repo2.buildResource("res3.cat", catProvider).finish();

        repo1.setValue(resourceOf(res1, res2));
        repo2.setValue(resourceOf(res3));

        assertThat(manager.requireValue().getContent(), is(mapOf(res1, res2, res3)));

        manager.refreshMapData();

        assertThat(repo1.lastRefreshSeed, allOf(
            is(mapOf(res1, res2)),
            hasEntry(is(res1.getUri()), sameInstance(res1)),
            hasEntry(is(res2.getUri()), sameInstance(res2))));
        assertThat(repo2.lastRefreshSeed, allOf(
            is(mapOf(res3)),
            hasEntry(is(res3.getUri()), sameInstance(res3))));
    }

    @Test
    @UiThreadTest
    public void doesNotRefreshRepositoryThatIsLoading() {

        repo1.setValue(BasicResource.loading());

        manager.refreshMapData();

        assertThat(repo1.refreshCount, is(0));
        assertThat(repo2.refreshCount, is(1));

        repo1.setValue(BasicResource.success());
        repo2.setValue(BasicResource.loading());

        manager.refreshMapData();

        assertThat(repo1.refreshCount, is(1));
        assertThat(repo2.refreshCount, is(1));
    }

    @Test
    public void doesNotRefreshRepositoryWithResolvingResources() throws MapDataResolveException, InterruptedException {
//        MapDataResource res1 = repo1.buildResource("res.cat", null).finish();
//        Lock lock = new ReentrantLock();
//        Condition resolveCondition = lock.newCondition();
//        AtomicBoolean resolveBlocked = new AtomicBoolean(false);
//
//        when(catProvider.resolveResource(res1)).then(invocation -> {
//            lock.lock();
//            resolveBlocked.set(true);
//            resolveCondition.signal();
//            while (resolveBlocked.get()) {
//                resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//            }
//            lock.unlock();
//            return repo1.resolveResource(res1, catProvider);
//        });
//
//        activateExecutor();
//
//        waitForMainThreadToRun(() -> repo1.setValue(resourceOf(res1)));
//
//        lock.lock();
//        while (!resolveBlocked.get()) {
//            resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//        }
//        lock.unlock();
//
//        waitForMainThreadToRun(() -> {
//            manager.refreshMapData();
//
//            assertThat(repo1.requireValue().getStatus(), not(Resource.Status.Loading));
//            assertThat(repo1.refreshCount, is(0));
//            assertThat(repo2.refreshCount, is(1));
//        });
//
//        lock.lock();
//        resolveBlocked.set(false);
//        resolveCondition.signal();
//        lock.unlock();
//
//        onMainLooper.assertThatWithin(timeout(), manager::getResources, is(mapOf(res1)));
//
//        waitForMainThreadToRun(() -> {
//            manager.refreshMapData();
//
//            assertThat(repo1.refreshCount, is(1));
//            assertThat(repo2.refreshCount, is(2));
//        });
    }

    @Test
    @UiThreadTest
    public void addsLayersAndResourcesWhenRepositoryAddsNewResources() {
        MapDataResource repo1_1 = repo1.buildResource("repo1.1.dog", dogProvider).finish();
        MapDataResource repo1_2 = repo1.buildResource("repo1.2.cat", catProvider).layers("repo1.2.cat.1", "repo1.2.cat.2").finish();
        MapDataResource repo2_1 = repo2.buildResource("repo2.1.dog", dogProvider).layers("repo2.1.dog.1").finish();
        repo1.setValue(resourceOf(repo1_1, repo1_2));
        repo2.setValue(resourceOf(repo2_1));

        assertThat(manager.getResources(), is(mapOf(repo1_1, repo1_2, repo2_1)));
        assertThat(manager.getLayers(), is(mapOfLayers(repo1_1, repo1_2, repo2_1)));
        verify(observer, times(2)).onChanged(changeCaptor.capture());
        Resource<? extends Map<URI, ? extends MapDataResource>> change = changeCaptor.getAllValues().get(0);
        assertThat(change.getContent(), is(mapOf(repo1_1, repo1_2)));
        change = changeCaptor.getAllValues().get(1);
        assertThat(change.getContent(), is(mapOf(repo1_1, repo1_2, repo2_1)));
    }

    @Test
    @UiThreadTest
    public void removesLayersAndResourcesWhenRepositoryRemovesResources() {
        MapDataResource doomed = repo1.buildResource("doomed.dog", dogProvider).layers("doomed.1").finish();
        repo1.setValue(resourceOf(doomed));

        assertThat(manager.getResources(), equalTo(mapOf(doomed)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(doomed)));

        repo1.setValue(resourceOf());

        assertThat(manager.getResources(), equalTo(emptyMap()));
        assertThat(manager.getLayers(), equalTo(emptyMap()));
        verify(observer, times(2)).onChanged(changeCaptor.capture());
        Resource<? extends Map<URI, ? extends MapDataResource>> change = changeCaptor.getValue();
        assertThat(change.getContent(), is(emptyMap()));
    }

    @Test
    @UiThreadTest
    public void doesNotFireUpdateIfResourceDidNotChange() {
        MapDataResource mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1").finish();
        repo1.setValue(resourceOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));
        verify(observer).onChanged(any());

        mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1").finish();
        repo1.setValue(resourceOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));
        verifyNoMoreInteractions(observer);
    }

    @Test
    @UiThreadTest
    public void firesUpdateWhenResourceContentTimestampChanges() {
        MapDataResource mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1").finish();
        repo1.setValue(resourceOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));

        long oldTimestamp = mod.getContentTimestamp();
        mod = repo1.updateContentTimestampOfResource(mod);
        repo1.setValue(resourceOf(mod));

        assertThat(mod.getContentTimestamp(), greaterThan(oldTimestamp));
        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getResources(), hasEntry(is(mod.getUri()), sameInstance(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));
        verify(observer, times(2)).onChanged(changeCaptor.capture());
        Resource<? extends Map<URI, ? extends MapDataResource>> change = changeCaptor.getValue();
        assertThat(change.getContent(), is(mapOf(mod)));
        assertThat(change.getContent(), hasEntry(is(mod.getUri()), sameInstance(mod)));
    }

    @Test
    @UiThreadTest
    public void addsLayersWhenRepositoryUpdatesResource() {
        MapDataResource mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1").finish();
        repo1.setValue(resourceOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));

        repo1.updateContentTimestampOfResource(mod);
        mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1", "mod.2").finish();
        repo1.setValue(resourceOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));
    }

    @Test
    @UiThreadTest
    public void removesLayersWhenRepositoryUpdatesResource() {
        MapDataResource mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1").finish();
        repo1.setValue(resourceOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));

        mod = repo1.updateContentTimestampOfResource(repo1.buildResource("mod.dog", dogProvider).finish());
        repo1.setValue(resourceOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(emptyMap()));
    }

    @Test
    @UiThreadTest
    public void doesNotFireUpdateUntilAllResourcesAreResolved() {
        MapDataResource unresolved = repo1.buildResource("unresolved.dog", null).finish();
        repo1.setValue(resourceOf(unresolved));

        assertThat(manager.getResources(), equalTo(emptyMap()));
        assertThat(manager.getLayers(), equalTo(emptyMap()));
        verify(observer, never()).onChanged(any());
    }

    @Test
    @UiThreadTest
    public void doesNotResolveNewResourcesRepositoryResolved() throws MapDataResolveException {
        MapDataResource res1 = repo1.buildResource("res1.dog", dogProvider).finish();
        MapDataResource res2 = repo1.buildResource("res2.cat", catProvider).finish();
        repo1.setValue(resourceOf(res1, res2));

        assertThat(requireNonNull(manager.getValue()).getContent(), is(mapOf(res1, res2)));
        assertThat(manager.getResources(), hasEntry(is(res1.getUri()), sameInstance(res1)));
        assertThat(manager.getResources(), hasEntry(is(res2.getUri()), sameInstance(res2)));
        verify(observer).onChanged(changeCaptor.capture());
        assertThat(changeCaptor.getValue(), sameInstance(manager.getValue()));
        verify(executor, never()).execute(any());
        verify(catProvider, never()).resolveResource(any());
        verify(dogProvider, never()).resolveResource(any());
    }

    @Test
    @UiThreadTest
    public void doesNotResolveUpdatedResourcesTheRepositoryResolved() throws MapDataResolveException {
        MapDataResource res1 = repo1.buildResource("res1.dog", dogProvider).finish();
        MapDataResource res2 = repo1.buildResource("res2.cat", catProvider).finish();
        repo1.setValue(resourceOf(res1, res2));

        assertThat(manager.getResources(), is(mapOf(res1, res2)));
        assertThat(manager.getResources(), hasEntry(is(res1.getUri()), sameInstance(res1)));
        assertThat(manager.getResources(), hasEntry(is(res2.getUri()), sameInstance(res2)));
        assertThat(manager.getLayers(), is(emptyMap()));

        res1 = repo1.updateContentTimestampAndLayers(res1, "layer1", "layer2");
        repo1.setValue(resourceOf(res1, res2));

        assertThat(manager.requireValue().getContent(), allOf(
            is(mapOf(res1, res2)),
            hasEntry(is(res1.getUri()), sameInstance(res1)),
            hasEntry(is(res2.getUri()), sameInstance(res2))));
        assertThat(manager.getLayers(), is(mapOfLayers(res1)));
        verify(executor, never()).execute(any());
        verify(catProvider, never()).resolveResource(any());
        verify(dogProvider, never()).resolveResource(any());
        verify(observer, times(2)).onChanged(changeCaptor.capture());
        Resource<? extends Map<URI, ? extends MapDataResource>> change = changeCaptor.getValue();
        assertThat(change, sameInstance(manager.getValue()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void asynchronouslyResolvesNewUnresolvedResources() throws MapDataResolveException {
        activateExecutor();

        MapDataResource res1 = repo1.buildResource("res1.dog", null).finish();
        MapDataResource res2 = repo1.buildResource("res2.cat", null).finish();
        MapDataResource res1Resolved = repo1.resolveResource(res1, dogProvider, "Res 1, Layer 1");
        MapDataResource res2Resolved = repo1.resolveResource(res2, catProvider, "Res 2, Layer 1", "Res 2, Layer 2");
        when(dogProvider.resolveResource(res1)).thenReturn(res1Resolved);
        when(catProvider.resolveResource(res2)).thenReturn(res2Resolved);

        repo1.postValue(resourceOf(res1, res2));

        assertThat(manager.getResources(), is(emptyMap()));
        assertThat(manager.getLayers(), is(emptyMap()));
        verify(dogProvider, beforeTimeout()).resolveResource(res1);
        verify(catProvider, beforeTimeout()).resolveResource(res2);
        verify(observer, beforeTimeout()).onChanged(changeCaptor.capture());

        deactivateExecutorAndWait();

        assertThat(repo1.requireValue().getContent(), containsInAnyOrder(sameInstance(res1Resolved), sameInstance(res2Resolved)));
        Resource<? extends Map<URI, ? extends MapDataResource>> change = changeCaptor.getValue();
        assertThat(change.getContent(), allOf(
            is(mapOf(res1Resolved, res2Resolved)),
            hasEntry(is(res1.getUri()), sameInstance(res1Resolved)),
            hasEntry(is(res2.getUri()), sameInstance(res2Resolved))));

        waitForMainThreadToRun(() -> {
            assertThat(manager.getResources(), allOf(
                is(mapOf(res1Resolved, res2Resolved)),
                hasEntry(is(res1.getUri()), sameInstance(res1Resolved)),
                hasEntry(is(res2.getUri()), sameInstance(res2Resolved))));
            assertThat(manager.getLayers(), is(mapOfLayers(res1Resolved, res2Resolved)));
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    public void asynchronouslyResolvesUpdatedUnresolvedResources() throws MapDataResolveException, InterruptedException {
        MapDataResource res1 = repo1.buildResource("res1.dog", dogProvider).finish();
        MapDataResource res2 = repo1.buildResource("res2.cat", catProvider).finish();

        waitForMainThreadToRun(() -> {
            repo1.setValue(resourceOf(res1, res2));

            assertThat(manager.getResources(), is(mapOf(res1, res2)));
            assertThat(manager.getLayers(), is(emptyMap()));
        });

        verify(executor, never()).execute(any());
        verify(dogProvider, never()).resolveResource(any());
        verify(catProvider, never()).resolveResource(any());
        verify(observer).onChanged(changeCaptor.capture());

        activateExecutor();

        repo1.updateContentTimestampOfResource(res1);
        repo1.updateContentTimestampOfResource(res2);
        MapDataResource res1Unresolved = repo1.buildResource(res1.requireResolved().getName(), null).finish();
        MapDataResource res2Unresolved = repo1.buildResource(res2.requireResolved().getName(), null).finish();
        MapDataResource res1Resolved = repo1.resolveResource(res1Unresolved, dogProvider, "R1/L1");
        MapDataResource res2Resolved = repo1.resolveResource(res2Unresolved, catProvider, "R2/L1", "R2/L2");
        when(dogProvider.resolveResource(res1Unresolved)).thenReturn(res1Resolved);
        when(catProvider.resolveResource(res2Unresolved)).thenReturn(res2Resolved);

        repo1.postValue(resourceOf(res1Unresolved, res2Unresolved));

        onMainLooper.assertThatWithin(timeout(), () -> manager.requireValue().getStatus(), is(Success));

        verify(dogProvider).resolveResource(res1Unresolved);
        verify(catProvider).resolveResource(res2Unresolved);
        verify(observer, times(2)).onChanged(changeCaptor.capture());

        waitForMainThreadToRun(() -> {
            Map<URI, MapDataResource> resources = manager.requireValue().getContent();
            assertThat(resources, allOf(
                is(mapOf(res1Resolved, res2Resolved)),
                hasEntry(is(res1.getUri()), sameInstance(res1Resolved)),
                hasEntry(is(res2.getUri()), sameInstance(res2Resolved))));
            assertThat(manager.getLayers(), is(mapOfLayers(res1Resolved, res2Resolved)));
        });
    }

    @Test
    public void resolvesUpdatedUnresolvedResourcesWithExistingDataWhenContentTimestampDidNotChange() throws MapDataResolveException, InterruptedException {
        MapDataResource res1Resolved = repo1.buildResource("res1.dog", dogProvider).layers("R1/L1").finish();

        waitForMainThreadToRun(() -> {
            repo1.setValue(resourceOf(res1Resolved));

            assertThat(manager.getResources(), is(mapOf(res1Resolved)));
            assertThat(manager.getLayers(), is(mapOfLayers(res1Resolved)));
        });

        verify(executor, never()).execute(any());
        verify(dogProvider, never()).resolveResource(any());
        verify(catProvider, never()).resolveResource(any());
        verify(observer).onChanged(any());

        activateExecutor();

        MapDataResource res1Unresolved = repo1.buildResource(res1Resolved.requireResolved().getName(), null).finish();

        assertThat(res1Unresolved.getContentTimestamp(), is(res1Resolved.getContentTimestamp()));

        repo1.postValue(resourceOf(res1Unresolved));

        onMainLooper.assertThatWithin(timeout(), manager::getResources,
            hasEntry(is(res1Resolved.getUri()), allOf(
                not(sameInstance(res1Resolved)),
                withValueSuppliedBy(MapDataResource::getResolved, sameInstance(res1Resolved.getResolved())))));

        deactivateExecutorAndWait();

        verify(dogProvider, never()).resolveResource(any());
        verify(catProvider, never()).resolveResource(any());

        waitForMainThreadToRun(() -> {
            @SuppressWarnings("unchecked")
            Set<MapDataResource> resources = (Set<MapDataResource>) repo1.requireValue().getContent();
            assertThat(resources, is(setOf(res1Resolved)));
            assertThat(resources, hasItem(withValueSuppliedBy(MapDataResource::getResolved, sameInstance(res1Resolved.getResolved()))));
            verifyNoMoreInteractions(observer);
            assertThat(manager.getResources(), is(mapOf(res1Resolved)));
            assertThat(manager.getResources(), hasEntry(is(res1Resolved.getUri()), not(sameInstance(res1Resolved))));
            assertThat(manager.getResources().get(res1Resolved.getUri()).getResolved(), sameInstance(res1Resolved.getResolved()));
            assertThat(manager.getLayers(), is(mapOfLayers(res1Resolved)));
        });
    }

    @Test
    public void resolvesResourcesInAscendingLexicalOrderOfUris() throws MapDataResolveException, InterruptedException {
        MapDataResource res1 = repo1.buildResource("a.cat", null).finish();
        MapDataResource res2 = repo1.buildResource("a.dog", null).finish();
        MapDataResource res3 = repo1.buildResource("b.dog", null).finish();
        List<MapDataResource> outOfOrder = Arrays.asList(res3, res1, res2);

        when(catProvider.resolveResource(res1)).thenReturn(repo1.resolveResource(res1, catProvider));
        when(dogProvider.resolveResource(res2)).thenReturn(repo1.resolveResource(res2, dogProvider));
        when(dogProvider.resolveResource(res3)).thenReturn(repo1.resolveResource(res3, dogProvider));

        Set<MapDataResource> resources = new TreeSet<>((a, b) -> outOfOrder.indexOf(a) - outOfOrder.indexOf(b));
        resources.addAll(outOfOrder);

        int i = 0;
        for (MapDataResource resourceInSet : resources) {
            assertThat(resourceInSet, sameInstance(outOfOrder.get(i++)));
        }

        activateExecutor();

        waitForMainThreadToRun(() -> repo1.setValue(new BasicResource<>(resources, Success)));

        onMainLooper.assertThatWithin(timeout(), () -> manager.requireValue().getStatus(), is(Success));

        deactivateExecutorAndWait();

        InOrder resolveOrder = inOrder(catProvider, dogProvider);
        resolveOrder.verify(catProvider).resolveResource(res1);
        resolveOrder.verify(dogProvider).resolveResource(res2);
        resolveOrder.verify(dogProvider).resolveResource(res3);
    }

    /*
     The concurrency meat: What happens when a repository fires a change while
     resources are resolving from a previous change?
     Baked into the following tests is the assumption that every change a repository
     fires results in MapDataManager conducting a serial loop over the sequence of
     the repository's resources to resolve them, then firing an update once the
     change is wholly processed.  The alternative would be that MapDataManager could
     begin resolving all resources from a single repository change, or multiple changes
     concurrently, which would invalidate the following tests, as well as potentially
     increase the number of updates MapDataManager fires, or overly complicate the
     mechanism of collating or grouping the updates.  Maybe that doesn't matter for
     practical purposes.  Granted this is a bit of an arbitrary granularity of
     grouping repository changes into MapDataUpdate events, but it's easier to manage
     and maps directly to the LiveData notifications that come from the MapDataRepository
     implementations.
     */

//    @Test
//    public void handlesRepositoryChangeThatAddsResourcesWhileResolvingConcurrently() throws MapDataResolveException, InterruptedException {
//        Lock resolveLock = new ReentrantLock();
//        AtomicBoolean resolveBlocked = new AtomicBoolean(false);
//        Condition resolveCondition = resolveLock.newCondition();
//        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
//        MapDataResource res1Resolved = repo1.resolveResource(res1, catProvider, "res1/layer1");
//        MapDataResource res2 = repo1.buildResource("res2.cat", null).finish();
//        MapDataResource res2Resolved = repo1.resolveResource(res2, catProvider, "res2/layer2");
//
//        when(catProvider.resolveResource(res1)).then(invoc -> {
//            resolveLock.lock();
//            resolveBlocked.set(true);
//            resolveCondition.signal();
//            while (resolveBlocked.get()) {
//                resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//            }
//            return res1Resolved;
//        });
//
//        activateExecutor();
//
//        repo1.postValue(resourceOf(res1));
//        resolveLock.lock();
//        while (!resolveBlocked.get()) {
//            resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//        }
//        resolveLock.unlock();
//
//        waitForMainThreadToRun(() -> repo1.setValue(resourceOf(res1, res2)));
//
//        when(catProvider.resolveResource(res2)).thenReturn(res2Resolved);
//
//        resolveLock.lock();
//        resolveBlocked.set(false);
//        resolveCondition.signal();
//        resolveLock.unlock();
//
//        onMainLooper.assertThatWithin(1000, () -> manager.requireValue().getStatus(), is(Success));
//
//        deactivateExecutorAndWait();
//
//        waitForMainThreadToRun(() -> {
//            verify(observer).onChanged(changeCaptor.capture());
//            Resource<? extends Map<URI, ? extends MapDataResource>> change = changeCaptor.getValue();
//            assertThat(change, sameInstance(manager.getValue()));
//            assertThat(manager.requireValue().getContent(), allOf(
//                is(mapOf(res1Resolved, res2Resolved)),
//                hasEntry(is(res1.getUri()), withValueSuppliedBy(MapDataResource::getResolved, sameInstance(res1Resolved.getResolved()))),
//                hasEntry(is(res2.getUri()), withValueSuppliedBy(MapDataResource::getResolved, sameInstance(res2Resolved.getResolved())))));
//        });
//    }
//
//    @Test
//    public void handlesRepositoryChangeThatUpdatesResourcesWhileResolvingConcurrently() throws MapDataResolveException, InterruptedException {
//        Lock resolveLock = new ReentrantLock();
//        AtomicBoolean resolveBlocked = new AtomicBoolean(false);
//        Condition resolveCondition = resolveLock.newCondition();
//        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
//        MapDataResource res1Resolved = repo1.resolveResource(res1, catProvider, "res1/layer1");
//        MapDataResource res2 = repo1.buildResource("res2.cat", null).finish();
//        MapDataResource res2Resolved = repo1.resolveResource(res2, catProvider, "res2/layer1");
//        Map<URI, MapDataResource> unresolvedResources = new HashMap<>(mapOf(res1, res2));
//        Map<URI, MapDataResource> resolvedResources = new HashMap<>(mapOf(res1Resolved, res2Resolved));
//
//        when(catProvider.resolveResource(any())).then(invoc -> {
//            resolveLock.lock();
//            MapDataResource unresolved = invoc.getArgument(0);
//            MapDataResource resolved = resolvedResources.get(unresolved.getUri());
//            resolveBlocked.set(true);
//            resolveCondition.signal();
//            while (resolveBlocked.get()) {
//                resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//            }
//            resolveLock.unlock();
//            return resolved;
//        });
//
//        activateExecutor();
//
//        waitForMainThreadToRun(() -> repo1.setValue(resourceOf(res1, res2)));
//
//        resolveLock.lock();
//        while (!resolveBlocked.get()) {
//            resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//        }
//        resolveBlocked.set(false);
//        resolveCondition.signal();
//        while (!resolveBlocked.get()) {
//            resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//        }
//        // don't let second resolve finish to force a concurrent change from the repository
//        resolveLock.unlock();
//
//        MapDataResource res1Updated = repo1.updateContentTimestampOfResource(res1);
//        MapDataResource res1UpdatedResovled = res1Updated.resolve(res1Resolved.requireResolved());
//        unresolvedResources.put(res1.getUri(), res1Updated);
//        resolvedResources.put(res1.getUri(), res1UpdatedResovled);
//
//        assertThat(res1Updated.getContentTimestamp(), greaterThan(res1.getContentTimestamp()));
//
//        waitForMainThreadToRun(() -> repo1.setValue(resourceOf(unresolvedResources.values())));
//
//        InOrder resolveVerification = inOrder(catProvider);
//        resolveVerification.verify(catProvider).resolveResource(res1Updated);
//        resolveVerification.verify(catProvider).resolveResource(res2);
//        resolveVerification.verifyNoMoreInteractions();
//
//        resolveLock.lock();
//        resolveBlocked.set(false);
//        resolveCondition.signal();
//        while (!resolveBlocked.get()) {
//            resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//        }
//        resolveBlocked.set(false);
//        resolveCondition.signal();
//        while (!resolveBlocked.get()) {
//            resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//        }
//        resolveBlocked.set(false);
//        resolveCondition.signal();
//        resolveLock.unlock();
//
//        resolveVerification.verify(catProvider).resolveResource(same(res1Updated));
//
//        onMainLooper.assertThatWithin(timeout(), manager::getResources, is(mapOf(res1Resolved, res2Resolved)));
//
//        deactivateExecutorAndWait();
//
//        verify(catProvider, times(2)).resolveResource(res1);
//        verify(catProvider, times(1)).resolveResource(res2);
//        verify(observer).onChanged(changeCaptor.capture());
//        assertThat(manager.getResources().get(res1.getUri()).getResolved(), sameInstance(res1Resolved.getResolved()));
//        assertThat(manager.getResources().get(res2.getUri()).getResolved(), sameInstance(res2Resolved.getResolved()));
//    }
//
//    /**
//     * Really this is already tested above, but the more tests the better, right?
//     */
//    @Test
//    public void handlesRepositoryChangeWithUnresolvedResourceThatWasResolvedInPendingChangeConcurrently() throws InterruptedException, MapDataResolveException {
//
//        Lock resolveLock = new ReentrantLock();
//        AtomicBoolean resolveBlocked = new AtomicBoolean(false);
//        Condition resolveCondition = resolveLock.newCondition();
//        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
//        MapDataResource res1Resolved = repo1.resolveResource(res1, catProvider, "res1/layer1");
//
//        when(catProvider.resolveResource(same(res1))).then(invoc -> {
//            resolveLock.lock();
//            resolveBlocked.set(true);
//            resolveCondition.signal();
//            while (resolveBlocked.get()) {
//                resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//            }
//            resolveLock.unlock();
//            return res1Resolved;
//        });
//
//        activateExecutor();
//
//        waitForMainThreadToRun(() -> repo1.setValue(resourceOf(res1)));
//
//        resolveLock.lock();
//        while (!resolveBlocked.get()) {
//            resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//        }
//        resolveLock.unlock();
//
//        MapDataResource res1Again = new MapDataResource(res1.getUri(), repo1, res1.getContentTimestamp());
//
//        waitForMainThreadToRun(() -> repo1.setValue(resourceOf(res1Again)));
//
//        assertThat(manager.getResources(), is(emptyMap()));
//
//        resolveLock.lock();
//        resolveBlocked.set(false);
//        resolveCondition.signal();
//        resolveLock.unlock();
//
//        onMainLooper.assertThatWithin(timeout(), () -> manager.requireValue().getStatus(), is(Success));
//
//        deactivateExecutorAndWait();
//
//        verify(catProvider, times(1)).resolveResource(res1);
//        verify(catProvider, never()).resolveResource(same(res1Again));
//        verify(observer).onChanged(changeCaptor.capture());
//        assertThat(manager.requireValue().getContent(), is(mapOf(res1Resolved)));
//        assertThat(manager.requireValue().getContent(), hasEntry(is(res1.getUri()), sameInstance(res1Resolved)));
//    }
//
//    @Test
//    public void handlesRepositoryChangeThatRemovesResourceResolvedInPendingChangeConcurrently() throws InterruptedException, MapDataResolveException {
//        Lock resolveLock = new ReentrantLock();
//        AtomicBoolean resolveBlocked = new AtomicBoolean(false);
//        Condition resolveCondition = resolveLock.newCondition();
//        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
//        MapDataResource res1Resolved = repo1.resolveResource(res1, catProvider, "res1/layer1");
//        MapDataResource res2 = repo1.buildResource("res2.cat", null).finish();
//        MapDataResource res2Resolved = repo1.resolveResource(res2, catProvider, "res2/layer1");
//
//        when(catProvider.resolveResource(res1)).then(invoc -> {
//            resolveLock.lock();
//            resolveBlocked.set(true);
//            resolveCondition.signal();
//            while (resolveBlocked.get()) {
//                resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//            }
//            resolveLock.unlock();
//            return res1Resolved;
//        });
//        when(catProvider.resolveResource(res2)).thenReturn(res2Resolved);
//
//        activateExecutor();
//
//        repo1.postValue(resourceOf(res1, res2));
//
//        resolveLock.lock();
//        while (!resolveBlocked.get()) {
//            resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//        }
//        resolveLock.unlock();
//
//        waitForMainThreadToRun(() -> repo1.setValue(resourceOf(res2)));
//
//        resolveLock.lock();
//        resolveBlocked.set(false);
//        resolveCondition.signal();
//        resolveLock.unlock();
//
//        onMainLooper.assertThatWithin(timeout(), manager::getResources, is(mapOf(res2Resolved)));
//
//        deactivateExecutorAndWait();
//
//        verify(catProvider, times(1)).resolveResource(res1);
//        verify(catProvider, times(1)).resolveResource(res2);
//        verify(observer).onChanged(changeCaptor.capture());
//        assertThat(manager.requireValue().getContent(), is(mapOf(res2)));
//        assertThat(manager.requireValue().getContent(), hasEntry(is(res2.getUri()), sameInstance(res2Resolved)));
//    }
//
//    @Test
//    public void handlesRepositoryChangeThatRemovesResourceUnresolvedInPendingChangeConcurrently() throws InterruptedException, MapDataResolveException {
//        Lock resolveLock = new ReentrantLock();
//        AtomicBoolean resolveBlocked = new AtomicBoolean(false);
//        Condition resolveCondition = resolveLock.newCondition();
//        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
//        MapDataResource res1Resolved = repo1.resolveResource(res1, catProvider, "res1/layer1");
//        MapDataResource res2 = repo1.buildResource("res2.cat", null).finish();
//
//        when(catProvider.resolveResource(res1)).then(invoc -> {
//            resolveLock.lock();
//            resolveBlocked.set(true);
//            resolveCondition.signal();
//            while (resolveBlocked.get()) {
//                resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//            }
//            resolveLock.unlock();
//            return res1Resolved;
//        });
//
//        activateExecutor();
//
//        repo1.postValue(resourceOf(res1, res2));
//
//        resolveLock.lock();
//        while (!resolveBlocked.get()) {
//            resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//        }
//        resolveLock.unlock();
//
//        waitForMainThreadToRun(() -> repo1.setValue(resourceOf(res1)));
//
//        resolveLock.lock();
//        resolveBlocked.set(false);
//        resolveCondition.signal();
//        resolveLock.unlock();
//
//        onMainLooper.assertThatWithin(timeout(), manager::getResources, is(mapOf(res1Resolved)));
//
//        deactivateExecutorAndWait();
//
//        verify(catProvider, times(1)).resolveResource(res1);
//        verify(catProvider, never()).resolveResource(res2);
//        verify(observer).onChanged(changeCaptor.capture());
//        assertThat(manager.requireValue().getContent(), is(mapOf(res1)));
//        assertThat(manager.requireValue().getContent(), hasEntry(is(res1.getUri()), withValueSuppliedBy(MapDataResource::getResolved, sameInstance(res1Resolved.getResolved()))));
//    }
//
//    @Test
//    public void handlesRepositoryChangeThatRemovesAllResourcesWhileResolvingConcurrently() throws InterruptedException, MapDataResolveException {
//        Lock resolveLock = new ReentrantLock();
//        AtomicBoolean resolveBlocked = new AtomicBoolean(false);
//        Condition resolveCondition = resolveLock.newCondition();
//        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
//        MapDataResource res1Resolved = repo1.resolveResource(res1, catProvider, "res1/layer1");
//        MapDataResource res2 = repo1.buildResource("res2.cat", null).finish();
//
//        when(catProvider.resolveResource(res1)).then(invoc -> {
//            resolveLock.lock();
//            resolveBlocked.set(true);
//            resolveCondition.signal();
//            while (resolveBlocked.get()) {
//                resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//            }
//            resolveLock.unlock();
//            return res1Resolved;
//        });
//
//        activateExecutor();
//
//        repo1.postValue(resourceOf(res1, res2));
//
//        resolveLock.lock();
//        try {
//            while (!resolveBlocked.get()) {
//                resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//            }
//        }
//        finally {
//            resolveLock.unlock();
//        }
//
//        waitForMainThreadToRun(() -> repo1.setValue(resourceOf()));
//
//        resolveLock.lock();
//        resolveBlocked.set(false);
//        resolveCondition.signal();
//        resolveLock.unlock();
//
//        deactivateExecutorAndWait();
//
//        verify(catProvider, times(1)).resolveResource(res1);
//        verify(catProvider, never()).resolveResource(res2);
//        verify(observer, never()).onChanged(any());
//        assertThat(manager.requireValue().getContent(), is(emptyMap()));
//    }
//
//    @Test
//    public void handlesRepositoryChangeThatResolvesResourcesWhileResolvingConcurrently() throws MapDataResolveException, InterruptedException {
//        Lock resolveLock = new ReentrantLock();
//        AtomicBoolean resolveBlocked = new AtomicBoolean(false);
//        Condition resolveCondition = resolveLock.newCondition();
//        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
//        MapDataResource res1RepoResolved = repo1.resolveResource(res1, catProvider, "res1/fromRepo");
//        MapDataResource res1ProviderResolved = repo1.resolveResource(res1, catProvider, "res1/fromProvider");
//        MapDataResource res2 = repo1.buildResource("res2.dog", null).finish();
//        MapDataResource res2RepoResolved = repo1.resolveResource(res2, dogProvider, "res2/fromRepo");
//        MapDataResource res2ProviderResolved = repo1.resolveResource(res2, dogProvider, "res2/fromProvider");
//        Map<URI, MapDataResource> providerResolvedResources = mapOf(res1ProviderResolved, res2ProviderResolved);
//
//        Answer<MapDataResource> resolveAnswer = invoc -> {
//            resolveLock.lock();
//            resolveBlocked.set(true);
//            resolveCondition.signal();
//            while (resolveBlocked.get()) {
//                resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//            }
//            resolveLock.unlock();
//            MapDataResource unresolvedResource = invoc.getArgument(0);
//            return providerResolvedResources.get(unresolvedResource.getUri());
//        };
//
//        when(catProvider.resolveResource(any())).then(resolveAnswer);
//        when(dogProvider.resolveResource(any())).then(resolveAnswer);
//
//        activateExecutor();
//
//        repo1.postValue(resourceOf(res1, res2));
//
//        resolveLock.lock();
//        while (!resolveBlocked.get()) {
//            resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//        }
//        resolveLock.unlock();
//
//        waitForMainThreadToRun(() -> repo1.setValue(resourceOf(res1RepoResolved, res2)));
//
//        resolveLock.lock();
//        resolveBlocked.set(false);
//        resolveCondition.signal();
//        while (!resolveBlocked.get()) {
//            resolveCondition.await(timeout(), TimeUnit.MILLISECONDS);
//        }
//        resolveLock.unlock();
//
//        waitForMainThreadToRun(() -> repo1.setValue(resourceOf(res1RepoResolved, res2RepoResolved)));
//
//        resolveLock.lock();
//        resolveBlocked.set(false);
//        resolveCondition.signal();
//        resolveLock.unlock();
//
//        onMainLooper.assertThatWithin(timeout(), manager::getResources, is(mapOf(res1RepoResolved, res2RepoResolved)));
//
//        deactivateExecutorAndWait();
//
//        assertThat(manager.getResources(), hasEntry(is(res1.getUri()), sameInstance(res1RepoResolved)));
//        assertThat(manager.getResources(), hasEntry(is(res2.getUri()), sameInstance(res2RepoResolved)));
//        assertThat(manager.getResources().get(res1.getUri()).getResolved(), sameInstance(res1RepoResolved.getResolved()));
//        assertThat(manager.getResources().get(res2.getUri()).getResolved(), sameInstance(res2RepoResolved.getResolved()));
//        verify(catProvider).resolveResource(res1);
//        verify(dogProvider).resolveResource(res2);
//        verify(observer).onChanged(changeCaptor.capture());
//        // TODO: assert observed values
//    }
//
//    @Test
//    public void doesNotResolveMultipleChangesFromOneRepositoryConcurrently() throws MapDataResolveException, InterruptedException {
//        Lock resolveLock = new ReentrantLock();
//        AtomicBoolean firstResolveBlocked = new AtomicBoolean(false);
//        Condition firstResolveChanged = resolveLock.newCondition();
//
//        MapDataResource res1 = repo2.buildResource("res1.cat", null).finish();
//        MapDataResource res2 = repo2.buildResource("res2.cat", null).finish();
//        MapDataResource res3 = repo2.buildResource("res3.cat", null).finish();
//
//        when(catProvider.resolveResource(res1)).then(invocation -> {
//            resolveLock.lock();
//            firstResolveBlocked.set(true);
//            firstResolveChanged.signal();
//            while (firstResolveBlocked.get()) {
//                firstResolveChanged.await();
//            }
//            resolveLock.unlock();
//            return repo2.resolveResource(res1, catProvider);
//        });
//
//        activateExecutor();
//
//        waitForMainThreadToRun(() -> {
//            repo2.setValue(resourceOf(res1));
//            verify(executor, times(1)).execute(any());
//        });
//
//        resolveLock.lock();
//        while (!firstResolveBlocked.get()) {
//            firstResolveChanged.await();
//        }
//        resolveLock.unlock();
//
//        waitForMainThreadToRun(() -> {
//            repo2.setValue(resourceOf(res2));
//            verify(executor, times(1)).execute(any());
//        });
//
//        waitForMainThreadToRun(() -> {
//            repo2.setValue(resourceOf(res3));
//            verify(executor, times(1)).execute(any());
//        });
//
//        MapDataResource res3Resolved = repo2.resolveResource(res3, catProvider);
//        when(catProvider.resolveResource(res3)).thenReturn(res3Resolved);
//
//        resolveLock.lock();
//        firstResolveBlocked.set(false);
//        firstResolveChanged.signal();
//        resolveLock.unlock();
//
//        onMainLooper.assertThatWithin(timeout(), manager::getResources, hasEntry(is(res3.getUri()), sameInstance(res3Resolved)));
//
//        verify(catProvider).resolveResource(res1);
//        verify(catProvider).resolveResource(res3);
//        verify(catProvider, never()).resolveResource(res2);
//        assertThat(manager.getResources().keySet(), hasSize(1));
//        verify(observer).onChanged(changeCaptor.capture());
//        // TODO: assert observed values
//    }
}
