package mil.nga.giat.mage.ui.observation.view

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import mil.nga.giat.mage.compat.server5.form.view.AttachmentsViewContentServer5
import mil.nga.giat.mage.database.model.event.Event
import mil.nga.giat.mage.form.FormViewModel
import mil.nga.giat.mage.observation.ObservationPermission
import mil.nga.giat.mage.observation.ObservationState
import mil.nga.giat.mage.observation.ObservationStatusState
import mil.nga.giat.mage.database.model.observation.Attachment
import mil.nga.giat.mage.database.model.observation.Observation
import mil.nga.giat.mage.sdk.Compatibility
import mil.nga.giat.mage.ui.coordinate.CoordinateTextButton
import mil.nga.giat.mage.ui.directions.DirectionsDialog
import mil.nga.giat.mage.ui.directions.NavigationType
import mil.nga.giat.mage.ui.theme.MageTheme3
import mil.nga.giat.mage.ui.theme.importantBackground
import mil.nga.giat.mage.ui.theme.onSurfaceDisabled
import mil.nga.giat.mage.utils.DateFormatFactory
import java.util.*

sealed class ObservationAction {
  data object Close: ObservationAction()
  data class Edit(val observationId: Long): ObservationAction()
  data class Favorites(val userIds: Collection<String>): ObservationAction()
  data class Directions(val type: NavigationType, val observation: Observation): ObservationAction()
  data class Attachment(val attachmentId: Long): ObservationAction()
  data object ReorderForms: ObservationAction()
}

sealed class ImportantType {
  data class Add(val note: String? = null): ImportantType()
  data object Remove: ImportantType()
}

