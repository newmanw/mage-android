package mil.nga.giat.mage.ui.observation.edit

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import mil.nga.giat.mage.form.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import mil.nga.giat.mage.database.model.event.Event
import mil.nga.giat.mage.form.field.*
import mil.nga.giat.mage.form.FormState
import mil.nga.giat.mage.ui.observation.attachment.AttachmentsViewContent
import mil.nga.giat.mage.ui.observation.view.FormHeaderContent
import mil.nga.giat.mage.database.model.observation.Attachment
import mil.nga.giat.mage.ui.theme.onSurfaceDisabled
import mil.nga.giat.mage.utils.DateFormatFactory
import java.util.*

@Composable
fun FormEditContent(
  event: Event?,
  formState: FormState,
  onFormDelete: (() -> Unit)? = null,
  onFieldClick: ((FieldState<*, *>) -> Unit)? = null,
  onAttachmentAction: ((AttachmentAction, Attachment, FieldState<*, *>) -> Unit)? = null,
  onMediaAction: ((MediaActionType, FormField<*>) -> Unit)? = null
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(Modifier.animateContentSize()) {
      FormHeaderContent(
        modifier = Modifier.padding(16.dp),
        formState = formState
      ) { formState.expanded.value = it }

      if (formState.expanded.value) {
        for (fieldState in formState.fields.sortedBy { it.definition.id }) {
          FieldEditContent(
            modifier = Modifier.padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
            event = event,
            fieldState = fieldState,
            onClick = { onFieldClick?.invoke(fieldState) },
            onMediaAction = { action -> onMediaAction?.invoke(action, fieldState.definition) },
            onAttachmentAction = { action, media -> onAttachmentAction?.invoke(action, media, fieldState) }
          )
        }

        Divider()

        Row(
          horizontalArrangement = Arrangement.End,
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          TextButton(
            onClick = { onFormDelete?.invoke() }
          ) {
            Text("DELETE FORM", color = MaterialTheme.colorScheme.error)
          }
        }
      }
    }
  }
}

@Composable
fun FieldEditContent(
  modifier: Modifier = Modifier,
  event: Event?,
  fieldState: FieldState<*, out FieldValue>,
  onMediaAction: ((MediaActionType) -> Unit)? = null,
  onAttachmentAction: ((AttachmentAction, Attachment) -> Unit)? = null,
  onClick: (() -> Unit)? = null
) {
  when (fieldState.definition.type) {
    FieldType.ATTACHMENT -> {
      AttachmentEdit(
        modifier,
        fieldState as AttachmentFieldState,
        onAttachmentAction,
        onMediaAction)
    }
    FieldType.CHECKBOX -> {
      CheckboxEdit(
        modifier,
        fieldState as BooleanFieldState
      ) {
        fieldState.answer = FieldValue.Boolean(it)
      }
    }
    FieldType.DATE -> {
      DateEdit(
        modifier,
        fieldState as DateFieldState,
        onClick)
    }
    FieldType.DROPDOWN -> {
      SelectEdit(
        modifier,
        fieldState as SelectFieldState,
        onClick
      )
    }
    FieldType.EMAIL -> {
      TextEdit(
        modifier,
        fieldState as EmailFieldState,
        icon = {
          Icon(
            imageVector = Icons.Outlined.Email,
            contentDescription = "Email",
          )
        }
      ) {
        fieldState.answer = FieldValue.Text(it)
      }
    }
    FieldType.GEOMETRY -> {
      GeometryEditContent(
        modifier = modifier,
        event = event,
        fieldState as GeometryFieldState,
        onClick = onClick
      )
    }
    FieldType.MULTISELECTDROPDOWN -> {
      MultiSelectEdit(
        modifier,
        fieldState as MultiSelectFieldState,
        onClick)
    }
    FieldType.NUMBERFIELD -> {
      NumberEdit(
        modifier,
        fieldState as NumberFieldState
      ) {
        fieldState.answer = FieldValue.Number(it)
      }
    }
    FieldType.PASSWORD -> {
      TextEdit(
        modifier,
        fieldState as TextFieldState,
        icon = {
          Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = "Password",
          )
        }
      ) {
        fieldState.answer = FieldValue.Text(it)
      }
    }
    FieldType.RADIO -> {
      RadioEdit(
        modifier,
        fieldState as RadioFieldState
      ) {
        fieldState.answer = FieldValue.Text(it)
      }
    }
    FieldType.TEXTFIELD -> {
      TextEdit(
        modifier,
        fieldState as TextFieldState,
        icon = {
          Icon(
            imageVector = Icons.Outlined.Title,
            contentDescription = "Text",
          )
        }
      ) {
        fieldState.answer = FieldValue.Text(it)
      }
    }
    FieldType.TEXTAREA -> {
      TextEdit(
        modifier,
        fieldState as TextFieldState,
        icon = {
          Icon(
            imageVector = Icons.Outlined.TextFields,
            contentDescription = "Text",
          )
        }
      ) {
        fieldState.answer = FieldValue.Text(it)
      }
    }
  }
}

