package app.music.backend.sonari.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.music.backend.sonari.generate.TasteMix
import app.music.backend.sonari.generate.TasteMixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Hands the screens the mixes the repository keeps: they are built on their own, never downloaded
 * until the user asks for it, and can be kept, or dropped as "not interested".
 */
@HiltViewModel
class TasteSuggestionsViewModel @Inject constructor(
  private val repository: TasteMixRepository,
) : ViewModel() {
  val mixes: StateFlow<List<TasteMix>> = repository.mixes
  val loading: StateFlow<Boolean> = repository.loading

  private val _savingId = MutableStateFlow<String?>(null)
  val savingId: StateFlow<String?> = _savingId.asStateFlow()

  private val _kept = MutableStateFlow<Set<String>>(emptySet())
  val kept: StateFlow<Set<String>> = _kept.asStateFlow()

  init {
    viewModelScope.launch { repository.refresh() }
  }

  fun refresh() {
    viewModelScope.launch { repository.refresh(force = true) }
  }

  fun save(playlistId: String) {
    if (playlistId in _kept.value) return
    viewModelScope.launch {
      _savingId.value = playlistId
      if (repository.keep(playlistId)) _kept.value = _kept.value + playlistId
      _savingId.value = null
    }
  }

  fun notInterested(playlistId: String, tracks: List<Pair<String, String>>) {
    viewModelScope.launch { repository.notInterested(playlistId, tracks) }
  }

  /** "Never recommend these": keeps the songs and their artists out of every future mix. */
  fun neverRecommend(tracks: List<Pair<String, String>>) {
    viewModelScope.launch { repository.neverRecommend(tracks) }
  }

  /** "Recommend more like this": grows one mix in place. */
  fun moreLikeThis(playlistId: String) {
    viewModelScope.launch { repository.moreLikeThis(playlistId) }
  }
}
