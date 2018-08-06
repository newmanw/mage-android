package mil.nga.giat.mage.map.view

import android.arch.lifecycle.*
import android.support.test.annotation.UiThreadTest
import android.support.test.runner.AndroidJUnit4
import com.nhaarman.mockitokotlin2.*
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.map.cache.*
import mil.nga.giat.mage.test.AsyncTesting
import mil.nga.giat.mage.test.AsyncTesting.waitForMainThreadToRun
import org.hamcrest.Matchers.contains
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.net.URI


@RunWith(AndroidJUnit4::class)
class MapLayersViewModelTest : LifecycleOwner {

    abstract class TestRepo : MapDataRepository()
    abstract class TestProvider : MapDataProvider
    class TestLayerDesc(layerName: String, resourceUri: URI, dataType: MapDataProvider) : MapLayerDescriptor(layerName, resourceUri, dataType.javaClass)

    @Rule
    @JvmField
    val testName = TestName()

    @Rule
    @JvmField
    val onMainThread = AsyncTesting.MainLooperAssertion()

    lateinit var mapDataManager: MapDataManager
    lateinit var repo1: TestRepo
    lateinit var provider1: TestProvider
    lateinit var provider2: TestProvider

    lateinit var mapData: MutableLiveData<Resource<Map<URI, MapDataResource>>>
    lateinit var res1: MapDataResource
    lateinit var res1Layer1: TestLayerDesc
    lateinit var res1Layer2: TestLayerDesc
    lateinit var res2: MapDataResource
    lateinit var res2Layer1: TestLayerDesc
    lateinit var res2Layer2: TestLayerDesc
    lateinit var res3: MapDataResource
    lateinit var res3Layer1: TestLayerDesc
    lateinit var allResources: Map<URI, MapDataResource>
    lateinit var observer: Observer<Resource<List<MapLayersViewModel.Layer>>>
    lateinit var observed: KArgumentCaptor<Resource<List<MapLayersViewModel.Layer>>>
    lateinit var lifecycle: LifecycleRegistry

    val testTimeout = 1000L

    override fun getLifecycle(): Lifecycle {
        return lifecycle
    }

    @Before
    fun setup() {

        repo1 = mock()
        provider1 = mock()
        provider2 = mock()

        mapDataManager = mock()
        mapData = MutableLiveData()
        whenever(mapDataManager.mapData).thenReturn(mapData)
        whenever(mapDataManager.layers).then { mapData.value?.content?.values?.
            flatMap({ it.layers.values })?.associateBy({ it.layerUri })?.toMutableMap() ?: emptyMap<URI, MapLayerDescriptor>() }
        whenever(mapDataManager.resourceForLayer(any())).then { mapData.value?.content?.get((it.getArgument(0) as MapLayerDescriptor).resourceUri)}

        val uri1 = URI("test", javaClass.simpleName, "/${testName.methodName}/res1",null)
        res1Layer1 = TestLayerDesc("res1.layer1", uri1, provider1)
        res1Layer2 = TestLayerDesc("res1.layer2", uri1, provider1)
        res1 = MapDataResource(uri1, repo1, 0, MapDataResource.Resolved("Test Resource 1", provider1.javaClass,
            setOf(res1Layer1, res1Layer2)))
        val uri2 = URI("test", javaClass.simpleName, "/${testName.methodName}/res2", null)
        res2Layer1 = TestLayerDesc("res2.layer1", uri2, provider2)
        res2Layer2 = TestLayerDesc("res2.layer2", uri2, provider2)
        res2 = MapDataResource(uri2, repo1, 0, MapDataResource.Resolved("Test Resource 2", provider2.javaClass,
            setOf(res2Layer1, res2Layer2)))
        val uri3 = URI("test", javaClass.simpleName, "/${testName.methodName}/res3", null)
        res3Layer1 = TestLayerDesc("res3.layer1", uri3, provider1)
        res3 = MapDataResource(uri3, repo1, 0, MapDataResource.Resolved("Test Resource 3", provider1.javaClass, setOf(res3Layer1)))
        allResources = setOf(res1, res2, res3).associateBy(MapDataResource::uri)

        observer = mock()
        observed = argumentCaptor()
        lifecycle = LifecycleRegistry(this)
        lifecycle.markState(Lifecycle.State.RESUMED)
    }

    @Test
    @UiThreadTest
    fun startsWithLayersInAlphanumericOrderGroupedByResourceUri() {

        val model = MapLayersViewModel(mapDataManager)
        model.layersInZOrder.observe(this, observer)
        mapData.value = Resource.success(allResources)

        assertThat(model.layersInZOrder.value!!.content!!.map(MapLayersViewModel.Layer::desc),
            contains<MapLayerDescriptor>(res1Layer1, res1Layer2, res2Layer1, res2Layer2, res3Layer1))
    }
}