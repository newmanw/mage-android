package mil.nga.giat.mage.data.repository.map

import android.app.Application
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import com.google.android.gms.maps.GoogleMap
import com.google.maps.android.compose.MapType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import mil.nga.giat.mage.R
import javax.inject.Inject

class MapRepository @Inject constructor(
   val application: Application,
   val sharedPreferences: SharedPreferences
) {
   val baseMapType: Flow<MapType> = callbackFlow {
      val listener = OnSharedPreferenceChangeListener { preferences, key ->
         try {
            if (key === application.getString(R.string.baseLayerKey)) {
               trySend(getMapType(preferences))
            }
         } catch (_: Throwable) { }
      }

      sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

      trySend(getMapType(sharedPreferences))

      awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
   }

   val mapCenter: Flow<Boolean> = callbackFlow {
      val mapCenterKey = application.getString(R.string.showMapCenterCoordinateKey)
      val listener = OnSharedPreferenceChangeListener { preferences, key ->
         try {
            if (key == mapCenterKey) {
               trySend(preferences.getBoolean(key, false))
            }
         } catch (_: Throwable) { }
      }

      sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

      trySend(sharedPreferences.getBoolean(mapCenterKey, false))

      awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
   }

   val mgrs: Flow<Boolean> = callbackFlow {
      val mgrsKey = application.getString(R.string.showMGRSKey)
      val listener = OnSharedPreferenceChangeListener { preferences, key ->
         try {
            if (key == mgrsKey) {
               trySend(preferences.getBoolean(key, false))
            }
         } catch (_: Throwable) { }
      }

      sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

      trySend(sharedPreferences.getBoolean(mgrsKey, false))

      awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
   }

   val gars: Flow<Boolean> = callbackFlow {
      val garsKey = application.getString(R.string.showGARSKey)
      val listener = OnSharedPreferenceChangeListener { preferences, key ->
         try {
            if (key == garsKey) {
               trySend(preferences.getBoolean(key, false))
            }
         } catch (_: Throwable) { }
      }

      sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

      trySend(sharedPreferences.getBoolean(garsKey, false))

      awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
   }

   private fun getMapType(preferences: SharedPreferences): MapType {
      return when (preferences.getInt(application.getString(R.string.baseLayerKey), GoogleMap.MAP_TYPE_NORMAL)) {
         GoogleMap.MAP_TYPE_NORMAL -> MapType.NORMAL
         GoogleMap.MAP_TYPE_SATELLITE -> MapType.NORMAL
         GoogleMap.MAP_TYPE_TERRAIN -> MapType.NORMAL
         GoogleMap.MAP_TYPE_HYBRID -> MapType.NORMAL
         else -> MapType.NONE
      }
   }
}