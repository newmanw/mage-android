package mil.nga.giat.mage.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.ContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.glide.rememberGlidePainter
import com.google.android.gms.maps.model.LatLng
import mil.nga.giat.mage.map.FeatureMapState
import mil.nga.giat.mage.ui.coordinate.CoordinateTextButton
import mil.nga.giat.mage.ui.sheet.DragHandle
import mil.nga.giat.mage.ui.theme.MageTheme3
import mil.nga.sf.Geometry

sealed class FeatureAction<T: Any> {
   class Directions<T: Any>(val id: T, val geometry: Geometry, val image: Any?): FeatureAction<Any>()
   class Location(val geometry: Geometry): FeatureAction<Any>()
   class Details<T: Any>(val id: T): FeatureAction<T>()
}

val LocalHeaderColor = compositionLocalOf { Color.Unspecified }

@Composable
fun <I: Any> BottomSheetContent(
   featureMapState: FeatureMapState<I>?,
   header: (@Composable () -> Unit)? = null,
   headerColor: Color? = null,
   actions: (@Composable () -> Unit)? = null,
   onAction: ((Any) -> Unit)? = null
) {
   MageTheme3 {
      Surface {
         FeatureContent(
            featureMapState = featureMapState,
            header = header,
            headerColor = headerColor ?: MaterialTheme.colorScheme.surface,
            actions = actions,
            onAction = onAction
         )
      }
   }
}

@Composable
private fun <I: Any> FeatureContent(
   featureMapState: FeatureMapState<I>?,
   header: (@Composable () -> Unit)? = null,
   headerColor: Color,
   actions: (@Composable () -> Unit)? = null,
   onAction: ((Any) -> Unit)? = null
) {
   if (featureMapState != null) {
      Column {
         CompositionLocalProvider(LocalHeaderColor provides headerColor) {
            DragHandle()
            header?.invoke()
         }

         FeatureHeaderContent(featureMapState, actions, onAction)

         OutlinedButton(
            modifier = Modifier
               .fillMaxWidth()
               .padding(all = 16.dp),
            onClick = {
               onAction?.invoke(FeatureAction.Details(featureMapState.id))
            }
         ) {
            Text(text = "More Details")
         }
      }
   }
}

@Composable
private fun <I: Any> FeatureHeaderContent(
   featureMapState: FeatureMapState<I>,
   actions: (@Composable () -> Unit)? = null,
   onAction: ((Any) -> Unit)?
) {
   Row(
      Modifier
         .padding(top = 8.dp)
         .fillMaxWidth()
   ) {
      Column {
         Row(Modifier.padding(start = 16.dp)) {
            Column(
               Modifier
                  .weight(1f)
                  .padding(end = 16.dp)
            ) {
               if (featureMapState.title != null) {
                  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                     Text(
                        text = featureMapState.title.uppercase(),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 16.dp)
                     )
                  }
               }

               if (featureMapState.primary?.isNotEmpty() == true) {
                  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                     Text(
                        text = featureMapState.primary,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                     )
                  }
               }

               if (featureMapState.secondary?.isNotEmpty() == true) {
                  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                     Text(
                        text = featureMapState.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                     )
                  }
               }
            }

            featureMapState.image?.let { image ->
               FeatureIcon(image)
            }
         }

         FeatureActions(featureMapState, actions, onAction)
      }
   }
}

@Composable
private fun <I: Any> FeatureActions(
   featureMapState: FeatureMapState<I>,
   actions: (@Composable () -> Unit)? = null,
   onAction: ((Any) -> Unit)? = null
) {
   Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth()
   ) {
      featureMapState.geometry?.let { geometry ->
         CoordinateTextButton(
            latLng = LatLng(geometry.centroid.y, geometry.centroid.x),
            icon = {
               Icon(
                  imageVector = Icons.Default.MyLocation,
                  contentDescription = "Location",
                  modifier = Modifier.size(16.dp)
               )
            },
            onCopiedToClipboard = {
               onAction?.invoke(FeatureAction.Location(geometry))
            }
         )
      }

      Row {
         actions?.invoke()

         featureMapState.geometry?.let { geometry ->
            IconButton(
               modifier = Modifier.padding(end = 8.dp),
               onClick = {
                  onAction?.invoke(
                     FeatureAction.Directions(
                        featureMapState.id,
                        geometry,
                        featureMapState.image
                     )
                  )
               }
            ) {
               Icon(
                  imageVector = Icons.Outlined.Directions,
                  contentDescription = "Directions",
                  tint = MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.medium)
               )
            }
         }
      }
   }
}

@Composable
private fun FeatureIcon(image: Any) {
   Box(modifier = Modifier
      .padding(end = 8.dp)
      .width(64.dp)
      .height(64.dp)
   ) {
      Image(
         painter = rememberGlidePainter(
            image
         ),
         contentDescription = "Observation Map Icon",
         Modifier.fillMaxSize()
      )
   }
}