@Composable
fun ObservationViewScreen(
  observationId: Long,
  onAction: (ObservationAction) -> Unit,
  viewModel: FormViewModel = hiltViewModel(),
) {
  var showBottomSheet by rememberSaveable { mutableStateOf(false) }
  var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
  var showDirectionsDialog by rememberSaveable { mutableStateOf(false) }

  val observationState by viewModel.observationState.observeAsState()

  LaunchedEffect(observationId) {
    viewModel.setObservation(observationId, observeChanges = true)
  }

  MageTheme3 {
    Scaffold(
      topBar = {
        ObservationViewTopBar { onAction(ObservationAction.Close) }
      },
      content = { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
          ObservationViewContent(
            event = viewModel.event,
            observationState = observationState,
            onSync = { viewModel.syncObservation() },
            onMore = { showBottomSheet = true },
            onImportant = { type ->
              when (type) {
                is ImportantType.Add -> { viewModel.flagObservation(type.note) }
                is ImportantType.Remove -> { viewModel.unflagObservation() }
              }
            },
            onFavorite = { viewModel.toggleFavorite() },
            onFavorites = {
              viewModel.observation.value?.favorites?.map { it.userId }?.let {
                onAction(ObservationAction.Favorites(it))
              }
            },
            onDirections = { showDirectionsDialog = true },
            onAttachmentTap = { onAction(ObservationAction.Attachment(it.id)) } // TODO need attachmentId
          )
        }
      },
      floatingActionButton = {
        if (observationState?.permissions?.contains(ObservationPermission.EDIT) == true) {
          FloatingActionButton(
            onClick = { onAction(ObservationAction.Edit(observationId)) }
          ) {
            Icon(
              Icons.Default.Edit,
              contentDescription = "Edit Observation",
              tint = Color.White
            )
          }
        }
      }
    )

    MoreBottomSheet(
      open = showBottomSheet,
      reorder = (observationState?.forms?.value?.size ?: 0) > 1,
      onDismiss = { showBottomSheet = false },
      onEdit = {
        onAction(ObservationAction.Edit(observationId))
        showBottomSheet = false
      },
      onDelete = {
        showDeleteDialog = true
        showBottomSheet = false
      },
      onReorder = {
        onAction(ObservationAction.ReorderForms)
        showBottomSheet = false
      }
    )

    DeleteDialog(
      open = showDeleteDialog,
      onDismiss = { showDeleteDialog = false },
      onDelete = {
        viewModel.deleteObservation()
        onAction(ObservationAction.Close)
      }
    )

    DirectionsDialog(
      open = showDirectionsDialog,
      onDismiss = { showDirectionsDialog = false },
      onDirections = { type ->
        viewModel.observation.value?.let {
          onAction(ObservationAction.Directions(type, it))
        }
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationViewTopBar(
  onClose: () -> Unit
) {
  TopAppBar(
    title = { Text("Observation") },
    navigationIcon = {
      IconButton(onClick = { onClose.invoke() }) {
        Icon(Icons.Default.ArrowBack, "Cancel Edit")
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
fun ObservationViewContent(
  event: Event?,
  observationState: ObservationState?,
  onSync: () -> Unit,
  onImportant: (ImportantType) -> Unit,
  onFavorite: () -> Unit,
  onFavorites: () -> Unit,
  onMore: () -> Unit,
  onDirections: () -> Unit,
  onAttachmentTap: (Attachment) -> Unit
) {
  if (observationState != null) {
    Surface(
      color = MaterialTheme.colorScheme.surface,
      contentColor = contentColorFor(MaterialTheme.colorScheme.surface),
      shadowElevation = 4.dp
    ) {
      val status by observationState.status
      ObservationViewStatusContent(
        status,
        onSync = { onSync() }
      )
    }

    Column(
      modifier = Modifier
        .background(Color(0x19000000))
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
        .padding(start = 8.dp, end = 8.dp, bottom = 80.dp)
    ) {
      val forms by observationState.forms

      ObservationViewHeaderContent(
        event = event,
        observationState = observationState,
        onFavorite = onFavorite,
        onFavorites = onFavorites,
        onImportant = onImportant,
        onMore = onMore,
        onDirections = onDirections
      )

      if (Compatibility.isServerVersion5(LocalContext.current)) {
        val attachments by observationState.attachments
        AttachmentsViewContentServer5(attachments) {
          onAttachmentTap.invoke(it)
        }
      }

      if (forms.isNotEmpty()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp)
        ) {
          CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Text(
              text = "FORMS",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
            )
          }
        }
      }

      for (formState in forms) {
        FormViewContent(
          formState,
          onAttachmentTap
        )
      }
    }
  }
}

@Composable
fun ObservationViewStatusContent(
  status: ObservationStatusState,
  onSync: () -> Unit
) {
  if (status.dirty) {
    if (status.error != null) {
      ObservationErrorStatus(error = status.error)
    } else {
      ObservationLocalStatus(status) {
        onSync()
      }
    }
  } else if (status.lastModified != null) {
    ObservationSyncStatus(syncDate = status.lastModified)
  }
}

@Composable
fun ObservationSyncStatus(
  syncDate: Date
) {
  val dateFormat =
    DateFormatFactory.format("yyyy-MM-dd HH:mm zz", Locale.getDefault(), LocalContext.current)

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp)
  ) {
    Icon(
      imageVector = Icons.Default.Check,
      contentDescription = "Sync",
      tint = Color(0xFF66BB6A),
      modifier = Modifier
        .width(24.dp)
        .height(24.dp)
        .padding(end = 8.dp)
    )

    Text(
      text = "Pushed on ${dateFormat.format(syncDate)}",
      style = MaterialTheme.typography.bodyMedium,
      color = Color(0xFF66BB6A)
    )
  }
}

@Composable
fun ObservationLocalStatus(
  status: ObservationStatusState,
  onSync: () -> Unit
) {
  var syncing by remember { mutableStateOf(false) }

  if (syncing && status.dirty) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceDisabled) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Sync,
          contentDescription = "Local",
          modifier = Modifier
            .width(24.dp)
            .height(24.dp)
            .padding(end = 8.dp)
        )

        Text(
          text = "Pushing changes...",
          style = MaterialTheme.typography.bodyLarge
        )
      }
    }
  } else {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Default.Sync,
          contentDescription = "Local",
          tint = Color(0xFFFFA726),
          modifier = Modifier
            .width(24.dp)
            .height(24.dp)
            .padding(end = 8.dp)
        )

        Text(
          text = "Changes Queued",
          style = MaterialTheme.typography.bodyLarge,
          color = Color(0xFFFFA726)
        )
      }

      TextButton(
        onClick = {
          syncing = true
          onSync()
        }
      ) {
        Text(text = "SYNC NOW")
      }
    }
  }
}

@Composable
fun ObservationErrorStatus(
  error: String
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 8.dp)
    ) {
      Icon(
        imageVector = Icons.Default.ErrorOutline,
        contentDescription = "Local",
        tint = Color(0xFFF44336),
        modifier = Modifier
          .width(24.dp)
          .height(24.dp)
          .padding(end = 8.dp)
      )

      Text(
        text = "Observation changes not pushed",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFF44336)
      )
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
      Text(
        text = error,
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }
}

