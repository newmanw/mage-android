package mil.nga.giat.mage.map.cache;


import android.app.Application;
import android.os.AsyncTask;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
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

    abstract static class TestDirRepository extends MapDataRepository {

        private final File dir;
        private Status status = Status.Success;

        TestDirRepository(File dir) {
            this.dir = dir;
        }

        @NotNull
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
            });
        }

        @Override
        protected void setValue(Set<MapDataResource> value) {
            super.setValue(value);
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
    }

    static class TestLayerDescriptor extends MapLayerDescriptor {

        TestLayerDescriptor(String overlayName, String cacheName, Class<? extends MapDataProvider> type) {
            super(overlayName, cacheName, type);
        }
    }

    private static Set<MapDataResource> setOfResources(MapDataResource... caches) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(caches)));
    }

    @Rule
    public TemporaryFolder testRoot = new TemporaryFolder();

    @Rule
    public TestName testName = new TestName();

    private File cacheDir1;
    private File cacheDir2;
    private MapDataManager.Config config;
    private MapDataManager mapDataManager;
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

        repo1 = new TestDirRepository(cacheDir1) {};
        repo2 = new TestDirRepository(cacheDir2) {};

        assertTrue(cacheDir1.isDirectory());
        assertTrue(cacheDir2.isDirectory());

        executor = mock(Executor.class);
        doAnswer(invocationOnMock -> {
            Runnable task = invocationOnMock.getArgument(0);
            AsyncTask.THREAD_POOL_EXECUTOR.execute(task);
            return null;
        }).when(executor).execute(any(Runnable.class));

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
        mapDataManager = new MapDataManager(config);
        mapDataManager.addUpdateListener(listener);
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
        mapDataManager.new MapDataUpdate(new MapDataManager.CreateUpdatePermission() {}, null, null, null);
    }

    @Test
    public void addsInitialAvailableLayersFromRepositories() throws URISyntaxException {
        MapDataResource initial1 = repo1.createResourceWithFileName("a.dog", null);
        MapDataResource initial2 = repo2.createResourceWithFileName("a.cat", null);
        MapDataResource initial3 = repo2.createResourceWithFileName("b.cat", null);
        repo1.setValue(setOfResources(initial1));
        repo2.setValue(setOfResources(initial2, initial3));

        mapDataManager = new MapDataManager(config);
        Map<URI, MapLayerDescriptor> layers = mapDataManager.getLayers();

        assertThat(layers.values(), equalTo(setOfResources(initial1, initial2, initial3)));
    }

    @Test
    public void addsResourcesWhenRepositoryAddsNewResources() {
        fail("unimplemented");
    }

    @Test
    public void removesResourcesWhenRepositoryRemovesResources() {
        fail("unimplemented");
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
    public void resolvesResourceFromCapableProvider() throws Exception {

        fail("evaluate whether to make tryImportResource() available only on the LocalStorageMapDataRepository");
        // TODO: maybe MapDataManager should just get the live data update when an external entity uses
        // LocalStorageMapDataRepository to copy a resource to local storage.

        File cacheFile = repo1.createFile("big_cache.dog");

        mapDataManager.tryImportResource(cacheFile.toURI());

        verify(dogProvider, timeout(1000)).resolveResource(resourceWithUri(cacheFile.toURI()));
        verify(catProvider, never()).resolveResource(any(MapDataResource.class));
    }

    @Test
    public void addsNewResolvedResourcesToResourceSet() throws Exception {
        MapDataResource resolved = repo2.createResourceWithFileName("data.cat", new MapDataResource.Resolved(cacheDir2.getName(), catProvider.getClass(), Collections.<MapLayerDescriptor>emptySet()));
        when(catProvider.resolveResource(resourceWithUri(resolved.getUri()))).thenReturn(resolved);

        mapDataManager.tryImportResource(resolved.getUri());

        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());

        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        Set<MapDataResource> caches = mapDataManager.getResources();

        assertThat(caches, equalTo(setOfResources(resolved)));
        assertThat(update.getAdded(), equalTo(setOfResources(resolved)));
        assertTrue(update.getUpdated().isEmpty());
        assertTrue(update.getRemoved().isEmpty());
        assertThat(update.getSource(), sameInstance(mapDataManager));
    }

    @Test
    public void refreshingFindsNewResourcesInRepositories() throws Exception {
        MapDataResource res1 = repo1.createResourceWithFileName("pluto.dog",
            new MapDataResource.Resolved("pluto", dogProvider.getClass(), Collections.emptySet()));
        MapDataResource res2 = repo2.createResourceWithFileName("figaro.cat",
            new MapDataResource.Resolved("figaro", catProvider.getClass(), Collections.emptySet()));

        when(dogProvider.resolveResource(resourceWithUri(res1.getUri()))).thenReturn(res1);
        when(catProvider.resolveResource(resourceWithUri(res2.getUri()))).thenReturn(res2);

        assertTrue(mapDataManager.getResources().isEmpty());

        mapDataManager.refreshMapData();

        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());

        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        Set<MapDataResource> resources = mapDataManager.getResources();

        assertThat(resources.size(), is(2));
        assertThat(resources, hasItems(res1, res2));
        assertThat(update.getAdded().size(), is(2));
        assertThat(update.getAdded(), hasItems(res1, res2));
        assertTrue(update.getUpdated().isEmpty());
        assertTrue(update.getRemoved().isEmpty());
        assertThat(update.getSource(), sameInstance(mapDataManager));
    }

    @Test
    public void refreshingGetsAvailableCachesFromProviders() {
//        MapDataResource dog1 = repo1.createResourceWithFileName("dog1.dog",
//            new MapDataResource.Resolved("dog1", dogProvider.getClass(), Collections.emptySet()));
//        MapDataResource dog2 = repo1.createResourceWithFileName("dog2.dog",
//            new MapDataResource.Resolved("dog2", dogProvider.getClass(), Collections.emptySet()));
//        MapDataResource cat = repo1.createResourceWithFileName("cat1.cat",
//            new MapDataResource.Resolved("cat1", catProvider.getClass(), Collections.emptySet()));
//
//        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet(), any(Executor.class))).thenReturn(setOfResources(dogCache1, dogCache2));
//        when(catProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(setOfResources(catCache));
//
//        mapDataManager.refreshMapData();
//
//        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());
//        verify(dogProvider).refreshResources(eq(Collections.<MapDataResource>emptySet()));
//        verify(catProvider).refreshResources(eq(Collections.<MapDataResource>emptySet()));
//
//        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
//        Set<MapDataResource> caches = mapDataManager.getResources();
//
//        assertThat(caches.size(), is(3));
//        assertThat(caches, hasItems(dogCache1, dogCache2, catCache));
//        assertThat(update.getAdded().size(), is(3));
//        assertThat(update.getAdded(), hasItems(dogCache1, dogCache2, catCache));
//        assertTrue(update.getUpdated().isEmpty());
//        assertTrue(update.getRemoved().isEmpty());
//        assertThat(update.getSource(), sameInstance(mapDataManager));
    }

    @Test
    public void refreshingRemovesCachesNoLongerAvailable() {
//        MapDataResource dogCache1 = new MapDataResource(new File(cacheDir1, "dog1.dog").toURI(), "dog1", dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
//        MapDataResource dogCache2 = new MapDataResource(new File(cacheDir1, "dog2.dog").toURI(), "dog2", dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
//        MapDataResource catCache = new MapDataResource(new File(cacheDir1, "cat1.cat").toURI(), "cat1", catProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
//
//        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(setOfResources(dogCache1, dogCache2));
//        when(catProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(setOfResources(catCache));
//
//        mapDataManager.refreshMapData();
//
//        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());
//        verify(dogProvider).refreshResources(eq(Collections.<MapDataResource>emptySet()));
//        verify(catProvider).refreshResources(eq(Collections.<MapDataResource>emptySet()));
//
//        Set<MapDataResource> caches = mapDataManager.getResources();
//
//        assertThat(caches.size(), is(3));
//        assertThat(caches, hasItems(dogCache1, dogCache2, catCache));
//
//        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(setOfResources(dogCache2));
//        when(catProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(Collections.<MapDataResource>emptySet());
//
//        mapDataManager.refreshMapData();
//
//        verify(listener, timeout(1000).times(2)).onMapDataUpdated(updateCaptor.capture());
//
//        verify(dogProvider).refreshResources(eq(setOfResources(dogCache1, dogCache2)));
//        verify(catProvider).refreshResources(eq(setOfResources(catCache)));
//
//        caches = mapDataManager.getResources();
//        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
//
//        assertThat(caches.size(), is(1));
//        assertThat(caches, hasItem(dogCache2));
//        assertThat(update.getAdded(), empty());
//        assertThat(update.getUpdated(), empty());
//        assertThat(update.getRemoved(), hasItems(dogCache1, catCache));
//        assertThat(update.getSource(), sameInstance(mapDataManager));
    }

    @Test
    public void refreshingUpdatesExistingCachesThatChanged() {
//        MapDataResource dogOrig = new MapDataResource(new File(cacheDir1, "dog1.dog").toURI(), "dog1", dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
//
//        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(setOfResources(dogOrig));
//
//        mapDataManager.refreshMapData();
//
//        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());
//
//        Set<MapDataResource> caches = mapDataManager.getResources();
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
//        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(setOfResources(dogUpdated));
//
//        mapDataManager.refreshMapData();
//
//        verify(listener, timeout(1000).times(2)).onMapDataUpdated(updateCaptor.capture());
//
//        Set<MapDataResource> overlaysRefreshed = mapDataManager.getResources();
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
//        assertThat(update.getSource(), sameInstance(mapDataManager));
    }

    @Test
    public void immediatelyBeginsRefreshOnExecutor() {
        final boolean[] overrodeMock = new boolean[]{false};
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                // make sure this answer overrides the one in the setup method
                overrodeMock[0] = true;
                return null;
            }
        }).when(executor).execute(any(Runnable.class));

        mapDataManager.refreshMapData();

        verify(executor).execute(any(Runnable.class));
        assertTrue(overrodeMock[0]);
    }

    @Test
    public void cannotRefreshMoreThanOnceConcurrently() throws Exception {
        final CyclicBarrier taskBegan = new CyclicBarrier(2);
        final CyclicBarrier taskCanProceed = new CyclicBarrier(2);
        final AtomicReference<Runnable> runningTask = new AtomicReference<>();

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                final Runnable task = invocation.getArgument(0);
                final Runnable blocked = new Runnable() {
                    @Override
                    public void run() {
                        if (runningTask.get() == this) {
                            try {
                                taskBegan.await();
                                taskCanProceed.await();
                            }
                            catch (Exception e) {
                                fail(e.getMessage());
                                throw new IllegalStateException(e);
                            }
                        }
                        task.run();
                    }
                };
                runningTask.compareAndSet(null, blocked);
                AsyncTask.SERIAL_EXECUTOR.execute(blocked);
                return null;
            }
        }).when(executor).execute(any(Runnable.class));

//        when(catProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(Collections.<MapDataResource>emptySet());
//        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(Collections.<MapDataResource>emptySet());

        mapDataManager.refreshMapData();

        verify(executor, times(1)).execute(any(Runnable.class));

        // wait for the background task to start, then try to start another refresh
        // and verify no new tasks were submitted to executor
        taskBegan.await();

        mapDataManager.refreshMapData();

        verify(executor, times(1)).execute(any(Runnable.class));

        taskCanProceed.await();

        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());
    }

    private static final MapDataResource MATCHER_RESOURCE = new MapDataResource(URI.create("test:matcher_resource"), mock(MapDataRepository.class), 0);

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
