package mil.nga.giat.mage.map.cache;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LifecycleRegistry;
import android.arch.lifecycle.Observer;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import mil.nga.giat.mage.sdk.datastore.layer.Layer;
import mil.nga.giat.mage.sdk.datastore.layer.LayerHelper;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeatureHelper;
import mil.nga.giat.mage.sdk.datastore.user.Event;
import mil.nga.giat.mage.sdk.datastore.user.EventHelper;
import mil.nga.giat.mage.sdk.exceptions.LayerException;
import mil.nga.giat.mage.sdk.http.resource.LayerResource;
import mil.nga.giat.mage.test.AsyncTesting;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@RunWith(AndroidJUnit4.class)
@SmallTest
public class StaticFeatureLayerRepositoryTest {

    private static long oneSecond() {
        return 1000;
    }

    @Rule
    public TestName testName = new TestName();

    @Rule
    public TemporaryFolder iconsDirRule = new TemporaryFolder();

    @Rule
    public AsyncTesting.MainLooperAssertion onMainThread = new AsyncTesting.MainLooperAssertion();

    private Event currentEvent;
    private EventHelper eventHelper;
    private LayerHelper layerHelper;
    private StaticFeatureHelper featureHelper;
    private LayerResource layerService;
    private File iconsDir;
    private StaticFeatureLayerRepository.NetworkCondition network;
    private StaticFeatureLayerRepository repo;
    private LifecycleOwner observerLifecycle;
    private Observer<Set<MapDataResource>> observer;

    @Before
    public void setupRepo() throws IOException {
        currentEvent = new Event(testName.getMethodName(), testName.getMethodName(), testName.getMethodName(), "", "");
        eventHelper = mock(EventHelper.class);
        layerHelper = mock(LayerHelper.class);
        featureHelper = mock(StaticFeatureHelper.class);
        layerService = mock(LayerResource.class);
        iconsDir = iconsDirRule.newFolder("icons");
        network = mock(StaticFeatureLayerRepository.NetworkCondition.class);
        repo = new StaticFeatureLayerRepository(eventHelper, layerHelper, featureHelper, layerService, iconsDir, network);
        observerLifecycle = new LifecycleOwner() {
            private Lifecycle myLifecycle = new LifecycleRegistry(this);
            @NonNull
            public Lifecycle getLifecycle() {
                return myLifecycle;
            }
        };
        observer = mock(Observer.class);

        when(eventHelper.getCurrentEvent()).thenReturn(currentEvent);
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
    public void fetchesLayersFromServerWhenDatabaseIsEmpty() throws InterruptedException, LayerException, IOException {
        when(layerHelper.readByEvent(currentEvent)).thenReturn(emptySet());
        when(layerService.getLayers(currentEvent)).thenReturn(Collections.singletonList(
            new Layer("1", "test", "layer 1", currentEvent)
        ));

        AsyncTesting.waitForMainThreadToRun(() -> {
            repo.observe(observerLifecycle, observer);
            repo.refreshAvailableMapData(emptyMap(), AsyncTask.THREAD_POOL_EXECUTOR);
        });

        onMainThread.assertThatWithin(oneSecond(), repo::getValue, not(nullValue()));
    }
}