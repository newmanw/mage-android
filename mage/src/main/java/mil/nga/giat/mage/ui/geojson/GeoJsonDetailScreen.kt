package mil.nga.giat.mage.ui.geojson

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import mil.nga.geopackage.map.geom.GoogleMapShapeConverter
import mil.nga.giat.mage.R
import mil.nga.giat.mage.database.model.feature.StaticFeature
import mil.nga.giat.mage.map.annotation.AnnotationStyle
import mil.nga.giat.mage.map.annotation.IconStyle
import mil.nga.giat.mage.map.annotation.ShapeStyle
import mil.nga.giat.mage.sdk.utils.ISO8601DateFormatFactory
import mil.nga.giat.mage.ui.coordinate.CoordinateTextButton
import mil.nga.giat.mage.ui.directions.DirectionsDialog
import mil.nga.giat.mage.ui.directions.NavigationType
import mil.nga.giat.mage.ui.map.camera.getCameraUpdate
import mil.nga.giat.mage.ui.theme.MageTheme3
import mil.nga.giat.mage.utils.DateFormatFactory
import mil.nga.sf.Geometry
import mil.nga.sf.GeometryType
import java.text.DateFormat
import java.util.Locale

data class GeoJsonFeatureKey(
   val layerId: Long,
   val featureId: Long
)

@Composable
fun GeoJsonDetailScreen(
   key: GeoJsonFeatureKey,
   onClose: () -> Unit,
   onNavigate: (NavigationType, StaticFeature) -> Unit,
   viewModel: GeoJsonViewModel = hiltViewModel()
) {
   val baseMap by viewModel.baseMap.observeAsState()
   val feature by viewModel.feature.observeAsState()
   var openDirectionsDialog by remember { mutableStateOf(false) }

   LaunchedEffect(key) {
      viewModel.setKey(key)
   }

   MageTheme3 {
      Scaffold(
         topBar = {
            TopBar(
               title = feature?.propertiesMap?.get("name")?.value,
               onClose = { onClose() }
            )
         },
         content = { paddingValues ->
            Surface(
               color = MaterialTheme.colorScheme.surfaceVariant,
               modifier = Modifier
                  .padding(paddingValues)
                  .fillMaxHeight()
                  .verticalScroll(rememberScrollState())
            ) {
               GeoJsonDetail(
                  baseMap = baseMap,
                  feature = feature,
                  onDirections = {
                     openDirectionsDialog = true
                  }
               )
            }
         }
      )

      DirectionsDialog(
         open = openDirectionsDialog,
         onDismiss = { openDirectionsDialog = false },
         onDirections = { type ->
            feature?.let {
               onNavigate(type, it)
               onClose()
            }
         }
      )
   }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
   title: String?,
   onClose: () -> Unit
) {
   TopAppBar(
      title = {
         Column {
            Text(title ?: "")
         }
      },
      navigationIcon = {
         IconButton(onClick = { onClose.invoke() }) {
            Icon(Icons.Default.Close, "Cancel Default")
         }
      },
      colors = TopAppBarDefaults.topAppBarColors(
         containerColor = MaterialTheme.colorScheme.primary,
         actionIconContentColor = Color.White,
         titleContentColor = Color.White,
         navigationIconContentColor = Color.White
      )
   )
}

@Composable
private fun GeoJsonDetail(
   baseMap: MapType?,
   feature: StaticFeature?,
   onDirections: () -> Unit
) {
   if (feature != null) {
      Column {
         GeoJsonHeader(
            baseMap = baseMap,
            feature = feature,
            onDirections = onDirections
         )
         GeoJsonProperties(
            properties = feature.propertiesMap?.mapValues { (_, feature ) ->
               feature.value.toString()
            } ?: emptyMap()
         )
      }
   }
}

@Composable
private fun GeoJsonHeader(
   baseMap: MapType?,
   feature: StaticFeature,
   onDirections: () -> Unit
) {
   Card(
      Modifier
         .fillMaxWidth()
         .padding(vertical = 16.dp, horizontal = 8.dp)
   ) {
      Column {
         GeoJsonDetail(feature = feature)

         Map(
            baseMap = baseMap,
            geometry = feature.geometry,
            style = AnnotationStyle.fromStaticFeature(
               feature = feature,
               context = LocalContext.current
            )
         )

         feature.geometry?.let { geometry ->
            CoordinateTextButton(
               latLng = LatLng(geometry.centroid.y, geometry.centroid.x),
               icon = {
                  Icon(
                     imageVector = Icons.Default.MyLocation,
                     contentDescription = "Location",
                     modifier = Modifier.size(16.dp)
                  )
               },
               onCopiedToClipboard = {}
            )
         }

         Divider(Modifier.fillMaxWidth())

         Actions(
            feature = feature,
            onDirections = onDirections
         )
      }
   }
}

