package mil.nga.giat.mage.newsfeed

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import androidx.paging.compose.items
import mil.nga.giat.mage.newsfeed.observation.ObservationItemState
import mil.nga.giat.mage.ui.theme.MageTheme
import mil.nga.giat.mage.ui.theme.importantBackground
import java.util.*

@Composable
fun ObservationsViewScreen(observations: LiveData<Flow<PagingData<ObservationItemState>>>) {
   val pagingState by observations.observeAsState()

   pagingState?.collectAsLazyPagingItems()?.let { lazyObservationItems ->
      MageTheme {
         Surface(
            color = Color(0x19000000),
            modifier = Modifier.fillMaxHeight()
         ) {
            LazyColumn(
               modifier = Modifier.padding(horizontal = 8.dp),
               contentPadding = PaddingValues(top = 16.dp)
            ) {
               items(lazyObservationItems) { observation ->
                  ObservationItem(observation)
               }
            }
         }
      }
   }
}

@Composable
fun ObservationItem(observationItemState: ObservationItemState?) {
   if (observationItemState != null) {
      Card(
         Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
      ) {
         ObservationItemHeaderContent(observationItemState)
      }
   }
}

@Composable
fun ObservationItemHeaderContent(observationItemState: ObservationItemState) {
   Card(Modifier
      .fillMaxWidth()
      .clickable {  }
   ) {
      Column {
         val important = observationItemState.importantState
         if (important != null) {
            Row(
               Modifier
                  .fillMaxWidth()
                  .background(MaterialTheme.colors.importantBackground)
                  .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
               Icon(
                  imageVector = Icons.Default.Flag,
                  contentDescription = "Important Flag",
                  modifier = Modifier
                     .height(40.dp)
                     .width(40.dp)
                     .padding(end = 8.dp)
               )

               Column {
                  CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                     Text(
                        text = "Flagged by ${important.user}".uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.overline,
                        fontWeight = FontWeight.SemiBold,
                     )
                  }

                  if (important.description != null) {
                     Text(
                        text = important.description
                     )
                  }
               }
            }
         }

         Row(modifier = Modifier
            .padding(top = 16.dp, start = 16.dp, bottom = 16.dp)
         ) {
            Column(Modifier.weight(1f)) {
               Row(
                  modifier = Modifier
                     .padding(bottom = 16.dp)
               ) {
                  CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                     observationItemState.user?.let {
                        Text(
                           text = it.uppercase(Locale.ROOT),
                           fontWeight = FontWeight.SemiBold,
                           style = MaterialTheme.typography.overline
                        )
                        Text(
                           text = "\u2022",
                           fontWeight = FontWeight.SemiBold,
                           style = MaterialTheme.typography.overline,
                           modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                        )
                     }

                     observationItemState.timestamp?.let {
                        Text(
                           text = it.uppercase(Locale.ROOT),
                           fontWeight = FontWeight.SemiBold,
                           style = MaterialTheme.typography.overline,
                           maxLines = 1,
                           overflow = TextOverflow.Ellipsis
                        )
                     }
                  }
               }

               ObservationItemHeaderContent(observationItemState.primary, observationItemState.secondary)
            }

            ObservationItemIcon(observationItemState.icon)
         }

//         ObservationActions(observationState) { onAction?.invoke(it) }
      }
   }
}

@Composable
fun ObservationItemHeaderContent(
   primary: String?,
   secondary: String?
) {
   if (primary != null) {
      Row {
         CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
            Text(
               text = primary,
               style = MaterialTheme.typography.h6,
               color = MaterialTheme.colors.primary
            )
         }
      }
   }

   if (secondary != null) {
      Row {
         CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
            Text(
               text = secondary,
               style = MaterialTheme.typography.subtitle1
            )
         }
      }
   }
}

@Composable
fun ObservationItemIcon(bitmap: Bitmap) {
   Box(modifier = Modifier
      .padding(start = 16.dp, end = 16.dp)
   ) {
      Image(
         bitmap = bitmap.asImageBitmap(),
         contentDescription = "Observation Icon",
         modifier = Modifier
            .width(40.dp)
            .height(40.dp)
      )
   }
}