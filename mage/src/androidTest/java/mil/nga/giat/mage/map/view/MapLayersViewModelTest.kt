package mil.nga.giat.mage.map.view

import android.arch.lifecycle.*
import android.support.test.annotation.UiThreadTest
import android.support.test.runner.AndroidJUnit4
import com.nhaarman.mockitokotlin2.*
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.data.Resource.Status.Loading
import mil.nga.giat.mage.data.Resource.Status.Success
import mil.nga.giat.mage.map.cache.*
import mil.nga.giat.mage.test.AsyncTesting
import mil.nga.giat.mage.test.AsyncTesting.waitForMainThreadToCall
import mil.nga.giat.mage.test.AsyncTesting.waitForMainThreadToRun
import mil.nga.giat.mage.test.TargetSuppliesPropertyValueMatcher.valueSuppliedBy
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.net.URI
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger


@RunWith(AndroidJUnit4::class)
class MapLayersViewModelTest : LifecycleOwner {

    abstract class TestRepo : MapDataRepository()
    abstract class TestProviderA : MapDataProvider
    abstract class TestProviderB : MapDataProvider
    class TestLayerDesc(layerName: String, resourceUri: URI, dataType: MapDataProvider) : MapLayerDescriptor(layerName, resourceUri, dataType.javaClass)
    class TestThread(target: Runnable, name: String) : Thread(target, name)

    @Rule
    @JvmField
    val testName = TestName()

    @Rule
    @JvmField
    val onMainThread = AsyncTesting.MainLooperAssertion()

    private lateinit var mapDataManager: MapDataManager
    private lateinit var repo1: TestRepo
    private lateinit var providerA: TestProviderA
    private lateinit var providerB: TestProviderB

    private lateinit var mapData: MutableLiveData<Resource<Map<URI, MapDataResource>>>
    private lateinit var res1: MapDataResource
    private lateinit var res1Layer1: MapLayerDescriptor
    private lateinit var res1Layer2: MapLayerDescriptor
    private lateinit var res2: MapDataResource
    private lateinit var res2Layer1: MapLayerDescriptor
    private lateinit var res2Layer2: MapLayerDescriptor
    private lateinit var res3: MapDataResource
    private lateinit var res3Layer1: MapLayerDescriptor
    private lateinit var allResources: Map<URI, MapDataResource>
    private lateinit var observer: Observer<Resource<List<MapLayersViewModel.Layer>>>
    private lateinit var observed: KArgumentCaptor<Resource<List<MapLayersViewModel.Layer>>>
    private lateinit var lifecycle: LifecycleRegistry
    private lateinit var executor: ThreadPoolExecutor
    private lateinit var realExecutor: ThreadPoolExecutor
    private lateinit var model: MapLayersViewModel

    private val testTimeout = 1000L

    override fun getLifecycle(): Lifecycle {
        return lifecycle
    }

    private fun mapOf(vararg resources: MapDataResource): Map<URI, MapDataResource> {
        return resources.associateBy(MapDataResource::uri)
    }