@Composable
private fun GeoJsonDetail(
   feature: StaticFeature
) {
   val properties = feature.propertiesMap

   val dateFormat: DateFormat =
      DateFormatFactory.format("yyyy-MM-dd HH:mm zz", Locale.getDefault(), LocalContext.current)
   val timestamp = properties["timestamp"]?.value?.let { timestamp ->
      try {
         ISO8601DateFormatFactory.ISO8601().parse(timestamp)?.let { date ->
            dateFormat.format(date)
         }
      } catch (e: Exception) { null }
   }

   val featureName = properties["name"]?.value
   val layerName = feature.layer.name

   Column(Modifier.padding(all = 16.dp)) {
      if (timestamp != null) {
         CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Text(
               text = timestamp.uppercase(),
               fontWeight = FontWeight.SemiBold,
               style = MaterialTheme.typography.labelSmall,
               maxLines = 1,
               overflow = TextOverflow.Ellipsis,
               modifier = Modifier.padding(bottom = 16.dp)
            )
         }
      }

      if (featureName?.isNotEmpty() == true) {
         CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Text(
               text = featureName,
               style = MaterialTheme.typography.titleLarge,
               maxLines = 1,
               overflow = TextOverflow.Ellipsis
            )
         }
      }

      if (layerName?.isNotEmpty() == true) {
         CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Text(
               text = layerName,
               style = MaterialTheme.typography.bodyMedium,
               maxLines = 1,
               overflow = TextOverflow.Ellipsis,
            )
         }
      }
   }
}

@Composable
private fun Actions(
   feature: StaticFeature,
   onDirections: () -> Unit
) {
   Row(
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth()
   ) {
      Row {
         feature.geometry?.let {
            IconButton(
               modifier = Modifier.padding(end = 8.dp),
               onClick = { onDirections() }
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
private fun Map(
   baseMap: MapType?,
   geometry: Geometry,
   style:AnnotationStyle
) {
   val cameraPositionState = rememberCameraPositionState()

   LaunchedEffect(geometry) {
      cameraPositionState.move(update = geometry.getCameraUpdate())
   }

   val uiSettings = MapUiSettings(
      zoomControlsEnabled = false,
      compassEnabled = false
   )

   val mapStyleOptions = if (isSystemInDarkTheme()) {
      MapStyleOptions.loadRawResourceStyle(LocalContext.current, R.raw.map_theme_night)
   } else null

   val properties = baseMap?.let { mapType ->
      MapProperties(
         mapType = mapType,
         mapStyleOptions = mapStyleOptions
      )
   } ?: MapProperties()

   GoogleMap(
      cameraPositionState = cameraPositionState,
      properties = properties,
      uiSettings = uiSettings,
      modifier = Modifier
         .height(150.dp)
         .fillMaxWidth()
   ) {
      if (geometry.geometryType == GeometryType.POINT) {
         val point = LatLng(geometry.centroid.y, geometry.centroid.x)

         val defaultIcon = AppCompatResources.getDrawable(LocalContext.current, R.drawable.default_marker)!!.toBitmap()
         var icon by remember { mutableStateOf(BitmapDescriptorFactory.fromBitmap(defaultIcon))}
         val iconStyle = style as? IconStyle

         Glide.with(LocalContext.current)
            .asBitmap()
            .load(iconStyle?.uri)
            .into(object : CustomTarget<Bitmap>(100, 100) {
               override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                  icon = BitmapDescriptorFactory.fromBitmap(resource)
               }

               override fun onLoadCleared(placeholder: Drawable?) {}
            })

         Marker(
            state = MarkerState(position = point),
            icon = icon
         )
      } else {
         val shape = GoogleMapShapeConverter().toShape(geometry).shape
         val shapeStyle = style as? ShapeStyle
         val strokeWidth = shapeStyle?.strokeWidth ?: 10f
         val strokeColor = shapeStyle?.strokeColor?.let { Color(it) } ?: Color.Black
         val fillColor = shapeStyle?.fillColor?.let { Color(it) } ?: Color.Black

         if (shape is PolylineOptions) {
            Polyline(
               points = shape.points,
               width = strokeWidth,
               color = strokeColor
            )
         } else if (shape is PolygonOptions) {
            Polygon(
               points = shape.points,
               holes = shape.holes,
               strokeWidth = strokeWidth,
               strokeColor = strokeColor,
               fillColor = fillColor
            )
         }
      }
   }
}

@Composable
private fun GeoJsonProperties(
   properties: Map<String, String>
) {
   CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
      Text(
         text = "Additional Information",
         style = MaterialTheme.typography.titleMedium,
         modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
      )
   }

   Card(
      Modifier
         .fillMaxWidth()
         .padding(all = 8.dp)
   ) {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
         CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Text(
               text = "Description",
               style = MaterialTheme.typography.bodyMedium,
               modifier = Modifier.padding(bottom = 4.dp)
            )
         }

         val description = properties["description"]
            AndroidView(
               factory = { context -> TextView(context) },
               update = { it.text = HtmlCompat.fromHtml("<div>$description</div>", HtmlCompat.FROM_HTML_MODE_COMPACT) },
            )
      }
   }
}