package mil.nga.giat.mage.ui.feed.item

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.maps.CameraUpdateFactory
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
import mil.nga.giat.mage.coordinate.CoordinateFormatter
import mil.nga.giat.mage.ui.theme.MageTheme
import mil.nga.giat.mage.ui.theme.topAppBarBackground
import mil.nga.sf.Geometry
import mil.nga.sf.GeometryType
import mil.nga.sf.util.GeometryUtils
import java.util.*

data class FeedItemKey(
   val feedId: String,
   val feedItemId: String,
)

sealed class FeedItemAction {
   class Location(val location: String) : FeedItemAction()
   class Directions(val feedItem: FeedItemState) : FeedItemAction()
}

@Composable
fun FeedItemScreen(
   feedItemKey: FeedItemKey,
   onClose: () -> Unit,
   onDirections: (FeedItemState) -> Unit,
   viewModel: FeedItemViewModel = hiltViewModel()
) {
   val baseMap by viewModel.baseMap.observeAsState()
   val itemState by viewModel.feedItem.observeAsState()
   val snackbarState by viewModel.snackbar.collectAsState()
   val scaffoldState = rememberScaffoldState()

   LaunchedEffect(feedItemKey) {
      viewModel.setFeedItem(key = feedItemKey)
   }

   LaunchedEffect(scaffoldState.snackbarHostState, snackbarState) {
      if (snackbarState.message.isNotEmpty()) {
         scaffoldState.snackbarHostState.showSnackbar(snackbarState.message, duration = SnackbarDuration.Short)
      }
   }

   MageTheme {
      Scaffold(
         scaffoldState = scaffoldState,
         topBar = {
            FeedItemTopBar() { onClose() }
         },
         content = { paddingValues ->
            Column(Modifier.padding(paddingValues)) {
               FeedItemContent(
                  baseMap = baseMap,
                  itemState = itemState,
                  onAction = { action ->
                     when (action) {
                        is FeedItemAction.Directions -> {
                           onDirections(action.feedItem)
                        }
                        is FeedItemAction.Location -> {
                           viewModel.copyToClipBoard(action.location)
                        }
                     }
                  }
               )
            }
         }
      )
   }
}

@Composable
fun FeedItemTopBar(
   onClose: () -> Unit
) {
   TopAppBar(
      backgroundColor = MaterialTheme.colors.topAppBarBackground,
      contentColor = Color.White,
      title = {},
      navigationIcon = {
         IconButton(onClick = { onClose.invoke() }) {
            Icon(Icons.Default.ArrowBack, "Back To MAGE")
         }
      }
   )
}

@Composable
fun FeedItemContent(
   baseMap: MapType?,
   itemState: FeedItemState?,
   onAction: ((FeedItemAction) -> Unit)? = null
) {
   if (itemState != null) {
      Column(
         modifier = Modifier
            .background(Color(0x19000000))
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
      ) {
         if (itemState.date?.isNotEmpty() == true || itemState.primary?.isNotEmpty() == true || itemState.secondary?.isNotEmpty() == true) {
            FeedItemHeaderContent(
               baseMap = baseMap,
               itemState = itemState,
               onAction
            )
         }

         if (itemState.properties.isNotEmpty()) {
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
               Text(
                  text = "PROPERTIES",
                  style = MaterialTheme.typography.caption,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.padding(vertical = 16.dp)
               )
            }

            Card {
               Column {
                  for (property in itemState.properties) {
                     FeedItemProperty(property = property)
                     Divider(Modifier.padding(start = 16.dp))
                  }
               }
            }
         }
      }
   }
}

