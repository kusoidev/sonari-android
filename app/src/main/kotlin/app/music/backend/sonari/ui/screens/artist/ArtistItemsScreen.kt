package app.music.backend.sonari.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import app.music.backend.sonari.innertube.models.AlbumItem
import app.music.backend.sonari.innertube.models.ArtistItem
import app.music.backend.sonari.innertube.models.PlaylistItem
import app.music.backend.sonari.innertube.models.SongItem
import app.music.backend.sonari.innertube.models.WatchEndpoint
import app.music.backend.sonari.LocalPlayerAwareWindowInsets
import app.music.backend.sonari.LocalPlayerConnection
import app.music.backend.sonari.R
import app.music.backend.sonari.constants.GridItemSize
import app.music.backend.sonari.constants.GridItemsSizeKey
import app.music.backend.sonari.constants.GridThumbnailHeight
import app.music.backend.sonari.models.toMediaMetadata
import app.music.backend.sonari.playback.queues.YouTubeQueue
import app.music.backend.sonari.ui.component.IconButton
import app.music.backend.sonari.ui.component.LocalMenuState
import app.music.backend.sonari.ui.component.YouTubeGridItem
import app.music.backend.sonari.ui.component.YouTubeListItem
import app.music.backend.sonari.ui.component.shimmer.GridItemPlaceHolder
import app.music.backend.sonari.ui.component.shimmer.ListItemPlaceHolder
import app.music.backend.sonari.ui.component.shimmer.ShimmerHost
import app.music.backend.sonari.ui.menu.YouTubeAlbumMenu
import app.music.backend.sonari.ui.menu.YouTubeArtistMenu
import app.music.backend.sonari.ui.menu.YouTubePlaylistMenu
import app.music.backend.sonari.ui.menu.YouTubeSongMenu
import app.music.backend.sonari.ui.utils.backToMain
import app.music.backend.sonari.utils.listItemShape
import app.music.backend.sonari.utils.rememberEnumPreference
import app.music.backend.sonari.viewmodels.ArtistItemsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistItemsScreen(
  navController: NavController,
  scrollBehavior: TopAppBarScrollBehavior,
  viewModel: ArtistItemsViewModel = hiltViewModel(),
) {
  val menuState = LocalMenuState.current
  val haptic = LocalHapticFeedback.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
  val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

  val lazyListState = rememberLazyListState()
  val lazyGridState = rememberLazyGridState()
  val coroutineScope = rememberCoroutineScope()
  val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

  val title by viewModel.title.collectAsState()
  val itemsPage by viewModel.itemsPage.collectAsState()

  LaunchedEffect(lazyListState) {
    snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } }
      .collect { shouldLoadMore ->
        if (!shouldLoadMore) return@collect
        viewModel.loadMore()
      }
  }

  LaunchedEffect(lazyGridState) {
    snapshotFlow { lazyGridState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } }
      .collect { shouldLoadMore ->
        if (!shouldLoadMore) return@collect
        viewModel.loadMore()
      }
  }

  if (itemsPage == null) {
    ShimmerHost(
      modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
    ) {
      repeat(8) { ListItemPlaceHolder() }
    }
  }

  if (itemsPage?.items?.firstOrNull() is SongItem) {
    LazyColumn(
      state = lazyListState,
      contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
      itemsIndexed(
        items = itemsPage?.items.orEmpty().distinctBy { it.id },
        key = { _, it -> it.id },
      ) { index, item ->
        YouTubeListItem(
          item = item,
          isActive =
            when (item) {
              is SongItem -> mediaMetadata?.id == item.id
              is AlbumItem -> mediaMetadata?.album?.id == item.id
              else -> false
            },
          isPlaying = isPlaying,
          shape = listItemShape(index, itemsPage?.items.orEmpty().distinctBy { it.id }.size),
          trailingContent = {
            IconButton(
              onClick = {
                menuState.show {
                  when (item) {
                    is SongItem ->
                      YouTubeSongMenu(
                        song = item,
                        navController = navController,
                        onDismiss = menuState::dismiss,
                      )
                    is AlbumItem ->
                      YouTubeAlbumMenu(
                        albumItem = item,
                        navController = navController,
                        onDismiss = menuState::dismiss,
                      )
                    is ArtistItem ->
                      YouTubeArtistMenu(
                        artist = item,
                        onDismiss = menuState::dismiss,
                      )
                    is PlaylistItem ->
                      YouTubePlaylistMenu(
                        playlist = item,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss,
                      )
                  }
                }
              },
            ) {
              Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = null,
              )
            }
          },
          modifier =
            Modifier.clickable {
              when (item) {
                is SongItem -> {
                  if (item.id == mediaMetadata?.id) {
                    playerConnection.togglePlayPause()
                  } else {
                    playerConnection.playQueue(
                      YouTubeQueue(
                        item.endpoint ?: WatchEndpoint(videoId = item.id),
                        item.toMediaMetadata()
                      ),
                    )
                  }
                }
                is AlbumItem -> navController.navigate("album/${item.id}")
                is ArtistItem -> navController.navigate("artist/${item.id}")
                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
              }
            },
        )
      }

      if (itemsPage?.continuation != null) {
        item(key = "loading") { ShimmerHost { repeat(3) { ListItemPlaceHolder() } } }
      }
    }
  } else {
    LazyVerticalGrid(
      state = lazyGridState,
      columns =
        GridCells.Adaptive(
          minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp
        ),
      contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) {
      items(items = itemsPage?.items.orEmpty().distinctBy { it.id }, key = { it.id }) { item ->
        YouTubeGridItem(
          item = item,
          isActive =
            when (item) {
              is SongItem -> mediaMetadata?.id == item.id
              is AlbumItem -> mediaMetadata?.album?.id == item.id
              else -> false
            },
          isPlaying = isPlaying,
          fillMaxWidth = true,
          coroutineScope = coroutineScope,
          modifier =
            Modifier.combinedClickable(
                onClick = {
                  when (item) {
                    is SongItem ->
                      playerConnection.playQueue(
                        YouTubeQueue(
                          item.endpoint ?: WatchEndpoint(videoId = item.id),
                          item.toMediaMetadata()
                        )
                      )
                    is AlbumItem -> navController.navigate("album/${item.id}")
                    is ArtistItem -> navController.navigate("artist/${item.id}")
                    is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                  }
                },
                onLongClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  menuState.show {
                    when (item) {
                      is SongItem ->
                        YouTubeSongMenu(
                          song = item,
                          navController = navController,
                          onDismiss = menuState::dismiss
                        )
                      is AlbumItem ->
                        YouTubeAlbumMenu(
                          albumItem = item,
                          navController = navController,
                          onDismiss = menuState::dismiss
                        )
                      is ArtistItem ->
                        YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                      is PlaylistItem ->
                        YouTubePlaylistMenu(
                          playlist = item,
                          coroutineScope = coroutineScope,
                          onDismiss = menuState::dismiss
                        )
                    }
                  }
                }
              )
              .animateItem()
        )
      }

      if (itemsPage?.continuation != null) {
        item(key = "loading") {
          ShimmerHost(Modifier.animateItem()) { GridItemPlaceHolder(fillMaxWidth = true) }
        }
      }
    }
  }

  TopAppBar(
    title = { Text(title) },
    navigationIcon = {
      IconButton(
        onClick = navController::navigateUp,
        onLongClick = navController::backToMain,
      ) {
        Icon(
          painterResource(R.drawable.arrow_back),
          contentDescription = null,
        )
      }
    },
  )
}
