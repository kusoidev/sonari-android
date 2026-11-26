package app.music.backend.sonari.generate

import android.content.Context
import app.music.backend.sonari.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Keeps the built mixes in one place: several screens ask for them, and building them twice would
 * overwrite the playlists with different songs.
 *
 * A build belongs to the app, not to the screen that asked for it: closing the sheet or leaving the
 * Library does not throw the work away and start over on the next visit. What the last build left is
 * written down beside the playlists it saved anyway, and read back at launch, so the row stands there
 * before the first build of the session has finished.
 */
@Singleton
class TasteMixRepository
@Inject
constructor(
  private val engine: LocalTasteEngine,
  @ApplicationScope private val scope: CoroutineScope,
  @ApplicationContext private val context: Context,
) {
  private val _mixes = MutableStateFlow<List<TasteMix>>(emptyList())
  val mixes: StateFlow<List<TasteMix>> = _mixes.asStateFlow()

  private val _loading = MutableStateFlow(false)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()

  private val mutex = Mutex()
  private var builtAt = 0L
  private var attemptedAt = 0L
  private var readSaved = false
  private var building: Deferred<Unit>? = null

  /** What the app runs as it opens: the mixes the last build left, then a build if they are old. */
  suspend fun warmUp() {
    readSavedMixes()
    refresh()
  }

  suspend fun refresh(force: Boolean = false) {
    readSavedMixes()
    if (!force && isFresh()) return

    // A build under way is waited for, and a forced one runs again afterwards: the songs it had
    // read may be the ones just banned, so its own result is not the one that was asked for.
    val running = synchronized(this) { building }
    if (running != null && running.isActive) {
      try {
        running.await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // A failed build reports itself; the mixes it left are the last ones that worked.
      }
      if (!force) return
    }

    val job = synchronized(this) { scope.async { build(force) }.also { building = it } }
    job.await()
  }

  suspend fun keep(playlistId: String): Boolean = engine.keep(playlistId)

  /** Never offer these songs again, drop the playlist they came from and build fresh mixes. */
  suspend fun notInterested(playlistId: String, tracks: List<Pair<String, String>>) {
    if (tracks.isNotEmpty()) runCatching { engine.exclude(tracks, excludeArtists = true) }
    _mixes.value = _mixes.value.filterNot { it.playlistId == playlistId }
    runCatching { engine.forget(playlistId) }
    refresh(force = true)
  }

  /** Never offer these songs, nor anything else by their artists, then build fresh mixes. */
  suspend fun neverRecommend(tracks: List<Pair<String, String>>) {
    if (tracks.isEmpty()) return
    runCatching { engine.exclude(tracks, excludeArtists = true) }
    refresh(force = true)
  }

  /** Grows one mix with more of what it already holds, and reflects that on screen straight away. */
  suspend fun moreLikeThis(playlistId: String) {
    val added = runCatching { engine.moreLikeThis(playlistId) }.getOrDefault(emptyList())
    if (added.isEmpty()) return

    _mixes.value =
      _mixes.value.map { mix ->
        if (mix.playlistId != playlistId) mix
        else mix.copy(suggestions = (mix.suggestions + added).distinctBy { it.key })
      }
  }

  /**
   * One build at a time: two screens asking at once would overwrite the same playlists with
   * different songs. A forced build runs even while the mixes are still fresh, since something they
   * were built from has changed.
   */
  private suspend fun build(force: Boolean) {
    mutex.withLock {
      if (!force && isFresh()) return@withLock

      _loading.value = true
      attemptedAt = System.currentTimeMillis()
      try {
        // What is already on the phone stands first, before a single request is made: the row is
        // there at once, and the mixes found over the network take over as they arrive.
        if (_mixes.value.isEmpty()) {
          val local =
            try {
              engine.buildLocalMixes()
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              Timber.e(e, "Building the For you mixes from the library failed")
              emptyList()
            }
          if (local.isNotEmpty()) {
            _mixes.value = local
            builtAt = System.currentTimeMillis()
            saveMixes(local)
          }
        }

        val built =
          try {
            engine.buildMixes()
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            Timber.e(e, "Building the For you mixes failed")
            emptyList()
          }

        if (built.isNotEmpty()) {
          _mixes.value = built
          builtAt = System.currentTimeMillis()
          saveMixes(built)
        }
      } finally {
        _loading.value = false
      }
    }
  }

  /**
   * Fresh until [RefreshAfterMillis] has passed since the last build. A build that found nothing to
   * build from is tried again after a minute, since a song played meanwhile may be all it needs.
   */
  private fun isFresh(): Boolean {
    val now = System.currentTimeMillis()
    return if (builtAt != 0L) {
      now - builtAt < RefreshAfterMillis
    } else {
      attemptedAt != 0L && now - attemptedAt < EmptyRetryMillis
    }
  }

  private suspend fun saveMixes(mixes: List<TasteMix>) =
    withContext(Dispatchers.IO) {
      try {
        val saved = JSONArray()
        mixes.forEach { mix ->
          saved.put(
            JSONObject().apply {
              put("playlistId", mix.playlistId)
              put("name", mix.playlistName)
              put("count", mix.songCount)
              put("cover", mix.artworkUrl ?: "")
            }
          )
        }
        cacheFile.writeText(
          JSONObject().apply {
            put("builtAt", builtAt)
            put("mixes", saved)
          }.toString()
        )
      } catch (e: Exception) {
        Timber.e(e, "Saving the For you mixes failed")
      }
    }

  /**
   * What the last build left, read once. Only the cards' own lines are kept: the songs sit in the
   * playlists the build saved, so they are not read back here.
   */
  private suspend fun readSavedMixes() {
    if (readSaved) return
    readSaved = true

    val saved =
      withContext(Dispatchers.IO) {
        try {
          if (!cacheFile.exists()) return@withContext null
          val data = JSONObject(cacheFile.readText())
          val savedMixes = mutableListOf<TasteMix>()
          val array = data.optJSONArray("mixes") ?: JSONArray()
          for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val playlistId = item.optString("playlistId").takeIf { it.isNotBlank() } ?: continue
            savedMixes +=
              TasteMix(
                playlistId = playlistId,
                playlistName = item.optString("name"),
                suggestions = emptyList(),
                builtSongCount = item.optInt("count"),
                builtCoverUrl = item.optString("cover").takeIf { it.isNotBlank() },
              )
          }
          savedMixes to data.optLong("builtAt")
        } catch (e: Exception) {
          Timber.e(e, "Reading the For you mixes failed")
          null
        }
      } ?: return

    if (_mixes.value.isEmpty() && saved.first.isNotEmpty()) _mixes.value = saved.first
    if (builtAt == 0L) builtAt = saved.second
  }

  private val cacheFile: File
    get() = File(context.filesDir, "for_you_mixes.json")

  private companion object {
    const val RefreshAfterMillis = 30L * 60 * 1000

    /** A build that left nothing is tried again this soon, so a play made meanwhile is picked up. */
    const val EmptyRetryMillis = 60L * 1000
  }
}
