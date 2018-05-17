package mil.nga.giat.mage.map.cache;


import android.app.Application;
import android.arch.lifecycle.Lifecycle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationWithTimeout;

import java.io.File;
import java.io.IOException;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import mil.nga.giat.mage.test.AsyncTesting;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
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
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
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
import static org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MapDataManagerTest {

    // had to do this to make Mockito generate a different class for
    // two mock providers, because it uses the same class for two
    // separate mock instances of MapDataProvider directly, which is
    // a collision for MapLayerDescriptor.getDataType()
    static abstract class CatProvider implements MapDataProvider {}
    static abstract class DogProvider implements MapDataProvider {}

    private static class TestDirRepository extends MapDataRepository {

        private final File dir;
        private Status status = Status.Success;

        TestDirRepository(File dir) {
            this.dir = dir;
        }

        @NonNull
        @Override
        public String getId() {
            return TestDirRepository.class.getName() + "." + dir.getName();
        }

        @NonNull
        @Override
        public Status getStatus() {
            return status;
        }

        private void setStatus(Status status) {
            this.status = status;
        }

        @Nullable
        @Override
        public String getStatusMessage() {
            return status.name();
        }

        @Override
        public int getStatusCode() {
            return status.ordinal();
        }

        @Override
        public boolean ownsResource(URI resourceUri) {
            return false;
        }

        @Override
        public void refreshAvailableMapData(Map<URI, MapDataResource> existingResolved, Executor executor) {
            status = Status.Loading;
            executor.execute(() -> {

            });
        }

        @Override
        protected void postValue(Set<MapDataResource> value) {
            super.postValue(value);
        }

        @Override
        protected void setValue(Set<MapDataResource> value) {
            super.setValue(value);
        }

        private Runnable makeRefreshRunnable(Map<URI, MapDataResource> existingResolved) {
            return () -> {
                Set<MapDataResource> resources = new HashSet<>();
                File[] files = dir.listFiles();
                for (File file : files) {
                    URI uri = file.toURI();
                    MapDataResource resolved = existingResolved.get(uri);
                    if (resolved == null) {
                        resources.add(new MapDataResource(uri, this, file.lastModified()));
                    }
                    else {
                        resources.add(resolved);
                    }
                }
                postValue(resources);
            };
        }

        private File createFile(String name) {
            File file = new File(dir, name);
            try {
                file.createNewFile();
            }
            catch (IOException e) {
                throw new RuntimeException("error creating file: " + file, e);
            }
            return file;
        }

        private MapDataResource createResourceWithFileName(String name, MapDataResource.Resolved resolved) {
            File file = createFile(name);
            if (resolved == null) {
                return new MapDataResource(file.toURI(), this, file.lastModified());
            }
            else {
                return new MapDataResource(file.toURI(), this, file.lastModified(), resolved);
            }
        }

        private ResourceBuilder buildResource(String name, MapDataProvider provider) {
            return new ResourceBuilder(name, provider == null ? null : provider.getClass());
        }

        private MapDataResource updateContentTimestampOfResource(MapDataResource resource) {
            if (!resource.getRepositoryId().equals(resource.getRepositoryId())) {
                throw new Error("cannot update content timestamp of a resource from another repository: " + resource.getUri());
            }
            File content = new File(resource.getUri());
            if (!content.setLastModified(Math.max(System.currentTimeMillis(), content.lastModified() + 1000))) {
                throw new Error("failed to update content timestamp of resource file: " + content);
            }
            if (resource.getResolved() != null) {
                return resource.resolve(resource.getResolved(), content.lastModified());
            }
            else {
                return new MapDataResource(resource.getUri(), this, content.lastModified());
            }
        }

        private MapDataResource resolveResource(MapDataResource unresolved, MapDataProvider provider, String... layerNames) {
            File file = new File(unresolved.getUri());
            if (!file.getParentFile().equals(dir)) {
                throw new Error("cannot resolve resource from another repository: " + file);
            }
            String name = file.getName();
            return new ResourceBuilder(name, provider.getClass()).layers(layerNames).finish();
        }

        private MapDataResource updateContentTimestampAndLayers(MapDataResource resource, String... layerNames) {
            return updateContentTimestampOfResource(
                new ResourceBuilder(resource.getResolved().getName(), resource.getResolved().getType())
                    .layers(layerNames).finish());
        }

        private class ResourceBuilder {
            private final File file;
            private final URI uri;
            private final Class<? extends MapDataProvider> type;
            private final Set<MapLayerDescriptor> layers = new HashSet<>();

            private ResourceBuilder(String name, Class<? extends MapDataProvider> type) {
                file = createFile(name);
                uri = file.toURI();
                this.type = type;
            }

            private ResourceBuilder layers(String... names) {
                for (String name : names) {
                    layers.add(new TestLayerDescriptor(name, uri, type));
                }
                return this;
            }

            private MapDataResource finish() {
                if (type == null) {
                    return new MapDataResource(uri, TestDirRepository.this, file.lastModified());
                }
                return new MapDataResource(uri, TestDirRepository.this, file.lastModified(),
                    new MapDataResource.Resolved(file.getName(), type, Collections.unmodifiableSet(layers)));
            }
        }
    }

    static class TestLayerDescriptor extends MapLayerDescriptor {

        TestLayerDescriptor(String overlayName, URI resourceUri, Class<? extends MapDataProvider> type) {
            super(overlayName, resourceUri, type);
        }

        @Override
        public String toString() {
            return getLayerUri().toString();
        }
    }

    private static Set<MapDataResource> setOf(MapDataResource... caches) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(caches)));
    }

    private static Set<MapDataResource> setOfResources(Collection<MapDataResource> resources) {
        return Collections.unmodifiableSet(new HashSet<>(resources));
    }

    private static Set<MapLayerDescriptor> setOf(MapLayerDescriptor... layers) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(layers)));
    }

    private static Set<MapLayerDescriptor> setOfLayers(Collection<MapLayerDescriptor> layers) {
        return Collections.unmodifiableSet(new HashSet<>(layers));
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

    private static Set<MapLayerDescriptor> flattenLayers(MapDataResource... resources) {
        Set<MapLayerDescriptor> layers = new HashSet<>();
        for (MapDataResource resource : resources) {
            if (resource.getResolved() != null) {
                layers.addAll(resource.getResolved().getLayerDescriptors().values());
            }
        }
        return layers;
    }

    private static long oneSecond() {
        return 300000;
    }

    private static VerificationWithTimeout withinOneSecond() {
        return Mockito.timeout(oneSecond());
    }

    @Rule
    public TemporaryFolder testRoot = new TemporaryFolder();

    @Rule
    public TestName testName = new TestName();

    @Rule
    public AsyncTesting.MainLooperAssertion mainLooperAssertion = new AsyncTesting.MainLooperAssertion();

    private File cacheDir1;
    private File cacheDir2;
    private MapDataManager.Config config;
    private MapDataManager manager;
    private Executor executor;
    private ThreadPoolExecutor realExecutor;
    private TestDirRepository repo1;
    private TestDirRepository repo2;
    private MapDataProvider catProvider;
    private MapDataProvider dogProvider;
    private MapDataManager.MapDataListener listener;
    private ArgumentCaptor<MapDataManager.MapDataUpdate> updateCaptor = ArgumentCaptor.forClass(MapDataManager.MapDataUpdate.class);

    @Before
    public void configureCacheManager() throws Exception {

        Application context = Mockito.mock(Application.class);

        cacheDir1 = testRoot.newFolder("cache1");
        cacheDir2 = testRoot.newFolder("cache2");

        repo1 = new TestDirRepository(cacheDir1);
        repo2 = new TestDirRepository(cacheDir2);

        assertTrue(cacheDir1.isDirectory());
        assertTrue(cacheDir2.isDirectory());

        catProvider = mock(CatProvider.class);
        dogProvider = mock(DogProvider.class);

        executor = mock(Executor.class);

        when(catProvider.canHandleResource(any(MapDataResource.class))).thenAnswer(invocationOnMock -> {
            MapDataResource res = invocationOnMock.getArgument(0);
            return res.getUri().getPath().toLowerCase().endsWith(".cat");
        });
        when(dogProvider.canHandleResource(any(MapDataResource.class))).thenAnswer(invocationOnMock -> {
            MapDataResource res = invocationOnMock.getArgument(0);
            return res.getUri().getPath().toLowerCase().endsWith(".dog");
        });

        config = new MapDataManager.Config()
            .context(context)
            .executor(executor)
            .providers(catProvider, dogProvider)
            .repositories(repo1, repo2)
            .updatePermission(new MapDataManager.CreateUpdatePermission(){});
        initializeManager(config);
    }

    private void initializeManager(MapDataManager.Config config) {
        if (manager != null) {
            manager.destroy();
        }
        manager = new MapDataManager(config);
        listener = mock(MapDataManager.MapDataListener.class);
        manager.addUpdateListener(listener);
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
    public void isMockable() {
        mock(MapDataManager.class, withSettings()
            .useConstructor(new MapDataManager.Config().updatePermission(new MapDataManager.CreateUpdatePermission(){})));
    }

    @Test(expected = IllegalArgumentException.class)
    public void requiresUpdatePermission() {
        MapDataManager manager = new MapDataManager(new MapDataManager.Config()
            .repositories()
            .providers()
            .executor(executor)
            .updatePermission(null));
    }

    @Test(expected = Error.class)
    public void cannotCreateUpdateWithoutPermission() {
        manager.new MapDataUpdate(new MapDataManager.CreateUpdatePermission() {},
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    @Test
    public void resumesAfterCreated() {
        assertThat(manager.getLifecycle().getCurrentState(), is(Lifecycle.State.RESUMED));
    }

    @Test
    @UiThreadTest
    public void doesNotRefreshOnConstruction() throws InterruptedException {
        MapDataRepository repo1 = mock(MapDataRepository.class);
        MapDataRepository repo2 = mock(MapDataRepository.class);
        initializeManager();

        verify(repo1, never()).refreshAvailableMapData(any(), any());
        verify(repo2, never()).refreshAvailableMapData(any(), any());
        verify(executor, never()).execute(any());
        verify(listener, never()).onMapDataUpdated(any());
    }

    @Test
    public void doesNotResolveOnConstruction() throws MapDataResolveException {
        AsyncTesting.waitForMainThreadToRun(() -> {
            repo1.setValue(setOf(repo1.buildResource("do_not_resolve_yet.dog", null).finish()));

            reset(executor);

            initializeManager();

            verify(executor, never()).execute(any());
            verify(listener, never()).onMapDataUpdated(any());
        });

        AsyncTesting.waitForMainThreadToRun(() -> {});

        verify(catProvider, never()).resolveResource(any());
        verify(dogProvider, never()).resolveResource(any());
    }

    @Test
    @UiThreadTest
    public void hasNoDataAfterConstruction() throws URISyntaxException {
        MapDataResource initial1 = repo1.buildResource("a.dog", dogProvider).layers("a.dog.1", "a.dog.2").finish();
        MapDataResource initial2 = repo2.buildResource("a.cat", catProvider).layers("a.cat.1").finish();
        MapDataResource initial3 = repo2.buildResource("b.dog", dogProvider).finish();
        repo1.setValue(setOf(initial1));
        repo2.setValue(setOf(initial2, initial3));

        initializeManager();
        Map<URI, MapDataResource> resources = manager.getResources();
        Map<URI, MapLayerDescriptor> layers = manager.getLayers();

        assertThat(resources, is(emptyMap()));
        assertThat(layers, is(emptyMap()));
        verify(listener, never()).onMapDataUpdated(any());
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
        verify(listener, never()).onMapDataUpdated(any());
    }

    @Test
    @UiThreadTest
    public void addsLayersAndResourcesWhenRepositoryAddsNewResources() {
        MapDataResource repo1_1 = repo1.buildResource("repo1.1.dog", dogProvider).finish();
        MapDataResource repo1_2 = repo1.buildResource("repo1.2.cat", catProvider).layers("repo1.2.cat.1", "repo1.2.cat.2").finish();
        MapDataResource repo2_1 = repo2.buildResource("repo2.1.dog", dogProvider).layers("repo2.1.dog.1").finish();
        repo1.setValue(setOf(repo1_1, repo1_2));
        repo2.setValue(setOf(repo2_1));

        assertThat(manager.getResources(), is(mapOf(repo1_1, repo1_2, repo2_1)));
        assertThat(manager.getLayers(), is(mapOfLayers(repo1_1, repo1_2, repo2_1)));
        verify(listener, times(2)).onMapDataUpdated(updateCaptor.capture());
        MapDataManager.MapDataUpdate update = updateCaptor.getAllValues().get(0);
        assertThat(update.getAdded(), is(mapOf(repo1_1, repo1_2)));
        assertThat(update.getUpdated(), is(emptyMap()));
        assertThat(update.getRemoved(), is(emptyMap()));
        update = updateCaptor.getAllValues().get(1);
        assertThat(update.getAdded(), is(mapOf(repo2_1)));
        assertThat(update.getUpdated(), is(emptyMap()));
        assertThat(update.getRemoved(), is(emptyMap()));
    }

    @Test
    @UiThreadTest
    public void removesLayersAndResourcesWhenRepositoryRemovesResources() {
        MapDataResource doomed = repo1.buildResource("doomed.dog", dogProvider).layers("doomed.1").finish();
        repo1.setValue(setOf(doomed));

        assertThat(manager.getResources(), equalTo(mapOf(doomed)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(doomed)));

        repo1.setValue(Collections.emptySet());

        assertThat(manager.getResources(), equalTo(emptyMap()));
        assertThat(manager.getLayers(), equalTo(emptyMap()));
        verify(listener, times(2)).onMapDataUpdated(updateCaptor.capture());
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded(), equalTo(emptyMap()));
        assertThat(update.getUpdated(), equalTo(emptyMap()));
        assertThat(update.getRemoved(), equalTo(mapOf(doomed)));
    }

    @Test
    @UiThreadTest
    public void doesNotFireUpdateIfResourceDidNotChange() {
        MapDataResource mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1").finish();
        repo1.setValue(setOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));
        verify(listener).onMapDataUpdated(any());

        mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1").finish();
        repo1.setValue(setOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));
        verifyNoMoreInteractions(listener);
    }

    @Test
    @UiThreadTest
    public void firesUpdateWhenResourceContentTimestampChanges() {
        MapDataResource mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1").finish();
        repo1.setValue(setOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));

        long oldTimestamp = mod.getContentTimestamp();
        mod = repo1.updateContentTimestampOfResource(mod);
        repo1.setValue(setOf(mod));

        assertThat(mod.getContentTimestamp(), greaterThan(oldTimestamp));
        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getResources(), hasEntry(is(mod.getUri()), sameInstance(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));
        verify(listener, times(2)).onMapDataUpdated(updateCaptor.capture());
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded(), is(emptyMap()));
        assertThat(update.getUpdated(), is(mapOf(mod)));
        assertThat(update.getRemoved(), is(emptyMap()));
    }

    @Test
    @UiThreadTest
    public void addsLayersWhenRepositoryUpdatesResource() {
        MapDataResource mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1").finish();
        repo1.setValue(setOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));

        repo1.updateContentTimestampOfResource(mod);
        mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1", "mod.2").finish();
        repo1.setValue(setOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));
        verify(listener, times(2)).onMapDataUpdated(updateCaptor.capture());
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded(), equalTo(emptyMap()));
        assertThat(update.getUpdated(), equalTo(mapOf(mod)));
        assertThat(update.getRemoved(), equalTo(emptyMap()));
    }

    @Test
    @UiThreadTest
    public void removesLayersWhenRepositoryUpdatesResource() {
        MapDataResource mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1").finish();
        repo1.setValue(setOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));

        mod = repo1.updateContentTimestampOfResource(repo1.buildResource("mod.dog", dogProvider).finish());
        repo1.setValue(setOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(emptyMap()));
        verify(listener, times(2)).onMapDataUpdated(updateCaptor.capture());
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded(), equalTo(emptyMap()));
        assertThat(update.getUpdated(), equalTo(mapOf(mod)));
        assertThat(update.getRemoved(), equalTo(emptyMap()));
    }

    @Test
    @UiThreadTest
    public void doesNotFireUpdateUntilAllResourcesAreResolved() {
        MapDataResource unresolved = repo1.buildResource("unresolved.dog", null).finish();
        repo1.setValue(setOf(unresolved));

        assertThat(manager.getResources(), equalTo(emptyMap()));
        assertThat(manager.getLayers(), equalTo(emptyMap()));
        verify(listener, never()).onMapDataUpdated(any());
    }

    @Test
    @UiThreadTest
    public void doesNotResolveNewResourcesRepositoryResolved() throws MapDataResolveException {
        MapDataResource res1 = repo1.buildResource("res1.dog", dogProvider).finish();
        MapDataResource res2 = repo1.buildResource("res2.cat", catProvider).finish();
        repo1.setValue(setOf(res1, res2));

        assertThat(manager.getResources(), is(mapOf(res1, res2)));
        assertThat(manager.getResources(), hasEntry(is(res1.getUri()), sameInstance(res1)));
        assertThat(manager.getResources(), hasEntry(is(res2.getUri()), sameInstance(res2)));
        verify(listener).onMapDataUpdated(updateCaptor.capture());
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded(), is(mapOf(res1, res2)));
        assertThat(update.getAdded(), allOf(
            hasEntry(is(res1.getUri()), sameInstance(res1)),
            hasEntry(is(res2.getUri()), sameInstance(res2))));
        assertThat(update.getUpdated(), is(emptyMap()));
        assertThat(update.getRemoved(), is(emptyMap()));
        assertThat(manager.getResources(), allOf(
            is(mapOf(res1, res2)),
            hasEntry(is(res1.getUri()), sameInstance(res1)),
            hasEntry(is(res2.getUri()), sameInstance(res2))
        ));
        verify(executor, never()).execute(any());
        verify(catProvider, never()).resolveResource(any());
        verify(dogProvider, never()).resolveResource(any());
    }

    @Test
    @UiThreadTest
    public void doesNotResolveUpdatedResourcesTheRepositoryResolved() throws MapDataResolveException {
        MapDataResource res1 = repo1.buildResource("res1.dog", dogProvider).finish();
        MapDataResource res2 = repo1.buildResource("res2.cat", catProvider).finish();
        repo1.setValue(setOf(res1, res2));

        assertThat(manager.getResources(), is(mapOf(res1, res2)));
        assertThat(manager.getResources(), hasEntry(is(res1.getUri()), sameInstance(res1)));
        assertThat(manager.getResources(), hasEntry(is(res2.getUri()), sameInstance(res2)));
        assertThat(manager.getLayers(), is(emptyMap()));

        res1 = repo1.updateContentTimestampAndLayers(res1, "layer1", "layer2");
        repo1.setValue(setOf(res1, res2));

        verify(listener, times(2)).onMapDataUpdated(updateCaptor.capture());
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded(), is(emptyMap()));
        assertThat(update.getUpdated(), is(mapOf(res1)));
        assertThat(update.getUpdated(), hasEntry(is(res1.getUri()), sameInstance(res1)));
        assertThat(update.getRemoved(), is(emptyMap()));
        assertThat(manager.getResources(), allOf(
            is(mapOf(res1, res2)),
            hasEntry(is(res1.getUri()), sameInstance(res1)),
            hasEntry(is(res2.getUri()), sameInstance(res2))
        ));
        assertThat(manager.getLayers(), is(mapOfLayers(res1)));
        verify(executor, never()).execute(any());
        verify(catProvider, never()).resolveResource(any());
        verify(dogProvider, never()).resolveResource(any());
    }

    @Test
    public void asynchronouslyResolvesNewUnresolvedResources() throws MapDataResolveException, ExecutionException, InterruptedException {
        activateExecutor();

        MapDataResource res1 = repo1.buildResource("res1.dog", null).finish();
        MapDataResource res2 = repo1.buildResource("res2.cat", null).finish();
        MapDataResource res1Resolved = repo1.resolveResource(res1, dogProvider, "Res 1, Layer 1");
        MapDataResource res2Resolved = repo1.resolveResource(res2, catProvider, "Res 2, Layer 1", "Res 2, Layer 2");
        when(dogProvider.resolveResource(res1)).thenReturn(res1Resolved);
        when(catProvider.resolveResource(res2)).thenReturn(res2Resolved);

        repo1.postValue(setOf(res1, res2));

        assertThat(manager.getResources(), is(emptyMap()));
        assertThat(manager.getLayers(), is(emptyMap()));
        verify(dogProvider, withinOneSecond()).resolveResource(res1);
        verify(catProvider, withinOneSecond()).resolveResource(res2);
        verify(listener, withinOneSecond()).onMapDataUpdated(updateCaptor.capture());

        deactivateExecutorAndWait();

        assertThat(repo1.getValue(), containsInAnyOrder(sameInstance(res1Resolved), sameInstance(res2Resolved)));
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded(), is(mapOf(res1Resolved, res2Resolved)));
        assertThat(update.getAdded(), allOf(
            hasEntry(is(res1.getUri()), sameInstance(res1Resolved)),
            hasEntry(is(res2.getUri()), sameInstance(res2Resolved))));
        assertThat(update.getUpdated(), is(emptyMap()));
        assertThat(update.getRemoved(), is(emptyMap()));

        AsyncTesting.waitForMainThreadToRun(() -> {
            assertThat(manager.getResources(), is(mapOf(res1Resolved, res2Resolved)));
            assertThat(manager.getResources(), allOf(
                hasEntry(is(res1.getUri()), sameInstance(res1Resolved)),
                hasEntry(is(res2.getUri()), sameInstance(res2Resolved))));
            assertThat(manager.getLayers(), is(mapOfLayers(res1Resolved, res2Resolved)));
        });
    }

    @Test
    public void asynchronouslyResolvesUpdatedUnresolvedResources() throws MapDataResolveException {
        MapDataResource res1 = repo1.buildResource("res1.dog", dogProvider).finish();
        MapDataResource res2 = repo1.buildResource("res2.cat", catProvider).finish();

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo1.setValue(setOf(res1, res2));

            assertThat(manager.getResources(), is(mapOf(res1, res2)));
            assertThat(manager.getLayers(), is(emptyMap()));
        });

        verify(executor, never()).execute(any());
        verify(dogProvider, never()).resolveResource(any());
        verify(catProvider, never()).resolveResource(any());
        verify(listener).onMapDataUpdated(updateCaptor.capture());

        activateExecutor();

        repo1.updateContentTimestampOfResource(res1);
        repo1.updateContentTimestampOfResource(res2);
        MapDataResource res1Unresolved = repo1.buildResource(res1.getResolved().getName(), null).finish();
        MapDataResource res2Unresolved = repo1.buildResource(res2.getResolved().getName(), null).finish();
        MapDataResource res1Resolved = repo1.resolveResource(res1Unresolved, dogProvider, "R1/L1");
        MapDataResource res2Resolved = repo1.resolveResource(res2Unresolved, catProvider, "R2/L1", "R2/L2");
        when(dogProvider.resolveResource(res1Unresolved)).thenReturn(res1Resolved);
        when(catProvider.resolveResource(res2Unresolved)).thenReturn(res2Resolved);

        repo1.postValue(setOf(res1Unresolved, res2Unresolved));

        verify(dogProvider, withinOneSecond()).resolveResource(res1Unresolved);
        verify(catProvider, withinOneSecond()).resolveResource(res2Unresolved);
        verify(listener, withinOneSecond().times(2)).onMapDataUpdated(updateCaptor.capture());

        deactivateExecutorAndWait();

        assertThat(repo1.getValue(), containsInAnyOrder(sameInstance(res1Resolved), sameInstance(res2Resolved)));
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded(), is(emptyMap()));
        assertThat(update.getUpdated(), is(mapOf(res1Resolved, res2Resolved)));
        assertThat(update.getUpdated(), allOf(
            hasEntry(is(res1.getUri()), sameInstance(res1Resolved)),
            hasEntry(is(res2.getUri()), sameInstance(res2Resolved))));
        assertThat(update.getRemoved(), is(emptyMap()));

        AsyncTesting.waitForMainThreadToRun(() -> {
            assertThat(manager.getResources(), is(mapOf(res1Resolved, res2Resolved)));
            assertThat(manager.getResources(), allOf(
                hasEntry(is(res1.getUri()), sameInstance(res1Resolved)),
                hasEntry(is(res2.getUri()), sameInstance(res2Resolved))));
            assertThat(manager.getLayers(), is(mapOfLayers(res1Resolved, res2Resolved)));
        });
    }

    @Test
    public void resolvesUpdatedUnresolvedResourcesWithExistingDataWhenContentTimestampDidNotChange() throws MapDataResolveException, InterruptedException {
        MapDataResource res1Resolved = repo1.buildResource("res1.dog", dogProvider).layers("R1/L1").finish();

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo1.setValue(setOf(res1Resolved));

            assertThat(manager.getResources(), is(mapOf(res1Resolved)));
            assertThat(manager.getLayers(), is(mapOfLayers(res1Resolved)));
        });

        verify(executor, never()).execute(any());
        verify(dogProvider, never()).resolveResource(any());
        verify(catProvider, never()).resolveResource(any());
        verify(listener).onMapDataUpdated(any());

        activateExecutor();

        MapDataResource res1Unresolved = repo1.buildResource(res1Resolved.getResolved().getName(), null).finish();

        assertThat(res1Unresolved.getContentTimestamp(), is(res1Resolved.getContentTimestamp()));

        repo1.postValue(setOf(res1Unresolved));

        mainLooperAssertion.assertOnMainThreadThatWithin(oneSecond(), manager::getResources,
            hasEntry(is(res1Resolved.getUri()),
                allOf(
                    not(sameInstance(res1Resolved)),
                    isResolved(res1Resolved.getResolved()))));

        deactivateExecutorAndWait();

        verify(dogProvider, never()).resolveResource(any());
        verify(catProvider, never()).resolveResource(any());

        AsyncTesting.waitForMainThreadToRun(() -> {
            assertThat(repo1.getValue(), is(setOf(res1Resolved)));
            assertThat(repo1.getValue(), hasItem(isResolved(res1Resolved.getResolved())));
            verifyNoMoreInteractions(listener);
            assertThat(manager.getResources(), is(mapOf(res1Resolved)));
            assertThat(manager.getResources(), hasEntry(is(res1Resolved.getUri()), not(sameInstance(res1Resolved))));
            assertThat(manager.getResources().get(res1Resolved.getUri()).getResolved(), sameInstance(res1Resolved.getResolved()));
            assertThat(manager.getLayers(), is(mapOfLayers(res1Resolved)));
        });
    }

    @Test
    public void resolvesResourcesAscendingLexicalOrderOfUris() throws MapDataResolveException, InterruptedException {
        MapDataResource res1 = repo1.buildResource("a.cat", null).finish();
        MapDataResource res2 = repo1.buildResource("a.dog", null).finish();
        MapDataResource res3 = repo1.buildResource("b.dog", null).finish();
        List<MapDataResource> outOfOrder = Arrays.asList(res3, res1, res2);

        when(catProvider.resolveResource(res1)).thenReturn(repo1.resolveResource(res1, catProvider));
        when(dogProvider.resolveResource(res2)).thenReturn(repo1.resolveResource(res2, dogProvider));
        when(dogProvider.resolveResource(res3)).thenReturn(repo1.resolveResource(res3, dogProvider));

        Set<MapDataResource> resources = new TreeSet<MapDataResource>((a, b) -> outOfOrder.indexOf(a) - outOfOrder.indexOf(b));
        resources.addAll(outOfOrder);

        int i = 0;
        for (MapDataResource resourceInSet : resources) {
            assertThat(resourceInSet, sameInstance(outOfOrder.get(i++)));
        }

        activateExecutor();

        repo1.postValue(resources);

        mainLooperAssertion.assertOnMainThreadThatWithin(oneSecond(), manager::getResources, is(mapOf(res1, res2, res3)));

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

    @Test
    public void handlesRepositoryChangeThatAddsResourcesWhileResolvingConcurrently() throws MapDataResolveException, InterruptedException {
        Lock resolveLock = new ReentrantLock();
        AtomicBoolean res1ResolveBegan = new AtomicBoolean(false);
        Condition res1ResolveBeganCondition = resolveLock.newCondition();
        AtomicBoolean res1ResolveBlocking = new AtomicBoolean(true);
        Condition res1ResolveBlockingCondition = resolveLock.newCondition();
        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
        MapDataResource res1Resolved = repo1.resolveResource(res1, catProvider, "res1/layer1");
        MapDataResource res2 = repo1.buildResource("res2.cat", null).finish();
        MapDataResource res2Resolved = repo1.resolveResource(res2, catProvider, "res2/layer2");

        when(catProvider.resolveResource(res1)).then(invoc -> {
            resolveLock.lock();
            res1ResolveBegan.set(true);
            res1ResolveBeganCondition.signal();
            resolveLock.unlock();
            resolveLock.lock();
            while (res1ResolveBlocking.get()) {
                res1ResolveBlockingCondition.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
            resolveLock.unlock();
            return res1Resolved;
        });

        activateExecutor();

        repo1.postValue(setOf(res1));
        resolveLock.lock();
        while (!res1ResolveBegan.get()) {
            res1ResolveBeganCondition.await(oneSecond(), TimeUnit.MILLISECONDS);
        }
        resolveLock.unlock();

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo1.setValue(setOf(res1, res2));
        });

        when(catProvider.resolveResource(res2)).thenReturn(res2Resolved);

        resolveLock.lock();
        res1ResolveBlocking.set(false);
        res1ResolveBlockingCondition.signal();
        resolveLock.unlock();

        mainLooperAssertion.assertOnMainThreadThatWithin(1000, manager::getResources, is(mapOf(res1, res2)));

        deactivateExecutorAndWait();

        AsyncTesting.waitForMainThreadToRun(() -> {
            verify(listener).onMapDataUpdated(updateCaptor.capture());
            MapDataManager.MapDataUpdate update = updateCaptor.getValue();
            assertThat(update.getAdded(), is(mapOf(res1, res2)));
            assertThat(update.getUpdated(), is(emptyMap()));
            assertThat(update.getRemoved(), is(emptyMap()));
            assertThat(manager.getResources(), hasEntry(is(res1.getUri()), equalTo(res1Resolved)));
            assertThat(manager.getResources().get(res1.getUri()).getResolved(), sameInstance(res1Resolved.getResolved()));
            assertThat(manager.getResources(), hasEntry(is(res2.getUri()), sameInstance(res2Resolved)));
        });
    }

    @Test
    public void handlesRepositoryChangeThatUpdatesResourcesWhileResolvingConcurrently() throws MapDataResolveException, InterruptedException {
        Lock resolveLock = new ReentrantLock();
        AtomicReference<MapDataResource> lastResolve = new AtomicReference<>();
        Condition lastResolveChanged = resolveLock.newCondition();
        Condition lastResolveRetrieved = resolveLock.newCondition();
        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
        MapDataResource res1Resolved = repo1.resolveResource(res1, catProvider, "res1/layer1");
        MapDataResource res2 = repo1.buildResource("res2.cat", null).finish();
        MapDataResource res2Resolved = repo1.resolveResource(res2, catProvider, "res2/layer1");
        Map<URI, MapDataResource> unresolvedResources = new HashMap<>(mapOf(res1, res2));
        Map<URI, MapDataResource> resolvedResources = new HashMap<>(mapOf(res1Resolved, res2Resolved));
        List<MapDataResource> resolveOrder = new ArrayList<>();

        when(catProvider.resolveResource(any())).then(invoc -> {
            resolveLock.lock();
            MapDataResource unresolved = invoc.getArgument(0);
            MapDataResource resolved = resolvedResources.get(unresolved.getUri());
            lastResolve.set(resolved);
            lastResolveChanged.signal();
            while (lastResolve.get() != null) {
                lastResolveRetrieved.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
            resolveLock.unlock();
            return resolved;
        });

        activateExecutor();

        repo1.postValue(setOf(res1, res2));

        resolveLock.lock();
        try {
            while (lastResolve.get() == null) {
                lastResolveChanged.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
            resolveOrder.add(lastResolve.getAndSet(null));
            lastResolveRetrieved.signal();
            while (lastResolve.get() == null) {
                lastResolveChanged.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
            // don't set null value and signal lastResolveRetrieved to keep the second
            // resolve waiting while posting another repository change concurrently
            resolveOrder.add(lastResolve.get());
        }
        finally {
            resolveLock.unlock();
        }

        long firstResolvedTimestamp = resolveOrder.get(0).getContentTimestamp();
        MapDataResource firstResolvedUpdated = repo1.updateContentTimestampOfResource(resolveOrder.get(0));
        MapDataResource firstUnresolvedUpdated = new MapDataResource(firstResolvedUpdated.getUri(), repo1, firstResolvedUpdated.getContentTimestamp());
        unresolvedResources.put(firstUnresolvedUpdated.getUri(), firstUnresolvedUpdated);
        resolvedResources.put(firstResolvedUpdated.getUri(), firstResolvedUpdated);

        assertThat(firstResolvedUpdated.getContentTimestamp(), greaterThan(firstResolvedTimestamp));

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo1.setValue(new HashSet<>(unresolvedResources.values()));
        });

        InOrder resolveVerification = inOrder(catProvider);
        resolveVerification.verify(catProvider).resolveResource(resolveOrder.get(0));
        resolveVerification.verify(catProvider).resolveResource(resolveOrder.get(1));
        resolveVerification.verifyNoMoreInteractions();

        resolveLock.lock();
        try {
            MapDataResource waitingToReturn = resolveOrder.get(1);
            if (!lastResolve.compareAndSet(waitingToReturn, null)) {
                fail("unexpected last resolved resource: " + lastResolve.get());
            }
            lastResolveRetrieved.signal();
            while (lastResolve.get() == null) {
                lastResolveChanged.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
            resolveOrder.add(lastResolve.getAndSet(null));
            lastResolveRetrieved.signal();
        }
        finally {
            resolveLock.unlock();
        }

        resolveVerification.verify(catProvider).resolveResource(firstUnresolvedUpdated);

        mainLooperAssertion.assertOnMainThreadThatWithin(oneSecond(), manager::getResources, is(mapOf(res1Resolved, res2Resolved)));

        deactivateExecutorAndWait();

        assertThat(resolveOrder, hasSize(3));
        verify(catProvider, times(2)).resolveResource(resourceWithUri(resolveOrder.get(0).getUri()));
        verify(catProvider, times(1)).resolveResource(resourceWithUri(resolveOrder.get(1).getUri()));
        verify(listener).onMapDataUpdated(updateCaptor.capture());
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded(), is(resolvedResources));
        assertThat(update.getUpdated(), is(emptyMap()));
        assertThat(update.getRemoved(), is(emptyMap()));
        assertThat(manager.getResources().get(res1.getUri()).getResolved(), sameInstance(res1Resolved.getResolved()));
        assertThat(manager.getResources().get(res2.getUri()).getResolved(), sameInstance(res2Resolved.getResolved()));
    }

    @Test
    public void handlesRepositoryChangeWithUnresolvedResourceThatWasResolvedInPendingChangeConcurrently() throws InterruptedException, MapDataResolveException {
        Lock resolveLock = new ReentrantLock();
        AtomicBoolean resolveBlocked = new AtomicBoolean(false);
        Condition resolveCondition = resolveLock.newCondition();
        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
        MapDataResource res1Resolved = repo1.resolveResource(res1, catProvider, "res1/layer1");

        when(catProvider.resolveResource(same(res1))).then(invoc -> {
            resolveLock.lock();
            resolveBlocked.set(true);
            resolveCondition.signal();
            while (resolveBlocked.get()) {
                resolveCondition.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
            resolveLock.unlock();
            return res1Resolved;
        });

        activateExecutor();

        repo1.postValue(setOf(res1));

        resolveLock.lock();
        try {
            while (!resolveBlocked.get()) {
                resolveCondition.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
        }
        finally {
            resolveLock.unlock();
        }

        MapDataResource res1Again = new MapDataResource(res1.getUri(), repo1, res1.getContentTimestamp());

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo1.setValue(setOf(res1Again));
        });

        assertThat(manager.getResources(), is(emptyMap()));

        resolveLock.lock();
        try {
            resolveBlocked.set(false);
            resolveCondition.signal();
        }
        finally {
            resolveLock.unlock();
        }

        mainLooperAssertion.assertOnMainThreadThatWithin(oneSecond(), manager::getResources, is(mapOf(res1Resolved)));

        deactivateExecutorAndWait();

        verify(catProvider, times(1)).resolveResource(res1);
        verify(catProvider, never()).resolveResource(same(res1Again));
        verify(listener).onMapDataUpdated(updateCaptor.capture());
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded(), is(mapOf(res1Resolved)));
        MapDataResource finalResource = update.getAdded().get(res1.getUri());
        assertThat(finalResource, not(sameInstance(res1Resolved)));
        assertThat(finalResource.getResolved(), sameInstance(res1Resolved.getResolved()));
        assertThat(update.getUpdated(), is(emptyMap()));
        assertThat(update.getRemoved(), is(emptyMap()));
        assertThat(manager.getResources(), hasEntry(is(res1.getUri()), sameInstance(finalResource)));
    }

    @Test
    public void handlesRepositoryChangeThatRemovesResourceResolvedInPendingChangeConcurrently() throws InterruptedException, MapDataResolveException {
        Lock resolveLock = new ReentrantLock();
        AtomicBoolean resolveBlocked = new AtomicBoolean(false);
        Condition resolveCondition = resolveLock.newCondition();
        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
        MapDataResource res1Resolved = repo1.resolveResource(res1, catProvider, "res1/layer1");
        MapDataResource res2 = repo1.buildResource("res2.cat", null).finish();
        MapDataResource res2Resolved = repo1.resolveResource(res2, catProvider, "res2/layer1");

        when(catProvider.resolveResource(res1)).then(invoc -> {
            resolveLock.lock();
            resolveBlocked.set(true);
            resolveCondition.signal();
            while (resolveBlocked.get()) {
                resolveCondition.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
            resolveLock.unlock();
            return res1Resolved;
        });
        when(catProvider.resolveResource(res2)).thenReturn(res2Resolved);

        activateExecutor();

        repo1.postValue(setOf(res1, res2));

        resolveLock.lock();
        try {
            while (!resolveBlocked.get()) {
                resolveCondition.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
        }
        finally {
            resolveLock.unlock();
        }

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo1.setValue(setOf(res2));
        });

        resolveLock.lock();
        try {
            resolveBlocked.set(false);
            resolveCondition.signal();
        }
        finally {
            resolveLock.unlock();
        }

        mainLooperAssertion.assertOnMainThreadThatWithin(oneSecond(), manager::getResources, is(mapOf(res2Resolved)));

        deactivateExecutorAndWait();

        verify(catProvider, times(1)).resolveResource(res1);
        verify(catProvider, times(1)).resolveResource(res2);
        verify(listener).onMapDataUpdated(updateCaptor.capture());
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded(), is(mapOf(res2)));
        assertThat(update.getUpdated(), is(emptyMap()));
        assertThat(update.getRemoved(), is(emptyMap()));
        assertThat(manager.getResources(), is(mapOf(res2)));
        assertThat(manager.getResources(), hasEntry(is(res2.getUri()), sameInstance(res2Resolved)));
    }

    @Test
    public void handlesRepositoryChangeThatRemovesResourceUnresolvedInPendingChangeConcurrently() throws InterruptedException, MapDataResolveException {
        Lock resolveLock = new ReentrantLock();
        AtomicBoolean resolveBlocked = new AtomicBoolean(false);
        Condition resolveCondition = resolveLock.newCondition();
        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
        MapDataResource res1Resolved = repo1.resolveResource(res1, catProvider, "res1/layer1");
        MapDataResource res2 = repo1.buildResource("res2.cat", null).finish();

        when(catProvider.resolveResource(res1)).then(invoc -> {
            resolveLock.lock();
            resolveBlocked.set(true);
            resolveCondition.signal();
            while (resolveBlocked.get()) {
                resolveCondition.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
            resolveLock.unlock();
            return res1Resolved;
        });

        activateExecutor();

        repo1.postValue(setOf(res1, res2));

        resolveLock.lock();
        try {
            while (!resolveBlocked.get()) {
                resolveCondition.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
        }
        finally {
            resolveLock.unlock();
        }

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo1.setValue(setOf(res1));
        });

        resolveLock.lock();
        try {
            resolveBlocked.set(false);
            resolveCondition.signal();
        }
        finally {
            resolveLock.unlock();
        }

        mainLooperAssertion.assertOnMainThreadThatWithin(oneSecond(), manager::getResources, is(mapOf(res1Resolved)));

        deactivateExecutorAndWait();

        verify(catProvider, times(1)).resolveResource(res1);
        verify(catProvider, never()).resolveResource(res2);
        verify(listener).onMapDataUpdated(updateCaptor.capture());
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded(), is(mapOf(res1)));
        assertThat(update.getUpdated(), is(emptyMap()));
        assertThat(update.getRemoved(), is(emptyMap()));
        assertThat(manager.getResources(), is(mapOf(res1)));
        assertThat(manager.getResources().get(res1.getUri()).getResolved(), sameInstance(res1Resolved.getResolved()));
    }

    @Test
    public void handlesRepositoryChangeThatRemovesAllResourcesWhileResolvingConcurrently() throws InterruptedException, MapDataResolveException {
        Lock resolveLock = new ReentrantLock();
        AtomicBoolean resolveBlocked = new AtomicBoolean(false);
        Condition resolveCondition = resolveLock.newCondition();
        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
        MapDataResource res1Resolved = repo1.resolveResource(res1, catProvider, "res1/layer1");
        MapDataResource res2 = repo1.buildResource("res2.cat", null).finish();

        when(catProvider.resolveResource(res1)).then(invoc -> {
            resolveLock.lock();
            resolveBlocked.set(true);
            resolveCondition.signal();
            while (resolveBlocked.get()) {
                resolveCondition.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
            resolveLock.unlock();
            return res1Resolved;
        });

        activateExecutor();

        repo1.postValue(setOf(res1, res2));

        resolveLock.lock();
        try {
            while (!resolveBlocked.get()) {
                resolveCondition.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
        }
        finally {
            resolveLock.unlock();
        }

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo1.setValue(emptySet());
        });

        resolveLock.lock();
        try {
            resolveBlocked.set(false);
            resolveCondition.signal();
        }
        finally {
            resolveLock.unlock();
        }

        deactivateExecutorAndWait();

        verify(catProvider, times(1)).resolveResource(res1);
        verify(catProvider, never()).resolveResource(res2);
        verify(listener, never()).onMapDataUpdated(any());
        assertThat(manager.getResources(), is(emptyMap()));
    }

    @Test
    public void handlesRepositoryChangeThatResolvesResourcesWhileResolvingConcurrently() throws MapDataResolveException, InterruptedException {
        Lock resolveLock = new ReentrantLock();
        AtomicBoolean resolveBlocked = new AtomicBoolean(false);
        Condition resolveCondition = resolveLock.newCondition();
        MapDataResource res1 = repo1.buildResource("res1.cat", null).finish();
        MapDataResource res1RepoResolved = repo1.resolveResource(res1, catProvider, "res1/fromRepo");
        MapDataResource res1ProviderResolved = repo1.resolveResource(res1, catProvider, "res1/fromProvider");
        MapDataResource res2 = repo1.buildResource("res2.dog", null).finish();
        MapDataResource res2RepoResolved = repo1.resolveResource(res2, dogProvider, "res2/fromRepo");
        MapDataResource res2ProviderResolved = repo1.resolveResource(res2, dogProvider, "res2/fromProvider");
        Map<URI, MapDataResource> providerResolvedResources = mapOf(res1ProviderResolved, res2ProviderResolved);

        Answer<MapDataResource> resolveAnswer = invoc -> {
            resolveLock.lock();
            resolveBlocked.set(true);
            resolveCondition.signal();
            while (resolveBlocked.get()) {
                resolveCondition.await(oneSecond(), TimeUnit.MILLISECONDS);
            }
            resolveLock.unlock();
            MapDataResource unresolvedResource = invoc.getArgument(0);
            MapDataResource resolvedResource = providerResolvedResources.get(unresolvedResource.getUri());
            return resolvedResource;
        };

        when(catProvider.resolveResource(any())).then(resolveAnswer);
        when(dogProvider.resolveResource(any())).then(resolveAnswer);

        activateExecutor();

        repo1.postValue(setOf(res1, res2));

        resolveLock.lock();
        while (!resolveBlocked.get()) {
            resolveCondition.await(oneSecond(), TimeUnit.MILLISECONDS);
        }
        resolveLock.unlock();

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo1.setValue(setOf(res1RepoResolved, res2));
        });

        resolveLock.lock();
        resolveBlocked.set(false);
        resolveCondition.signal();
        while (!resolveBlocked.get()) {
            resolveCondition.await(oneSecond(), TimeUnit.MILLISECONDS);
        }
        resolveLock.unlock();

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo1.setValue(setOf(res1RepoResolved, res2RepoResolved));
        });

        resolveLock.lock();
        resolveBlocked.set(false);
        resolveCondition.signal();
        resolveLock.unlock();

        mainLooperAssertion.assertOnMainThreadThatWithin(oneSecond(), manager::getResources, is(mapOf(res1RepoResolved, res2RepoResolved)));

        deactivateExecutorAndWait();

        assertThat(manager.getResources(), hasEntry(is(res1.getUri()), sameInstance(res1RepoResolved)));
        assertThat(manager.getResources(), hasEntry(is(res2.getUri()), sameInstance(res2RepoResolved)));
        assertThat(manager.getResources().get(res1.getUri()).getResolved(), sameInstance(res1RepoResolved.getResolved()));
        assertThat(manager.getResources().get(res2.getUri()).getResolved(), sameInstance(res2RepoResolved.getResolved()));
        verify(catProvider).resolveResource(res1);
        verify(dogProvider).resolveResource(res2);
        verify(listener).onMapDataUpdated(updateCaptor.capture());
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded(), is(mapOf(res1RepoResolved, res2RepoResolved)));
        assertThat(update.getAdded(), hasEntry(is(res1.getUri()), sameInstance(res1RepoResolved)));
        assertThat(update.getAdded(), hasEntry(is(res2.getUri()), sameInstance(res2RepoResolved)));
    }

    @Test
    public void doesNotResolveMultipleChangesFromOneRepositoryConcurrently() throws MapDataResolveException, InterruptedException {
        Lock resolveLock = new ReentrantLock();
        AtomicBoolean firstResolveBlocked = new AtomicBoolean(false);
        Condition firstResolveChanged = resolveLock.newCondition();

        MapDataResource res1 = repo2.buildResource("res1.cat", null).finish();
        MapDataResource res2 = repo2.buildResource("res2.cat", null).finish();
        MapDataResource res3 = repo2.buildResource("res3.cat", null).finish();

        when(catProvider.resolveResource(res1)).then(invocation -> {
            resolveLock.lock();
            try {
                firstResolveBlocked.set(true);
                firstResolveChanged.signal();
                while (firstResolveBlocked.get()) {
                    firstResolveChanged.await();
                }
                return repo2.resolveResource(res1, catProvider);
            }
            finally {
                resolveLock.unlock();
            }
        });

        activateExecutor();

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo2.setValue(setOf(res1));
            verify(executor, times(1)).execute(any());
        });

        resolveLock.lock();
        try {
            while (!firstResolveBlocked.get()) {
                firstResolveChanged.await();
            }
        }
        finally {
            resolveLock.unlock();
        }

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo2.setValue(setOf(res2));
            verify(executor, times(1)).execute(any());
        });

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo2.setValue(setOf(res3));
            verify(executor, times(1)).execute(any());
        });

        MapDataResource res3Resolved = repo2.resolveResource(res3, catProvider);
        when(catProvider.resolveResource(res3)).thenReturn(res3Resolved);

        resolveLock.lock();
        try {
            firstResolveBlocked.set(false);
            firstResolveChanged.signal();
        }
        finally {
            resolveLock.unlock();
        }

        mainLooperAssertion.assertOnMainThreadThatWithin(oneSecond(), manager::getResources, hasEntry(is(res3.getUri()), sameInstance(res3Resolved)));

        verify(catProvider).resolveResource(res1);
        verify(catProvider).resolveResource(res3);
        verify(catProvider, never()).resolveResource(res2);
        assertThat(manager.getResources().keySet(), hasSize(1));
        verify(listener).onMapDataUpdated(updateCaptor.capture());
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        assertThat(update.getAdded().keySet(), hasSize(1));
        assertThat(update.getAdded(), hasEntry(is(res3.getUri()), sameInstance(res3Resolved)));
        assertThat(update.getUpdated(), is(emptyMap()));
        assertThat(update.getRemoved(), is(emptyMap()));
    }

    @Test
    public void stopsResolvingChangeWhenSameRepositoryFiresChangeConcurrently() {
        fail("unimplemented");
    }

    @Test
    public void providesResolvedResourcesToRepositoryForRefresh() {
        fail("unimplemented");
    }

    /**
     * TODO: evaluate whether these next three are the way we want this to behave.
     * LiveData.setData() will notify observers serially, so should the MapDataUpdate
     * events be collated or just dispatch updates always as they come, regardless of
     * resolved state and other refreshing repositories? should MapDataManager let
     * repositories handle whether to execute a refresh when one is already in progress?
     * maybe it's all just fine.
     */

    @Test
    public void doesNotRefreshRepositoryThatIsAlreadyRefreshing() {
        fail("unimplemented");
    }


    private static final MapDataRepository MATCHER_REPO = new MapDataRepository() {

        @NonNull
        @Override
        public String getId() {
            return MapDataManagerTest.class.getCanonicalName() + ".MATCHER_REPO";
        }

        @Override
        public boolean ownsResource(URI resourceUri) {
            return false;
        }

        @Override
        public void refreshAvailableMapData(Map<URI, MapDataResource> resolvedResources, Executor executor) {

        }

        @NonNull
        @Override
        public Status getStatus() {
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

    private static final MapDataResource MATCHER_RESOURCE = new MapDataResource(URI.create("test:matcher_resource"), MATCHER_REPO, 0);

    private static MapDataResource resourceWithUri(URI expected) {
        MapDataResourceUriMatcher matcher = new MapDataResourceUriMatcher(expected);
        mockingProgress().getArgumentMatcherStorage().reportMatcher(matcher);
        return MATCHER_RESOURCE;
    }

    private static class MapDataResourceUriMatcher implements ArgumentMatcher<MapDataResource> {

        private final URI expected;

        private MapDataResourceUriMatcher(URI expected) {
            this.expected = expected;
        }

        @Override
        public boolean matches(MapDataResource argument) {
            return expected.equals(argument.getUri());
        }
    }

    private static class MapDataResourceIsResolvedMatcher extends TypeSafeMatcher<MapDataResource> {

        private final MapDataResource.Resolved resolved;

        private MapDataResourceIsResolvedMatcher(MapDataResource.Resolved resolved) {
            this.resolved = resolved;
        }

        @Override
        protected boolean matchesSafely(MapDataResource item) {
            return item.getResolved() == resolved;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("a MapDataResource resolved with " + resolved);
        }

        @Override
        protected void describeMismatchSafely(MapDataResource item, Description mismatchDescription) {
            super.describeMismatchSafely(item, mismatchDescription);
            if (item.getResolved() == null) {
                mismatchDescription.appendText("was not resolved");
            }
            else {
                mismatchDescription.appendText("resolved with " + item.getResolved());
            }
        }
    }

    private static Matcher<MapDataResource> isResolved(MapDataResource.Resolved resolved) {
        return new MapDataResourceIsResolvedMatcher(resolved);
    }
}