    @Before
    fun setup() {

        repo1 = mock()
        providerA = mock {
            on { canHandleResource(any()) }.then { answer ->
                (answer.arguments[0] as MapDataResource).uri.toString().endsWith(".a")
            }
        }
        providerB = mock {
            on { canHandleResource(any()) }.then { answer ->
                (answer.arguments[0] as MapDataResource).uri.toString().endsWith(".b")
            }
        }

        mapData = MutableLiveData()
        mapDataManager = mock { _ ->
            on { mapData }.thenReturn(mapData)
            on { layers }.then { mapData.value?.content?.values?.
                flatMap { resource -> resource.layers.values }?.associateBy {
                layerDesc -> layerDesc.layerUri }?.toMutableMap() ?: emptyMap<URI, MapLayerDescriptor>() }
            on { resourceForLayer(any()) }.then { mapData.value?.content?.get((it.getArgument(0) as MapLayerDescriptor).resourceUri) }
            on { providers }.thenReturn(listOf(providerA, providerB).associateBy(MapDataProvider::javaClass))
        }

        val uri1 = URI("test", javaClass.simpleName, "/${testName.methodName}/res1.a",null)
        res1Layer1 = TestLayerDesc("layer1", uri1, providerA)
        res1Layer2 = TestLayerDesc("layer2", uri1, providerA)
        res1 = MapDataResource(uri1, repo1, 0, MapDataResource.Resolved("Test Resource 1", providerA.javaClass,
            setOf(res1Layer1, res1Layer2)))
        val uri2 = URI("test", javaClass.simpleName, "/${testName.methodName}/res2.b", null)
        res2Layer1 = TestLayerDesc("layer1", uri2, providerB)
        res2Layer2 = TestLayerDesc("layer2", uri2, providerB)
        res2 = MapDataResource(uri2, repo1, 0, MapDataResource.Resolved("Test Resource 2", providerB.javaClass,
            setOf(res2Layer1, res2Layer2)))
        val uri3 = URI("test", javaClass.simpleName, "/${testName.methodName}/res3.a", null)
        res3Layer1 = TestLayerDesc("layer1", uri3, providerA)
        res3 = MapDataResource(uri3, repo1, 0, MapDataResource.Resolved("Test Resource 3", providerA.javaClass, setOf(res3Layer1)))
        allResources = setOf(res1, res2, res3).associateBy(MapDataResource::uri)

        observer = mock()
        observed = argumentCaptor()
        lifecycle = LifecycleRegistry(this)
        lifecycle.markState(Lifecycle.State.RESUMED)
        val threadFactory = object : ThreadFactory {

            var count = AtomicInteger(0)

            override fun newThread(r: Runnable): Thread {
                return TestThread(r, "${MapLayersViewModel@javaClass.simpleName}-${count.incrementAndGet()}")
            }
        }
        realExecutor = ThreadPoolExecutor(3, 3, 5, TimeUnit.MILLISECONDS, LinkedBlockingQueue(), threadFactory)
        executor = mock {
            on { execute(any()) }.then { invoc ->
                val target = invoc.arguments[0] as Runnable
                realExecutor.execute(target)
                null
            }
        }

        model = waitForMainThreadToCall {
            val model = MapLayersViewModel(mapDataManager, executor)
            model.layersInZOrder.observe(this, observer)
            model
        }
    }

    @After
    fun waitForThreadPoolTermination() {
        executor.shutdown()
        executor.awaitTermination(testTimeout, TimeUnit.MILLISECONDS)
    }

    @Test
    @UiThreadTest
    fun startsWithLayersInAlphanumericOrderGroupedByResourceUri() {

        mapData.value = Resource.success(allResources)

        assertThat(model.layersInZOrder.value!!.content!!.map(MapLayersViewModel.Layer::desc),
            contains(res1Layer1, res1Layer2, res2Layer1, res2Layer2, res3Layer1))
    }

    @Test
    @UiThreadTest
    fun listIsEmptyWhenMapDataIsLoading() {

        mapData.value = Resource.loading()

        assertThat(model.layersInZOrder.value!!.status, equalTo(Loading))
        assertThat(model.layersInZOrder.value!!.content, equalTo(emptyList()))

        mapData.value = Resource.loading(allResources)

        assertThat(model.layersInZOrder.value!!.status, equalTo(Loading))
        assertThat(model.layersInZOrder.value!!.content, equalTo(emptyList()))
    }

    @Test
    @UiThreadTest
    fun populatesListWhenMapDataFinishesLoading() {

        mapData.value = Resource.loading()

        assertThat(model.layersInZOrder.value!!.status, equalTo(Loading))
        assertThat(model.layersInZOrder.value!!.content, equalTo(emptyList()))

        mapData.value = Resource.success(allResources)

        assertThat(model.layersInZOrder.value!!.status, equalTo(Success))
        assertThat(model.layersInZOrder.value!!.content!!.map(MapLayersViewModel.Layer::desc),
            equalTo(listOf(res1Layer1, res1Layer2, res2Layer1, res2Layer2, res3Layer1)))
    }

    @Test
    @UiThreadTest
    fun allLayersAreInitiallyHidden() {

        mapData.value = Resource.success(allResources)

        assertThat(model.layersInZOrder.value!!.content, everyItem(valueSuppliedBy(MapLayersViewModel.Layer::isVisible, equalTo(false))))
    }

    @Test
    @UiThreadTest
    fun allLayersInitialZIndexesMatchPositions() {

        mapData.value = Resource.success(allResources)

        model.layersInZOrder.value!!.content!!.forEachIndexed { index, layer ->
            assertThat(layer.desc.layerUri.toString(), layer.zIndex, equalTo(index)) }
    }

