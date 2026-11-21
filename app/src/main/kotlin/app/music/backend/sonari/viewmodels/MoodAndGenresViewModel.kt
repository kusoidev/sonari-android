package app.music.backend.sonari.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.music.backend.sonari.innertube.YouTube
import app.music.backend.sonari.innertube.pages.MoodAndGenres
import dagger.hilt.android.lifecycle.HiltViewModel
import app.music.backend.sonari.utils.reportException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MoodAndGenresViewModel @Inject constructor() : ViewModel() {
  val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)

  init {
    viewModelScope.launch {
      YouTube.moodAndGenres()
        .onSuccess { moodAndGenres.value = it }
        .onFailure { reportException(it) }
    }
  }
}