@Composable
fun AttachmentEdit(
  modifier: Modifier = Modifier,
  fieldState: AttachmentFieldState,
  onAttachmentAction: ((AttachmentAction, Attachment) -> Unit)? = null,
  onMediaAction: ((MediaActionType) -> Unit)? = null
) {
  val attachments = fieldState.answer?.attachments?.filter { it.action != Media.ATTACHMENT_DELETE_ACTION } ?: listOf()
  var size by remember { mutableStateOf(attachments.size) }
  val error = fieldState.getError()
  val fieldDefinition = fieldState.definition as? AttachmentFormField

  LaunchedEffect(attachments.size) {
    if (size != attachments.size) {
      fieldState.onFocusChange(true)
      fieldState.enableShowErrors()
    }

    size = attachments.size
  }

  Column(modifier) {
    Column(
      Modifier
        .fillMaxWidth()
        .clip(shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
      CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        val titleColor = if (error == null) Color.Unspecified else MaterialTheme.colorScheme.error
        val min = fieldDefinition?.min?.toInt() ?: 0
        Text(
          text = "${fieldState.definition.title} ${if (min > 0) "*" else ""}",
          fontSize = 12.sp,
          color = titleColor,
          modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
        )
      }

      Column {
        if (attachments.isEmpty()) {
          CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceDisabled) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier
                .fillMaxSize()
                .height(200.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.InsertDriveFile,
                contentDescription = "Form",
                modifier = Modifier
                  .padding(end = 8.dp)
                  .height(60.dp)
                  .width(60.dp)
              )

              Text(
                text = "No Attachments",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
              )
            }
          }
        } else {
          AttachmentsViewContent(
            attachments,
            deletable = true,
            onAttachmentAction = onAttachmentAction
          )
        }
      }

      Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))

      CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(
          horizontalArrangement = Arrangement.End,
          modifier = Modifier.fillMaxWidth()
        ) {
          val restrict = fieldDefinition?.allowedAttachmentTypes?.isNotEmpty() == true

          if (!restrict || fieldDefinition?.allowedAttachmentTypes?.contains(AttachmentType.AUDIO) == true) {
            IconButton(
              modifier = Modifier.padding(horizontal = 4.dp),
              onClick = { onMediaAction?.invoke(MediaActionType.VOICE) }
            ) {
              Icon(Icons.Default.Mic, "Capture Audio")
            }
          }

          if (!restrict || fieldDefinition?.allowedAttachmentTypes?.contains(AttachmentType.IMAGE) == true) {
            IconButton(
              modifier = Modifier.padding(horizontal = 4.dp),
              onClick = { onMediaAction?.invoke(MediaActionType.PHOTO) }
            ) {
              Icon(Icons.Default.PhotoCamera, "Capture Photo")
            }
          }

          if (!restrict || fieldDefinition?.allowedAttachmentTypes?.any { it == AttachmentType.IMAGE || it == AttachmentType.VIDEO } == true) {
            IconButton(
              modifier = Modifier.padding(horizontal = 4.dp),
              onClick = { onMediaAction?.invoke(MediaActionType.GALLERY) }
            ) {
              Icon(Icons.Default.PhotoLibrary, "Capture Gallery")
            }
          }

          if (!restrict || fieldDefinition?.allowedAttachmentTypes?.contains(AttachmentType.VIDEO) == true) {
            IconButton(
              modifier = Modifier.padding(horizontal = 4.dp),
              onClick = { onMediaAction?.invoke(MediaActionType.VIDEO) }
            ) {
              Icon(Icons.Default.Videocam, "Capture Video")
            }
          }

          if (!restrict) {
            IconButton(
              modifier = Modifier.padding(horizontal = 4.dp),
              onClick = { onMediaAction?.invoke(MediaActionType.FILE) }
            ) {
              Icon(Icons.Default.AttachFile, "Attach File")
            }
          }
        }
      }

    }

    error?.let { error -> TextFieldError(textError = error) }
  }
}

