package mil.nga.giat.mage.ui.geojson

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import mil.nga.giat.mage.map.StaticFeatureMapState
import mil.nga.giat.mage.ui.map.FeatureAction
import mil.nga.giat.mage.ui.map.BottomSheetContent
import mil.nga.giat.mage.ui.theme.MageTheme3
import mil.nga.sf.Geometry

sealed class GeoJsonAction {
   class Details(val key: GeoJsonFeatureKey)
   class Directions(val geometry: Geometry, val icon: Any?): GeoJsonAction()
   class Location(val geometry: Geometry): GeoJsonAction()
}

@Composable
fun GeoJsonSummary(
   featureMapState: StaticFeatureMapState?,
   onAction: (Any) -> Unit
) {
   if (featureMapState != null) {
      MageTheme3 {
         Surface(
            color = MaterialTheme.colorScheme.surfaceVariant
         ) {
            Column {
               BottomSheetContent(
                  featureMapState,
                  onAction = { action ->
                     when (action) {
                        is FeatureAction.Details<*> -> {
                           onAction(
                              GeoJsonAction.Details(featureMapState.id)
                           )
                        }
                        is FeatureAction.Directions<*> -> {
                           onAction(
                              GeoJsonAction.Directions(
                                 action.geometry,
                                 action.image
                              )
                           )
                        }
                        is FeatureAction.Location -> {
                           onAction(GeoJsonAction.Location(action.geometry))
                        }
                     }
                  }
               )
            }
         }
      }
   }
}