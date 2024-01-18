package mil.nga.giat.mage.observation

import android.location.Location
import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng
import kotlinx.parcelize.Parcelize
import mil.nga.giat.mage.database.model.observation.Observation
import mil.nga.sf.Geometry
import mil.nga.sf.Point
import mil.nga.sf.util.GeometryUtils

@Parcelize
data class ObservationLocation(
   val geometry: Geometry? = null,
   val accuracy: Float? = null,
   val provider: String? = null,
   val time: Long = 0,
   private var elapsedRealtimeNanos: Long = 0
) : Parcelable {

   constructor(location: Location): this(
      geometry = Point(location.longitude, location.latitude),
      accuracy = location.accuracy,
      provider = location.provider,
      time = location.time,
      elapsedRealtimeNanos = location.elapsedRealtimeNanos
   )

   constructor(observation: Observation): this(
      geometry = observation.geometry,
      provider = observation.provider,
      accuracy = observation.accuracy
   )

   constructor(
      latLng: LatLng,
      accuracy: Float? = null,
      provider: String? = null,
      time: Long = 0,
   ) : this(
      geometry = Point(latLng.longitude, latLng.latitude),
      accuracy = accuracy,
      provider = provider,
      time = time,
   )

   val centroid: Point
      get() = GeometryUtils.getCentroid(geometry)

   val centroidLatLng: LatLng
      get() {
         val point = GeometryUtils.getCentroid(geometry)
         return LatLng(point.y, point.x)
      }

   companion object {
      /**
       * Provider for manually set locations
       */
      const val MANUAL_PROVIDER = "manual"

      /**
       * Check if the points form a rectangle
       *
       * @param points points
       * @return true if a rectangle
       */
      fun checkIfRectangle(points: List<Point>): Boolean {
         return checkIfRectangleAndFindSide(points) != null
      }

      /**
       * Check if the points form a rectangle and return if the side one has the same x
       *
       * @param points points
       * @return null if not a rectangle, true if same x side 1, false if same y side 1
       */
      private fun checkIfRectangleAndFindSide(points: List<Point>): Boolean? {
         var sameXSide1: Boolean? = null
         val size = points.size
         if (size == 4 || size == 5) {
            val point1 = points[0]
            val lastPoint = points[points.size - 1]
            val closed = point1.x == lastPoint.x && point1.y == lastPoint.y
            if (closed && size == 5 || !closed && size == 4) {
               val point2 = points[1]
               val point3 = points[2]
               val point4 = points[3]
               if (point1.x == point2.x && point2.y == point3.y) {
                  if (point1.y == point4.y && point3.x == point4.x) {
                     sameXSide1 = true
                  }
               } else if (point1.y == point2.y && point2.x == point3.x) {
                  if (point1.x == point4.x && point3.y == point4.y) {
                     sameXSide1 = false
                  }
               }
            }
         }
         return sameXSide1
      }
   }
}