@Composable
fun ObservationViewHeaderContent(
  event: Event?,
  observationState: ObservationState? = null,
  onImportant: (ImportantType) -> Unit,
  onFavorite: () -> Unit,
  onFavorites: () -> Unit,
  onMore: () -> Unit,
  onDirections: () -> Unit
) {
  var showImportant by rememberSaveable { mutableStateOf(false) }
  var importantNote by rememberSaveable { mutableStateOf(observationState?.important?.value?.description) }

  val dateFormat =
    DateFormatFactory.format("yyyy-MM-dd HH:mm zz", Locale.getDefault(), LocalContext.current)

  val formState = observationState?.forms?.value?.firstOrNull()
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 8.dp)
  ) {
    Column {
      val important = observationState?.important?.value
      if (important != null) {
        Row(
          Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.importantBackground)
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
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
              Text(
                text = "Flagged by ${important.user}".uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
              )
            }

            if (important.description != null) {
              Text(important.description)
            }
          }
        }
      }

      Row(
        modifier = Modifier.padding(top = 16.dp, start = 16.dp, bottom = 16.dp)
      ) {
        Column(Modifier.weight(1f)) {
          Row(
            modifier = Modifier.padding(bottom = 16.dp)
          ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
              observationState?.userDisplayName?.let {
                Text(
                  text = it.uppercase(Locale.ROOT),
                  fontWeight = FontWeight.SemiBold,
                  style = MaterialTheme.typography.labelSmall
                )
                Text(
                  text = "\u2022",
                  fontWeight = FontWeight.SemiBold,
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                )
              }

              observationState?.timestampFieldState?.answer?.date?.let {
                Text(
                  text = dateFormat.format(it).uppercase(Locale.ROOT),
                  fontWeight = FontWeight.SemiBold,
                  style = MaterialTheme.typography.labelSmall
                )
              }
            }
          }

          FormHeaderContent(formState)
        }
      }

      observationState?.geometryFieldState?.answer?.location?.let { location ->
        Box(
          Modifier
            .fillMaxWidth()
            .height(150.dp)
        ) {
          val mapState = MapState(observationState.geometryFieldState.defaultMapCenter, observationState.geometryFieldState.defaultMapZoom)
          MapViewContent(
            event = event,
            mapState = mapState,
            formState = formState,
            location = location
          )
        }

        CoordinateTextButton(
          latLng = location.centroidLatLng,
          icon = {
            Icon(
              Icons.Default.MyLocation,
              contentDescription = "Observation Location",
              modifier = Modifier.size(14.dp))
          },
          accuracy = {
            Row {
              if (location.provider != null && location.provider.lowercase() != "manual") {
                Text(
                  text = location.provider.uppercase(),
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(end = 2.dp)
                )
              }

              if (location.accuracy != null && location.accuracy != 0.0f) {
                Text(
                  text = " \u00B1 ${location.accuracy}",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          },
          onCopiedToClipboard = {}
        )
      }

      Divider(Modifier.fillMaxWidth())

      Column(
        Modifier
          .fillMaxWidth()
          .animateContentSize()
      ) {
          ImportantEditContent(
            open = showImportant,
            note = importantNote,
            onNote = { importantNote = it },
            onAdd = { note: String? ->
              showImportant = false
              onImportant(ImportantType.Add(note))
            },
            onRemove = {
              importantNote = null
              showImportant = false
              onImportant(ImportantType.Remove)
            },
            onDismiss = { showImportant = false }
          )
      }

      ObservationActions(
        observationState = observationState,
        onImportant = { showImportant = !showImportant },
        onDirections = onDirections,
        onFavorite = onFavorite,
        onFavorites = onFavorites,
        onMore = onMore
      )
    }
  }
}

@Composable
fun ImportantEditContent(
  open: Boolean,
  note: String?,
  onNote: (String) -> Unit,
  onAdd: (String?) -> Unit,
  onRemove: () -> Unit,
  onDismiss: () -> Unit
) {
  val focusManager = LocalFocusManager.current

  if (open) {
    Column(Modifier.padding(16.dp)) {
      TextField(
        value = note ?: "",
        onValueChange = { onNote(it) },
        label = { Text("Note") },
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth()
      )

      if (note != null) {
        Row(
          horizontalArrangement = Arrangement.End,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
        ) {
          TextButton(
            onClick = { onRemove() },
            modifier = Modifier.padding(end = 8.dp)
          ) {
            Text("REMOVE")
          }

          Button(
            onClick = { onAdd(note) },
          ) {
            Text("UPDATE")
          }
        }
      } else {
        Row(
          horizontalArrangement = Arrangement.End,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
        ) {
          TextButton(
            onClick = { onDismiss() },
            modifier = Modifier.padding(end = 8.dp)
          ) { Text("CANCEL") }

          Button(
            onClick = { onAdd(note) },
          ) {
            Text("FLAG AS IMPORTANT")
          }
        }
      }
    }

    Divider()
  }
}

