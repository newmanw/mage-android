package mil.nga.giat.mage.map.view

import android.arch.lifecycle.*
import android.support.test.annotation.UiThreadTest
import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.nhaarman.mockitokotlin2.*
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.data.Resource.Status.Loading
import mil.nga.giat.mage.data.Resource.Status.Success
import mil.nga.giat.mage.map.MapElementSpec
import mil.nga.giat.mage.map.MapMarkerSpec
import mil.nga.giat.mage.map.cache.*
import mil.nga.giat.mage.map.cache.MapDataProvider.LayerQuery
import mil.nga.giat.mage.map.view.MapLayersViewModel.Layer
import mil.nga.giat.mage.test.AsyncTesting
import mil.nga.giat.mage.test.AsyncTesting.waitForMainThreadToCall
import mil.nga.giat.mage.test.AsyncTesting.waitForMainThreadToRun
import mil.nga.giat.mage.test.ConcurrentMethodControl
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


fun <E> List<E>.copy(): List<E> {
    return asSequence().toList()
}

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
    private lateinit var allLayerDescs: List<MapLayerDescriptor>
    private lateinit var observer: Observer<Resource<List<Layer>>>
    private lateinit var observed: KArgumentCaptor<Resource<List<Layer>>>
    private lateinit var listener: MapLayersViewModel.LayerListener
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

    private fun mapOf(vararg elements: MapElementSpec): Map<Any, MapElementSpec> {
        return elements.associateBy(MapElementSpec::id)
    }

    private fun bounds(lon: Double, lat: Double, rad: Double): LatLngBounds {
        return LatLngBounds(LatLng(lat - rad, lon - rad), LatLng(lat + rad, lon + rad))
    }

    private fun assertZIndexesMatchInversePositions(layers: List<Layer>) {
        layers.forEachIndexed { pos, layer ->
            assertThat("z-index/position mismatch: ${layer.zIndex}/$pos", layer.zIndex, equalTo(layers.size - pos))
        }
    }

    private fun providerForLayer(layer: Layer): MapDataProvider {
        return providerForLayer(layer.desc)
    }

    private fun providerForLayer(layer: MapLayerDescriptor): MapDataProvider {
        return when {
            layer.dataType == providerA.javaClass -> providerA
            layer.dataType == providerB.javaClass -> providerB
            else -> throw Error("unknown layer data type: ${layer.layerUri}[${layer.dataType}]")
        }
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
        allLayerDescs = listOf(res1Layer1, res1Layer2, res2Layer1, res2Layer2, res3Layer1)

        observer = mock()
        observed = argumentCaptor()
        listener = mock()
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
            model.layerEvents.listen(this, listener)
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
    fun startsWithLayersInDescendingAlphanumericOrderGroupedByResourceUri() {

        mapData.value = Resource.success(allResources)

        assertThat(model.layersInZOrder.value!!.content!!.map(Layer::desc),
            contains(res1Layer1, res1Layer2, res2Layer1, res2Layer2, res3Layer1))
        assertZIndexesMatchInversePositions(model.layersInZOrder.value!!.content!!)
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
        assertThat(model.layersInZOrder.value!!.content!!.map(Layer::desc),
            equalTo(listOf(res1Layer1, res1Layer2, res2Layer1, res2Layer2, res3Layer1)))
        assertZIndexesMatchInversePositions(model.layersInZOrder.value!!.requireContent())
    }

    @Test
    @UiThreadTest
    fun allLayersAreInitiallyHidden() {

        mapData.value = Resource.success(allResources)

        assertThat(model.layersInZOrder.value!!.content, everyItem(valueSuppliedBy(Layer::isVisible, equalTo(false))))
    }

    @Test
    @UiThreadTest
    fun allLayersInitialZIndexesMatchPositions() {

        mapData.value = Resource.success(allResources)

        assertZIndexesMatchInversePositions(model.layersInZOrder.value!!.content!!)
    }

    @Test
    @UiThreadTest
    fun mapDataChangeWithNewResources() {

        mapData.value = Resource.success(mapOf(res1))

        assertThat(model.layersInZOrder.value!!.content!!.map(Layer::desc), equalTo(listOf(res1Layer1, res1Layer2)))

        mapData.value = Resource.success(mapOf(res1, res2))

        assertThat(model.layersInZOrder.value!!.content!!.map(Layer::desc), equalTo(listOf(res1Layer1, res1Layer2, res2Layer1, res2Layer2)))
        assertZIndexesMatchInversePositions(model.layersInZOrder.value!!.requireContent())
    }

    @Test
    @UiThreadTest
    fun mapDataChangeWithModifiedResources() {

        val res1Mod = res1.resolve(MapDataResource.Resolved(res1.requireResolved().name, res1.requireResolved().type, setOf(res1Layer2)))
        mapData.value = Resource.success(mapOf(res1Mod))

        assertThat(model.layersInZOrder.value!!.content!!.map(Layer::desc), equalTo(listOf(res1Layer2)))

        mapData.value = Resource.success(mapOf(res1))

        assertThat(model.layersInZOrder.value!!.content!!.map(Layer::desc), equalTo(listOf(res1Layer2, res1Layer1)))
        assertZIndexesMatchInversePositions(model.layersInZOrder.value!!.requireContent())
    }

    @Test
    @UiThreadTest
    fun mapDataChangeWithRemovedResources() {

        mapData.value = Resource.success(allResources)

        assertThat(model.layersInZOrder.value!!.content!!.map(Layer::desc),
            equalTo(listOf(res1Layer1, res1Layer2, res2Layer1, res2Layer2, res3Layer1)))

        mapData.value = Resource.success(mapOf(res1, res3))

        assertThat(model.layersInZOrder.value!!.content!!.map(Layer::desc), equalTo(listOf(res1Layer1, res1Layer2, res3Layer1)))
        assertZIndexesMatchInversePositions(model.layersInZOrder.value!!.requireContent())

        mapData.value = Resource.success(emptyMap())

        assertThat(model.layersInZOrder.value!!.content, equalTo(emptyList()))
    }

    @Test
    @UiThreadTest
    fun mapDataChangeToLoadingLeavesExistingLayerListIntact() {

        mapData.value = Resource.success(allResources)
        val layers = model.layersInZOrder.value!!.content

        assertThat(model.layersInZOrder.value!!.content!!.map(Layer::desc),
            equalTo(listOf(res1Layer1, res1Layer2, res2Layer1, res2Layer2, res3Layer1)))

        mapData.value = Resource.loading()

        assertThat(model.layersInZOrder.value!!.content, equalTo(layers))
        assertZIndexesMatchInversePositions(model.layersInZOrder.value!!.requireContent())
    }

    @Test
    @UiThreadTest
    fun allLayerElementsAreInitiallySuccessWithNullValue() {

        mapData.value = Resource.success(allResources)
        val layers = model.layersInZOrder.value!!.content!!

        assertThat(layers, hasSize(5))
        for (layer in layers) {
            assertThat(layer.toString(), layer.elements.status, equalTo(Success))
            assertThat(layer.toString(), layer.elements.content, nullValue())
        }
    }

    @Test
    @UiThreadTest
    fun showLayerBeforeBoundsAreSetDoesNotLoadElements() {

        mapData.value = Resource.success(allResources)
        val layer = model.layerAt(0)

        assertThat(layer.elements.status, equalTo(Success))
        assertThat(layer.elements.content, nullValue())

        model.setLayerVisible(layer, true)

        verifyNoMoreInteractions(executor)
        verifyNoMoreInteractions(providerA)
        verifyNoMoreInteractions(providerB)
        verify(listener).layerVisibilityChanged(layer, 0)
        verify(listener, never()).layerElementsChanged(any(), any(), any())
        assertThat(layer.elements.status, equalTo(Success))
        assertThat(layer.elements.content, nullValue())
    }

    @Test
    @UiThreadTest
    fun showLayerMarksLayerElementStatusLoadingImmediately() {

        model.mapBoundsChanged(bounds(10.0, 10.0, 1.0))
        mapData.value = Resource.success(allResources)
        var layer = model.layerAt(0)

        assertThat(layer.elements.status, equalTo(Success))
        assertThat(layer.elements.content, nullValue())

        val query = mock<LayerQuery> {
            on { fetchMapElements(any()) }.thenReturn(emptyMap())
        }
        whenever(providerA.createQueryForLayer(layer.desc)).thenReturn(query)
        model.setLayerVisible(layer, true)
        layer = model.layerAt(0)

        assertThat(layer.elements.status, equalTo(Loading))
        assertThat(layer.elements.content, nullValue())
        verify(listener).layerVisibilityChanged(layer, 0)
        verify(listener, never()).layerElementsChanged(any(), any(), any())
    }

    @Test
    @UiThreadTest
    fun mapBoundsChangeMarksVisibleLayersElementStatusLoadingSerially() {

        val query = mock<LayerQuery> { on { fetchMapElements(any()) }.thenReturn(emptyMap())}
        whenever(providerA.createQueryForLayer(any())).thenReturn(query)
        whenever(providerB.createQueryForLayer(any())).thenReturn(query)

        mapData.value = Resource.success(allResources)
        val layers = model.layersInZOrder.value!!.content!!
        model.setLayerVisible(layers[1], true)
        model.setLayerVisible(layers[3], true)
        model.mapBoundsChanged(bounds(120.0, 13.0, 5.0))

        assertThat(layers[1].elements.status, equalTo(Loading))
        assertThat(layers[3].elements.status, equalTo(Loading))
        assertThat(layers[0].elements.status, equalTo(Success))
        assertThat(layers[2].elements.status, equalTo(Success))
        assertThat(layers[4].elements.status, equalTo(Success))
        verify(listener).layerElementsChanged(layers[1], 1, emptyMap())
        verify(listener).layerElementsChanged(layers[3], 3, emptyMap())
    }

    @Test
    fun showLayerForTheFirstTimeCreatesNewLayerQueryOnExecutorThread() {

        waitForMainThreadToRun {
            mapData.value = Resource.success(allResources)
            model.mapBoundsChanged(bounds(-126.0, 46.0, 3.0))
        }

        val createdOnBGThread = AtomicBoolean(false)
        val query = mock<LayerQuery> {
            on { fetchMapElements(any()) }.thenReturn(emptyMap())
        }
        whenever(providerA.createQueryForLayer(res3Layer1)).then { _ ->
            createdOnBGThread.set(Thread.currentThread() is TestThread)
            query
        }

        waitForMainThreadToRun { model.setLayerVisible(model.layersInZOrder.value!!.content!!.last(), true) }

        onMainThread.assertThatWithin(testTimeout, createdOnBGThread::get, equalTo(true))
    }

    @Test
    fun reusesLayerQueryForMapBoundsChange() {

        val query = mock<LayerQuery> {
            on { fetchMapElements(any()) }.thenReturn(mapOf(MapMarkerSpec("point1", null, MarkerOptions())))
            on { hasDynamicElements() }.thenReturn(true)
        }
        val provider = providerForLayer(res2Layer1)
        whenever(provider.createQueryForLayer(res2Layer1)).thenReturn(query)
        val bounds1 = bounds(120.0, 30.0, 10.0)
        val bounds2 = bounds(110.0, 30.0, 10.0)
        val layerPos = allLayerDescs.indexOf(res2Layer1)

        waitForMainThreadToRun {
            mapData.value = Resource.success(allResources)
            model.setLayerVisible(model.layerAt(layerPos), true)
            model.mapBoundsChanged(bounds1)
        }

        onMainThread.assertThatWithin(testTimeout, { model.layerAt(layerPos).elements.status }, equalTo(Success))

        waitForMainThreadToRun {
            model.mapBoundsChanged(bounds2)
        }

        onMainThread.assertThatWithin(testTimeout, { model.layerAt(layerPos).elements.status }, equalTo(Success))

        verify(provider).createQueryForLayer(res2Layer1)
        verify(query).fetchMapElements(bounds1)
        verify(query).fetchMapElements(bounds2)
    }

    @Test
    fun reusesLayerQueryForShowingLayer() {

        val layerDesc = res1Layer2
        val layerPos = allLayerDescs.indexOf(layerDesc)
        val query = mock<LayerQuery> {
            on { fetchMapElements(any()) }.thenReturn(mapOf(MapMarkerSpec("point1", null, MarkerOptions())))
        }
        val provider = providerForLayer(layerDesc)
        whenever(provider.createQueryForLayer(layerDesc)).thenReturn(query)
        val bounds1 = bounds(120.0, 30.0, 10.0)
        val bounds2 = bounds(30.0, 120.0, 1.0)

        waitForMainThreadToRun {
            mapData.value = Resource.success(allResources)
            model.mapBoundsChanged(bounds1)
            val layers = model.layersInZOrder.value!!.content!!
            val layer = layers[layerPos]
            model.setLayerVisible(layer, true)
        }

        onMainThread.assertThatWithin(testTimeout, { model.layerAt(layerPos).elements.status }, equalTo(Success))

        waitForMainThreadToRun {
            model.setLayerVisible(model.layerAt(layerPos), false)
            model.mapBoundsChanged(bounds2)
        }

        onMainThread.assertThatWithin(testTimeout, { model.layerAt(layerPos).elements.status }, equalTo(Success))

        waitForMainThreadToRun { model.setLayerVisible(model.layerAt(layerPos), true) }

        verify(provider).createQueryForLayer(layerDesc)
        verify(query).fetchMapElements(bounds1)
        verify(query).fetchMapElements(bounds2)
    }

    @Test
    fun doesNotReloadElementsWhenShowingLayerIfBoundsDidNotChange() {

        val marker = MapMarkerSpec("point1", null, MarkerOptions())
        val bounds = bounds(12.0, 15.0, 3.0)
        val query = mock<LayerQuery> {
            on { fetchMapElements(bounds) }.thenReturn(mapOf(marker))
        }
        val provider = providerForLayer(res2Layer1)
        whenever(provider.createQueryForLayer(res2Layer1)).thenReturn(query)
        val layerPos = allLayerDescs.indexOf(res2Layer1)

        waitForMainThreadToRun {
            mapData.value = Resource.success(allResources)
            model.mapBoundsChanged(bounds)
            val layer = model.layerAt(layerPos)

            assertThat(layer.desc, equalTo(res2Layer1))

            model.setLayerVisible(layer, true)
            val visibleLayer = model.layerAt(layerPos)

            assertThat(visibleLayer.elements.status, equalTo(Loading))
            verify(listener).layerVisibilityChanged(visibleLayer, layerPos)
        }

        onMainThread.assertThatWithin(testTimeout, { model.layerAt(layerPos).elements.status }, equalTo(Success))

        val elements = model.layerAt(layerPos).elements

        assertThat(elements.requireContent(), equalTo(mapOf(marker)))
        verify(provider).createQueryForLayer(model.layerAt(layerPos).desc)
        verify(query).fetchMapElements(bounds)

        waitForMainThreadToRun {
            model.setLayerVisible(model.layerAt(layerPos), false)
            val layer = model.layerAt(layerPos)

            assertThat(layer.elements.status, equalTo(Success))
            assertThat(layer.elements.content, equalTo(elements.content))
            verify(listener, times(2)).layerVisibilityChanged(layer, layerPos)
        }

        waitForMainThreadToRun {
            model.setLayerVisible(model.layerAt(layerPos), true)
            val layer = model.layerAt(layerPos)

            assertThat(layer.elements.status, equalTo(Success))
            assertThat(layer.elements, sameInstance(elements))
            assertThat(layer.elements.content, equalTo(mapOf(marker)))
            verify(listener, times(3)).layerVisibilityChanged(layer, layerPos)
        }

        verifyNoMoreInteractions(provider)
        verifyNoMoreInteractions(query)
    }

    @Test
    fun doesNotReloadElementsForHiddenLayerWhenMapBoundsChange() {

        val bounds = bounds(33.0, 50.0, 2.0)
        val elements = mapOf(MapMarkerSpec("m1", null, MarkerOptions()))
        val provider = providerForLayer(allLayerDescs[0])
        val query = mock<LayerQuery> {
            on { fetchMapElements(bounds) }.thenReturn(elements)
        }
        whenever(provider.createQueryForLayer(allLayerDescs[0])).thenReturn(query)

        waitForMainThreadToRun {
            model.mapBoundsChanged(bounds)
            mapData.value = Resource.success(allResources)
            model.setLayerVisible(model.layerAt(0), true)
        }

        onMainThread.assertThatWithin(testTimeout, { model.layerAt(0).elements.status }, equalTo(Success))

        assertThat(model.layerAt(0).elements.content, equalTo(elements))
        verify(provider).createQueryForLayer(allLayerDescs[0])
        verify(query).fetchMapElements(bounds)

        waitForMainThreadToRun {
            model.setLayerVisible(model.layerAt(0), false)
            model.mapBoundsChanged(bounds(0.0, 0.0, 2.0))

            assertThat(model.layerAt(0).elements.status, equalTo(Success))
            assertThat(model.layerAt(0).elements.content, equalTo(elements))
        }

        waitForThreadPoolTermination()

        verifyNoMoreInteractions(provider)
        verifyNoMoreInteractions(query)
    }

    @Test
    fun doesNotReloadElementsWhenLayerDoesNotHaveDynamicElements() {
        fail("unimplemented")
    }

    @Test
    fun doesNotReloadElementsWhenLayerHasDynamicElementsAndDoesNotSupportDynamicQueries() {
        fail("unimplemented")
    }

    @Test
    fun layerRemovedWhileFetchingElementsConcurrently() {

        val control = ConcurrentMethodControl(testTimeout)
        val elements = mapOf(MapMarkerSpec("m1", null, MarkerOptions()))
        val query = mock<LayerQuery> {
            on { fetchMapElements(any()) }.then { _ ->
                control.enterAndAwaitRelease()
                elements
            }
        }
        val provider = providerForLayer(allLayerDescs[0])
        whenever(provider.createQueryForLayer(allLayerDescs[0])).thenReturn(query)

        waitForMainThreadToRun {
            mapData.value = Resource.success(allResources)
            model.mapBoundsChanged(bounds(38.0, 53.0, 1.0))
            model.setLayerVisible(model.layerAt(0), true)
            verify(listener).layerVisibilityChanged(model.layerAt(0), 0)
        }

        control.waitUntilBlocked()

        waitForMainThreadToRun {
            mapData.value = Resource.success(allResources.minus(res1.uri))
        }

        control.release()

        waitForThreadPoolTermination()

        assertThat(model.layersInZOrder.value!!.requireContent(), not(anyOf(hasItem(res1Layer1), hasItem(res1Layer2))))
        verifyNoMoreInteractions(listener)
        argumentCaptor<Resource<List<Layer>>> {
            verify(observer, times(2)).onChanged(capture())
            assertThat(allValues[0].requireContent(), hasSize(5))
            assertThat(allValues[1].requireContent(), hasSize(3))
        }
    }

    @Test
    fun mapBoundsChangeWhileLayerElementsFetchingConcurrently() {
        fail("unimplemented")
    }

    @Test
    fun createsLayerQueryWhenElementFetchGetsPreempted() {
        fail("unimplemented")
    }

    @Test
    @UiThreadTest
    fun zOrderShiftNoop() {

        mapData.value = Resource.success(allResources)

        verify(observer).onChanged(model.layersInZOrder.value)

        val layers = model.layersInZOrder.value!!.content!!
        model.moveZIndex(0, 0)

        verify(listener, never()).zOrderShift(any())
        verifyNoMoreInteractions(observer)
        assertThat(layers, sameInstance(model.layersInZOrder.value!!.content!!))
    }

    @Test
    @UiThreadTest
    fun zOrderShiftTopToBottom() {

        mapData.value = Resource.success(allResources)

        verify(observer).onChanged(model.layersInZOrder.value)

        val before = model.layersInZOrder.value!!.content!!
        val beforeCopy = before.copy()
        model.moveZIndex(0, before.lastIndex)

        verify(listener).zOrderShift(0..before.lastIndex)
        verifyNoMoreInteractions(observer)

        val after = model.layersInZOrder.value!!.content!!
        assertThat(before, sameInstance(after))
        assertThat(after.size, equalTo(beforeCopy.size))
        assertThat(after.last(), equalTo(beforeCopy.first()))
        assertThat(after.first(), equalTo(beforeCopy[1]))
        assertThat(after.subList(0, beforeCopy.lastIndex - 1), equalTo(beforeCopy.subList(1, beforeCopy.lastIndex)))
        assertZIndexesMatchInversePositions(after)
    }

    @Test
    @UiThreadTest
    fun zOrderShiftBottomToTop() {

        mapData.value = Resource.success(allResources)
        val before = model.layersInZOrder.value!!.content!!.copy()
        model.moveZIndex(before.lastIndex, 0)

        verify(listener).zOrderShift(0..before.lastIndex)

        val after = model.layersInZOrder.value!!.content!!
        assertThat(after.size, equalTo(before.size))
        assertThat(after.first(), equalTo(before.last()))
        assertThat(after.last(), equalTo(before[before.lastIndex - 1]))
        assertThat(after.subList(1, after.lastIndex), equalTo(before.subList(0, before.lastIndex - 1)))
        assertZIndexesMatchInversePositions(after)
    }

    @Test
    @UiThreadTest
    fun zOrderShiftTopToMiddle() {

        mapData.value = Resource.success(allResources)
        val before = model.layersInZOrder.value!!.content!!.copy()
        model.moveZIndex(0, 2)

        verify(listener).zOrderShift(0..2)

        val after = model.layersInZOrder.value!!.content!!
        assertThat(after.size, equalTo(before.size))
        assertThat(after[0], equalTo(before[1]))
        assertThat(after[1], equalTo(before[2]))
        assertThat(after[2], equalTo(before[0]))
        assertThat(after.subList(3, before.size), equalTo(before.subList(3, before.size)))
        assertZIndexesMatchInversePositions(after)
    }

    @Test
    @UiThreadTest
    fun zOrderShiftBottomToMiddle() {

        mapData.value = Resource.success(allResources)
        val before = model.layersInZOrder.value!!.content!!.copy()
        model.moveZIndex(4, 2)

        verify(listener).zOrderShift(2..4)

        val after = model.layersInZOrder.value!!.content!!
        assertThat(after.size, equalTo(before.size))
        assertThat(after[2], equalTo(before[4]))
        assertThat(after[3], equalTo(before[2]))
        assertThat(after[4], equalTo(before[3]))
        assertThat(after.subList(0, 2), equalTo(before.subList(0, 2)))
        assertZIndexesMatchInversePositions(after)
    }

    @Test
    @UiThreadTest
    fun zOrderShiftMiddleToTop() {

        mapData.value = Resource.success(allResources)
        val before = model.layersInZOrder.value!!.content!!.copy()
        model.moveZIndex(2, 0)

        verify(listener).zOrderShift(0..2)

        val after = model.layersInZOrder.value!!.content!!
        assertThat(after.size, equalTo(before.size))
        assertThat(after[0], equalTo(before[2]))
        assertThat(after[1], equalTo(before[0]))
        assertThat(after[2], equalTo(before[1]))
        assertThat(after.subList(3, 5), equalTo(before.subList(3, 5)))
        assertZIndexesMatchInversePositions(after)
    }

    @Test
    @UiThreadTest
    fun zOrderShiftMiddleToBottom() {

        mapData.value = Resource.success(allResources)
        val before = model.layersInZOrder.value!!.content!!.copy()
        model.moveZIndex(2, 4)

        verify(listener).zOrderShift(2..4)

        val after = model.layersInZOrder.value!!.content!!
        assertThat(after.size, equalTo(before.size))
        assertThat(after[4], equalTo(before[2]))
        assertThat(after[3], equalTo(before[4]))
        assertThat(after[2], equalTo(before[3]))
        assertThat(after.subList(0, 2), equalTo(before.subList(0, 2)))
        assertZIndexesMatchInversePositions(after)
    }

    @Test
    @UiThreadTest
    fun zOrderShiftMiddleToLower() {

        mapData.value = Resource.success(allResources)
        val before = model.layersInZOrder.value!!.content!!.copy()
        model.moveZIndex(1, 3)

        verify(listener).zOrderShift(1..3)

        val after = model.layersInZOrder.value!!.content!!
        assertThat(after.size, equalTo(before.size))
        assertThat(after[3], allOf(equalTo(before[1]), not(sameInstance(before[1]))))
        assertThat(after[2], allOf(equalTo(before[3]), not(sameInstance(before[3]))))
        assertThat(after[1], allOf(equalTo(before[2]), not(sameInstance(before[2]))))
        assertThat(after[0], sameInstance(before[0]))
        assertThat(after[4], sameInstance(before[4]))
        assertZIndexesMatchInversePositions(after)
    }

    @Test
    @UiThreadTest
    fun zOrderShiftMiddleToHigher() {

        mapData.value = Resource.success(allResources)
        val before = model.layersInZOrder.value!!.content!!.copy()
        model.moveZIndex(3, 1)

        verify(listener).zOrderShift(1..3)

        val after = model.layersInZOrder.value!!.content!!
        assertThat(after.size, equalTo(before.size))
        assertThat(after[1], allOf(equalTo(before[3]), not(sameInstance(before[3]))))
        assertThat(after[3], allOf(equalTo(before[2]), not(sameInstance(before[2]))))
        assertThat(after[2], allOf(equalTo(before[1]), not(sameInstance(before[1]))))
        assertThat(after[0], sameInstance(before[0]))
        assertThat(after[4], sameInstance(before[4]))
        assertZIndexesMatchInversePositions(after)
    }
}