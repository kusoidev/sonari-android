package app.music.backend.sonari.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.music.backend.sonari.generate.LocalTasteEngine
import app.music.backend.sonari.generate.TasteSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Keeps the taste mix ready for the home row, so opening the app shows suggestions instead of a
 * button the user has to find and press.
 */
@HiltViewModel
class TasteSuggestionsViewModel @Inject constructor(
  private val localTasteEngine: LocalTasteEngine,
) : ViewModel() {
  private val _suggestions = MutableStateFlow<List<TasteSuggestion>>(emptyList())
  val suggestions: StateFlow<List<TasteSuggestion>> = _suggestions.asStateFlow()

  private val _loading = MutableStateFlow(true)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()

  private val _saving = MutableStateFlow(false)
  val saving: StateFlow<Boolean> = _saving.asStateFlow()

  private val _savedPlaylistId = MutableStateFlow<String?>(null)
  val savedPlaylistId: StateFlow<String?> = _savedPlaylistId.asStateFlow()

  private var job: Job? = null

  init {
    load()
  }

  fun load(force: Boolean = false) {
    if (job?.isActive == true) return
    if (!force && _suggestions.value.isNotEmpty()) return

    job =
      viewModelScope.launch {
        _loading.value = true
        val found = localTasteEngine.suggest(HomeSuggestionCount)
        _suggestions.value = found
        _loading.value = false
      }
  }

  fun saveAsPlaylist() {
    if (_suggestions.value.isEmpty() || _saving.value) return
    viewModelScope.launch {
      _saving.value = true
      val id = localTasteEngine.save(_suggestions.value)
      if (id != null) _savedPlaylistId.value = id
      _saving.value = false
    }
  }

  companion object {
    const val HomeSuggestionCount = 12
  }
}
