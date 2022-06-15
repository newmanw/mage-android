package mil.nga.giat.mage.coordinate

import android.content.Context
import androidx.preference.PreferenceManager
import com.google.android.gms.maps.model.LatLng
import mil.nga.giat.mage.R
import java.text.DecimalFormat

class CoordinateFormatter(context: Context) {
   private val coordinateSystem: CoordinateSystem
   private val latLngFormat = DecimalFormat("###.00000")

   fun format(latLng: LatLng): String {
      return when (coordinateSystem) {
         CoordinateSystem.WGS84 -> {
            latLngFormat.format(latLng.latitude) + ", " + latLngFormat.format(latLng.longitude)
         }
         CoordinateSystem.MGRS -> {
            val mgrs = mil.nga.mgrs.features.Point.create(latLng.longitude, latLng.latitude).toMGRS()
            mgrs.coordinate()
         }
         CoordinateSystem.DMS -> {
            DMS.from(latLng).format()
         }
      }
   }

   init {
      val value = PreferenceManager.getDefaultSharedPreferences(context).getInt(
         context.getString(R.string.coordinateSystemViewKey),
         context.resources.getInteger(R.integer.coordinateSystemViewDefaultValue)
      )

      coordinateSystem = CoordinateSystem.fromPreference(value)
   }
}