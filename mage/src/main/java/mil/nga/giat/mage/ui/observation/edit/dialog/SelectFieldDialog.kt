package mil.nga.giat.mage.ui.observation.edit.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import mil.nga.giat.mage.form.Choice
import mil.nga.giat.mage.form.MultiChoiceFormField
import mil.nga.giat.mage.form.SingleChoiceFormField
import mil.nga.giat.mage.form.field.MultiSelectFieldState
import mil.nga.giat.mage.form.field.SelectFieldState

@Composable
fun SelectFieldDialog(
  state: SelectFieldState?,
  onSave: (SelectFieldState, Choice) -> Unit,
  onClear: (SelectFieldState) -> Unit,
  onCancel: () -> Unit,
  viewModel: SelectFieldDialogViewModel = hiltViewModel()
) {
  val query by viewModel.query.observeAsState("")
  val choices by viewModel.choices.observeAsState(emptyList())

  val definition = state?.definition as? SingleChoiceFormField
  LaunchedEffect(definition) {
    if (definition != null) {
      viewModel.setChoices(definition.choices)
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
            }
          )

          Search(
            query = query,
            onQueryChange = {
              viewModel.setQuery(it)
            },
            onSearch = {
              viewModel.setQuery(it)
            }
          )
          Content(
            multi = false,
            choices = choices,
            selected = listOfNotNull(state.answer?.text),
            onSelect = { choice, _ -> onSave(state, choice) }
          )
        }
      }
    }
  }
}

@Composable
fun MultiSelectFieldDialog(
  state: MultiSelectFieldState?,
  onSave: (MultiSelectFieldState, List<String>) -> Unit,
  onClear: (MultiSelectFieldState) -> Unit,
  onCancel: () -> Unit,
  viewModel: SelectFieldDialogViewModel = hiltViewModel()
) {
  val query by viewModel.query.observeAsState("")
  val choices by viewModel.choices.observeAsState(emptyList())
  val selected = remember { mutableStateListOf<String>() }

  val definition = state?.definition as? MultiChoiceFormField
  LaunchedEffect(definition) {
    if (definition != null) {
      viewModel.setChoices(definition.choices)
    }
  }

  LaunchedEffect(state?.answer?.choices) {
    selected.clear()
    state?.answer?.choices?.let { selected.addAll(it) }
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

              TextButton(onClick = { onSave(state, selected.toList()) }) {
                Text("Save")
              }
            }
          )

          Search(
            query = query,
            onQueryChange = {
              viewModel.setQuery(it)
            },
            onSearch = {
              viewModel.setQuery(it)
            }
          )

          Content(
            multi = true,
            choices = choices,
            selected = selected,
            onSelect = { choice, select ->
              if (select) {
                selected.add(choice.title)
              } else {
                selected.remove(choice.title)
              }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Search(
  query: String,
  onQueryChange: (String) -> Unit,
  onSearch: (String) -> Unit
) {
  val focusManager = LocalFocusManager.current

  SearchBar(
    placeholder = { Text(text = "Search choices") },
    leadingIcon = { Icon(Icons.Default.Search, "search") },
    query = query,
    onQueryChange = { onQueryChange(it) },
    onSearch = {
      focusManager.clearFocus()
      onSearch(it)
    },
    active = false,
    onActiveChange = { },
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
  ){}
}

@Composable
private fun Content(
  multi: Boolean,
  choices: List<Choice>,
  selected: List<String>,
  onSelect: (Choice, Boolean) -> Unit
) {
  Column(
    Modifier
      .padding(horizontal = 24.dp)
      .fillMaxHeight()
      .verticalScroll(rememberScrollState())
  ) {
    choices.forEach { choice ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .padding(vertical = 4.dp)
          .clickable { }
      ) {
        Text(
          text = choice.title,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.weight(1f)
        )

        val checked = selected.any { it == choice.title }
        if (multi) {
          Checkbox(
            checked = checked,
            onCheckedChange = { onSelect(choice, !checked) }
          )
        } else {
          RadioButton(
            selected = checked,
            onClick = { onSelect(choice, !checked) }
          )
        }
      }

      Divider(Modifier.fillMaxWidth())
    }
  }
}







