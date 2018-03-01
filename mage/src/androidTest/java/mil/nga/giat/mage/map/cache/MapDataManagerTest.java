package mil.nga.giat.mage.map.cache;


import android.app.Application;
import android.os.AsyncTask;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MapDataManagerTest {

    // had to do this to make Mockito generate a different class for
    // two mock providers, because it uses the same class for two
    // separate mock instances of CacheProvider directly, which is
    // a collision for MapLayerDescriptor.getCacheType()
    static abstract class CatProvider implements CacheProvider {}
    static abstract class DogProvider implements CacheProvider {}

    static class TestLayerDescriptor extends MapLayerDescriptor {

        TestLayerDescriptor(String overlayName, String cacheName, Class<? extends CacheProvider> type) {
            super(overlayName, cacheName, type);
        }
    }

    private static Set<MapCache> cacheSetWithCaches(MapCache... caches) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(caches)));
    }

    @Rule
    public TemporaryFolder testRoot = new TemporaryFolder();

    @Rule
    public TestName testName = new TestName();

    private File cacheDir1;
    private File cacheDir2;
    private List<File> cacheDirs;
    private MapDataManager mapDataManager;
    private Executor executor;
    private CacheProvider catProvider;
    private CacheProvider dogProvider;
    private MapDataManager.CacheOverlaysUpdateListener listener;
    private ArgumentCaptor<MapDataManager.CacheOverlayUpdate> updateCaptor = ArgumentCaptor.forClass(MapDataManager.CacheOverlayUpdate.class);

    @Before
    public void configureCacheManager() throws Exception {

        Application context = Mockito.mock(Application.class);

        cacheDirs = Arrays.asList(
            cacheDir1 = testRoot.newFolder("cache1"),
            cacheDir2 = testRoot.newFolder("cache2")
        );

        assertTrue(cacheDir1.isDirectory());
        assertTrue(cacheDir2.isDirectory());

        executor = mock(Executor.class);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) {
                Runnable task = invocationOnMock.getArgument(0);
                AsyncTask.SERIAL_EXECUTOR.execute(task);
                return null;
            }
        }).when(executor).execute(any(Runnable.class));

        catProvider = mock(CatProvider.class);
        dogProvider = mock(DogProvider.class);

        when(catProvider.isCacheFile(any(URI.class))).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocationOnMock) {
                URI file = invocationOnMock.getArgument(0);
                return file.getPath().toLowerCase().endsWith(".cat");
            }
        });
        when(dogProvider.isCacheFile(any(URI.class))).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocationOnMock) {
                URI file = invocationOnMock.getArgument(0);
                return file.getPath().toLowerCase().endsWith(".dog");
            }
        });

        MapDataManager.Config config = new MapDataManager.Config()
            .context(context)
            .executor(executor)
            .providers(catProvider, dogProvider)
            .cacheLocations(new MapDataRepository() {
                @Override
                public List<File> retrieveMapDataResources() {
                    return cacheDirs;
                }
            })
            .updatePermission(new MapDataManager.CreateUpdatePermission(){});


        listener = mock(MapDataManager.CacheOverlaysUpdateListener.class);
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
            .cacheLocations(null)
            .executor(executor)
            .providers()
            .updatePermission(null));
    }

    @Test(expected = Error.class)
    public void cannotCreateUpdateWithoutPermission() {
        mapDataManager.new CacheOverlayUpdate(new MapDataManager.CreateUpdatePermission() {}, null, null, null);
    }

    @Test
    public void importsCacheWithCapableProvider() throws Exception {
        File cacheFile = new File(cacheDir1, "big_cache.dog");

        assertTrue(cacheFile.createNewFile());

        mapDataManager.tryImportCacheFile(cacheFile.toURI());

        verify(dogProvider, timeout(1000)).importCacheFromFile(cacheFile.toURI());
        verify(catProvider, never()).importCacheFromFile(any(URI.class));
    }

    @Test
    public void addsImportedCacheOverlayToCacheOverlaySet() throws Exception {
        File cacheFile = new File(cacheDir2, "data.cat");
        MapCache catCache = new MapCache(cacheDir2.getName(), catProvider.getClass(), cacheFile.toURI(), Collections.<MapLayerDescriptor>emptySet());
        when(catProvider.importCacheFromFile(cacheFile.toURI())).thenReturn(catCache);

        assertTrue(cacheFile.createNewFile());

        mapDataManager.tryImportCacheFile(cacheFile.toURI());

        verify(listener, timeout(1000)).onCacheOverlaysUpdated(updateCaptor.capture());

        MapDataManager.CacheOverlayUpdate update = updateCaptor.getValue();
        Set<MapCache> caches = mapDataManager.getCaches();

        assertThat(caches.size(), is(1));
        assertThat(caches, hasItem(catCache));
        assertThat(update.added.size(), is(1));
        assertThat(update.added, hasItem(catCache));
        assertTrue(update.updated.isEmpty());
        assertTrue(update.removed.isEmpty());
        assertThat(update.source, sameInstance(mapDataManager));
    }

    @Test
    public void refreshingFindsCachesInProvidedLocations() throws Exception {
        File cache1File = new File(cacheDir1, "pluto.dog");
        File cache2File = new File(cacheDir2, "figaro.cat");
        MapCache cache1 = new MapCache(cache1File.getName(), dogProvider.getClass(), cache1File.toURI(), Collections.<MapLayerDescriptor>emptySet());
        MapCache cache2 = new MapCache(cache2File.getName(), catProvider.getClass(), cache2File.toURI(), Collections.<MapLayerDescriptor>emptySet());
        when(dogProvider.importCacheFromFile(cache1File.toURI())).thenReturn(cache1);
        when(catProvider.importCacheFromFile(cache2File.toURI())).thenReturn(cache2);

        assertTrue(cache1File.createNewFile());
        assertTrue(cache2File.createNewFile());

        mapDataManager.refreshAvailableCaches();

        verify(listener, timeout(1000)).onCacheOverlaysUpdated(updateCaptor.capture());

        MapDataManager.CacheOverlayUpdate update = updateCaptor.getValue();
        Set<MapCache> caches = mapDataManager.getCaches();

        assertThat(caches.size(), is(2));
        assertThat(caches, hasItems(cache1, cache2));
        assertThat(update.added.size(), is(2));
        assertThat(update.added, hasItems(cache1, cache2));
        assertTrue(update.updated.isEmpty());
        assertTrue(update.removed.isEmpty());
        assertThat(update.source, sameInstance(mapDataManager));
    }

    @Test
    public void refreshingGetsAvailableCachesFromProviders() {
        MapCache dogCache1 = new MapCache("dog1", dogProvider.getClass(), new File(cacheDir1, "dog1.dog").toURI(), Collections.<MapLayerDescriptor>emptySet());
        MapCache dogCache2 = new MapCache("dog2", dogProvider.getClass(), new File(cacheDir1, "dog2.dog").toURI(), Collections.<MapLayerDescriptor>emptySet());
        MapCache catCache = new MapCache("cat1", catProvider.getClass(), new File(cacheDir1, "cat1.cat").toURI(), Collections.<MapLayerDescriptor>emptySet());

        when(dogProvider.refreshCaches(ArgumentMatchers.<MapCache>anySet())).thenReturn(cacheSetWithCaches(dogCache1, dogCache2));
        when(catProvider.refreshCaches(ArgumentMatchers.<MapCache>anySet())).thenReturn(cacheSetWithCaches(catCache));

        mapDataManager.refreshAvailableCaches();

        verify(listener, timeout(1000)).onCacheOverlaysUpdated(updateCaptor.capture());
        verify(dogProvider).refreshCaches(eq(Collections.<MapCache>emptySet()));
        verify(catProvider).refreshCaches(eq(Collections.<MapCache>emptySet()));

        MapDataManager.CacheOverlayUpdate update = updateCaptor.getValue();
        Set<MapCache> caches = mapDataManager.getCaches();

        assertThat(caches.size(), is(3));
        assertThat(caches, hasItems(dogCache1, dogCache2, catCache));
        assertThat(update.added.size(), is(3));
        assertThat(update.added, hasItems(dogCache1, dogCache2, catCache));
        assertTrue(update.updated.isEmpty());
        assertTrue(update.removed.isEmpty());
        assertThat(update.source, sameInstance(mapDataManager));
    }

    @Test
    public void refreshingRemovesCachesNoLongerAvailable() {
        MapCache dogCache1 = new MapCache("dog1", dogProvider.getClass(), new File(cacheDir1, "dog1.dog").toURI(), Collections.<MapLayerDescriptor>emptySet());
        MapCache dogCache2 = new MapCache("dog2", dogProvider.getClass(), new File(cacheDir1, "dog2.dog").toURI(), Collections.<MapLayerDescriptor>emptySet());
        MapCache catCache = new MapCache("cat1", catProvider.getClass(), new File(cacheDir1, "cat1.cat").toURI(), Collections.<MapLayerDescriptor>emptySet());

        when(dogProvider.refreshCaches(ArgumentMatchers.<MapCache>anySet())).thenReturn(cacheSetWithCaches(dogCache1, dogCache2));
        when(catProvider.refreshCaches(ArgumentMatchers.<MapCache>anySet())).thenReturn(cacheSetWithCaches(catCache));

        mapDataManager.refreshAvailableCaches();

        verify(listener, timeout(1000)).onCacheOverlaysUpdated(updateCaptor.capture());
        verify(dogProvider).refreshCaches(eq(Collections.<MapCache>emptySet()));
        verify(catProvider).refreshCaches(eq(Collections.<MapCache>emptySet()));

        Set<MapCache> caches = mapDataManager.getCaches();

        assertThat(caches.size(), is(3));
        assertThat(caches, hasItems(dogCache1, dogCache2, catCache));

        when(dogProvider.refreshCaches(ArgumentMatchers.<MapCache>anySet())).thenReturn(cacheSetWithCaches(dogCache2));
        when(catProvider.refreshCaches(ArgumentMatchers.<MapCache>anySet())).thenReturn(Collections.<MapCache>emptySet());

        mapDataManager.refreshAvailableCaches();

        verify(listener, timeout(1000).times(2)).onCacheOverlaysUpdated(updateCaptor.capture());

        verify(dogProvider).refreshCaches(eq(cacheSetWithCaches(dogCache1, dogCache2)));
        verify(catProvider).refreshCaches(eq(cacheSetWithCaches(catCache)));

        caches = mapDataManager.getCaches();
        MapDataManager.CacheOverlayUpdate update = updateCaptor.getValue();

        assertThat(caches.size(), is(1));
        assertThat(caches, hasItem(dogCache2));
        assertThat(update.added, empty());
        assertThat(update.updated, empty());
        assertThat(update.removed, hasItems(dogCache1, catCache));
        assertThat(update.source, sameInstance(mapDataManager));
    }

    @Test
    public void refreshingUpdatesExistingCachesThatChanged() {
        MapCache dogOrig = new MapCache("dog1", dogProvider.getClass(), new File(cacheDir1, "dog1.dog").toURI(), Collections.<MapLayerDescriptor>emptySet());

        when(dogProvider.refreshCaches(ArgumentMatchers.<MapCache>anySet())).thenReturn(cacheSetWithCaches(dogOrig));

        mapDataManager.refreshAvailableCaches();

        verify(listener, timeout(1000)).onCacheOverlaysUpdated(updateCaptor.capture());

        Set<MapCache> caches = mapDataManager.getCaches();
        MapDataManager.CacheOverlayUpdate update = updateCaptor.getValue();

        assertThat(caches.size(), is(1));
        assertThat(caches, hasItem(dogOrig));
        assertThat(update.added.size(), is(1));
        assertThat(update.added, hasItem(dogOrig));
        assertThat(update.updated, empty());
        assertThat(update.removed, empty());

        MapCache dogUpdated = new MapCache("dog1", dogProvider.getClass(), new File(cacheDir1, "dog1.dog").toURI(), Collections.<MapLayerDescriptor>emptySet());

        when(dogProvider.refreshCaches(ArgumentMatchers.<MapCache>anySet())).thenReturn(cacheSetWithCaches(dogUpdated));

        mapDataManager.refreshAvailableCaches();

        verify(listener, timeout(1000).times(2)).onCacheOverlaysUpdated(updateCaptor.capture());

        Set<MapCache> overlaysRefreshed = mapDataManager.getCaches();
        update = updateCaptor.getValue();

        assertThat(overlaysRefreshed, not(sameInstance(caches)));
        assertThat(overlaysRefreshed.size(), is(1));
        assertThat(overlaysRefreshed, hasItem(sameInstance(dogUpdated)));
        assertThat(overlaysRefreshed, hasItem(dogOrig));
        assertThat(update.added, empty());
        assertThat(update.updated.size(), is(1));
        assertThat(update.updated, hasItem(sameInstance(dogUpdated)));
        assertThat(update.removed, empty());
        assertThat(update.source, sameInstance(mapDataManager));
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

        mapDataManager.refreshAvailableCaches();

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

        when(catProvider.refreshCaches(ArgumentMatchers.<MapCache>anySet())).thenReturn(Collections.<MapCache>emptySet());
        when(dogProvider.refreshCaches(ArgumentMatchers.<MapCache>anySet())).thenReturn(Collections.<MapCache>emptySet());

        mapDataManager.refreshAvailableCaches();

        verify(executor, times(1)).execute(any(Runnable.class));

        // wait for the background task to start, then try to start another refresh
        // and verify no new tasks were submitted to executor
        taskBegan.await();

        mapDataManager.refreshAvailableCaches();

        verify(executor, times(1)).execute(any(Runnable.class));

        taskCanProceed.await();

        verify(listener, timeout(1000)).onCacheOverlaysUpdated(updateCaptor.capture());
    }
}
