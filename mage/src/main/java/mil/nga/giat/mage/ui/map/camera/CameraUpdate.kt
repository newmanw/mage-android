package mil.nga.giat.mage.ui.map.camera

import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import mil.nga.giat.mage.observation.ObservationLocation
import mil.nga.proj.ProjectionConstants
import mil.nga.sf.Geometry
import mil.nga.sf.GeometryType
import mil.nga.sf.util.GeometryEnvelopeBuilder
import mil.nga.sf.util.GeometryUtils

private const val DEFAULT_POINT_ZOOM = 17
private const val DEFAULT_PADDING = 100

private fun isManualProvider(provider: String?): Boolean =
   provider == ObservationLocation.MANUAL_PROVIDER

fun Geometry.getCameraUpdate(
   pointZoomDefault: Int = DEFAULT_POINT_ZOOM,
   padding: Int = DEFAULT_PADDING
): CameraUpdate {
   return if (geometryType == GeometryType.POINT) {
      val latLng = LatLng(centroid.y, centroid.x)
      CameraUpdateFactory.newLatLngZoom(latLng, pointZoomDefault.toFloat())
   } else {
      val copy = copy()
      GeometryUtils.minimizeGeometry(copy, ProjectionConstants.WGS84_HALF_WORLD_LON_WIDTH)
      val envelope = GeometryEnvelopeBuilder.buildEnvelope(copy)

      val boundsBuilder = LatLngBounds.Builder()
      boundsBuilder.include(LatLng(envelope.minY, envelope.minX))
      boundsBuilder.include(LatLng(envelope.minY, envelope.maxX))
      boundsBuilder.include(LatLng(envelope.maxY, envelope.minX))
      boundsBuilder.include(LatLng(envelope.maxY, envelope.maxX))
      CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), padding)
   }
}

fun ObservationLocation.getCameraUpdate(
   pointZoomDefault: Int = DEFAULT_POINT_ZOOM,
   padding: Int = DEFAULT_PADDING
): CameraUpdate? {
   return if (geometry?.geometryType == GeometryType.POINT) {
      val latLng = LatLng(centroid.y, centroid.x)
      if (!isManualProvider(provider) && accuracy != null) {
         val latitudePadding = (accuracy / 111325).toDouble()
         val bounds = LatLngBounds(
            LatLng(latLng.latitude - latitudePadding, latLng.longitude),
            LatLng(latLng.latitude + latitudePadding, latLng.longitude)
         )
         CameraUpdateFactory.newLatLngBounds(bounds, padding)
      } else {
         CameraUpdateFactory.newLatLngZoom(latLng, pointZoomDefault.toFloat())
      }
   } else if (geometry != null) {
      val copy = geometry.copy()
      GeometryUtils.minimizeGeometry(copy, ProjectionConstants.WGS84_HALF_WORLD_LON_WIDTH)
      val envelope = GeometryEnvelopeBuilder.buildEnvelope(copy)

      val boundsBuilder = LatLngBounds.Builder()
      boundsBuilder.include(LatLng(envelope.minY, envelope.minX))
      boundsBuilder.include(LatLng(envelope.minY, envelope.maxX))
      boundsBuilder.include(LatLng(envelope.maxY, envelope.minX))
      boundsBuilder.include(LatLng(envelope.maxY, envelope.maxX))
      CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), padding)
   } else null
}