@Composable
fun FeedItemHeaderContent(
   baseMap: MapType?,
   itemState: FeedItemState,
   onAction: ((FeedItemAction) -> Unit)? = null
) {
   Card(
      Modifier
         .fillMaxWidth()
         .padding(vertical = 8.dp)
   ) {
      Column {
         Row {
            if (itemState.date != null || itemState.primary != null || itemState.secondary != null) {
               Column(
                  modifier = Modifier
                     .weight(1f)
                     .padding(16.dp),
                  verticalArrangement = Arrangement.Center,
               ) {
                  if (itemState.date != null) {
                     Row(modifier = Modifier.padding(bottom = 16.dp)) {
                        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                           Text(
                              text = itemState.date.uppercase(Locale.ROOT),
                              fontWeight = FontWeight.Bold,
                              style = MaterialTheme.typography.overline
                           )
                        }
                     }
                  }

                  if (itemState.primary != null) {
                     Row(modifier = Modifier.padding(bottom = 4.dp)) {
                        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
                           Text(
                              text = itemState.primary,
                              style = MaterialTheme.typography.h6,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis
                           )
                        }
                     }
                  }

                  if (itemState.secondary != null) {
                     Row {
                        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                           Text(
                              text = itemState.secondary,
                              style = MaterialTheme.typography.subtitle1,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis
                           )
                        }
                     }
                  }
               }
            }
         }

         itemState.geometry?.let { geometry ->
            Box(
               Modifier
                  .fillMaxWidth()
                  .height(150.dp)
            ) {
               FeedItemMapContent(baseMap, geometry, itemState.iconUrl)
            }
         }

         FeedItemActions(itemState, onAction)
      }
   }
}

@Composable
private fun FeedItemProperty(property: Pair<String, String>) {
   Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
      CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
         Text(
            modifier = Modifier.padding(bottom = 4.dp),
            text = property.first,
            style = MaterialTheme.typography.overline,
            fontWeight = FontWeight.Bold
         )
      }

      CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
         Text(
            text = property.second,
            style = MaterialTheme.typography.subtitle1
         )
      }
   }
}

@Composable
fun FeedItemActions(
   itemState: FeedItemState,
   onAction: ((FeedItemAction) -> Unit)? = null
) {
   Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
         .fillMaxWidth()
         .padding(vertical = 4.dp, horizontal = 8.dp)
   ) {
      itemState.geometry?.let { geometry ->
         val locationText = CoordinateFormatter(LocalContext.current).format(LatLng(geometry.centroid.y, geometry.centroid.x))
         Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
               .clip(MaterialTheme.shapes.medium)
               .height(48.dp)
               .clickable { onAction?.invoke(FeedItemAction.Location(locationText)) }
               .padding(8.dp)
         ) {
            Icon(
               imageVector = Icons.Default.GpsFixed,
               contentDescription = "Location",
               tint = MaterialTheme.colors.primary,
               modifier = Modifier
                  .height(24.dp)
                  .width(24.dp)
                  .padding(end = 4.dp)
            )

            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
               Text(
                  text = locationText,
                  color = MaterialTheme.colors.primary,
                  style = MaterialTheme.typography.body2
               )
            }
         }

         IconButton(
            onClick = { onAction?.invoke(FeedItemAction.Directions(itemState)) }
         ) {
            Icon(
               imageVector = Icons.Outlined.Directions,
               contentDescription = "Directions",
               tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
            )
         }
      }
   }
}

@Composable
fun FeedItemMapContent(
   baseMap: MapType?,
   geometry: Geometry,
   iconUrl: String?
) {
   val cameraPositionState = rememberCameraPositionState()

   LaunchedEffect(geometry) {
      val latLng = LatLng(geometry.centroid.y, geometry.centroid.x)
      cameraPositionState.move(
         update = CameraUpdateFactory.newLatLngZoom(latLng, 16f)
      )
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
      modifier = Modifier.fillMaxSize()
   ) {
      if (geometry.geometryType == GeometryType.POINT) {
         val centroid = GeometryUtils.getCentroid(geometry)
         val point = LatLng(centroid.y, centroid.x)

         val defaultIcon = AppCompatResources.getDrawable(LocalContext.current, R.drawable.default_marker)!!.toBitmap()
         var icon by remember { mutableStateOf(BitmapDescriptorFactory.fromBitmap(defaultIcon))}

         Glide.with(LocalContext.current)
            .asBitmap()
            .load(iconUrl)
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
         if (shape is PolylineOptions) {
            Polyline(points = shape.points)
         } else if (shape is PolygonOptions) {
            Polygon(points = shape.points, holes = shape.holes)
         }
      }
   }
}