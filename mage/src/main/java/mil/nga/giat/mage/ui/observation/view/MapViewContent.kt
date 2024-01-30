package mil.nga.giat.mage.ui.observation.view

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import mil.nga.geopackage.map.geom.GoogleMapShapeConverter
import mil.nga.giat.mage.R
import mil.nga.giat.mage.database.model.event.Event
import mil.nga.giat.mage.form.FormState
import mil.nga.giat.mage.form.field.FieldValue
import mil.nga.giat.mage.map.annotation.MapAnnotation
import mil.nga.giat.mage.map.annotation.ShapeStyle
import mil.nga.giat.mage.observation.ObservationLocation
import mil.nga.giat.mage.ui.map.camera.getCameraUpdate
import mil.nga.sf.GeometryType
import mil.nga.sf.util.GeometryUtils

data class MapState(val center: LatLng?, val zoom: Float?)

@Composable
fun MapViewContent(
   event: Event?,
   formState: FormState?,
   location: ObservationLocation,
   viewModel: MapViewModel = hiltViewModel()
) {
  val context = LocalContext.current

  val baseMap by viewModel.baseMap.observeAsState()

  val primaryFieldState = formState?.fields?.find { it.definition.name == formState.definition.primaryMapField }
  val primary = (primaryFieldState?.answer as? FieldValue.Text)?.text

  val secondaryFieldState = formState?.fields?.find { it.definition.name == formState.definition.secondaryMapField }
  val secondary = (secondaryFieldState?.answer as? FieldValue.Text)?.text

  val cameraPositionState = rememberCameraPositionState()

  LaunchedEffect(location) {
    location.getCameraUpdate()?.let { cameraPositionState.move(update = it) }
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
    if (location.geometry?.geometryType == GeometryType.POINT) {
      val formId = formState?.id ?: 0
      val defaultIcon = AppCompatResources.getDrawable(LocalContext.current, R.drawable.default_marker)!!.toBitmap()
      var icon by remember { mutableStateOf(BitmapDescriptorFactory.fromBitmap(defaultIcon))}
      LaunchedEffect(formId, location, primary, secondary) {
        if (formState != null) {
          val annotation = MapAnnotation.fromObservationProperties(formId, location.geometry, location.time, location.accuracy, formState.eventId, formState.definition.id, primary, secondary, context)
          Glide.with(context)
            .asBitmap()
            .load(annotation)
            .into(object : CustomTarget<Bitmap>(100, 100) {
              override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                icon = BitmapDescriptorFactory.fromBitmap(resource)
              }

              override fun onLoadCleared(placeholder: Drawable?) {}
            })
        }
      }

      val latLng = LatLng(location.geometry.centroid.y, location.geometry.centroid.x)
      Marker(
        state = MarkerState(position = latLng),
        icon = icon
      )

      if (!location.provider.equals(ObservationLocation.MANUAL_PROVIDER, true) && location.accuracy != null) {
        Circle(
          center = latLng,
          strokeColor = colorResource(R.color.accuracy_circle_stroke),
          strokeWidth = 2f,
          fillColor = colorResource(R.color.accuracy_circle_fill),
          radius = location.accuracy.toDouble()
        )
      }
    } else {
      val shape = GoogleMapShapeConverter().toShape(location.geometry).shape
      val style = ShapeStyle.fromForm(event, formState, LocalContext.current)

      if (shape is PolylineOptions) {
        Polyline(
          points = shape.points,
          width = style.strokeWidth,
          color = Color(style.strokeColor)
        )
      } else if (shape is PolygonOptions) {
        Polygon(
          points = shape.points,
          holes = shape.holes,
          strokeWidth = style.strokeWidth,
          strokeColor = Color(style.strokeColor),
          fillColor = Color(style.fillColor)
        )
      }
    }
  }
}

@Composable
fun MapViewContent(
  location: ObservationLocation,
  viewModel: MapViewModel = hiltViewModel()
) {
  val baseMap by viewModel.baseMap.observeAsState()

  val cameraPositionState = rememberCameraPositionState()
  LaunchedEffect(location) {
    location.getCameraUpdate()?.let { cameraPositionState.move(update = it) }
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
    if (location.geometry?.geometryType == GeometryType.POINT) {
      val centroid = GeometryUtils.getCentroid(location.geometry)
      val point = LatLng(centroid.y, centroid.x)

      val color: Int = android.graphics.Color.parseColor("#1E88E5")
      val hsv = FloatArray(3)
      android.graphics.Color.colorToHSV(color, hsv)

      Marker(
        state = MarkerState(position = point),
        icon = BitmapDescriptorFactory.defaultMarker(hsv[0])
      )
    } else {
      val shape = GoogleMapShapeConverter().toShape(location.geometry).shape
      if (shape is PolylineOptions) {
        Polyline(points = shape.points,)
      } else if (shape is PolygonOptions) {
        Polygon(
          points = shape.points,
          holes = shape.holes
        )
      }
    }
  }
}