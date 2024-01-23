package mil.nga.giat.mage.ui.observation.edit

import android.os.Parcelable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import mil.nga.giat.mage.compat.server5.form.view.AttachmentsViewContentServer5
import mil.nga.giat.mage.database.model.event.Event
import mil.nga.giat.mage.form.FormState
import mil.nga.giat.mage.form.FormViewModel
import mil.nga.giat.mage.form.field.*
import mil.nga.giat.mage.observation.ObservationState
import mil.nga.giat.mage.observation.ObservationValidationResult
import mil.nga.giat.mage.sdk.Compatibility.Companion.isServerVersion5
import mil.nga.giat.mage.database.model.observation.Attachment
import mil.nga.giat.mage.ui.theme.MageTheme3

enum class AttachmentAction {
  VIEW, DELETE
}

enum class MediaActionType {
  GALLERY, PHOTO, VIDEO, VOICE, FILE
}

@Parcelize
data class MediaAction (
   val type: MediaActionType,
   val formIndex: Int?,
   val fieldName: String?
): Parcelable

@Composable
fun ObservationEditScreen(
   onSave: (() -> Unit)? = null,
   onCancel: (() -> Unit)? = null,
   onAddForm: (() -> Unit)? = null,
   onDeleteForm: ((Int) -> Unit)? = null,
   onReorderForms: (() -> Unit)? = null,
   onFieldClick: ((FieldState<*, *>) -> Unit)? = null,
   onAttachmentAction: ((AttachmentAction, Attachment, FieldState<*, *>?) -> Unit)? = null,
   onMediaAction: ((MediaAction) -> Unit)? = null,
   viewModel: FormViewModel = hiltViewModel()
) {
  val observationState by viewModel.observationState.observeAsState()
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val listState = rememberLazyListState()

  MageTheme3 {
    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        ObservationEditTopBar(
          isNewObservation = observationState?.id == null,
          onSave = {
            observationState?.let { state ->
              when (val result = state.validate()) {
                is ObservationValidationResult.Invalid -> {
                  scope.launch {
                    snackbarHostState.showSnackbar(result.error)
                  }
                }
                is ObservationValidationResult.Valid -> onSave?.invoke()
              }
            }
          },
          onCancel = { onCancel?.invoke() }
        )
      },
      content = { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
          if (isServerVersion5(LocalContext.current)) {
            ObservationMediaBar { onMediaAction?.invoke(MediaAction(it, null, null)) }
          }

          ObservationEditContent(
            event = viewModel.event,
            observationState = observationState,
            listState = listState,
            onFieldClick = onFieldClick,
            onMediaAction = onMediaAction,
            onAttachmentAction = { action, media, fieldState ->
              when (action) {
                AttachmentAction.VIEW -> onAttachmentAction?.invoke(action, media, fieldState)
                AttachmentAction.DELETE -> {
                  val attachmentFieldState = fieldState as AttachmentFieldState
                  val attachments = attachmentFieldState.answer?.attachments?.toMutableList() ?: mutableListOf()
                  val index = attachments.indexOf(media)
                  val attachment = attachments[index]

                  scope.launch {
                    val result = snackbarHostState.showSnackbar("Attachment removed.", "UNDO")
                    if (result == SnackbarResult.ActionPerformed) {
                      // TODO should I modify state here?
                      if (attachment.url?.isNotEmpty() == true) {
                        attachment.action = null
                      }
                      attachmentFieldState.answer = FieldValue.Attachment(attachments)
                    }
                  }
                  onAttachmentAction?.invoke(action, media, fieldState)
                }
              }
            },
            onReorderForms = onReorderForms,
            onDeleteForm = { index, formState ->
              scope.launch {
                val result = snackbarHostState.showSnackbar("Form deleted", "UNDO")
                if (result == SnackbarResult.ActionPerformed) {
                  // TODO should I modify state here?
                  val forms = observationState?.forms?.value?.toMutableList() ?: mutableListOf()
                  forms.add(index, formState)
                  observationState?.forms?.value = forms
                }
              }
              onDeleteForm?.invoke(index)
            }
          )
        }
      },
      floatingActionButton = {
        val max = observationState?.definition?.maxObservationForms
        val totalForms = observationState?.forms?.value?.size ?: 0
        if (max == null || totalForms < max) {
          ExtendedFloatingActionButton(
            icon = {
              Icon(
                Icons.Default.NoteAdd,
                contentDescription = "Add Form",
                tint = Color.White
              )
            },
            text = { Text("ADD FORM", color = Color.White) },
            onClick = { onAddForm?.invoke() }
          )
        }
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationEditTopBar(
  isNewObservation: Boolean,
  onSave: () -> Unit,
  onCancel: () -> Unit
) {
  val title = if (isNewObservation) "Create Observation" else "Observation Edit"
  TopAppBar(
    title = { Text(title) },
    navigationIcon = {
      IconButton(onClick = { onCancel.invoke() }) {
        Icon(Icons.Default.Close, "Cancel Edit")
      }
    },
    actions = {
      TextButton(
        onClick = { onSave.invoke() }
      ) {
        Text("SAVE")
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
fun ObservationMediaBar(
  onAction: (MediaActionType) -> Unit
) {
  Surface(
    shadowElevation = 2.dp,
  ) {
    Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      modifier = Modifier.fillMaxWidth()
    ) {
      IconButton(onClick = { onAction.invoke(MediaActionType.GALLERY) }) {
        Icon(Icons.Default.Image, "Capture Gallery", tint = Color(0xFF66BB6A))
      }
      IconButton(onClick = { onAction.invoke(MediaActionType.PHOTO) }) {
        Icon(Icons.Default.PhotoCamera, "Capture Photo", tint = Color(0xFF42A5F5))
      }
      IconButton(onClick = { onAction.invoke(MediaActionType.VIDEO) }) {
        Icon(Icons.Default.Videocam, "Capture Video", tint = Color(0xFFEC407A))
      }
      IconButton(onClick = { onAction.invoke(MediaActionType.VOICE) }) {
        Icon(Icons.Default.Mic, "Capture Audio", tint = Color(0xFFAB47BC))
      }
    }
  }
}

@Composable
fun ObservationEditContent(
   event: Event?,
   observationState: ObservationState?,
   listState: LazyListState,
   onFieldClick: ((FieldState<*, *>) -> Unit)? = null,
   onMediaAction: ((MediaAction) -> Unit)? = null,
   onAttachmentAction: ((AttachmentAction, Attachment, FieldState<*, *>?) -> Unit)? = null,
   onDeleteForm: ((Int, FormState) -> Unit)? = null,
   onReorderForms: (() -> Unit)? = null
) {
  val context = LocalContext.current

  if (observationState != null) {
    val forms by observationState.forms
    var previousForms by remember { mutableStateOf<List<FormState>>(listOf()) }

    // TODO scroll to added element, not last
    LaunchedEffect(forms.size) {
      if (previousForms.isNotEmpty() && forms.size > previousForms.size) {
        // find new form that was added, diff between forms and previous forms
        val addedForm = forms.filterNot { previousForms.contains(it) }.first()
        val scrollTo = forms.indexOf(addedForm) + 2 // account for 2 "header" items in list
        listState.animateScrollToItem(scrollTo)
      }

      previousForms = forms
    }

    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(
        start = 8.dp,
        end = 8.dp,
        top = 8.dp,
        bottom = 72.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .background(Color(0x19000000))
        .fillMaxHeight()
    ) {
      item {
        ObservationEditHeaderContent(
          event = event,
          timestamp = observationState.timestampFieldState,
          geometry = observationState.geometryFieldState,
          formState = forms.getOrNull(0),
          onTimestampClick = { onFieldClick?.invoke(observationState.timestampFieldState) },
          onLocationClick = { onFieldClick?.invoke(observationState.geometryFieldState) }
        )
      }

      if (isServerVersion5(context)) {
        item {
          val attachments by observationState.attachments
          AttachmentsViewContentServer5(attachments) {
            onAttachmentAction?.invoke(AttachmentAction.VIEW, it, null)
          }
        }
      }

      if (forms.isNotEmpty()) {
        item {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
          ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
              Text(
                text = "FORMS",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                  .weight(1f)
                  .padding(vertical = 16.dp)
              )
            }

            if (forms.size > 1) {
              IconButton(
                onClick = { onReorderForms?.invoke() },
              ) {
                Icon(
                  Icons.Default.SwapVert,
                  tint = MaterialTheme.colorScheme.primary,
                  contentDescription = "Reorder Forms")
              }
            }
          }
        }
      }

      itemsIndexed(forms) { index, formState ->
        FormEditContent(
          event = event,
          formState = formState,
          onFormDelete = { onDeleteForm?.invoke(index, formState) },
          onFieldClick = { onFieldClick?.invoke(it) },
          onMediaAction = { type, field ->
            onMediaAction?.invoke(MediaAction(type, index, field.name))
          },
          onAttachmentAction = onAttachmentAction
        )
      }
    }
  }
}

@Composable
fun ObservationEditHeaderContent(
  event: Event?,
  timestamp: DateFieldState,
  geometry: GeometryFieldState,
  formState: FormState? = null,
  onTimestampClick: (() -> Unit)? = null,
  onLocationClick: (() -> Unit)? = null
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
      DateEdit(
        modifier = Modifier.padding(bottom = 16.dp),
        fieldState = timestamp,
        onClick = onTimestampClick
      )

      GeometryEditContent(
        event = event,
        fieldState = geometry,
        formState = formState,
        onClick = onLocationClick,
        modifier = Modifier.padding(bottom = 16.dp)
      )
    }
  }
}