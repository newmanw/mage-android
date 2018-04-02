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
import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import mil.nga.giat.mage.test.AsyncTesting;

import static java.util.Collections.emptyMap;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
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
            return resource.resolve(resource.getResolved(), content.lastModified());
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
        return 1000;
    }

    private static VerificationWithTimeout withinOneSecond() {
        return Mockito.timeout(1000);
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
            if (!localRef.awaitTermination(300, TimeUnit.SECONDS)) {
                throw new Error(testMethodName + " timed out waiting for thread pool termination");
            }
        }
        catch (InterruptedException e) {
            throw new Error(testMethodName + " interrupted waiting for thread pool termination");
        }
    }

    private void activateExecutor() {
        realExecutor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
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

    /**
     * TODO: let MapDataManager resolve the resource with existing Resolved instance or let the
     * MapDataProvider make that decision and go through the same path?  for now, the former,
     * but consider situations when the latter makes more sense than saving the cycles by
     * reusing the Resolved instance.
     * @throws MapDataResolveException
     */
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

    @Test
    public void mergesMultipleChangesFromTheSameRepositoryToOneUpdate() {
        fail("unimplemented");
    }

    @Test
    public void mergesChangesFromMultipleRepositoriesToOneUpdate() {
        fail("unimplemented");
    }

//    @Test
//    public void resolvesResourceFromCapableProvider() throws Exception {
//
//        fail("evaluate whether to make tryImportResource() available only on the LocalStorageMapDataRepository");
//        // TODO: maybe MapDataManager should just get the live data update when an external entity uses
//        // LocalStorageMapDataRepository to copy a resource to local storage.
//
//        File cacheFile = repo1.createFile("big_cache.dog");
//
//        manager.tryImportResource(cacheFile.toURI());
//
//        verify(dogProvider, timeout(1000)).resolveResource(resourceWithUri(cacheFile.toURI()));
//        verify(catProvider, never()).resolveResource(any(MapDataResource.class));
//    }
//
//    @Test
//    public void addsNewResolvedResourcesToResourceSet() throws Exception {
//        MapDataResource resolved = repo2.createResourceWithFileName("data.cat", new MapDataResource.Resolved(cacheDir2.getName(), catProvider.getClass(), Collections.<MapLayerDescriptor>emptySet()));
//        when(catProvider.resolveResource(resourceWithUri(resolved.getUri()))).thenReturn(resolved);
//
//        manager.tryImportResource(resolved.getUri());
//
//        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());
//
//        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
//        Set<MapDataResource> caches = manager.getResources();
//
//        assertThat(caches, equalTo(setOf(resolved)));
//        assertThat(update.getAdded(), equalTo(setOf(resolved)));
//        assertTrue(update.getUpdated().isEmpty());
//        assertTrue(update.getRemoved().isEmpty());
//        assertThat(update.getSource(), sameInstance(manager));
//    }
//
//    @Test
//    public void refreshingFindsNewResourcesInRepositories() throws Exception {
//        MapDataResource res1 = repo1.createResourceWithFileName("pluto.dog",
//            new MapDataResource.Resolved("pluto", dogProvider.getClass(), Collections.emptySet()));
//        MapDataResource res2 = repo2.createResourceWithFileName("figaro.cat",
//            new MapDataResource.Resolved("figaro", catProvider.getClass(), Collections.emptySet()));
//
//        when(dogProvider.resolveResource(resourceWithUri(res1.getUri()))).thenReturn(res1);
//        when(catProvider.resolveResource(resourceWithUri(res2.getUri()))).thenReturn(res2);
//
//        assertTrue(manager.getResources().isEmpty());
//
//        manager.refreshMapData();
//
//        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());
//
//        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
//        Set<MapDataResource> resources = manager.getResources();
//
//        assertThat(resources.size(), is(2));
//        assertThat(resources, hasItems(res1, res2));
//        assertThat(update.getAdded().size(), is(2));
//        assertThat(update.getAdded(), hasItems(res1, res2));
//        assertTrue(update.getUpdated().isEmpty());
//        assertTrue(update.getRemoved().isEmpty());
//        assertThat(update.getSource(), sameInstance(manager));
//    }
//
//    @Test
//    public void refreshingGetsAvailableCachesFromProviders() {
//        MapDataResource dog1 = repo1.createResourceWithFileName("dog1.dog",
//            new MapDataResource.Resolved("dog1", dogProvider.getClass(), Collections.emptySet()));
//        MapDataResource dog2 = repo1.createResourceWithFileName("dog2.dog",
//            new MapDataResource.Resolved("dog2", dogProvider.getClass(), Collections.emptySet()));
//        MapDataResource cat = repo1.createResourceWithFileName("cat1.cat",
//            new MapDataResource.Resolved("cat1", catProvider.getClass(), Collections.emptySet()));
//
//        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet(), any(Executor.class))).thenReturn(setOf(dogCache1, dogCache2));
//        when(catProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(setOf(catCache));
//
//        manager.refreshMapData();
//
//        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());
//        verify(dogProvider).refreshResources(eq(Collections.<MapDataResource>emptySet()));
//        verify(catProvider).refreshResources(eq(Collections.<MapDataResource>emptySet()));
//
//        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
//        Set<MapDataResource> caches = manager.getResources();
//
//        assertThat(caches.size(), is(3));
//        assertThat(caches, hasItems(dogCache1, dogCache2, catCache));
//        assertThat(update.getAdded().size(), is(3));
//        assertThat(update.getAdded(), hasItems(dogCache1, dogCache2, catCache));
//        assertTrue(update.getUpdated().isEmpty());
//        assertTrue(update.getRemoved().isEmpty());
//        assertThat(update.getSource(), sameInstance(manager));
//    }
//
//    @Test
//    public void refreshingRemovesCachesNoLongerAvailable() {
//        MapDataResource dogCache1 = new MapDataResource(new File(cacheDir1, "dog1.dog").toURI(), "dog1", dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
//        MapDataResource dogCache2 = new MapDataResource(new File(cacheDir1, "dog2.dog").toURI(), "dog2", dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
//        MapDataResource catCache = new MapDataResource(new File(cacheDir1, "cat1.cat").toURI(), "cat1", catProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
//
//        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(setOf(dogCache1, dogCache2));
//        when(catProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(setOf(catCache));
//
//        manager.refreshMapData();
//
//        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());
//        verify(dogProvider).refreshResources(eq(Collections.<MapDataResource>emptySet()));
//        verify(catProvider).refreshResources(eq(Collections.<MapDataResource>emptySet()));
//
//        Set<MapDataResource> caches = manager.getResources();
//
//        assertThat(caches.size(), is(3));
//        assertThat(caches, hasItems(dogCache1, dogCache2, catCache));
//
//        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(setOf(dogCache2));
//        when(catProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(Collections.<MapDataResource>emptySet());
//
//        manager.refreshMapData();
//
//        verify(listener, timeout(1000).times(2)).onMapDataUpdated(updateCaptor.capture());
//
//        verify(dogProvider).refreshResources(eq(setOf(dogCache1, dogCache2)));
//        verify(catProvider).refreshResources(eq(setOf(catCache)));
//
//        caches = manager.getResources();
//        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
//
//        assertThat(caches.size(), is(1));
//        assertThat(caches, hasItem(dogCache2));
//        assertThat(update.getAdded(), empty());
//        assertThat(update.getUpdated(), empty());
//        assertThat(update.getRemoved(), hasItems(dogCache1, catCache));
//        assertThat(update.getSource(), sameInstance(manager));
//    }
//
//    @Test
//    public void refreshingUpdatesExistingCachesThatChanged() {
//        MapDataResource dogOrig = new MapDataResource(new File(cacheDir1, "dog1.dog").toURI(), "dog1", dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
//
//        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(setOf(dogOrig));
//
//        manager.refreshMapData();
//
//        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());
//
//        Set<MapDataResource> caches = manager.getResources();
//        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
//
//        assertThat(caches.size(), is(1));
//        assertThat(caches, hasItem(dogOrig));
//        assertThat(update.getAdded().size(), is(1));
//        assertThat(update.getAdded(), hasItem(dogOrig));
//        assertThat(update.getUpdated(), empty());
//        assertThat(update.getRemoved(), empty());
//
//        MapDataResource dogUpdated = new MapDataResource(new File(cacheDir1, "dog1.dog").toURI(), "dog1", dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
//
//        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(setOf(dogUpdated));
//
//        manager.refreshMapData();
//
//        verify(listener, timeout(1000).times(2)).onMapDataUpdated(updateCaptor.capture());
//
//        Set<MapDataResource> overlaysRefreshed = manager.getResources();
//        update = updateCaptor.getValue();
//
//        assertThat(overlaysRefreshed, not(sameInstance(caches)));
//        assertThat(overlaysRefreshed.size(), is(1));
//        assertThat(overlaysRefreshed, hasItem(sameInstance(dogUpdated)));
//        assertThat(overlaysRefreshed, hasItem(dogOrig));
//        assertThat(update.getAdded(), empty());
//        assertThat(update.getUpdated().size(), is(1));
//        assertThat(update.getUpdated(), hasItem(sameInstance(dogUpdated)));
//        assertThat(update.getRemoved(), empty());
//        assertThat(update.getSource(), sameInstance(manager));
//    }
//
//    @Test
//    public void immediatelyBeginsRefreshOnExecutor() {
//        final boolean[] overrodeMock = new boolean[]{false};
//        doAnswer(new Answer() {
//            @Override
//            public Object answer(InvocationOnMock invocation) {
//                // make sure this answer overrides the one in the setup method
//                overrodeMock[0] = true;
//                return null;
//            }
//        }).when(executor).execute(any(Runnable.class));
//
//        manager.refreshMapData();
//
//        verify(executor).execute(any(Runnable.class));
//        assertTrue(overrodeMock[0]);
//    }
//
//    @Test
//    public void cannotRefreshMoreThanOnceConcurrently() throws Exception {
//        final CyclicBarrier taskBegan = new CyclicBarrier(2);
//        final CyclicBarrier taskCanProceed = new CyclicBarrier(2);
//        final AtomicReference<Runnable> runningTask = new AtomicReference<>();
//
//        doAnswer(new Answer() {
//            @Override
//            public Object answer(InvocationOnMock invocation) {
//                final Runnable task = invocation.getArgument(0);
//                final Runnable blocked = new Runnable() {
//                    @Override
//                    public void run() {
//                        if (runningTask.get() == this) {
//                            try {
//                                taskBegan.await();
//                                taskCanProceed.await();
//                            }
//                            catch (Exception e) {
//                                fail(e.getMessage());
//                                throw new IllegalStateException(e);
//                            }
//                        }
//                        task.run();
//                    }
//                };
//                runningTask.compareAndSet(null, blocked);
//                AsyncTask.SERIAL_EXECUTOR.execute(blocked);
//                return null;
//            }
//        }).when(executor).execute(any(Runnable.class));
//
////        when(catProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(Collections.<MapDataResource>emptySet());
////        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(Collections.<MapDataResource>emptySet());
//
//        manager.refreshMapData();
//
//        verify(executor, times(1)).execute(any(Runnable.class));
//
//        // wait for the background task to start, then try to start another refresh
//        // and verify no new tasks were submitted to executor
//        taskBegan.await();
//
//        manager.refreshMapData();
//
//        verify(executor, times(1)).execute(any(Runnable.class));
//
//        taskCanProceed.await();
//
//        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());
//    }

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