@Composable
fun CheckboxEdit(
  modifier: Modifier = Modifier,
  fieldState: BooleanFieldState,
  onAnswer: (Boolean) -> Unit,
) {
  val value = fieldState.answer?.boolean == true

  Column(modifier) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 4.dp)) {
      Checkbox(
        checked = value,
        onCheckedChange = onAnswer,
        colors = CheckboxDefaults.colors(MaterialTheme.colorScheme.primary),
        modifier = Modifier.padding(end = 4.dp)
      )

      Text(text = fieldState.definition.title)
    }

    fieldState.getError()?.let { error -> TextFieldError(textError = error) }
  }
}

@Composable
fun DateEdit(
  modifier: Modifier = Modifier,
  fieldState: DateFieldState,
  onClick: (() -> Unit)? = null
) {
  val dateFormat = DateFormatFactory.format("yyyy-MM-dd HH:mm zz", Locale.getDefault(), LocalContext.current)
  val date = fieldState.answer?.date

  val focusManager = LocalFocusManager.current
  val labelColor = if (fieldState.showErrors()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceDisabled

  Column(modifier) {
    TextField(
      value = if (date != null) dateFormat.format(date) else "",
      onValueChange = { },
      label = { Text("${fieldState.definition.title}${if (fieldState.definition.required) " *" else ""}") },
      enabled = false,
      isError = fieldState.showErrors(),
      colors = TextFieldDefaults.colors(
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface,
        disabledLabelColor = labelColor,
      ),
      trailingIcon = {
        Icon(
          imageVector = Icons.Outlined.Today,
          contentDescription = "Calendar",
        )
      },
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = {
          onClick?.invoke()
          focusManager.clearFocus()
          fieldState.onFocusChange(true)
          fieldState.enableShowErrors()
        })
    )

    fieldState.getError()?.let { error -> TextFieldError(textError = error) }
  }
}

