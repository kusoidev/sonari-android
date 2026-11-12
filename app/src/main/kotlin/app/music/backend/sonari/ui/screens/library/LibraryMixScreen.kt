package app.music.backend.sonari.ui.screens.library

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.music.backend.sonari.LocalPlayerAwareWindowInsets
import app.music.backend.sonari.LocalPlayerConnection
import app.music.backend.sonari.R
import app.music.backend.sonari.constants.AlbumViewTypeKey
import app.music.backend.sonari.constants.CONTENT_TYPE_HEADER
import app.music.backend.sonari.constants.CONTENT_TYPE_PLAYLIST
import app.music.backend.sonari.constants.GridItemSize
import app.music.backend.sonari.constants.GridItemsSizeKey
import app.music.backend.sonari.constants.GridThumbnailHeight
import app.music.backend.sonari.constants.LibraryViewType
import app.music.backend.sonari.constants.MixSortDescendingKey
import app.music.backend.sonari.constants.MixSortType
import app.music.backend.sonari.constants.MixSortTypeKey
import app.music.backend.sonari.constants.ShowBottomPlaylistKey
import app.music.backend.sonari.constants.ShowCachedPlaylistKey
import app.music.backend.sonari.constants.ShowDownloadedPlaylistKey
import app.music.backend.sonari.constants.ShowExportedPlaylistKey
import app.music.backend.sonari.constants.ShowLikedPlaylistKey
import app.music.backend.sonari.constants.ShowTopPlaylistKey
import app.music.backend.sonari.constants.ThumbnailCornerRadius
import app.music.backend.sonari.constants.ListThumbnailSize
import app.music.backend.sonari.constants.YtmSyncKey
import app.music.backend.sonari.db.entities.Album
import app.music.backend.sonari.db.entities.Artist
import app.music.backend.sonari.db.entities.Playlist
import app.music.backend.sonari.db.entities.PlaylistEntity
import app.music.backend.sonari.extensions.reversed
import app.music.backend.sonari.ui.component.AlbumGridItem
import app.music.backend.sonari.ui.component.AlbumListItem
import app.music.backend.sonari.ui.component.ArtistGridItem
import app.music.backend.sonari.ui.component.ArtistListItem
import app.music.backend.sonari.ui.component.AutoPlaylistButton
import app.music.backend.sonari.ui.component.GridItem
import app.music.backend.sonari.ui.component.ListItem
import app.music.backend.sonari.ui.component.LocalMenuState
import app.music.backend.sonari.ui.component.PlaylistGridItem
import app.music.backend.sonari.ui.component.PlaylistListItem
import app.music.backend.sonari.ui.component.SortHeader
import app.music.backend.sonari.ui.menu.AlbumMenu
import app.music.backend.sonari.ui.menu.ArtistMenu
import app.music.backend.sonari.ui.menu.PlaylistMenu
import app.music.backend.sonari.utils.joinByBullet
import app.music.backend.sonari.utils.listItemShape
import app.music.backend.sonari.utils.rememberEnumPreference
import app.music.backend.sonari.utils.rememberPreference
import app.music.backend.sonari.viewmodels.LibraryMixViewModel
import coil3.compose.AsyncImage
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(
  ExperimentalFoundationApi::class,
  ExperimentalMaterial3Api::class,
  ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun LibraryMixScreen(
  navController: NavController,
  filterContent: @Composable () -> Unit,
  viewModel: LibraryMixViewModel = hiltViewModel(),
) {
  val menuState = LocalMenuState.current
  val haptic = LocalHapticFeedback.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
  val mediaMetadata by playerConnection.mediaMetadata.collectAsState()


  var viewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
  val (sortType, onSortTypeChange) = rememberEnumPreference(MixSortTypeKey, MixSortType.CREATE_DATE)
  val (sortDescending, onSortDescendingChange) = rememberPreference(MixSortDescendingKey, true)
  val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

  val (ytmSync) = rememberPreference(YtmSyncKey, true)

  val topSize by viewModel.topValue.collectAsState(initial = 50)
  val likedPlaylist =
    Playlist(
      playlist =
        PlaylistEntity(id = UUID.randomUUID().toString(), name = stringResource(R.string.liked)),
      songCount = 0,
      songThumbnails = emptyList(),
    )

  val downloadPlaylist =
    Playlist(
      playlist =
        PlaylistEntity(id = UUID.randomUUID().toString(), name = stringResource(R.string.offline)),
      songCount = 0,
      songThumbnails = emptyList(),
    )

  val topPlaylist =
    Playlist(
      playlist =
        PlaylistEntity(
          id = UUID.randomUUID().toString(),
          name = stringResource(R.string.my_top) + " $topSize"
        ),
      songCount = 0,
      songThumbnails = emptyList(),
    )

  val cachePlaylist =
    Playlist(
      playlist =
        PlaylistEntity(
          id = UUID.randomUUID().toString(),
          name = stringResource(R.string.cached_playlist)
        ),
      songCount = 0,
      songThumbnails = emptyList(),
    )

  val uploadedPlaylist =
    Playlist(
      playlist =
        PlaylistEntity(
          id = UUID.randomUUID().toString(),
          name = stringResource(R.string.uploaded_playlist)
        ),
      songCount = 0,
      songThumbnails = emptyList(),
    )

  val showLiked = true
  val showDownloaded = true

  val albums = viewModel.albums.collectAsState()
  val artist = viewModel.artists.collectAsState()
  val playlist = viewModel.playlists.collectAsState()
  val allSongsCount by viewModel.allSongsCount.collectAsState()

  val allPlaylist =
    Playlist(
      playlist =
        PlaylistEntity(id = "all", name = stringResource(R.string.filter_all)),
      songCount = allSongsCount,
      songThumbnails = emptyList(),
    )

  val collator = Collator.getInstance(Locale.getDefault())
  collator.strength = Collator.PRIMARY

  fun <T> List<T>.sortItems(): List<T> {
    return when (sortType) {
      MixSortType.CREATE_DATE ->
        this.sortedBy { item ->
          when (item) {
            is Album -> item.album.bookmarkedAt
            is Artist -> item.artist.bookmarkedAt
            is Playlist -> item.playlist.createdAt
            else -> LocalDateTime.now()
          }
        }
      MixSortType.NAME ->
        this.sortedWith(
          compareBy(collator) { item ->
            when (item) {
              is Album -> item.album.title
              is Artist -> item.artist.name
              is Playlist -> item.playlist.name
              else -> ""
            }
          },
        )
      MixSortType.LAST_UPDATED ->
        this.sortedBy { item ->
          when (item) {
            is Album -> item.album.lastUpdateTime
            is Artist -> item.artist.lastUpdateTime
            is Playlist -> item.playlist.lastUpdateTime
            else -> LocalDateTime.now()
          }
        }
    }.reversed(sortDescending)
  }

  val sortedPlaylists = playlist.value.sortItems()
  val pinnedPlaylists = sortedPlaylists.filter { it.playlist.isPinned }
  val otherPlaylists = sortedPlaylists.filterNot { it.playlist.isPinned }
  val sortedArtists = artist.value.sortItems()
  val sortedAlbums = albums.value.sortItems()

  val coroutineScope = rememberCoroutineScope()

  val lazyListState = rememberLazyListState()
  val lazyGridState = rememberLazyGridState()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val scrollToTop =
    backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

  LaunchedEffect(scrollToTop?.value) {
    if (scrollToTop?.value == true) {
      when (viewType) {
        LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
        LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
      }
      backStackEntry?.savedStateHandle?.set("scrollToTop", false)
    }
  }

  LaunchedEffect(Unit) {
    if (ytmSync) {
      withContext(Dispatchers.IO) { viewModel.syncAllLibrary() }
    }
  }

  val headerContent =
    @Composable {
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
              MixSortType.CREATE_DATE -> R.string.sort_by_create_date
              MixSortType.LAST_UPDATED -> R.string.sort_by_last_updated
              MixSortType.NAME -> R.string.sort_by_name
            }
          },
        )

        Spacer(Modifier.weight(1f))

        Spacer(modifier = Modifier.width(16.dp))
      }
    }

  val isRefreshing by viewModel.isRefreshing.collectAsState()
  val pullRefreshState = rememberPullToRefreshState()

  PullToRefreshBox(
    state = pullRefreshState,
    isRefreshing = isRefreshing,
    onRefresh = viewModel::refresh,
    indicator = {
      PullToRefreshDefaults.LoadingIndicator(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        modifier = Modifier.align(Alignment.TopCenter),
      )
    }
  ) {
    when (viewType) {
      LibraryViewType.LIST ->
        LazyColumn(
          state = lazyListState,
          contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
          item(
            key = "filter",
            contentType = CONTENT_TYPE_HEADER,
          ) {
            filterContent()
          }

          item(
            key = "header",
            contentType = CONTENT_TYPE_HEADER,
          ) {
            headerContent()
          }

          item(
            key = "auto_playlists_grid",
            contentType = CONTENT_TYPE_HEADER,
          ) {
            FlowRow(
              maxItemsInEachRow = 2,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
              val itemModifier = Modifier.weight(1f)
              if (showLiked) {
                AutoPlaylistButton(
                  title = stringResource(R.string.liked),
                  icon = R.drawable.favorite,
                  iconTint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                  onClick = { navController.navigate("auto_playlist/liked") },
                  modifier = itemModifier
                )
              }
              if (showDownloaded) {
                AutoPlaylistButton(
                  title = stringResource(R.string.offline),
                  icon = R.drawable.offline,
                  iconTint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                  onClick = { navController.navigate("auto_playlist/downloaded") },
                  modifier = itemModifier
                )
              }
              AutoPlaylistButton(
                title = stringResource(R.string.history),
                icon = R.drawable.music_history,
                iconTint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                onClick = { navController.navigate("history") },
                modifier = itemModifier
              )
            }
          }

          item(
            key = "playlists_header",
            contentType = CONTENT_TYPE_HEADER,
          ) {
            androidx.compose.material3.Text(
              text = stringResource(R.string.filter_playlists),
              style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )
          }

          val allPlaylists = pinnedPlaylists + otherPlaylists
          items(
            items = allPlaylists.distinctBy { it.id },
            key = { "playlist_${it.id}" },
            contentType = { CONTENT_TYPE_PLAYLIST },
          ) { item ->
            PlaylistListItem(
              playlist = item,
              trailingContent = {
                IconButton(
                  onClick = {
                    menuState.show {
                      PlaylistMenu(
                        playlist = item,
                        coroutineScope = coroutineScope,
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
              },
              modifier =
                Modifier.fillMaxWidth()
                  .combinedClickable(
                    onClick = { navController.navigate("local_playlist/${item.id}") },
                    onLongClick = {
                      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                      menuState.show {
                        PlaylistMenu(
                          playlist = item,
                          coroutineScope = coroutineScope,
                          onDismiss = menuState::dismiss,
                        )
                      }
                    },
                  )
                  .animateItem(),
            )
          }


          item(
            key = "all_playlist",
            contentType = CONTENT_TYPE_PLAYLIST,
          ) {
            PlaylistListItem(
              playlist = allPlaylist,
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { navController.navigate("auto_playlist/all") },
            )
          }

          if (sortedArtists.isNotEmpty()) {
            item(
              key = "artists_header",
              contentType = CONTENT_TYPE_HEADER,
            ) {
              androidx.compose.material3.Text(
                text = stringResource(R.string.filter_artists),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
              )
            }

            items(
              items = sortedArtists.distinctBy { it.id },
              key = { "artist_${it.id}" },
              contentType = { "artist" },
            ) { item ->
              ArtistListItem(
                artist = item,
                trailingContent = {
                  IconButton(
                    onClick = {
                      menuState.show {
                        ArtistMenu(
                          originalArtist = item,
                          coroutineScope = coroutineScope,
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
                },
                modifier =
                  Modifier.fillMaxWidth()
                    .combinedClickable(
                      onClick = { navController.navigate("artist/${item.id}") },
                      onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                          ArtistMenu(
                            originalArtist = item,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                          )
                        }
                      },
                    )
                    .animateItem(),
              )
            }
          }

          if (sortedAlbums.isNotEmpty()) {
            item(
              key = "albums_header",
              contentType = CONTENT_TYPE_HEADER,
            ) {
              androidx.compose.material3.Text(
                text = stringResource(R.string.filter_albums),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
              )
            }

            items(
              items = sortedAlbums.distinctBy { it.id },
              key = { "album_${it.id}" },
              contentType = { "album" },
            ) { item ->
              AlbumListItem(
                album = item,
                isActive = item.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                trailingContent = {
                  IconButton(
                    onClick = {
                      menuState.show {
                        AlbumMenu(
                          originalAlbum = item,
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
                },
                modifier =
                  Modifier.fillMaxWidth()
                    .combinedClickable(
                      onClick = { navController.navigate("album/${item.id}") },
                      onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                          AlbumMenu(
                            originalAlbum = item,
                            navController = navController,
                            onDismiss = menuState::dismiss,
                          )
                        }
                      },
                    )
                    .animateItem(),
              )
            }
          }
        }
      LibraryViewType.GRID ->
        LazyVerticalGrid(
          state = lazyGridState,
          columns =
            GridCells.Adaptive(
              minSize =
                GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp,
            ),
          contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
          item(
            key = "filter",
            span = { GridItemSpan(maxLineSpan) },
            contentType = CONTENT_TYPE_HEADER,
          ) {
            filterContent()
          }

          item(
            key = "header",
            span = { GridItemSpan(maxLineSpan) },
            contentType = CONTENT_TYPE_HEADER,
          ) {
            headerContent()
          }

          item(
            key = "auto_playlists_grid",
            span = { GridItemSpan(maxLineSpan) },
            contentType = CONTENT_TYPE_HEADER,
          ) {
            FlowRow(
              maxItemsInEachRow = 2,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
              val itemModifier = Modifier.weight(1f)
              if (showLiked) {
                AutoPlaylistButton(
                  title = stringResource(R.string.liked),
                  icon = R.drawable.favorite,
                  iconTint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                  onClick = { navController.navigate("auto_playlist/liked") },
                  modifier = itemModifier
                )
              }
              if (showDownloaded) {
                AutoPlaylistButton(
                  title = stringResource(R.string.offline),
                  icon = R.drawable.offline,
                  iconTint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                  onClick = { navController.navigate("auto_playlist/downloaded") },
                  modifier = itemModifier
                )
              }
              AutoPlaylistButton(
                title = stringResource(R.string.history),
                icon = R.drawable.music_history,
                iconTint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                onClick = { navController.navigate("history") },
                modifier = itemModifier
              )
            }
          }

          item(
            key = "playlists_header",
            span = { GridItemSpan(maxLineSpan) },
            contentType = CONTENT_TYPE_HEADER,
          ) {
            androidx.compose.material3.Text(
              text = stringResource(R.string.filter_playlists),
              style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )
          }

          val allPlaylists = pinnedPlaylists + otherPlaylists
          items(
            items = allPlaylists.distinctBy { it.id },
            key = { "playlist_${it.id}" },
            contentType = { CONTENT_TYPE_PLAYLIST },
          ) { item ->
            PlaylistGridItem(
              playlist = item,
              fillMaxWidth = true,
              modifier =
                Modifier.fillMaxWidth()
                  .combinedClickable(
                    onClick = { navController.navigate("local_playlist/${item.id}") },
                    onLongClick = {
                      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                      menuState.show {
                        PlaylistMenu(
                          playlist = item,
                          coroutineScope = coroutineScope,
                          onDismiss = menuState::dismiss,
                        )
                      }
                    },
                  )
                  .animateItem(),
            )
          }


          item(
            key = "all_playlist",
            contentType = CONTENT_TYPE_PLAYLIST,
          ) {
            PlaylistGridItem(
              playlist = allPlaylist,
              fillMaxWidth = true,
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { navController.navigate("auto_playlist/all") },
            )
          }

          if (sortedArtists.isNotEmpty()) {
            item(
              key = "artists_header",
              span = { GridItemSpan(maxLineSpan) },
              contentType = CONTENT_TYPE_HEADER,
            ) {
              androidx.compose.material3.Text(
                text = stringResource(R.string.filter_artists),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
              )
            }

            items(
              items = sortedArtists.distinctBy { it.id },
              key = { "artist_${it.id}" },
              contentType = { "artist" },
            ) { item ->
              ArtistGridItem(
                artist = item,
                fillMaxWidth = true,
                modifier =
                  Modifier.fillMaxWidth()
                    .combinedClickable(
                      onClick = { navController.navigate("artist/${item.id}") },
                      onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                          ArtistMenu(
                            originalArtist = item,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                          )
                        }
                      },
                    )
                    .animateItem(),
              )
            }
          }

          if (sortedAlbums.isNotEmpty()) {
            item(
              key = "albums_header",
              span = { GridItemSpan(maxLineSpan) },
              contentType = CONTENT_TYPE_HEADER,
            ) {
              androidx.compose.material3.Text(
                text = stringResource(R.string.filter_albums),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
              )
            }

            items(
              items = sortedAlbums.distinctBy { it.id },
              key = { "album_${it.id}" },
              contentType = { "album" },
            ) { item ->
              AlbumGridItem(
                album = item,
                isActive = item.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                coroutineScope = coroutineScope,
                fillMaxWidth = true,
                modifier =
                  Modifier.fillMaxWidth()
                    .combinedClickable(
                      onClick = { navController.navigate("album/${item.id}") },
                      onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                          AlbumMenu(
                            originalAlbum = item,
                            navController = navController,
                            onDismiss = menuState::dismiss,
                          )
                        }
                      },
                    )
                    .animateItem(),
              )
            }
          }
        }
    }
  }
}
