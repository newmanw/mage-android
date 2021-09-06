package mil.nga.giat.mage.newsfeed.observation

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import mil.nga.giat.mage.observation.ObservationImportantState
import mil.nga.giat.mage.sdk.datastore.observation.Attachment
import mil.nga.giat.mage.sdk.datastore.observation.Observation
import mil.nga.giat.mage.sdk.datastore.observation.ObservationImportant

class ObservationItemState(
   val user: String?,
   val timestamp: String?,
   val primary: String?,
   val secondary: String?,
   val icon: Bitmap,
   val importantState: ObservationImportantState? = null,
   attachments: Collection<Attachment> = emptyList(),
) {

}