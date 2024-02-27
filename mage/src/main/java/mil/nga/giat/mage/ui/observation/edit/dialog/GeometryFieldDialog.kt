package mil.nga.giat.mage.ui.observation.edit.dialog

import android.graphics.Point
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Pentagon
import androidx.compose.material.icons.outlined.Rectangle
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import mil.nga.giat.mage.R
import mil.nga.giat.mage.form.field.GeometryFieldState
import mil.nga.giat.mage.observation.ObservationLocation
import mil.nga.giat.mage.ui.theme.onSurfaceDisabled
import mil.nga.sf.util.GeometryUtils
import kotlin.math.roundToInt

// TODO
// Geometry dialog fix all.  add invalid field state, lines, polys, box
// Date/time dialog fix gmt/local settings
// Fix the form defaults to use new compose dialogs

enum class GeometryType {
  POINT, LINE, POLYGON, BOX
}

val tabs = listOf("Lat/Lng", "DMS", "MGRS", "GARS")

@Composable
fun GeometryFieldDialog(
  state: GeometryFieldState?,
  onSave: (GeometryFieldState, ObservationLocation) -> Unit,
  onClear: (GeometryFieldState) -> Unit,
  onCancel: () -> Unit,
  viewModel: GeometryFieldDialogViewModel = hiltViewModel()
) {
  var canvasBox by remember { mutableStateOf<Pair<Point, Point>?>(null) }
  var canvasPoints by remember { mutableStateOf(listOf<Point>()) }

  val baseMap by viewModel.baseMap.observeAsState()
  val tab by viewModel.tab.observeAsState(0) // TODO needs to default from preferences
  val mapLocation by viewModel.mapLocation.observeAsState()
  val geometryType by viewModel.geometryType.observeAsState()

  val latLngState by viewModel.latLngState.observeAsState()
  val dmsState by viewModel.dmsState.observeAsState()
  val mgrsState by viewModel.mgrsState.observeAsState()
  val garsState by viewModel.garsState.observeAsState()

  // TODO remove focus from text fields if map moves
  // TODO need to show invalid MGRS but still keep text

  val cameraPositionState = rememberCameraPositionState()
  LaunchedEffect(cameraPositionState.isMoving) {
    if (cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
      viewModel.setMapLocation(cameraPositionState.position.target)
    }
  }

  LaunchedEffect(state) {
    state?.answer?.location?.let { location ->
      val latLng = LatLng(location.centroid.y, location.centroid.x)
      viewModel.setMapLocation(latLng)
      cameraPositionState.move(update = CameraUpdateFactory.newLatLngZoom(latLng, 16f))
    }
  }

  if (state != null) {
    Dialog(
      properties  = DialogProperties(usePlatformDefaultWidth = false),
      onDismissRequest = { onCancel() }
    ) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxSize()
      ) {
        Column {
          TopBar(
            title = state.definition.title,
            onCancel = { onCancel() },
            actions = {
              TextButton(onClick = { onClear(state) }) {
                Text("Clear")
              }

              TextButton(
                onClick = {
                  val latLng = cameraPositionState.position.target
                  val location = ObservationLocation(latLng = latLng)
                  onSave(state, location)
                }
              ) {
                Text("Save")
              }
            }
          )

          Tabs(
            tab = tab,
            latLng = latLngState,
            dms = dmsState,
            mgrs = mgrsState,
            gars = garsState,
            onTabChange = { viewModel.setTab(it) },
            onLatLngChange = { latitude, longitude -> viewModel.setLatLng(latitude, longitude) },
            onDmsChange = { latitude, longitude -> viewModel.setDms(latitude, longitude) },
            onMgrsChange = { viewModel.setMgrs(it) },
            onGarsChange = { viewModel.setGars(it) }
          )

          Box(Modifier.weight(1f)) {
            Map(
              baseMap = baseMap,
              location = mapLocation,
              canvasBox = canvasBox,
              canvasPoints = canvasPoints,
              cameraPositionState = cameraPositionState
            )

            // TODO only if we are drawing I guess
            if (geometryType != null) {
              CanvasPath {
                canvasPoints = it
                viewModel.setGeometryType(null)
              }

//              CanvasBox { (start, end) ->
//                if (start != null && end != null) {
//                  canvasBox = Pair(start, end)
//                }
//                viewModel.setGeometryType(null)
//              }
            }

            val enabledContentColor = MaterialTheme.colorScheme.surfaceVariant
            val enabledContainerColor = MaterialTheme.colorScheme.primary
            val disabledContentColor = MaterialTheme.colorScheme.primary
            val disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant

            Row(
              horizontalArrangement = Arrangement.End,
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
            ) {
              FloatingActionButton(
                contentColor = if (geometryType == GeometryType.POINT) enabledContentColor else disabledContentColor,
                containerColor = if (geometryType == GeometryType.POINT) enabledContainerColor else disabledContainerColor,
                onClick = {
                  canvasBox = null
                  canvasPoints = emptyList()
                  viewModel.setGeometryType(GeometryType.POINT)
                },
                modifier = Modifier
                  .padding(end = 8.dp)
                  .size(40.dp)
              ) {
                Icon(
                  imageVector = Icons.Outlined.LocationOn,
                  contentDescription = "Point"
                )
              }

              FloatingActionButton(
                contentColor = if (geometryType == GeometryType.LINE) enabledContentColor else disabledContentColor,
                containerColor = if (geometryType == GeometryType.LINE) enabledContainerColor else disabledContainerColor,
                onClick = {
                  canvasBox = null
                  canvasPoints = emptyList()
                  viewModel.setGeometryType(GeometryType.LINE)
                },
                modifier = Modifier
                  .padding(end = 8.dp)
                  .size(40.dp)
              ) {
                Icon(
                  imageVector = Icons.Outlined.Timeline,
                  contentDescription = "Line"
                )
              }

              FloatingActionButton(
                contentColor = if (geometryType == GeometryType.BOX) enabledContentColor else disabledContentColor,
                containerColor = if (geometryType == GeometryType.BOX) enabledContainerColor else disabledContainerColor,
                onClick = {
                  canvasBox = null
                  canvasPoints = emptyList()
                  viewModel.setGeometryType(GeometryType.BOX)
                },
                modifier = Modifier
                  .padding(end = 8.dp)
                  .size(40.dp)
              ) {
                Icon(
                  imageVector = Icons.Outlined.Rectangle,
                  contentDescription = "Rectangle"
                )
              }

              FloatingActionButton(
                contentColor = if (geometryType == GeometryType.POLYGON) enabledContentColor else disabledContentColor,
                containerColor = if (geometryType == GeometryType.POLYGON) enabledContainerColor else disabledContainerColor,
                onClick = {
                  canvasBox = null
                  canvasPoints = emptyList()
                  viewModel.setGeometryType(GeometryType.POLYGON)
                },
                modifier = Modifier.size(40.dp)
              ) {
                Icon(
                  imageVector = Icons.Outlined.Pentagon,
                  contentDescription = "Polygon"
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TopBar(
  title: String,
  onCancel: () -> Unit,
  actions: @Composable RowScope.() -> Unit = {}
) {
  Box(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .height(56.dp)
        .fillMaxWidth()
    ) {
      IconButton(
        onClick = { onCancel() },
        modifier = Modifier.padding(end = 8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "close"
        )
      }

      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.weight(1f)
      )

      actions()
    }
  }
}

@Composable
private fun Tabs(
  tab: Int,
  latLng: LatLngState?,
  dms: DmsState?,
  mgrs: MgrsState?,
  gars: GarsState?,
  onTabChange: (Int) -> Unit,
  onLatLngChange: (String, String) -> Unit,
  onDmsChange: (String, String) -> Unit,
  onMgrsChange: (String) -> Unit,
  onGarsChange: (String) -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    TabRow(selectedTabIndex = tab) {
      tabs.forEachIndexed { index, title ->
        Tab(text = { Text(title) },
          selected = tab == index,
          onClick = { onTabChange(index) }
        )
      }
    }

    Surface(
      contentColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
      when (tab) {
        0 -> {
          Row(
            Modifier
              .fillMaxWidth()
              .padding(8.dp)
          ) {
            TextField(
              value = latLng?.latitude ?: "",
              placeholder = { Text("Latitude") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              onValueChange = { latitude ->
                latLng?.longitude?.let { longitude ->
                  onLatLngChange(latitude, longitude)
                }
              },
              modifier = Modifier
                .weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            TextField(
              value = latLng?.longitude ?: "",
              placeholder = { Text("Longitude") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              onValueChange = { longitude ->
                latLng?.latitude?.let { latitude ->
                  onLatLngChange(latitude, longitude)
                }
              },
              modifier = Modifier.weight(1f)
            )
          }
        }
        1 -> {
          Row(
            Modifier
              .fillMaxWidth()
              .padding(8.dp)
          ) {
            TextField(
              value = dms?.latitude ?: "",
              placeholder = { Text("Latitude") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
              onValueChange = { latitude ->
                dms?.longitude?.let { longitude ->
                  onDmsChange(latitude, longitude)
                }
              },
              modifier = Modifier
                .weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            TextField(
              value = dms?.longitude ?: "",
              placeholder = { Text("Longitude") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
              onValueChange = { longitude ->
                dms?.latitude?.let { latitude ->
                  onDmsChange(latitude, longitude)
                }
              },
              modifier = Modifier.weight(1f)
            )
          }
        }
        2 -> {
          TextField(
            value = mgrs?.mgrs ?: "",
            placeholder = { Text("MGRS") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            onValueChange = { onMgrsChange(it) },
            modifier = Modifier
              .fillMaxWidth(1f)
              .padding(8.dp)
          )
        }
        3 -> {
          TextField(
            value = gars?.gars ?: "",
            placeholder = { Text("GARS") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            onValueChange = { onGarsChange(it) },
            modifier = Modifier
              .fillMaxWidth(1f)
              .padding(8.dp)
          )
        }
      }
    }
  }
}

//@Composable
//private fun Map(
//  baseMap: MapType?,
//  location: LatLng?,
//  geometryType: GeometryType,
//  canvasPoints: List<Point>,
//  cameraPositionState: CameraPositionState
//) {
//  var points by remember { mutableStateOf(listOf<LatLng>()) }
//
//  LaunchedEffect(location) {
//    location?.let {
//      cameraPositionState.move(update = CameraUpdateFactory.newLatLngZoom(it, 16f))
//    }
//  }
//
//  val uiSettings = MapUiSettings(
//    zoomControlsEnabled = false,
//    compassEnabled = false
//  )
//
//  val mapStyleOptions = if (isSystemInDarkTheme()) {
//    MapStyleOptions.loadRawResourceStyle(LocalContext.current, R.raw.map_theme_night)
//  } else null
//
//  val properties = baseMap?.let { mapType ->
//    MapProperties(
//      mapType = mapType,
//      mapStyleOptions = mapStyleOptions
//    )
//  } ?: MapProperties()
//
//  Box(
//    modifier = Modifier.fillMaxSize()
//  ) {
//    GoogleMap(
//      cameraPositionState = cameraPositionState,
//      properties = properties,
//      uiSettings = uiSettings,
//      modifier = Modifier.fillMaxSize()
//    ) {
//
//      when (geometryType) {
//        GeometryType.POINT -> {}
//        GeometryType.POLYGON -> {
//          cameraPositionState.projection?.let { projection ->
//            points = if (points.isEmpty()) {
//              emptyList()
//            } else {
//              val boundsBuilder = LatLngBounds.Builder()
//              canvasPoints.map {
//                val latLng = projection.fromScreenLocation(
//                  Point(it.x, it.y)
//                )
//                boundsBuilder.include(latLng)
//                latLng
//              }
//            }
//          }
//
//          if (points.isNotEmpty()) {
//            Polygon(
//              points = points,
//              fillColor = Color.Black.copy(alpha = 0.4f)
//            )
//          }
//        }
//        else -> {}
//      }
//    }
//
//    Icon(
//      painter = painterResource(id = R.drawable.ic_point_scan_outlined_24),
//      tint = MaterialTheme.colorScheme.onSurfaceDisabled,
//      contentDescription = "map center",
//      modifier = Modifier
//        .align(Alignment.Center)
//        .size(64.dp)
//    )
//  }
//}

@Composable
private fun Map(
  baseMap: MapType?,
  location: LatLng?,
  canvasBox: Pair<Point, Point>?,
  canvasPoints: List<Point>,
  cameraPositionState: CameraPositionState
) {

  var simplifySlider by remember { mutableStateOf(1f) }
  var geoBox by remember { mutableStateOf<Pair<Point, Point>?>(null) }
  var geoPoints by remember { mutableStateOf(listOf<LatLng>()) }

  LaunchedEffect(location) {
    location?.let {
      cameraPositionState.move(update = CameraUpdateFactory.newLatLngZoom(it, 16f))
    }
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

  Box() {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      properties = properties,
      uiSettings = uiSettings,
      cameraPositionState = cameraPositionState
    ) {
//      cameraPositionState.projection?.let { projection ->
//        geoPoints = if (canvasPoints.isEmpty()) {
//          emptyList()
//        } else {
//          canvasPoints.map {
//            projection.fromScreenLocation(Point(it.x, it.y))
//          }
//        }
//      }

      if (geoPoints.isNotEmpty()) {
        Polygon(
          points = geoPoints,
          fillColor = Color.Black.copy(alpha = 0.4f)
        )
      }

      cameraPositionState.projection?.let { projection ->
        canvasBox?.let { (start, end) ->
          val upperLeft = projection.fromScreenLocation(Point(start.x, start.y))
          val upperRight = projection.fromScreenLocation(Point(end.x, start.y))
          val lowerRight = projection.fromScreenLocation(Point(end.x, end.y))
          val lowerLeft = projection.fromScreenLocation(Point(start.x, end.y))

          Polygon(
            points = listOf(upperLeft, upperRight, lowerRight, lowerLeft),
            fillColor = Color.Black.copy(alpha = 0.4f)
          )
        }
      }
    }

    Icon(
      painter = painterResource(id = R.drawable.ic_point_scan_outlined_24),
      tint = MaterialTheme.colorScheme.onSurfaceDisabled,
      contentDescription = "map center",
      modifier = Modifier
        .align(Alignment.Center)
        .size(64.dp)
    )

    if (canvasPoints.isNotEmpty()) {
      Box(
        Modifier
          .fillMaxWidth()
          .padding(16.dp)
          .clip(MaterialTheme.shapes.extraLarge)
          .align(Alignment.BottomCenter)
          .background(MaterialTheme.colorScheme.surface)
      ) {
        Row {
          Slider(
            value = simplifySlider,
            onValueChange = { slider ->
              simplifySlider = slider
              cameraPositionState.projection?.let { projection ->
                val points = canvasPoints.map {
                  val latLng = projection.fromScreenLocation(Point(it.x, it.y))
                  mil.nga.sf.Point(latLng.longitude, latLng.latitude)
                }
                // 1 is no simplify,
                // .5 is double the tolerance
                val tolerance = 100.0 / slider
                Log.i("Billy", "tolerance is $tolerance")
                geoPoints = GeometryUtils.simplifyPoints(points, tolerance).map {
                  LatLng(it.y, it.x)
                }
              }
            },
            modifier = Modifier.weight(1f)
          )
        }
      }
    }
  }
}

fun Offset.toPoint() = Point(x.roundToInt(), y.roundToInt())