@Composable
fun TextEdit(
  modifier: Modifier = Modifier,
  fieldState: FieldState<String, out FieldValue.Text>,
  icon: @Composable (() -> Unit)? = null,
  onAnswer: (String) -> Unit,
) {
  val focusManager = LocalFocusManager.current

  val keyboardType = if (fieldState.definition.type == FieldType.EMAIL) {
    KeyboardType.Email
  } else {
    KeyboardType.Text
  }

  Column(modifier) {
    TextField(
      value = fieldState.answer?.text ?: "",
      onValueChange = onAnswer,
      label = { Text("${fieldState.definition.title}${if (fieldState.definition.required) " *" else ""}") },
      singleLine = fieldState.definition.type != FieldType.TEXTAREA,
      isError = fieldState.showErrors(),
      keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
      keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
      visualTransformation = if (fieldState.definition.type == FieldType.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None,
      trailingIcon = icon,
      modifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { focusState ->
          val focused = focusState.isFocused
          fieldState.onFocusChange(focused)
          if (!focused) {
            fieldState.enableShowErrors()
          }
        }
    )

    fieldState.getError()?.let { error -> TextFieldError(textError = error) }
  }
}

@Composable
fun NumberEdit(
  modifier: Modifier = Modifier,
  fieldState: FieldState<Number, FieldValue.Number>,
  onAnswer: (String) -> Unit,
) {
  val focusManager = LocalFocusManager.current

  Column(modifier) {
    TextField(
      value = fieldState.answer?.number ?: "",
      onValueChange = { onAnswer(it) },
      label = { Text("${fieldState.definition.title}${if (fieldState.definition.required) " *" else ""}") },
      singleLine = fieldState.definition.type != FieldType.TEXTAREA,
      isError = fieldState.showErrors(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
      trailingIcon = {
        Icon(
          imageVector = Icons.Outlined.Tag,
          contentDescription = "Number",
        )
      },
      modifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { focusState ->
          val focused = focusState.isFocused
          fieldState.onFocusChange(focused)
          if (!focused) {
            fieldState.enableShowErrors()
          }
        }
    )

    fieldState.getError()?.let { error -> TextFieldError(textError = error) }
  }
}

@Composable
fun MultiSelectEdit(
  modifier: Modifier = Modifier,
  fieldState: MultiSelectFieldState,
  onClick: (() -> Unit)? = null
) {
  val focusManager = LocalFocusManager.current
  val labelColor = if (fieldState.showErrors()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceDisabled

  Column(modifier) {
    TextField(
      value = fieldState.answer?.choices?.joinToString(", ") ?: "",
      onValueChange = { },
      label = { Text("${fieldState.definition.title}${if (fieldState.definition.required) " *" else ""}") },
      enabled = false,
      isError = fieldState.showErrors(),
      colors = TextFieldDefaults.colors(
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface,
        disabledLabelColor = labelColor,
      ),
      trailingIcon = {
        Icon(
          imageVector = Icons.Filled.ArrowDropDown,
          contentDescription = "Dropdown",
        )
      },
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = {
          onClick?.invoke()
          focusManager.clearFocus()
          fieldState.onFocusChange(true)
          fieldState.enableShowErrors()
        })
    )

    fieldState.getError()?.let { error -> TextFieldError(textError = error) }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectEdit(
  modifier: Modifier = Modifier,
  fieldState: SelectFieldState,
  onClick: (() -> Unit)? = null
) {
  val focusManager = LocalFocusManager.current
  val labelColor = if (fieldState.showErrors()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceDisabled

  Column(modifier) {
    TextField(
      value = fieldState.answer?.text ?: "",
      onValueChange = { },
      label = { Text("${fieldState.definition.title}${if (fieldState.definition.required) " *" else ""}") },
      enabled = false,
      isError = fieldState.showErrors(),
      colors = TextFieldDefaults.colors(
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface,
        disabledLabelColor = labelColor,
      ),
      trailingIcon = {
        Icon(
          imageVector = Icons.Filled.ArrowDropDown,
          contentDescription = "Dropdown",
        )
      },
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = {
          onClick?.invoke()
          focusManager.clearFocus()
          fieldState.onFocusChange(true)
          fieldState.enableShowErrors()
        })
    )

    fieldState.getError()?.let { error -> TextFieldError(textError = error) }
  }
}

@Composable
fun RadioEdit(
  modifier: Modifier = Modifier,
  fieldState: RadioFieldState,
  onAnswer: (String) -> Unit,
) {
  val definition = fieldState.definition as SingleChoiceFormField

  Column(modifier) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
      Text(
        text = "${fieldState.definition.title}${if (fieldState.definition.required) " *" else ""}",
        fontSize = 14.sp,
        modifier = Modifier.padding(bottom = 8.dp)
      )
    }

    definition.choices.forEach { choice ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        RadioButton(
          selected = (fieldState.answer?.text == choice.title),
          onClick = { onAnswer.invoke(choice.title) },
          colors = RadioButtonDefaults.colors(MaterialTheme.colorScheme.primary),
          modifier = Modifier.padding(end = 4.dp)
        )

        Text(text = choice.title)
      }
    }

    fieldState.getError()?.let { error -> TextFieldError(textError = error) }
  }
}

/**
 * To be removed when [TextField]s support error
 */
@Composable
fun TextFieldError(textError: String) {
  Row(modifier = Modifier.fillMaxWidth()) {
    Spacer(modifier = Modifier.width(16.dp))

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
      Text(
        text = textError,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
      )
    }
  }
}