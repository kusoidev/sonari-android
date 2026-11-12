package app.music.backend.sonari.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import app.music.backend.sonari.innertube.models.AlbumItem
import app.music.backend.sonari.innertube.models.ArtistItem
import app.music.backend.sonari.innertube.models.PlaylistItem
import app.music.backend.sonari.LocalPlayerAwareWindowInsets
import app.music.backend.sonari.LocalPlayerConnection
import app.music.backend.sonari.R
import app.music.backend.sonari.constants.GridItemSize
import app.music.backend.sonari.constants.GridItemsSizeKey
import app.music.backend.sonari.constants.GridThumbnailHeight
import app.music.backend.sonari.ui.component.IconButton
import app.music.backend.sonari.ui.component.LocalMenuState
import app.music.backend.sonari.ui.component.YouTubeGridItem
import app.music.backend.sonari.ui.component.shimmer.GridItemPlaceHolder
import app.music.backend.sonari.ui.component.shimmer.ShimmerHost
import app.music.backend.sonari.ui.menu.YouTubeAlbumMenu
import app.music.backend.sonari.ui.menu.YouTubeArtistMenu
import app.music.backend.sonari.ui.menu.YouTubePlaylistMenu
import app.music.backend.sonari.ui.utils.backToMain
import app.music.backend.sonari.utils.rememberEnumPreference
import app.music.backend.sonari.viewmodels.BrowseViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
  navController: NavController,
  scrollBehavior: TopAppBarScrollBehavior,
  browseId: String?,
  viewModel: BrowseViewModel = hiltViewModel(),
) {
  val menuState = LocalMenuState.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
  val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

  val title by viewModel.title.collectAsState()
  val items by viewModel.items.collectAsState()

  val coroutineScope = rememberCoroutineScope()
  val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

  LazyVerticalGrid(
    columns =
      GridCells.Adaptive(
        minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp
      ),
    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
  ) {
    items?.let { items ->
      items(items = items.distinctBy { it.id }, key = { it.id }) { item ->
        YouTubeGridItem(
          item = item,
          isPlaying = isPlaying,
          fillMaxWidth = true,
          coroutineScope = coroutineScope,
          modifier =
            Modifier.combinedClickable(
              onClick = {
                when (item) {
                  is AlbumItem -> navController.navigate("album/${item.id}")
                  is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                  is ArtistItem -> navController.navigate("artist/${item.id}")
                  else -> {}
                }
              },
              onLongClick = {
                menuState.show {
                  when (item) {
                    is AlbumItem ->
                      YouTubeAlbumMenu(
                        albumItem = item,
                        navController = navController,
                        onDismiss = menuState::dismiss
                      )
                    is PlaylistItem -> {
                      YouTubePlaylistMenu(
                        playlist = item,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss
                      )
                    }
                    is ArtistItem -> {
                      YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                    }
                    else -> {}
                  }
                }
              }
            )
        )
      }

      if (items.isEmpty()) {
        items(8) { ShimmerHost { GridItemPlaceHolder(fillMaxWidth = true) } }
      }
    }
  }

  TopAppBar(
    title = { Text(title ?: "") },
    navigationIcon = {
      IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
      }
    }
  )
}
