package mil.nga.giat.mage.map.view

import android.arch.lifecycle.MutableLiveData
import android.support.test.runner.AndroidJUnit4
import mil.nga.giat.mage.data.Resource
import mil.nga.giat.mage.map.cache.MapDataManager
import mil.nga.giat.mage.map.cache.MapDataResource
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.net.URI


@RunWith(AndroidJUnit4::class)
class MapLayersViewModelTest {

    @Mock
    lateinit var mapDataManager: MapDataManager

    lateinit var mapData: MutableLiveData<Resource<Map<URI, MapDataResource>>>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        `when`(mapDataManager.mapData).thenReturn(mapData)
    }

    @Test
    fun startsWithLayersInAlphanumericOrderGroupedByResource() {

        mapData.value = 
    }
}