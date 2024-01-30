package mil.nga.giat.mage.ui.coordinate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import mil.nga.giat.mage.coordinate.CoordinateFormatter

@Composable
fun CoordinateText(
   latLng: LatLng,
   icon: @Composable (() -> Unit)? = null,
   accuracy: @Composable (() -> Unit)? = null,
) {
   val context = LocalContext.current
   val formatter = CoordinateFormatter(context)
   val text =  formatter.format(latLng)

   Row(verticalAlignment = Alignment.CenterVertically) {
      icon?.let {
         Box(Modifier.padding(end = 4.dp)) {
            it()
         }
      }

      Text(text = text)

      accuracy?.let {
         Box(Modifier.padding(start = 8.dp)) {
            it()
         }
      }
   }
}