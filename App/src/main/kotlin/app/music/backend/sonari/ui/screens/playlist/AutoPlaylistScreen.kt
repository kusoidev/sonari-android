package app.music.backend.sonari.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import app.music.backend.sonari.LocalDownloadUtil
import app.music.backend.sonari.LocalPlayerAwareWindowInsets
import app.music.backend.sonari.LocalPlayerConnection
import app.music.backend.sonari.R
import app.music.backend.sonari.constants.HideExplicitKey
import app.music.backend.sonari.constants.DownloadedSongSortTypeKey
import app.music.backend.sonari.constants.DownloadedSongSortDescendingKey
import app.music.backend.sonari.constants.SongSortDescendingKey
import app.music.backend.sonari.constants.SongSortType
import app.music.backend.sonari.constants.SongSortTypeKey
import app.music.backend.sonari.constants.YtmSyncKey
import app.music.backend.sonari.db.entities.Song
import app.music.backend.sonari.extensions.toMediaItem
import app.music.backend.sonari.models.MediaMetadata
import app.music.backend.sonari.playback.DownloadUtil
import app.music.backend.sonari.playback.ExoDownloadService
import app.music.backend.sonari.playback.PlayerConnection
import app.music.backend.sonari.playback.queues.ListQueue
import app.music.backend.sonari.ui.component.DefaultDialog
import app.music.backend.sonari.ui.component.DraggableScrollbar
import app.music.backend.sonari.ui.component.EmptyPlaceholder
import app.music.backend.sonari.ui.component.IconButton
import app.music.backend.sonari.ui.component.LocalMenuState
import app.music.backend.sonari.ui.component.SongListItem
import app.music.backend.sonari.ui.component.SortHeader
import app.music.backend.sonari.ui.menu.AutoPlaylistMenu
import app.music.backend.sonari.ui.menu.SelectionSongMenu
import app.music.backend.sonari.ui.menu.SongMenu
import app.music.backend.sonari.ui.screens.library.PlaylistShuffleOverlay
import app.music.backend.sonari.ui.screens.library.rememberPlaylistShuffle
import app.music.backend.sonari.ui.utils.backToMain
import app.music.backend.sonari.utils.listItemShape
import app.music.backend.sonari.utils.makeTimeString
import app.music.backend.sonari.utils.rememberEnumPreference
import app.music.backend.sonari.utils.rememberPreference
import app.music.backend.sonari.viewmodels.AutoPlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AutoPlaylistScreen(
  navController: NavController,
  scrollBehavior: TopAppBarScrollBehavior,
  viewModel: AutoPlaylistViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val downloadUtil = LocalDownloadUtil.current
  val menuState = LocalMenuState.current
  val haptic = LocalHapticFeedback.current
  val focusManager = LocalFocusManager.current
  val playerConnection = LocalPlayerConnection.current
  val isPlaying by (playerConnection?.isEffectivelyPlaying ?: flowOf(false)).collectAsState(false)
  val mediaMetadata by
    (playerConnection?.mediaMetadata ?: flowOf<MediaMetadata?>(null)).collectAsState(null)
  val playlist =
    when (viewModel.playlist) {
      "liked" -> stringResource(R.string.liked)
      "uploaded" -> stringResource(R.string.uploaded_playlist)
      "exported" -> stringResource(R.string.action_exported)
      "all" -> stringResource(R.string.filter_all)
      else -> stringResource(R.string.offline)
    }

  val songs by viewModel.likedSongs.collectAsState(null)

  val mutableSongs = remember { mutableStateListOf<Song>() }

  var isSearching by remember { mutableStateOf(false) }
  var query by remember { mutableStateOf(TextFieldValue()) }
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(isSearching) {
    if (isSearching) {
      focusRequester.requestFocus()
    }
  }

  val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

  val (ytmSync) = rememberPreference(YtmSyncKey, true)

  val likeLength = remember(songs) { songs?.fastSumBy { it.song.duration } ?: 0 }

  val playlistId = viewModel.playlist
  val playlistType =
    when (playlistId) {
      "liked" -> PlaylistType.LIKE
      "downloaded" -> PlaylistType.DOWNLOAD
      "uploaded" -> PlaylistType.UPLOADED
      "exported" -> PlaylistType.EXPORTED
      else -> PlaylistType.OTHER
    }

  var inSelectMode by rememberSaveable { mutableStateOf(false) }
  val selection =
    rememberSaveable(
      saver =
        listSaver<MutableList<String>, String>(
          save = { it.toList() },
          restore = { it.toMutableStateList() }
        )
    ) {
      mutableStateListOf()
    }
  val onExitSelectionMode = {
    inSelectMode = false
    selection.clear()
  }

  if (isSearching) {
    BackHandler {
      isSearching = false
      query = TextFieldValue()
    }
  } else if (inSelectMode) {
    BackHandler(onBack = onExitSelectionMode)
  }

  val (sortType, onSortTypeChange) =
    rememberEnumPreference(
      if (playlistId == "downloaded") DownloadedSongSortTypeKey else SongSortTypeKey,
      SongSortType.CREATE_DATE,
    )
  val (sortDescending, onSortDescendingChange) = rememberPreference(
    if (playlistId == "downloaded") DownloadedSongSortDescendingKey else SongSortDescendingKey,
    true,
  )

  var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }

  LaunchedEffect(Unit) {
    if (ytmSync) {
      withContext(Dispatchers.IO) {
        if (playlistType == PlaylistType.LIKE) {
          viewModel.syncLikedSongs()
        }
        if (playlistType == PlaylistType.UPLOADED) {
          viewModel.syncUploadedSongs()
        }
      }
    }
  }

  LaunchedEffect(songs) {
    mutableSongs.apply {
      clear()
      songs?.let { addAll(it) }
    }
    if (songs.isNullOrEmpty()) {
      downloadState = Download.STATE_STOPPED
      return@LaunchedEffect
    }
    downloadUtil.downloads.collect { downloads ->
      downloadState = DownloadUtil.computeDownloadState(songs?.map { it.song.id }, downloads)
    }
  }

  var showRemoveDownloadDialog by remember { mutableStateOf(false) }

  if (showRemoveDownloadDialog) {
    DefaultDialog(
      onDismiss = { showRemoveDownloadDialog = false },
      content = {
        Text(
          text = stringResource(R.string.remove_download_playlist_confirm, playlist),
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(horizontal = 18.dp),
        )
      },
      buttons = {
        TextButton(
          onClick = { showRemoveDownloadDialog = false },
        ) {
          Text(text = stringResource(android.R.string.cancel))
        }

        TextButton(
          onClick = {
            showRemoveDownloadDialog = false
            songs!!.forEach { song ->
              DownloadService.sendRemoveDownload(
                context,
                ExoDownloadService::class.java,
                song.song.id,
                false,
              )
            }
          },
        ) {
          Text(text = stringResource(android.R.string.ok))
        }
      },
    )
  }

  val filteredSongs =
    remember(songs, query) {
      if (query.text.isEmpty()) songs ?: emptyList()
      else
        songs?.filter { song ->
          song.song.title.contains(query.text, true) ||
            song.artists.any { it.name.contains(query.text, true) }
        } ?: emptyList()
    }

  LaunchedEffect(filteredSongs) {
    selection.fastForEachReversed { songId ->
      if (filteredSongs.find { it.id == songId } == null) {
        selection.remove(songId)
      }
    }
  }

  val state = rememberLazyListState()

  // Shuffling a playlist scrolls to the songs, spins the wheel there, then plays what it landed on.
  val (shuffleRoulette, shufflePlaylist) =
    rememberPlaylistShuffle(songs.orEmpty(), state) { items ->
      playerConnection?.playQueue(ListQueue(title = playlist, items = items))
    }
  var headerHeightPx by remember { mutableIntStateOf(0) }

  val isRefreshing by viewModel.isRefreshing.collectAsState()
  val pullRefreshState = rememberPullToRefreshState()
  val canRefresh = playlistType == PlaylistType.LIKE || playlistType == PlaylistType.UPLOADED

  Box(
    modifier =
      Modifier.fillMaxSize()
        .then(
          if (canRefresh) {
            Modifier.pullToRefresh(
              state = pullRefreshState,
              isRefreshing = isRefreshing,
              onRefresh = viewModel::refresh
            )
          } else {
            Modifier
          }
        ),
  ) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val headerHeight = with(density) { headerHeightPx.toDp() }
    if (songs?.isNotEmpty() == true) {
      // The blurred cover is the whole page's backdrop, not just the header's: it runs behind the
      // songs and melts into the background at the bottom, so it never ends on a hard line.
      app.music.backend.sonari.ui.component.OnlineBlur(
        thumbnailUrl = songs!!.first().thumbnailUrl,
        modifier = Modifier.fillMaxWidth().height(headerHeight + 260.dp)
      )
    }

    LazyColumn(
      state = state,
      modifier = Modifier.fillMaxSize(),
      // The playlist itself turns during a shuffle spin, so it may not be dragged while it does.
      userScrollEnabled = !shuffleRoulette.running,
      contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
      if (songs != null) {
        if (songs!!.isEmpty()) {
          item(key = "empty_placeholder") {
            EmptyPlaceholder(
              icon = R.drawable.music_note,
              text = stringResource(R.string.playlist_is_empty),
            )
          }
        } else {
          if (!isSearching) {
            item(key = "playlist_header") {
              AutoPlaylistHeader(
                name = playlist,
                songs = songs!!,
                onShuffle = shufflePlaylist,
                likeLength = likeLength,
                downloadState = downloadState,
                onShowRemoveDownloadDialog = { showRemoveDownloadDialog = true },
                menuState = menuState,
                playerConnection = playerConnection,
                modifier = Modifier.animateItem(),
                onHeightChanged = { headerHeightPx = it },
              )
            }
          }

          item(key = "songs_header") {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(start = 16.dp),
            ) {
              SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { sortType ->
                  when (sortType) {
                    SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                    SongSortType.NAME -> R.string.sort_by_name
                    SongSortType.ARTIST -> R.string.sort_by_artist
                    SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                  }
                },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        if (filteredSongs.isNotEmpty()) {
          itemsIndexed(
            items = filteredSongs,
            key = { _, song -> song.id },
          ) { index, song ->
            val onCheckedChange: (Boolean) -> Unit = {
              if (it) {
                selection.add(song.id)
              } else {
                selection.remove(song.id)
              }
            }

            SongListItem(
              song = song,
              isActive =
                song.song.id == mediaMetadata?.id ||
                  (shuffleRoulette.pickedSongId == song.song.id &&
                    (mediaMetadata == null ||
                      mediaMetadata?.id == shuffleRoulette.pickedSongId)),
              isPlaying = isPlaying,
              showInLibraryIcon = true,
              shape = listItemShape(index, filteredSongs.size),
              trailingContent = {
                if (inSelectMode) {
                  Checkbox(checked = song.id in selection, onCheckedChange = onCheckedChange)
                } else {
                  IconButton(
                    onClick = {
                      menuState.show {
                        SongMenu(
                          originalSong = song,
                          navController = navController,
                          onDismiss = menuState::dismiss,
                        )
                      }
                    },
                  ) {
                    Icon(
                      painter = painterResource(R.drawable.more_vert),
                      contentDescription = null,
                    )
                  }
                }
              },
              modifier =
                Modifier.fillMaxWidth()
                  .combinedClickable(
                    onClick = {
                      if (inSelectMode) {
                        onCheckedChange(song.id !in selection)
                      } else if (song.song.id == mediaMetadata?.id) {
                        playerConnection?.togglePlayPause()
                      } else {
                        val playSongs = if (isSearching) filteredSongs else (songs ?: emptyList())
                        playerConnection?.playQueue(
                          ListQueue(
                            title = playlist,
                            items = playSongs.map { it.toMediaItem() },
                            startIndex = playSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                          ),
                        )
                      }
                    },
                    onLongClick = {
                      if (!inSelectMode) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        inSelectMode = true
                        onCheckedChange(true)
                      }
                    },
                  )
                  .animateItem()
            )
          }
        }
      }
      item { Spacer(Modifier.height(50.dp)) }
    }

    DraggableScrollbar(
      modifier =
        Modifier.padding(
            LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues()
          )
          .align(Alignment.CenterEnd),
      scrollState = state,
      headerItems = 2
    )

    if (canRefresh) {
      Indicator(
        isRefreshing = isRefreshing,
        state = pullRefreshState,
        modifier =
          Modifier.align(Alignment.TopCenter)
            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
      )
    }

    TopAppBar(
      title = {
        when {
          inSelectMode -> {
            Text(
              text = pluralStringResource(R.plurals.n_song, selection.size, selection.size),
              style = MaterialTheme.typography.titleLarge
            )
          }
          isSearching -> {
            TextField(
              value = query,
              onValueChange = { query = it },
              placeholder = {
                Text(
                  text = stringResource(R.string.search),
                  style = MaterialTheme.typography.titleLarge
                )
              },
              singleLine = true,
              textStyle = MaterialTheme.typography.titleLarge,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
              colors =
                TextFieldDefaults.colors(
                  focusedContainerColor = Color.Transparent,
                  unfocusedContainerColor = Color.Transparent,
                  focusedIndicatorColor = Color.Transparent,
                  unfocusedIndicatorColor = Color.Transparent,
                  disabledIndicatorColor = Color.Transparent,
                ),
              modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
          }
          else -> {
            // Title is removed from the top bar
          }
        }
      },
      navigationIcon = {
        IconButton(
          onClick = {
            when {
              isSearching -> {
                isSearching = false
                query = TextFieldValue()
                focusManager.clearFocus()
              }
              inSelectMode -> {
                onExitSelectionMode()
              }
              else -> {
                navController.navigateUp()
              }
            }
          },
          onLongClick = {
            if (!isSearching && !inSelectMode) {
              navController.backToMain()
            }
          }
        ) {
          Icon(
            painter =
              painterResource(if (inSelectMode) R.drawable.close else R.drawable.arrow_back),
            contentDescription = null
          )
        }
      },
      actions = {
        if (inSelectMode) {
          Checkbox(
            checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
            onCheckedChange = {
              if (selection.size == filteredSongs.size) {
                selection.clear()
              } else {
                selection.clear()
                selection.addAll(filteredSongs.map { it.id })
              }
            }
          )
          IconButton(
            enabled = selection.isNotEmpty(),
            onClick = {
              menuState.show {
                SelectionSongMenu(
                  songSelection = filteredSongs.filter { it.id in selection },
                  onDismiss = menuState::dismiss,
                  clearAction = onExitSelectionMode,
                )
              }
            },
          ) {
            Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
          }
        } else if (!isSearching) {
          IconButton(onClick = { isSearching = true }) {
            Icon(painter = painterResource(R.drawable.search), contentDescription = null)
          }
        }
      },
      colors =
        if (inSelectMode) {
          androidx.compose.material3.TopAppBarDefaults.topAppBarColors()
        } else {
          // Search included: the bar has to stay transparent so the blurred
          // backdrop of the playlist keeps showing through instead of a black band.
          androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
          )
        }
    )

    PlaylistShuffleOverlay(shuffleRoulette)
  }
}

