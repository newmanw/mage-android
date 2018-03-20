package mil.nga.giat.mage.map.cache;


import android.app.Application;
import android.arch.lifecycle.Lifecycle;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

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
import java.util.concurrent.Executor;

import static java.util.Collections.emptyMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
            executor.execute(() -> {

            });
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
                throw new RuntimeException("error creating file", e);
            }
            return file;
        }

        private MapDataResource createResourceWithFileName(String name, MapDataResource.Resolved resolved) {
            File file = createFile(name);
            return new MapDataResource(file.toURI(), this, file.lastModified(), resolved);
        }

        private ResourceBuilder buildResource(String name, MapDataProvider provider) {
            return new ResourceBuilder(name, provider);
        }

        private class ResourceBuilder {
            private final File file;
            private final URI uri;
            private final Class<? extends MapDataProvider> type;
            private final Set<MapLayerDescriptor> layers = new HashSet<>();

            private ResourceBuilder(String name, MapDataProvider provider) {
                file = createFile(name);
                uri = file.toURI();
                this.type = provider.getClass();
            }

            private ResourceBuilder layers(String... names) {
                for (String name : names) {
                    layers.add(new TestLayerDescriptor(name, uri, type));
                }
                return this;
            }

            private MapDataResource finish() {
                MapDataResource.Resolved resolved = null;
                if (type != null) {
                    resolved = new MapDataResource.Resolved(file.getPath(), type, Collections.unmodifiableSet(layers));
                }
                return new MapDataResource(uri, TestDirRepository.this, file.lastModified(), resolved);
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

    @Rule
    public TemporaryFolder testRoot = new TemporaryFolder();

    @Rule
    public TestName testName = new TestName();

    private File cacheDir1;
    private File cacheDir2;
    private MapDataManager.Config config;
    private MapDataManager manager;
    private Executor executor;
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

        executor = mock(Executor.class);

        catProvider = mock(CatProvider.class);
        dogProvider = mock(DogProvider.class);

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

        listener = mock(MapDataManager.MapDataListener.class);
        manager = new MapDataManager(config);
        manager.addUpdateListener(listener);
    }

    private void activateExecutor() {
        doAnswer(invocationOnMock -> {
            Runnable task = invocationOnMock.getArgument(0);
            AsyncTask.THREAD_POOL_EXECUTOR.execute(task);
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
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    }

    @Test
    public void resumesAfterCreated() {
        manager = new MapDataManager(config);

        assertThat(manager.getLifecycle().getCurrentState(), is(Lifecycle.State.RESUMED));
    }

    @Test
    public void doesNotRefreshOnConstruction() throws InterruptedException {
        doAnswer(invocation -> {
            throw new Error("unexpected async task execution in test " + testName.getMethodName());
        }).when(executor).execute(any());

        MapDataRepository repo1 = mock(MapDataRepository.class);
        MapDataRepository repo2 = mock(MapDataRepository.class);
        manager = new MapDataManager(config.repositories(repo1, repo2));

        verify(repo1, never()).refreshAvailableMapData(any(), any());
        verify(repo2, never()).refreshAvailableMapData(any(), any());
        verify(executor, never()).execute(any());
    }

    @Test
    public void refreshSeriallyBeginsRefreshOnRepositories() {
        MapDataRepository repo1 = mock(MapDataRepository.class);
        when(repo1.getId()).thenReturn("repo1");
        MapDataRepository repo2 = mock(MapDataRepository.class);
        when(repo2.getId()).thenReturn("repo2");
        manager = new MapDataManager(config.repositories(repo1, repo2));
        manager.refreshMapData();

        verify(repo1).refreshAvailableMapData(anyMap(), same(executor));
        verify(repo2).refreshAvailableMapData(anyMap(), same(executor));
        verify(executor, never()).execute(any());
    }

    @Test
    @UiThreadTest
    public void addsInitialAvailableLayersFromRepositories() throws URISyntaxException {
        MapDataResource initial1 = repo1.buildResource("a.dog", dogProvider).layers("a.dog.1", "a.dog.2").finish();
        MapDataResource initial2 = repo2.buildResource("a.cat", catProvider).layers("a.cat.1").finish();
        MapDataResource initial3 = repo2.buildResource("b.dog", dogProvider).finish();
        repo1.setValue(setOf(initial1));
        repo2.setValue(setOf(initial2, initial3));

        manager = new MapDataManager(config);
        Map<URI, MapLayerDescriptor> layers = manager.getLayers();

        assertThat(setOfLayers(layers.values()), equalTo(flattenLayers(initial1, initial2, initial3)));
    }

    @Test
    @UiThreadTest
    public void addsLayersAndResourcesWhenRepositoryAddsNewResources() {
        manager = new MapDataManager(config);
        MapDataResource repo1_1 = repo1.createResourceWithFileName("repo1.1.dog", null);
        MapDataResource repo1_2 = repo1.buildResource("repo1.2.cat", catProvider).layers("repo1.2.cat.1", "repo1.2.cat.2").finish();
        MapDataResource repo2_1 = repo2.buildResource("repo2.1.dog", dogProvider).layers("repo2.1.dog.1").finish();
        repo1.setValue(setOf(repo1_1, repo1_2));
        repo2.setValue(setOf(repo2_1));

        assertThat(manager.getResources(), equalTo(mapOf(repo1_1, repo1_2, repo2_1)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(repo1_1, repo1_2, repo2_1)));
    }

    @Test
    @UiThreadTest
    public void removesLayersAndResourcesWhenRepositoryRemovesResources() {
        MapDataResource doomed = repo1.buildResource("doomed.dog", dogProvider).layers("doomed.1").finish();
        repo1.setValue(setOf(doomed));
        manager = new MapDataManager(config);

        assertThat(manager.getResources(), equalTo(mapOf(doomed)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(doomed)));

        repo1.setValue(Collections.emptySet());

        assertThat(manager.getResources(), equalTo(emptyMap()));
        assertThat(manager.getLayers(), equalTo(emptyMap()));
    }

    @Test
    @UiThreadTest
    public void addsLayersWhenRepositoryUpdatesResource() {
        MapDataResource mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1").finish();
        repo1.setValue(setOf(mod));
        manager = new MapDataManager(config);

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));

        mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1", "mod.2").finish();
        repo1.setValue(setOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));
    }

    @Test
    @UiThreadTest
    public void removesLayersWhenRepositoryUpdatesResource() {
        MapDataResource mod = repo1.buildResource("mod.dog", dogProvider).layers("mod.1").finish();
        repo1.setValue(setOf(mod));
        manager = new MapDataManager(config);

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(mapOfLayers(mod)));

        mod = repo1.buildResource("mod.dog", dogProvider).finish();
        repo1.setValue(setOf(mod));

        assertThat(manager.getResources(), equalTo(mapOf(mod)));
        assertThat(manager.getLayers(), equalTo(emptyMap()));
    }

    @Test
    public void doesNotResolveNewResourcesThatTheRepositoryResolved() {
        fail("unimplemented");
    }

    @Test
    public void resolvesNewResourcesThatTheRepositoryDidNotResolve() {
        fail("unimplemented");
    }

    @Test
    public void providesResolvedResourcesToRepositoryForRefresh() {

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
}
