package mil.nga.giat.mage.ui.observation.edit.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mil.nga.giat.mage.form.field.DateFieldState
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFieldDialog(
  state: DateFieldState?,
  onSave: (DateFieldState, Date) -> Unit,
  onClear: (DateFieldState) -> Unit,
  onCancel: () -> Unit
) {
  val datePickerState = rememberDatePickerState()

  LaunchedEffect(state) {
    state?.answer?.date?.time?.let { timestamp ->
      datePickerState.setSelection(timestamp)
    }
  }

  if (state != null) {
    Dialog(
      properties  = DialogProperties(usePlatformDefaultWidth = false),
      onDismissRequest = { onCancel() }
    ) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxSize()
      ) {
        Column {
          TopBar(
            title = state.definition.title,
            onCancel = { onCancel() },
            actions = {
              TextButton(onClick = { onClear(state) }) {
                Text("Clear")
              }

              TextButton(
                onClick = {
                  datePickerState.selectedDateMillis?.let { timestamp ->
                    onSave(state, Date(timestamp))
                  }
                }
              ) {
                Text("Save")
              }
            }
          )

          DatePicker(
            state = datePickerState,
            title = null,
            showModeToggle = false
          )
        }
      }
    }
  }
}

@Composable
private fun TopBar(
  title: String,
  onCancel: () -> Unit,
  actions: @Composable RowScope.() -> Unit = {}
) {
  Box(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .height(56.dp)
        .fillMaxWidth()
    ) {
      IconButton(
        onClick = { onCancel() },
        modifier = Modifier.padding(end = 8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "close"
        )
      }

      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.weight(1f)
      )

      actions()
    }
  }
}






