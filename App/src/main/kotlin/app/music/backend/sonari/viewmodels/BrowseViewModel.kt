package app.music.backend.sonari.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.music.backend.sonari.innertube.YouTube
import app.music.backend.sonari.innertube.models.YTItem
import dagger.hilt.android.lifecycle.HiltViewModel
import app.music.backend.sonari.utils.reportException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BrowseViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
  private val browseId: String? = savedStateHandle.get<String>("browseId")

  val items = MutableStateFlow<List<YTItem>?>(emptyList())
  val title = MutableStateFlow<String?>("")

  init {
    viewModelScope.launch {
      browseId?.let {
        YouTube.browse(browseId, null)
          .onSuccess { result ->
            title.value = result.title

            val allItems = result.items.flatMap { it.items }
            items.value = allItems
          }
          .onFailure { reportException(it) }
      }
    }
  }
}
