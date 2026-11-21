package app.music.backend.sonari.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.navigation.NavController
import app.music.backend.sonari.LocalPlayerAwareWindowInsets
import app.music.backend.sonari.R
import app.music.backend.sonari.constants.ChipSortTypeKey
import app.music.backend.sonari.constants.LibraryFilter
import app.music.backend.sonari.ui.component.ChipsRow
import app.music.backend.sonari.ui.component.DefaultDialog
import app.music.backend.sonari.ui.component.Material3SettingsGroup
import app.music.backend.sonari.ui.component.Material3SettingsItem
import app.music.backend.sonari.ui.component.TextFieldDialog
import app.music.backend.sonari.utils.onlinePlaylistIdFromUrl
import app.music.backend.sonari.utils.onlinePlaylistRoute
import app.music.backend.sonari.utils.rememberEnumPreference

@Composable
fun LibraryScreen(navController: NavController) {
  var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
  var showFabMenu by remember { mutableStateOf(false) }
  var showImportMenu by remember { mutableStateOf(false) }
  var showYoutubeImportDialog by remember { mutableStateOf(false) }
  var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
  var showTasteGenerationDialog by rememberSaveable { mutableStateOf(false) }
  val context = LocalContext.current

  LaunchedEffect(Unit) {
    if (
      filterType == LibraryFilter.ALBUMS ||
        filterType == LibraryFilter.PODCASTS ||
        filterType == LibraryFilter.LOCAL ||
        filterType == LibraryFilter.SPOTIFY
    ) {
      filterType = LibraryFilter.LIBRARY
    }
  }

  BackHandler(enabled = filterType != LibraryFilter.LIBRARY) { filterType = LibraryFilter.LIBRARY }

  val filterContent =
    @Composable {
      Row {
        ChipsRow(
          chips =
            listOf(
              LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
              LibraryFilter.SONGS to stringResource(R.string.filter_songs),
              LibraryFilter.ARTISTS to stringResource(R.string.filter_artists),
            ),
          currentValue = filterType,
          onValueUpdate = {
            filterType =
              if (filterType == it) {
                LibraryFilter.LIBRARY
              } else {
                it
              }
          },
          modifier = Modifier.weight(1f),
        )
      }
    }

  val currentInsets = LocalPlayerAwareWindowInsets.current
  val density = LocalDensity.current
  val layoutDirection = LocalLayoutDirection.current
  val fabHeightPlusSpacing = 128.dp
  val newInsets =
    remember(currentInsets, density, layoutDirection) {
      object : WindowInsets {
        override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
          currentInsets.getLeft(density, layoutDirection)

        override fun getTop(density: Density) = currentInsets.getTop(density)

        override fun getRight(density: Density, layoutDirection: LayoutDirection) =
          currentInsets.getRight(density, layoutDirection)

        override fun getBottom(density: Density) =
          currentInsets.getBottom(density) + with(density) { fabHeightPlusSpacing.roundToPx() }
      }
    }

  CompositionLocalProvider(LocalPlayerAwareWindowInsets provides newInsets) {
    val shuffleRoulette = remember { ShuffleRouletteState() }
    // The "+" used to float permanently over the library, sitting on top of the tiles and cards.
    // It now tucks itself away while you scroll down, comes back as soon as you scroll up, and
    // returns on its own when the list comes to rest.
    var fabVisible by remember { mutableStateOf(true) }
    val fabNestedScroll =
      remember {
        object : NestedScrollConnection {
          override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) {
              if (available.y < -3f) {
                fabVisible = false
              } else if (available.y > 3f) {
                fabVisible = true
              }
            }
            return Offset.Zero
          }
        }
      }
    LaunchedEffect(fabVisible) {
      if (!fabVisible) {
        delay(1200)
        fabVisible = true
      }
    }
    val fabSlide by
      animateDpAsState(
        targetValue = if (fabVisible) 0.dp else 96.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "fabSlide",
      )
    val fabAlpha by animateFloatAsState(
      targetValue = if (fabVisible) 1f else 0f,
      animationSpec = tween(durationMillis = 160),
      label = "fabAlpha",
    )
    val fabScale by animateFloatAsState(
      targetValue = if (fabVisible) 1f else 0.82f,
      animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
      label = "fabScale",
    )

    Box(
      modifier = Modifier.fillMaxSize().nestedScroll(fabNestedScroll),
    ) {
      when (filterType) {
        LibraryFilter.LIBRARY -> LibraryHomeScreen(navController, filterContent)
        LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(navController, filterContent)
        LibraryFilter.SONGS ->
          LibrarySongsScreen(
            navController,
            { filterType = LibraryFilter.LIBRARY },
            roulette = shuffleRoulette,
          )
        LibraryFilter.ALBUMS ->
          LibraryAlbumsScreen(navController, { filterType = LibraryFilter.LIBRARY })
        LibraryFilter.ARTISTS ->
          LibraryArtistsScreen(navController, { filterType = LibraryFilter.LIBRARY })
        LibraryFilter.LOCAL ->
          LocalSongScreen(
            navController = navController,
            onBack = { filterType = LibraryFilter.LIBRARY },
            isEmbedded = true,
            filterContent = filterContent,
          )
        LibraryFilter.SPOTIFY ->
          LibrarySpotifyScreen(navController, filterContent)
        LibraryFilter.PODCASTS ->
          LibraryPodcastsScreen(navController, { filterType = LibraryFilter.LIBRARY })
      }

      val bottomPadding = with(density) { currentInsets.getBottom(density).toDp() }
      Box(modifier = Modifier.fillMaxSize().padding(end = 16.dp, bottom = bottomPadding + 20.dp)) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.align(Alignment.BottomEnd)
              .offset(y = fabSlide)
              .alpha(fabAlpha)
              .graphicsLayer {
                scaleX = fabScale
                scaleY = fabScale
              }
        ) {
          // Shuffle sits right next to the add button and tucks away with the exact same
          // scroll logic; it only makes sense where a song list is on screen.
          if (filterType == LibraryFilter.SONGS) {
            androidx.compose.material3.FloatingActionButton(
              onClick = { if (fabVisible) shuffleRoulette.begin() },
              shape = CircleShape,
              containerColor = MaterialTheme.colorScheme.onSurface,
              contentColor = MaterialTheme.colorScheme.surface
            ) {
              Icon(painter = painterResource(R.drawable.shuffle), contentDescription = stringResource(R.string.shuffle))
            }
          }
          androidx.compose.material3.FloatingActionButton(
            onClick = { if (fabVisible) showFabMenu = true },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface
          ) {
            Icon(painter = painterResource(R.drawable.add), contentDescription = "Add")
          }
        }
      }
    }
  }

  if (showFabMenu) {
    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    androidx.compose.material3.ModalBottomSheet(
      onDismissRequest = { showFabMenu = false },
      sheetState =
        @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
      Column(modifier = Modifier.padding(bottom = 24.dp, start = 16.dp, end = 16.dp)) {
        Material3SettingsGroup(
          compact = true,
          items =
            listOf(
              Material3SettingsItem(
                title = { Text(stringResource(R.string.create_playlist)) },
                icon = painterResource(R.drawable.add),
                onClick = {
                  showFabMenu = false
                  showCreatePlaylistDialog = true
                }
              ),
              Material3SettingsItem(
                title = { Text("Create from taste") },
                icon = painterResource(R.drawable.tast),
                tintIcon = false,
                iconShape = androidx.compose.foundation.shape.CircleShape,
                onClick = {
                  showFabMenu = false
                  showTasteGenerationDialog = true
                }
              ),
              Material3SettingsItem(
                title = { Text(stringResource(R.string.import_playlist)) },
                icon = painterResource(R.drawable.download),
                onClick = {
                  showFabMenu = false
                  showImportMenu = true
                }
              )
            )
        )
      }
    }
  }

  if (showImportMenu) {
    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    androidx.compose.material3.ModalBottomSheet(
      onDismissRequest = { showImportMenu = false },
      sheetState =
        @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
      Column(modifier = Modifier.padding(bottom = 24.dp)) {
        androidx.compose.material3.ListItem(
          headlineContent = { Text(stringResource(R.string.import_from_youtube_music)) },
          modifier =
            Modifier.clickable {
              showImportMenu = false
              showYoutubeImportDialog = true
            }
        )
      }
    }
  }

  if (showYoutubeImportDialog) {
    var url by remember { mutableStateOf(TextFieldValue("")) }
    val invalidUrlMessage = stringResource(R.string.invalid_playlist_url)
    app.music.backend.sonari.ui.component.TextFieldDialog(
      icon = { Icon(painter = painterResource(R.drawable.link), contentDescription = null) },
      title = {
        Column {
          Text(text = stringResource(R.string.import_youtube_music_playlist_title))
          Text(
            text = stringResource(R.string.import_youtube_music_playlist_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
          )
        }
      },
      initialTextFieldValue = url,
      autoFocus = true,
      onDismiss = { showYoutubeImportDialog = false },
      onDone = { finalUrl ->
        val route = onlinePlaylistRoute(onlinePlaylistIdFromUrl(finalUrl))
        if (route != null) {
          navController.navigate(route)
        } else {
          android.widget.Toast.makeText(
              context,
              invalidUrlMessage,
              android.widget.Toast.LENGTH_SHORT
            )
            .show()
        }
        showYoutubeImportDialog = false
      }
    )
  }

  if (showCreatePlaylistDialog) {
    app.music.backend.sonari.ui.component.CreatePlaylistDialog(
      onDismiss = { showCreatePlaylistDialog = false },
      initialTextFieldValue = null,
      allowSyncing = false,
      onPlaylistCreated = { playlistId ->
        showCreatePlaylistDialog = false
        navController.navigate("local_playlist/$playlistId")
      }
    )
  }

  if (showTasteGenerationDialog) {
    app.music.backend.sonari.ui.component.CreateFromTasteDialog(
      onDismiss = { showTasteGenerationDialog = false },
      onPlaylistCreated = { playlistId ->
        showTasteGenerationDialog = false
        navController.navigate("local_playlist/$playlistId")
      }
    )
  }
}
