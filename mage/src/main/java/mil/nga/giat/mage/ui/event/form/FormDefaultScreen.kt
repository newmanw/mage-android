package mil.nga.giat.mage.ui.event.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import mil.nga.giat.mage.database.model.event.Event
import mil.nga.giat.mage.form.FieldType
import mil.nga.giat.mage.form.FormState
import mil.nga.giat.mage.form.field.FieldState
import mil.nga.giat.mage.ui.observation.edit.FieldEditContent
import mil.nga.giat.mage.ui.theme.MageTheme3

data class FormKey(
  val eventId: Long,
  val formId: Long
)

@Composable
fun FormDefaultScreen(
  key: FormKey,
  onClose: () -> Unit,
  onFieldTap: (FieldState<*, *>) -> Unit,
  viewModel: FormDefaultViewModel = hiltViewModel()
) {
  val formState by viewModel.formState.observeAsState()

  LaunchedEffect(key) {
    viewModel.setForm(key)
  }

  MageTheme3 {
    Scaffold(
      topBar = {
        TopBar(
          formName = formState?.definition?.name,
          onClose = { onClose() },
          onSave = {
            viewModel.saveDefaults()
            onClose()
          }
        )
      },
      content = { paddingValues ->
        Surface(Modifier.padding(paddingValues)) {
          Content(
            event = viewModel.event,
            formState = formState,
            onReset = {
              viewModel.resetDefaults()
            },
            onFieldClick = onFieldTap
          )
        }
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
  formName: String?,
  onClose: () -> Unit,
  onSave: () -> Unit
) {
  TopAppBar(
    title = {
      Column {
        Text(formName ?: "")
      }
    },
    navigationIcon = {
      IconButton(onClick = { onClose.invoke() }) {
        Icon(Icons.Default.Close, "Cancel Default")
      }
    },
    actions = {
      TextButton(
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
        onClick = { onSave() }
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
fun Content(
  event: Event?,
  formState: FormState?,
  onReset: (() -> Unit)? = null,
  onFieldClick: ((FieldState<*, *>) -> Unit)? = null
) {
  Surface(
    Modifier
      .fillMaxHeight()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 8.dp)
  ) {
    if (formState?.definition != null) {
      DefaultContent(
        event = event,
        formState = formState,
        onReset = onReset,
        onFieldClick = onFieldClick
      )
    }
  }
}

@Composable
fun DefaultHeader(
  name: String,
  description: String?,
  color: String
) {
  Row(
    modifier = Modifier.padding(vertical = 16.dp)
  ) {
    Icon(
      imageVector = Icons.Default.Description,
      contentDescription = "Form",
      tint = Color(android.graphics.Color.parseColor(color)),
      modifier = Modifier
        .width(40.dp)
        .height(40.dp)
    )

    Column(
      Modifier
        .weight(1f)
        .padding(start = 16.dp)
        .fillMaxWidth()
    ) {
      Text(
        text = name,
        style = MaterialTheme.typography.headlineSmall
      )

      CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
        if (description?.isNotEmpty() == true) {
          Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }
    }
  }
}

@Composable
fun DefaultContent(
  event: Event?,
  formState: FormState,
  onReset: (() -> Unit)? = null,
  onFieldClick: ((FieldState<*, *>) -> Unit)? = null
) {
  Column {
    Text(
      text = "Custom Form Defaults",
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.padding(16.dp)
    )

    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
      Text(
        text = "Personalize the default values MAGE will autofill when you add this form to an observation.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
      )
    }

    DefaultFormContent(
      event = event,
      formState = formState,
      onFieldClick = onFieldClick
    )

    Divider()

    Row(
      horizontalArrangement = Arrangement.End,
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      TextButton(
        onClick = { onReset?.invoke() },
      ) {
        Text(text = "RESET TO SERVER DEFAULTS")
      }
    }
  }
}

@Composable
fun DefaultFormContent(
  event: Event?,
  formState: FormState,
  onFieldClick: ((FieldState<*, *>) -> Unit)? = null
) {
  val fields = formState.fields
    .filter { it.definition.type != FieldType.ATTACHMENT }
    .sortedBy { it.definition.id }

  for (fieldState in fields) {
    FieldEditContent(
      modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
      event = event,
      fieldState = fieldState,
      onClick = { onFieldClick?.invoke(fieldState) }
    )
  }
}
