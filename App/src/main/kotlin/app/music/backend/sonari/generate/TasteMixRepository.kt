package app.music.backend.sonari.generate

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the built mixes in one place: several screens ask for them, and building them twice would
 * overwrite the playlists with different songs.
 */
@Singleton
class TasteMixRepository
@Inject
constructor(
  private val engine: LocalTasteEngine,
) {
  private val _mixes = MutableStateFlow<List<TasteMix>>(emptyList())
  val mixes: StateFlow<List<TasteMix>> = _mixes.asStateFlow()

  private val _loading = MutableStateFlow(false)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()

  private val mutex = Mutex()
  private var builtAt = 0L

  suspend fun refresh(force: Boolean = false) {
    mutex.withLock {
      val now = System.currentTimeMillis()
      val stillFresh = builtAt != 0L && now - builtAt < RefreshAfterMillis
      if (!force && stillFresh) return

      _loading.value = true
      val built = runCatching { engine.buildMixes() }.getOrDefault(emptyList())
      if (built.isNotEmpty() || _mixes.value.isEmpty()) _mixes.value = built
      _loading.value = false
      builtAt = System.currentTimeMillis()
    }
  }

  suspend fun keep(playlistId: String): Boolean = engine.keep(playlistId)

  /** Never offer these songs again, drop the playlist they came from and build fresh mixes. */
  suspend fun notInterested(playlistId: String, tracks: List<Pair<String, String>>) {
    if (tracks.isNotEmpty()) engine.exclude(tracks)
    _mixes.value = _mixes.value.filterNot { it.playlistId == playlistId }
    runCatching { engine.forget(playlistId) }
    refresh(force = true)
  }

  private companion object {
    const val RefreshAfterMillis = 30L * 60 * 1000
  }
}
