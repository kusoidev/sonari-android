package app.music.backend.sonari.ui.screens.artist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import app.music.backend.sonari.LocalPlayerAwareWindowInsets
import app.music.backend.sonari.LocalPlayerConnection
import app.music.backend.sonari.R
import app.music.backend.sonari.constants.CONTENT_TYPE_ALBUM
import app.music.backend.sonari.constants.CONTENT_TYPE_HEADER
import app.music.backend.sonari.constants.GridItemSize
import app.music.backend.sonari.constants.GridItemsSizeKey
import app.music.backend.sonari.constants.GridThumbnailHeight
import app.music.backend.sonari.ui.component.IconButton
import app.music.backend.sonari.ui.component.AlbumGridItem
import app.music.backend.sonari.ui.component.LocalMenuState
import app.music.backend.sonari.ui.utils.backToMain
import app.music.backend.sonari.utils.rememberEnumPreference
import app.music.backend.sonari.viewmodels.ArtistAlbumsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistAlbumsScreen(
  navController: NavController,
  scrollBehavior: TopAppBarScrollBehavior,
  viewModel: ArtistAlbumsViewModel = hiltViewModel(),
) {
  val menuState = LocalMenuState.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
  val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

  val artist by viewModel.artist.collectAsState()
  val albums by viewModel.albums.collectAsState()

  val coroutineScope = rememberCoroutineScope()
  val lazyGridState = rememberLazyGridState()
  val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

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
  if (inSelectMode) {
    BackHandler(onBack = onExitSelectionMode)
  }

  val snackbarHostState = remember { SnackbarHostState() }

  Box(modifier = Modifier.fillMaxSize()) {
    LazyVerticalGrid(
      state = lazyGridState,
      columns =
        GridCells.Adaptive(
          minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp
        ),
      contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) {
      item(
        key = "header",
        span = { GridItemSpan(maxLineSpan) },
        contentType = CONTENT_TYPE_HEADER
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 16.dp)
        ) {
          Spacer(Modifier.weight(1f))

          Text(
            text = pluralStringResource(R.plurals.n_album, albums.size, albums.size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
          )
        }
      }

      items(
        items = albums.distinctBy { it.id },
        key = { it.id },
        contentType = { CONTENT_TYPE_ALBUM }
      ) { album ->
        Text("Albums")
      }
    }

    TopAppBar(
      title = { Text(artist?.artist?.name.orEmpty()) },
      navigationIcon = {
        IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
          Icon(painter = painterResource(id = R.drawable.arrow_back), contentDescription = null)
        }
      },
      scrollBehavior = scrollBehavior
    )

    SnackbarHost(
      hostState = snackbarHostState,
      modifier =
        Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
          .align(Alignment.BottomCenter)
    )
  }
}
