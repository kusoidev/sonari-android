package app.music.backend.sonari.ui.screens.library

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.music.backend.sonari.LocalPlayerAwareWindowInsets
import app.music.backend.sonari.LocalPlayerConnection
import app.music.backend.sonari.R
import app.music.backend.sonari.constants.CONTENT_TYPE_SONG
import app.music.backend.sonari.constants.HideExplicitKey
import app.music.backend.sonari.constants.SongFilter
import app.music.backend.sonari.constants.SongFilterKey
import app.music.backend.sonari.constants.SongSortDescendingKey
import app.music.backend.sonari.constants.SongSortType
import app.music.backend.sonari.constants.SongSortTypeKey
import app.music.backend.sonari.constants.YtmSyncKey
import app.music.backend.sonari.extensions.toMediaItem
import app.music.backend.sonari.playback.queues.ListQueue
import app.music.backend.sonari.ui.component.ChipsRow
import app.music.backend.sonari.ui.component.LocalMenuState
import app.music.backend.sonari.ui.component.SongListItem
import app.music.backend.sonari.ui.component.SortHeader
import app.music.backend.sonari.ui.menu.SelectionSongMenu
import app.music.backend.sonari.ui.menu.SongMenu
import app.music.backend.sonari.utils.listItemShape
import app.music.backend.sonari.utils.rememberEnumPreference
import app.music.backend.sonari.utils.rememberPreference
import app.music.backend.sonari.viewmodels.LibrarySongsViewModel
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibrarySongsScreen(
  navController: NavController,
  onDeselect: () -> Unit,
  viewModel: LibrarySongsViewModel = hiltViewModel(),
  roulette: ShuffleRouletteState? = null,
) {
  val context = LocalContext.current
  val menuState = LocalMenuState.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val haptic = LocalHapticFeedback.current
  val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
  val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

  val (sortType, onSortTypeChange) =
    rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
  val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

  val (ytmSync) = rememberPreference(YtmSyncKey, true)
  val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

  val songs by viewModel.allSongs.collectAsState()
  // The whole library, whatever the filter chips are showing: shuffling plays this, not the subset.
  val librarySongs by viewModel.librarySongs.collectAsState()

  var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIBRARY)

  var inSelectMode by rememberSaveable { mutableStateOf(false) }
  val selection = remember { mutableStateListOf<String>() }
  val onExitSelectionMode = {
    inSelectMode = false
    selection.clear()
  }

  LaunchedEffect(Unit) {
    if (ytmSync) {
      when (filter) {
        SongFilter.LIKED -> viewModel.syncLikedSongs()
        SongFilter.LIBRARY -> viewModel.syncLibrarySongs()
        SongFilter.UPLOADED -> viewModel.syncUploadedSongs()
        else -> return@LaunchedEffect
      }
    }
  }

  val lazyListState = rememberLazyListState()
  // Where the roulette's needle sits and where the row it is aiming at is drawn — the two
  // measurements the wheel needs to stop on that row.
  val needleProbe = remember { ShuffleNeedleProbe() }
  // While the wheel spins, the rows smear with speed. Quantised so the blur is only rebuilt a
  // handful of times over the spin instead of on every frame.
  val motionBlur = 8.dp * (((roulette?.speed ?: 0f) * 4f).roundToInt() / 4f)

  val backStackEntry by navController.currentBackStackEntryAsState()
  val scrollToTop =
    backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

  LaunchedEffect(scrollToTop?.value) {
    if (scrollToTop?.value == true) {
      lazyListState.animateScrollToItem(0)
      backStackEntry?.savedStateHandle?.set("scrollToTop", false)
    }
  }

  val filteredSongs =
    if (hideExplicit) {
      songs.filter { !it.song.explicit }
    } else {
      songs
    }

  // The shuffle FAB lives next to the library's add button in LibraryScreen; it bumps the
  // roulette tick and this screen runs the wheel over the songs on the list, then queues the whole
  // library shuffled with the winner first.
  val currentSongs by rememberUpdatedState(filteredSongs)
  val currentLibrary by rememberUpdatedState(librarySongs)
  LaunchedEffect(roulette?.tick) {
    val state = roulette ?: return@LaunchedEffect
    if (state.tick == 0 || currentSongs.isEmpty()) return@LaunchedEffect
    val winnerRow = Random.nextInt(currentSongs.size)
    val winner = currentSongs[winnerRow]
    state.run(
      listState = lazyListState,
      probe = needleProbe,
      rowCount = currentSongs.size,
      winnerRow = winnerRow,
      winnerId = winner.id,
    )
    val queue =
      (currentLibrary.ifEmpty { currentSongs })
        .shuffled()
        .filter { it.id != winner.id }
        .toMutableList()
        .apply { add(0, winner) }
    playerConnection.playQueue(
      ListQueue(
        title = context.getString(R.string.queue_all_songs),
        items = queue.map { it.toMediaItem() },
      ),
    )
  }

  val listInsets = LocalPlayerAwareWindowInsets.current.asPaddingValues()

  Box(
    modifier = Modifier.fillMaxSize(),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // The chips and the sort header sit above the list rather than inside it, so a spinning
      // shuffle only ever moves the songs and the top of the screen stays still.
      Column(modifier = Modifier.padding(top = listInsets.calculateTopPadding())) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Spacer(Modifier.width(12.dp))
          FilterChip(
            label = { Text(stringResource(R.string.songs)) },
            selected = true,
            colors =
              FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surface
              ),
            onClick = onDeselect,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
              Icon(painter = painterResource(R.drawable.close), contentDescription = "")
            },
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
          )
          ChipsRow(
            chips =
              listOf(
                SongFilter.LIKED to stringResource(R.string.filter_liked),
                SongFilter.LIBRARY to stringResource(R.string.filter_library),
                SongFilter.UPLOADED to stringResource(R.string.filter_uploaded),
                SongFilter.DOWNLOADED to stringResource(R.string.filter_downloaded),
                SongFilter.EXPORTED to stringResource(R.string.action_exported),
              ),
            currentValue = filter,
            onValueUpdate = { filter = it },
            modifier = Modifier.weight(1f),
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 16.dp),
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
          )

          Spacer(Modifier.weight(1f))

          Text(
            text = pluralStringResource(R.plurals.n_song, filteredSongs.size, filteredSongs.size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }

      Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
          state = lazyListState,
          contentPadding = PaddingValues(bottom = listInsets.calculateBottomPadding()),
          modifier =
            Modifier.fillMaxSize()
              .then(if (motionBlur > 0.4.dp) Modifier.blur(motionBlur) else Modifier),
        ) {
          itemsIndexed(
            items = filteredSongs,
            key = { _, item -> item.song.id },
            contentType = { _, _ -> CONTENT_TYPE_SONG },
          ) { index, song ->
            val onCheckedChange: (Boolean) -> Unit = {
              if (it) {
                selection.add(song.id)
              } else {
                selection.remove(song.id)
              }
            }
            val isWinner = roulette != null && roulette.winnerSongId == song.id
            // One row at a time reports its position: the one the wheel is aiming at.
            val isTarget = roulette != null && roulette.targetSongId == song.id
            val winnerPulse by
              animateFloatAsState(
                targetValue = if (isWinner) 1.045f else 1f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
                label = "rouletteWinnerPulse",
              )
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .graphicsLayer {
                    scaleX = winnerPulse
                    scaleY = winnerPulse
                  }
                  .then(
                    if (isTarget) {
                      Modifier.onGloballyPositioned { coords ->
                        needleProbe.rowTopY = coords.positionInRoot().y
                        needleProbe.rowHeight = coords.size.height.toFloat()
                      }
                    } else {
                      Modifier
                    }
                  )
            ) {
              SongListItem(
                song = song,
                showInLibraryIcon = true,
                isActive = song.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                showLikedIcon = true,
                showDownloadIcon = filter != SongFilter.DOWNLOADED,
                showSize = filter == SongFilter.DOWNLOADED,
                shape = listItemShape(index, filteredSongs.size),
                trailingContent = {
                  if (inSelectMode) {
                    Checkbox(
                      checked = selection.contains(song.id),
                      onCheckedChange = onCheckedChange
                    )
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
                          onCheckedChange(!selection.contains(song.id))
                        } else if (song.id == mediaMetadata?.id) {
                          playerConnection.togglePlayPause()
                        } else {
                          playerConnection.playQueue(
                            ListQueue(
                              title = context.getString(R.string.queue_all_songs),
                              items = filteredSongs.map { it.toMediaItem() },
                              startIndex = index,
                            ),
                          )
                        }
                      },
                      onLongClick = {
                        if (!inSelectMode) {
                          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                          inSelectMode = true
                          onCheckedChange(true)
                        } else {
                          menuState.show {
                            SongMenu(
                              originalSong = song,
                              navController = navController,
                              onDismiss = menuState::dismiss,
                            )
                          }
                        }
                      }
                    )
                    .animateItem(),
              )
            }
          }
        }

        roulette?.let { state -> ShuffleFrostOverlay(state, Modifier.matchParentSize()) }
        roulette?.let { state ->
          ShuffleNeedleOverlay(state, needleProbe, Modifier.align(Alignment.Center))
        }
      }
    }

    androidx.compose.animation.AnimatedVisibility(
      visible = inSelectMode,
      enter = androidx.compose.animation.fadeIn(),
      exit = androidx.compose.animation.fadeOut(),
      modifier = Modifier.align(Alignment.TopCenter)
    ) {
      androidx.compose.material3.TopAppBar(
        title = {
          Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
        },
        navigationIcon = {
          IconButton(onClick = onExitSelectionMode) {
            Icon(painter = painterResource(R.drawable.close), contentDescription = null)
          }
        },
        actions = {
          Checkbox(
            checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
            onCheckedChange = {
              if (selection.size == filteredSongs.size) {
                selection.clear()
              } else {
                selection.clear()
                selection.addAll(filteredSongs.map { it.song.id })
              }
            }
          )
          IconButton(
            enabled = selection.isNotEmpty(),
            onClick = {
              menuState.show {
                SelectionSongMenu(
                  songSelection = selection.mapNotNull { id -> songs.find { it.song.id == id } },
                  onDismiss = menuState::dismiss,
                  clearAction = onExitSelectionMode
                )
              }
            }
          ) {
            Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
          ),
        windowInsets = WindowInsets(0.dp)
      )
    }
  }
}