    @Test
    @UiThreadTest
    fun mapDataChangeWithNewResources() {

        mapData.value = Resource.success(mapOf(res1))

        assertThat(model.layersInZOrder.value!!.content!!.map(MapLayersViewModel.Layer::desc), equalTo(listOf(res1Layer1, res1Layer2)))

        mapData.value = Resource.success(mapOf(res1, res2))

        assertThat(model.layersInZOrder.value!!.content!!.map(MapLayersViewModel.Layer::desc), equalTo(listOf(res1Layer1, res1Layer2, res2Layer1, res2Layer2)))
    }

    @Test
    @UiThreadTest
    fun mapDataChangeWithModifiedResources() {

        val res1Mod = res1.resolve(MapDataResource.Resolved(res1.requireResolved().name, res1.requireResolved().type, setOf(res1Layer2)))
        mapData.value = Resource.success(mapOf(res1Mod))

        assertThat(model.layersInZOrder.value!!.content!!.map(MapLayersViewModel.Layer::desc), equalTo(listOf(res1Layer2)))

        mapData.value = Resource.success(mapOf(res1))

        assertThat(model.layersInZOrder.value!!.content!!.map(MapLayersViewModel.Layer::desc), equalTo(listOf(res1Layer2, res1Layer1)))
    }

    @Test
    @UiThreadTest
    fun mapDataChangeWithRemovedResources() {

        mapData.value = Resource.success(allResources)

        assertThat(model.layersInZOrder.value!!.content!!.map(MapLayersViewModel.Layer::desc),
            equalTo(listOf(res1Layer1, res1Layer2, res2Layer1, res2Layer2, res3Layer1)))

        mapData.value = Resource.success(mapOf(res1, res3))

        assertThat(model.layersInZOrder.value!!.content!!.map(MapLayersViewModel.Layer::desc), equalTo(listOf(res1Layer1, res1Layer2, res3Layer1)))

        mapData.value = Resource.success(emptyMap())

        assertThat(model.layersInZOrder.value!!.content, equalTo(emptyList()))
    }

    @Test
    @UiThreadTest
    fun allLayerElementsAreInitiallySuccessNonNullEmpty() {

        mapData.value = Resource.success(allResources)
        val layers = model.layersInZOrder.value!!.content!!

        assertThat(layers, hasSize(5))
        for (layer in layers) {
            val elements = model.elementsForLayer(layer)
            assertThat(layer.toString(), elements.value!!.content, equalTo(emptyMap()))
        }
    }

    @Test
    @UiThreadTest
    fun showLayerBeforeBoundsAreSetDoesNotLoadElements() {

        mapData.value = Resource.success(allResources)
        val layer = model.layerAt(0)
        val elements = model.elementsForLayer(layer)

        assertThat(elements.value!!.status, equalTo(Success))
        assertThat(elements.value!!.content, equalTo(emptyMap()))

        model.setLayerVisibility(layer, true)

        verifyNoMoreInteractions(executor)
        assertThat(elements.value!!.status, equalTo(Success))
        assertThat(elements.value!!.content, equalTo(emptyMap()))
    }

    @Test
    fun showLayerForTheFirstTimeCreatesNewLayerQueryOnExecutorThread() {

        waitForMainThreadToRun { mapData.value = Resource.success(allResources) }

        val createdOnBGThread = AtomicBoolean(false)
        val query = mock<MapDataProvider.LayerQuery> {
            on { fetchMapElements(any()) }.thenReturn(emptyMap())
        }
        whenever(providerA.createQueryForLayer(res3Layer1)).then { _ ->
            createdOnBGThread.set(Thread.currentThread() is TestThread)
            query
        }

        waitForMainThreadToRun { model.setLayerVisibility(model.layersInZOrder.value!!.content!!.last(), true) }

        onMainThread.assertThatWithin(testTimeout, createdOnBGThread::get, equalTo(true))
    }

    @Test
    @UiThreadTest
    fun showLayerMarksLayerElementStatusLoadingImmediately() {

        mapData.value = Resource.success(allResources)
        val layer = model.layerAt(0)
        val elements = model.elementsForLayer(layer)

        assertThat(elements.value!!.status, equalTo(Success))

        model.setLayerVisibility(layer, true)

        assertThat(elements.value!!.status, equalTo(Loading))
    }

    @Test
    @UiThreadTest
    fun zOrderShiftTopToBottom() {

        mapData.value = Resource.success(allResources)

        fail("unimplemented")
    }

    @Test
    fun reusesLayerQueryForMapBoundsChange() {
        fail("unimplemented")
    }

    @Test
    fun reusesLayerQueryForShowingLayer() {
        fail("unimplemented")
    }
}