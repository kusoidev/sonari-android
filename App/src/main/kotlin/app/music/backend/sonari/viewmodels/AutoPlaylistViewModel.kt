package app.music.backend.sonari.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import app.music.backend.sonari.constants.ExportedSongIdsKey
import app.music.backend.sonari.constants.DownloadedSongSortTypeKey
import app.music.backend.sonari.constants.DownloadedSongSortDescendingKey
import app.music.backend.sonari.constants.HideExplicitKey
import app.music.backend.sonari.constants.HideVideoSongsKey
import app.music.backend.sonari.constants.SongSortDescendingKey
import app.music.backend.sonari.constants.SongSortType
import app.music.backend.sonari.constants.SongSortTypeKey
import app.music.backend.sonari.db.MusicDatabase
import app.music.backend.sonari.extensions.filterExplicit
import app.music.backend.sonari.extensions.filterVideoSongs
import app.music.backend.sonari.extensions.toEnum
import app.music.backend.sonari.playback.DownloadUtil
import app.music.backend.sonari.utils.SyncUtils
import app.music.backend.sonari.utils.dataStore
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel
@Inject
constructor(
  @ApplicationContext context: Context,
  private val database: MusicDatabase,
  savedStateHandle: SavedStateHandle,
  private val syncUtils: SyncUtils,
  private val downloadUtil: DownloadUtil,
) : ViewModel() {
  val playlist = savedStateHandle.get<String>("playlist")!!
  private val sortTypeKey = if (playlist == "downloaded") DownloadedSongSortTypeKey else SongSortTypeKey
  private val sortDescendingKey =
    if (playlist == "downloaded") DownloadedSongSortDescendingKey else SongSortDescendingKey

  private val _isRefreshing = MutableStateFlow(false)
  val isRefreshing = _isRefreshing.asStateFlow()

  init {
    // Opening the library's "All" or "Downloaded" list re-checks what is really on disk, so a
    // finished download can never be missing from it.
    if (playlist == "all" || playlist == "downloaded") {
      downloadUtil.reconcileNow()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  val likedSongs =
    context.dataStore.data
      .map {
        Triple(
          Triple(
            (try {
                it[sortTypeKey]
              } catch (e: Exception) {
                null
              })
              .toEnum(SongSortType.CREATE_DATE) to
              ((try {
                it[sortDescendingKey]
              } catch (e: Exception) {
                null
              }) ?: true),
            (try {
              it[HideExplicitKey]
            } catch (e: Exception) {
              null
            }) ?: false,
            (try {
              it[HideVideoSongsKey]
            } catch (e: Exception) {
              null
            }) ?: false
          ),
          (try {
            it[ExportedSongIdsKey]
          } catch (e: Exception) {
            null
          }) ?: "",
          Unit
        )
      }
      .distinctUntilChanged()
      .flatMapLatest { (triple, exportedSongIds, _) ->
        val (sortDesc, hideExplicit, hideVideoSongs) = triple
        val (sortType, descending) = sortDesc
        when (playlist) {
          "liked" ->
            database.likedSongs(sortType, descending).map {
              it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs)
            }
          "downloaded" ->
            // "Downloaded" is a list of what is on this device, so it never hides video songs:
            // a track you played from the VIDEOS tab and downloaded has to be here.
            database.downloadedSongs(sortType, descending).map {
              it.filterExplicit(hideExplicit)
            }
          "uploaded" ->
            database.uploadedSongs(sortType, descending).map {
              it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs)
            }
          "all" ->
            kotlinx.coroutines.flow
              .combine(
                database.allSongs(sortType, descending),
                downloadUtil.downloads,
              ) { songs, downloads ->
                // The download index is the source of truth for what is actually on
                // disk. Merge it in so a song still shows under "All" even if its
                // isDownloaded flag was not persisted (e.g. older downloads or an
                // interrupted metadata write).
                val downloadedIds =
                  downloads.keys.filter { DownloadUtil.hasDownload(it, downloads) }
                val knownIds = songs.mapTo(HashSet()) { it.id }
                val missingIds = downloadedIds.filterNot { it in knownIds }
                if (missingIds.isEmpty()) {
                  songs
                } else {
                  (database.getSongsByIds(missingIds) + songs).distinctBy { it.id }
                }
              }
              // "All" is the whole library, videos included. Filtering `isVideo` here used to hide
              // everything you played from the search VIDEOS tab, which read as "my download never
              // showed up" even though the row and the file were both there.
              .map { it.filterExplicit(hideExplicit) }
          "exported" -> {
            val ids = exportedSongIds.split(",").filter { it.isNotBlank() }
            database.getSongsByIdsFlow(ids).map {
              it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs)
            }
          }
          else -> kotlinx.coroutines.flow.flowOf(emptyList())
        }
      }
      .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

  fun syncLikedSongs() {
    viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
  }

  fun syncUploadedSongs() {
    viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      _isRefreshing.value = true
      when (playlist) {
        "liked" -> syncUtils.syncLikedSongsSuspend()
        "uploaded" -> syncUtils.syncUploadedSongsSuspend()
      }
      _isRefreshing.value = false
    }
  }
}
