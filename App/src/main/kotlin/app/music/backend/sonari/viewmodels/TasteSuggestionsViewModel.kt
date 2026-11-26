package app.music.backend.sonari.viewmodels

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import app.music.backend.sonari.constants.AutoDownloadPlaylistsKey
import app.music.backend.sonari.generate.LocalTasteEngine
import app.music.backend.sonari.generate.TasteMix
import app.music.backend.sonari.playback.ExoDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the "For you" playlist built and downloaded on its own, so opening the app finds a playlist
 * rather than a row of loose songs. Downloads are skipped only when the setting turns them off.
 */
@HiltViewModel
class TasteSuggestionsViewModel @Inject constructor(
  private val localTasteEngine: LocalTasteEngine,
  @ApplicationContext private val context: Context,
  private val dataStore: DataStore<Preferences>,
) : ViewModel() {
  private val _mix = MutableStateFlow<TasteMix?>(null)
  val mix: StateFlow<TasteMix?> = _mix.asStateFlow()

  private val _loading = MutableStateFlow(true)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()

  private val _saving = MutableStateFlow(false)
  val saving: StateFlow<Boolean> = _saving.asStateFlow()

  private val _kept = MutableStateFlow(false)
  val kept: StateFlow<Boolean> = _kept.asStateFlow()

  private var job: Job? = null

  init {
    load()
  }

  fun load(force: Boolean = false) {
    if (job?.isActive == true) return
    if (!force && _mix.value != null) return

    job =
      viewModelScope.launch {
        _loading.value = true
        val size = Random.nextInt(MinSongs, MaxSongs + 1)
        val built = localTasteEngine.build(size)
        _mix.value = built
        _loading.value = false
        if (built != null) download(built)
      }
  }

  /** Keeps the built playlist in the playlists list. */
  fun save() {
    if (_mix.value == null || _saving.value) return
    viewModelScope.launch {
      _saving.value = true
      if (localTasteEngine.keep()) _kept.value = true
      _saving.value = false
    }
  }

  private suspend fun download(mix: TasteMix) {
    val auto =
      runCatching { dataStore.data.first()[AutoDownloadPlaylistsKey] }.getOrNull() ?: true
    if (!auto) return

    mix.suggestions.forEach { suggestion ->
      val id = suggestion.song.id
      // Only YouTube ids can go through the app's downloader; SoundCloud ones are skipped.
      if (id.length != 11) return@forEach
      val request =
        DownloadRequest.Builder(id, id.toUri())
          .setCustomCacheKey(id)
          .setData(suggestion.song.title.toByteArray())
          .build()
      DownloadService.sendAddDownload(context, ExoDownloadService::class.java, request, false)
    }
  }

  companion object {
    const val MinSongs = 5
    const val MaxSongs = 50
  }
}
