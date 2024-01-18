package mil.nga.giat.mage.ui.directions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class NavigationType {
   Map, Bearing
}

@Composable
fun DirectionsDialog(
   open: Boolean,
   onDismiss: () -> Unit,
   onDirections: (NavigationType) -> Unit
) {
   if (open) {
      AlertDialog(
         icon = {
            Icon(
               Icons.Default.Directions,
               contentDescription = "Directions",
               tint = MaterialTheme.colorScheme.primary
            )
         },
         title = {
            Text(text = "Navigate with?")
         },
         text = {
            Column(Modifier.fillMaxWidth()) {
               Row(
                  Modifier
                     .fillMaxWidth()
                     .clickable { onDirections(NavigationType.Map) }
                     .padding(vertical = 16.dp, horizontal = 8.dp)
               ) {
                  Icon(
                     Icons.Outlined.Map,
                     contentDescription = "Map",
                     modifier = Modifier.padding(end = 8.dp)
                  )
                  Text(
                     text = "Maps",
                     style = MaterialTheme.typography.titleMedium
                  )
               }

               Row(
                  Modifier
                     .fillMaxWidth()
                     .clickable { onDirections(NavigationType.Bearing) }
                     .padding(vertical = 16.dp, horizontal = 8.dp)
               ) {
                  Icon(
                     Icons.Outlined.NearMe,
                     contentDescription = "Map",
                     modifier = Modifier.padding(end = 8.dp)
                  )
                  Text(
                     text = "Bearing",
                     style = MaterialTheme.typography.titleMedium
                  )
               }
            }
         },
         onDismissRequest = { onDismiss() },
         confirmButton = {
            TextButton(
               onClick = { onDismiss() }
            ) {
               Text("Dismiss")
            }
         }
      )
   }
}