@Composable
fun ObservationActions(
  observationState: ObservationState?,
  onImportant: () -> Unit,
  onDirections: () -> Unit,
  onFavorite: () -> Unit,
  onFavorites: () -> Unit,
  onMore: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .padding(start = 8.dp)
        .clip(MaterialTheme.shapes.small)
        .clickable { onFavorites() }
        .padding(8.dp)
    ) {
      val favorites = observationState?.favorites?.value ?: 0
      if (favorites > 0) {
        val favoriteLabel = if (favorites > 1) "favorites" else "favorite"
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceDisabled) {
          Text(
            text = "$favorites $favoriteLabel".uppercase(),
            style = MaterialTheme.typography.titleSmall
          )
        }
      }
    }

    Row {
      if (observationState?.permissions?.contains(ObservationPermission.FLAG) == true) {
        val isFlagged = observationState.important.value != null
        val flagTint = if (isFlagged) {
          Color(0XFFFF9100)
        } else {
          MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.medium)
        }

        IconButton(
          modifier = Modifier.padding(end = 8.dp),
          onClick = { onImportant() }
        ) {
          Icon(
            imageVector = if (isFlagged) Icons.Default.Flag else Icons.Outlined.Flag,
            tint = flagTint,
            contentDescription = "Flag",
          )
        }
      }

      val isFavorite = observationState?.favorite?.value == true
      val favoriteTint = if (isFavorite) {
        Color(0XFF7ED31F)
      } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.medium)
      }

      IconButton(
        modifier = Modifier.padding(end = 8.dp),
        onClick = { onFavorite() }
      ) {
        Icon(
          imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
          tint = favoriteTint,
          contentDescription = "Favorite",
        )
      }

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

      if (observationState?.permissions?.contains(ObservationPermission.DELETE) == true) {
        IconButton(
          modifier = Modifier.padding(end = 8.dp),
          onClick = { onMore() }
        ) {
          Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Observation more actions",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.medium)
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreBottomSheet(
  open: Boolean,
  reorder: Boolean,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onReorder: () -> Unit,
  onDismiss: () -> Unit
) {
  val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  if (open) {
    ModalBottomSheet(
      onDismissRequest = { onDismiss() },
      sheetState = bottomSheetState,
    ) {
      Column(
        Modifier
          .fillMaxWidth()
          .padding(top = 16.dp, bottom = 32.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(vertical = 16.dp, horizontal = 16.dp)
        ) {
          CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Icon(
              Icons.Outlined.Edit,
              contentDescription = "Edit Observation",
              modifier = Modifier.padding(end = 16.dp)
            )
            Text(
              text = "Edit Observation",
              style = MaterialTheme.typography.bodyLarge
            )
          }
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onDelete() }
            .padding(vertical = 16.dp, horizontal = 16.dp)
        ) {
          CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Icon(
              Icons.Default.DeleteOutline,
              contentDescription = "Delete Observation",
              modifier = Modifier.padding(end = 16.dp)
            )
            Text(
              text = "Delete Observation",
              style = MaterialTheme.typography.bodyLarge
            )
          }
        }
        if (reorder) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onReorder() }
              .padding(vertical = 16.dp, horizontal = 16.dp)
          ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
              Icon(
                Icons.Default.SwapVert,
                contentDescription = "Reorder Observation Forms",
                modifier = Modifier.padding(end = 16.dp)
              )
              Text(
                text = "Reorder Observation Forms",
                style = MaterialTheme.typography.bodyLarge
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun DeleteDialog(
  open: Boolean,
  onDismiss: () -> Unit,
  onDelete: () -> Unit
) {
  if (open) {
    AlertDialog(
      icon = {
        Icon(
          Icons.Default.DeleteOutline,
          contentDescription = "Delete",
          tint = MaterialTheme.colorScheme.primary
        )
      },
      title = {
        Text(text = "Delete Observation")
      },
      text = {
        Text(text = "Are you sure you want to delete this observation?")
      },
      onDismissRequest = { onDismiss() },
      confirmButton = {
        TextButton(
          onClick = { onDelete() }
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { onDismiss() }
        ) {
          Text("Cancel")
        }
      }
    )
  }
}