@Composable
private fun AutoPlaylistHeader(
  name: String,
  songs: List<Song>,
  onShuffle: () -> Unit,
  likeLength: Int,
  downloadState: Int,
  onShowRemoveDownloadDialog: () -> Unit,
  menuState: app.music.backend.sonari.ui.component.MenuState,
  playerConnection: PlayerConnection?,
  onHeightChanged: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val downloadUtil = LocalDownloadUtil.current

  Box(modifier = modifier.fillMaxWidth().onSizeChanged { onHeightChanged(it.height) }) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.height(50.dp))

      Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
        contentAlignment = Alignment.Center
      ) {
        AsyncImage(
          model = songs[0].thumbnailUrl,
          contentDescription = null,
          modifier =
            Modifier.then(
                if (LocalConfiguration.current.screenWidthDp > 600) Modifier.size(300.dp)
                else Modifier.fillMaxWidth().aspectRatio(1f)
              )
              .clip(RoundedCornerShape(8.dp)),
          contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
      }

      Spacer(Modifier.height(32.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 32.dp)
      ) {
        Text(
          text = name,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }

      Spacer(Modifier.height(24.dp))

      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
      ) {
        androidx.compose.material3.Button(
          onClick = onShuffle,
          shape = androidx.compose.material3.ButtonDefaults.shape,
          colors =
            androidx.compose.material3.ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant,
              contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
          modifier = Modifier.weight(1f)
        ) {
          Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              painter = painterResource(R.drawable.shuffle),
              contentDescription = null,
              modifier = Modifier.size(20.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.shuffle),
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
              overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
          }
        }

        androidx.compose.material3.Button(
          onClick = {
            playerConnection?.playQueue(
              ListQueue(
                title = name,
                items = songs.map { it.toMediaItem() },
              ),
            )
          },
          shape = androidx.compose.material3.ButtonDefaults.shape,
          colors =
            androidx.compose.material3.ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary
            ),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
          modifier = Modifier.weight(1f)
        ) {
          Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              painter = painterResource(R.drawable.play),
              contentDescription = null,
              modifier = Modifier.size(20.dp),
              tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.play), style = MaterialTheme.typography.labelLarge)
          }
        }

        Surface(
          onClick = {
            menuState.show {
              AutoPlaylistMenu(
                downloadState = downloadState,
                onQueue = { playerConnection?.addToQueue(songs.map { it.toMediaItem() }) },
                onDownload = {
                  val downloads = downloadUtil.downloads.value
                  val missingSongs =
                    songs.filter { song -> !DownloadUtil.hasDownload(song.song.id, downloads) }
                  if (missingSongs.isNotEmpty()) {
                    missingSongs.forEach { song ->
                      val downloadRequest =
                        DownloadRequest.Builder(song.song.id, song.song.id.toUri())
                          .setCustomCacheKey(song.song.id)
                          .setData(song.song.title.toByteArray())
                          .build()
                      DownloadService.sendAddDownload(
                        context,
                        ExoDownloadService::class.java,
                        downloadRequest,
                        false,
                      )
                    }
                  } else if (downloadState == Download.STATE_COMPLETED) {
                    onShowRemoveDownloadDialog()
                  } else {
                    songs.forEach { song ->
                      DownloadService.sendRemoveDownload(
                        context,
                        ExoDownloadService::class.java,
                        song.song.id,
                        false,
                      )
                    }
                  }
                },
                onDismiss = { menuState.dismiss() }
              )
            }
          },
          shape = androidx.compose.foundation.shape.CircleShape,
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.size(48.dp)
        ) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
              painter = painterResource(R.drawable.more_vert),
              contentDescription = null,
              modifier = Modifier.size(20.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Spacer(Modifier.height(16.dp))

      Text(
        text =
          buildString {
            append(pluralStringResource(R.plurals.n_song, songs.size, songs.size))
            if (likeLength > 0) {
              append(" • ")
              append(makeTimeString(likeLength * 1000L))
            }
          },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(Modifier.height(16.dp))
    }
  }
}

enum class PlaylistType {
  LIKE,
  DOWNLOAD,
  UPLOADED,
  EXPORTED,
  OTHER
}
