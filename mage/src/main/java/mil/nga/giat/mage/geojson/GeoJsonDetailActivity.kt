package mil.nga.giat.mage.geojson

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import mil.nga.giat.mage.database.model.feature.StaticFeature
import mil.nga.giat.mage.ui.directions.NavigationType
import mil.nga.giat.mage.ui.geojson.GeoJsonDetailScreen
import mil.nga.giat.mage.ui.geojson.GeoJsonFeatureKey
import mil.nga.giat.mage.utils.googleMapsUri

@AndroidEntryPoint
class GeoJsonDetailActivity : AppCompatActivity() {
   enum class ResultType { NAVIGATE }

   public override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)

      require(intent.hasExtra(LAYER_ID_EXTRA)) { "LAYER_ID_EXTRA is required to launch FormDefaultActivity" }
      require(intent.hasExtra(FEATURE_ID_EXTRA)) { "FEATURE_ID_EXTRA is required to launch FormDefaultActivity" }

      setContent {
         GeoJsonDetailScreen(
            key = GeoJsonFeatureKey(
               layerId = intent.getLongExtra(LAYER_ID_EXTRA, 0),
               featureId = intent.getLongExtra(FEATURE_ID_EXTRA, 0)
            ),
            onClose = { finish() },
            onNavigate = { type, feature ->
               onNavigate(type, feature)
            }
         )
      }
   }

   private fun onNavigate(type: NavigationType, feature: StaticFeature) {
      when (type) {
         NavigationType.Map -> {
            val intent = Intent(Intent.ACTION_VIEW, feature.geometry.googleMapsUri())
            startActivity(intent)
         }
         NavigationType.Bearing -> {
            val data = Intent()
            data.putExtra(RESULT_TYPE_EXTRA, ResultType.NAVIGATE)
            data.putExtra(LAYER_ID_EXTRA, feature.layer.id)
            data.putExtra(FEATURE_ID_EXTRA, feature.id)
            setResult(Activity.RESULT_OK, data)

            finish()
         }
      }
   }

   companion object {
      const val RESULT_TYPE_EXTRA = "RESULT_TYPE_EXTRA"
      const val LAYER_ID_EXTRA = "LAYER_ID_EXTRA"
      const val FEATURE_ID_EXTRA = "FEATURE_ID_EXTRA"

      fun intent(context: Context, layerId: Long, featureId: Long): Intent {
         val intent = Intent(context, GeoJsonDetailActivity::class.java)
         intent.putExtra(LAYER_ID_EXTRA, layerId)
         intent.putExtra(FEATURE_ID_EXTRA, featureId)
         return intent
      }
   }
}
