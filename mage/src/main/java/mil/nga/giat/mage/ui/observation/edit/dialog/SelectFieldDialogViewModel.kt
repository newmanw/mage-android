package mil.nga.giat.mage.ui.observation.edit.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mil.nga.giat.mage.form.Choice
import javax.inject.Inject

@HiltViewModel
open class SelectFieldDialogViewModel @Inject constructor(
) : ViewModel() {

   private val queryFlow = MutableSharedFlow<String>(replay = 1)
   private val choicesFlow = MutableSharedFlow<List<Choice>>(replay = 1)

   fun setChoices(choices: List<Choice>) {
      viewModelScope.launch {
         choicesFlow.emit(choices)
         queryFlow.emit("")
      }
   }

   fun setQuery(query: String) {
      viewModelScope.launch {
         queryFlow.emit(query)
      }
   }

   val query = queryFlow.map { it }.asLiveData()

   val choices = combine(choicesFlow, queryFlow) { choices, query ->
      if (query.isNotEmpty()) {
         choices.filter { choice ->
            choice.title.lowercase().contains(query.lowercase())
         }
      } else choices
   }.asLiveData()
}