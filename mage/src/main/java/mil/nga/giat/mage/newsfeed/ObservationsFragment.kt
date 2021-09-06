package mil.nga.giat.mage.newsfeed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ObservationsFragment: Fragment() {
   private lateinit var viewModel: ObservationsViewModel

   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)

      viewModel = ViewModelProvider(this).get(ObservationsViewModel::class.java)
   }

   override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
   ): View {
      return ComposeView(requireContext()).apply {
         setContent {
            ObservationsViewScreen(viewModel.observationPagingFlowLiveData)
         }
      }
   }
}