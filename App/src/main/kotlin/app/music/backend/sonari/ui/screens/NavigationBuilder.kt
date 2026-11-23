package app.music.backend.sonari.ui.screens

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import app.music.backend.sonari.innertube.pages.PodcastPage
import app.music.backend.sonari.constants.DarkModeKey
import app.music.backend.sonari.constants.PureBlackKey
import app.music.backend.sonari.sonari.changelog.ChangelogScreen
import app.music.backend.sonari.sonari.commitscreen.CommitScreen
import app.music.backend.sonari.sonari.updater.UpdateScreen
import app.music.backend.sonari.ui.screens.ambient.AmbientModeScreen
import app.music.backend.sonari.ui.screens.artist.ArtistAlbumsScreen
import app.music.backend.sonari.ui.screens.artist.ArtistItemsScreen
import app.music.backend.sonari.ui.screens.artist.ArtistScreen
import app.music.backend.sonari.ui.screens.artist.ArtistSongsScreen
import app.music.backend.sonari.ui.screens.equalizer.EqScreen
import app.music.backend.sonari.ui.screens.equalizer.axion.AxionEqScreen
import app.music.backend.sonari.ui.screens.generate.GenerateScreen
import app.music.backend.sonari.ui.screens.library.DuplicatesScreen
import app.music.backend.sonari.ui.screens.library.LibraryScreen
import app.music.backend.sonari.ui.screens.library.LocalSongScreen
import app.music.backend.sonari.ui.screens.playlist.AutoPlaylistScreen
import app.music.backend.sonari.ui.screens.playlist.BottomPlaylistScreen
import app.music.backend.sonari.ui.screens.playlist.CachePlaylistScreen
import app.music.backend.sonari.ui.screens.playlist.LocalPlaylistScreen
import app.music.backend.sonari.ui.screens.playlist.OnlinePlaylistScreen
import app.music.backend.sonari.ui.screens.playlist.SpotifyPlaylistDetailScreen
import app.music.backend.sonari.ui.screens.playlist.TopPlaylistScreen
import app.music.backend.sonari.ui.screens.recognition.RecognitionHistoryScreen
import app.music.backend.sonari.ui.screens.recognition.RecognitionScreen
import app.music.backend.sonari.ui.screens.search.OnlineSearchResult
import app.music.backend.sonari.ui.screens.search.SearchScreen
import app.music.backend.sonari.ui.screens.settings.AboutScreen
import app.music.backend.sonari.ui.screens.settings.AccountSettingsScreen
import app.music.backend.sonari.ui.screens.settings.AiSettings
import app.music.backend.sonari.ui.screens.settings.AppIconSettingsScreen
import app.music.backend.sonari.ui.screens.settings.AppearanceSettings
import app.music.backend.sonari.ui.screens.settings.BackupAndRestore
import app.music.backend.sonari.ui.screens.settings.BlockedArtistsScreen
import app.music.backend.sonari.ui.screens.settings.ContentSettings
import app.music.backend.sonari.ui.screens.settings.DarkMode
import app.music.backend.sonari.ui.screens.settings.GlassEffectSettings
import app.music.backend.sonari.ui.screens.settings.PlayerSettings
import app.music.backend.sonari.ui.screens.settings.PrivacySettings
import app.music.backend.sonari.ui.screens.settings.SettingsScreen
import app.music.backend.sonari.ui.screens.settings.StorageSettings
import app.music.backend.sonari.ui.screens.settings.ThemeScreen
import app.music.backend.sonari.ui.screens.settings.UpdateSettings
import app.music.backend.sonari.ui.screens.settings.UptimeScreen
import app.music.backend.sonari.ui.screens.settings.integrations.ListenTogetherSettings
import app.music.backend.sonari.utils.rememberEnumPreference
import app.music.backend.sonari.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
  navController: NavHostController,
  scrollBehavior: TopAppBarScrollBehavior,
  activity: Activity,
  snackbarHostState: SnackbarHostState
) {
  composable(Screens.Home.route) {
    HomeScreen(navController = navController, snackbarHostState = snackbarHostState)
  }

  composable(Screens.Search.route) {
    val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme =
      remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
      }
    val pureBlack = remember(pureBlackEnabled, useDarkTheme) { pureBlackEnabled && useDarkTheme }
    SearchScreen(navController = navController, pureBlack = pureBlack)
  }

  composable(Screens.Library.route) { LibraryScreen(navController) }

  composable("history") { HistoryScreen(navController) }

  composable("ambient_mode") { AmbientModeScreen(navController) }

  composable(
    route = "podcast_video",
    enterTransition = { fadeIn(tween(300)) },
    exitTransition = { fadeOut(tween(300)) },
    popEnterTransition = { fadeIn(tween(300)) },
    popExitTransition = { fadeOut(tween(300)) },
  ) {
    app.music.backend.sonari.ui.screens.podcast.PodcastVideoScreen(navController)
  }

  composable("local_songs") { LocalSongScreen(navController) }

  composable("local_duplicates") { DuplicatesScreen(navController) }

  composable("stats") { StatsScreen(navController) }

  composable("mood_and_genres") { MoodAndGenresScreen(navController, scrollBehavior) }

  composable("account") { AccountScreen(navController, scrollBehavior) }

  composable("new_release") { NewReleaseScreen(navController, scrollBehavior) }

  composable("charts_screen") { ChartsScreen(navController) }

  composable(
    route = "browse/{browseId}",
    arguments = listOf(navArgument("browseId") { type = NavType.StringType })
  ) {
    BrowseScreen(navController, scrollBehavior, it.arguments?.getString("browseId"))
  }

  composable(
    route = "search/{query}",
    arguments =
      listOf(
        navArgument("query") { type = NavType.StringType },
      ),
    enterTransition = { fadeIn(tween(250)) },
    exitTransition = {
      if (targetState.destination.route?.startsWith("search/") == true) {
        fadeOut(tween(200))
      } else {
        fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
      }
    },
    popEnterTransition = {
      if (initialState.destination.route?.startsWith("search/") == true) {
        fadeIn(tween(250))
      } else {
        fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
      }
    },
    popExitTransition = { fadeOut(tween(200)) },
  ) {
    OnlineSearchResult(navController)
  }

  composable(
    route = "album/{albumId}",
    arguments =
      listOf(
        navArgument("albumId") { type = NavType.StringType },
      ),
  ) {
    AlbumScreen(navController, scrollBehavior)
  }

  composable(
    route = "artist/{artistId}",
    arguments =
      listOf(
        navArgument("artistId") { type = NavType.StringType },
      ),
  ) { backStackEntry ->
    // A podcast episode's "artist" is its show (an MPSP browse id), which is a playlist.
    val artistId = backStackEntry.arguments?.getString("artistId")
    if (PodcastPage.isShowBrowseId(artistId)) {
      LaunchedEffect(artistId) {
        navController.navigate("online_playlist/${PodcastPage.showId(artistId!!)}") {
          popUpTo(backStackEntry.destination.id) { inclusive = true }
        }
      }
    } else {
      ArtistScreen(navController, scrollBehavior)
    }
  }

  composable(
    route = "artist/{artistId}/songs",
    arguments =
      listOf(
        navArgument("artistId") { type = NavType.StringType },
      ),
  ) {
    ArtistSongsScreen(navController, scrollBehavior)
  }

  composable(
    route = "artist/{artistId}/albums",
    arguments = listOf(navArgument("artistId") { type = NavType.StringType })
  ) {
    ArtistAlbumsScreen(navController, scrollBehavior)
  }

  composable(
    route = "artist/{artistId}/items?browseId={browseId}?params={params}",
    arguments =
      listOf(
        navArgument("artistId") { type = NavType.StringType },
        navArgument("browseId") {
          type = NavType.StringType
          nullable = true
        },
        navArgument("params") {
          type = NavType.StringType
          nullable = true
        },
      ),
  ) {
    ArtistItemsScreen(navController, scrollBehavior)
  }

  composable(
    route = "online_playlist/{playlistId}",
    arguments =
      listOf(
        navArgument("playlistId") { type = NavType.StringType },
      ),
  ) {
    OnlinePlaylistScreen(navController, scrollBehavior)
  }

  composable(
    route = "spotify_playlist/{playlistId}?title={title}&cover={cover}",
    arguments =
      listOf(
        navArgument("playlistId") { type = NavType.StringType },
        navArgument("title") {
          type = NavType.StringType
          defaultValue = "Playlist"
        },
        navArgument("cover") {
          type = NavType.StringType
          defaultValue = ""
        },
      ),
  ) { backStackEntry ->
    val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
    val title = backStackEntry.arguments?.getString("title") ?: "Playlist"
    val cover = backStackEntry.arguments?.getString("cover") ?: ""
    SpotifyPlaylistDetailScreen(
      navController = navController,
      scrollBehavior = scrollBehavior,
      playlistId = playlistId,
      playlistTitle = title,
      playlistCover = cover,
    )
  }

  composable(
    route = "local_playlist/{playlistId}",
    arguments =
      listOf(
        navArgument("playlistId") { type = NavType.StringType },
      ),
  ) {
    LocalPlaylistScreen(navController, scrollBehavior)
  }

  composable(
    route = "auto_playlist/{playlist}",
    arguments =
      listOf(
        navArgument("playlist") { type = NavType.StringType },
      ),
  ) {
    AutoPlaylistScreen(navController, scrollBehavior)
  }

  composable(
    route = "cache_playlist/{playlist}",
    arguments =
      listOf(
        navArgument("playlist") { type = NavType.StringType },
      ),
  ) {
    CachePlaylistScreen(navController, scrollBehavior)
  }

  composable(
    route = "top_playlist/{top}",
    arguments =
      listOf(
        navArgument("top") { type = NavType.StringType },
      ),
  ) {
    TopPlaylistScreen(navController, scrollBehavior)
  }

  composable(
    route = "bottom_playlist/{bottom}",
    arguments =
      listOf(
        navArgument("bottom") { type = NavType.StringType },
      ),
  ) {
    BottomPlaylistScreen(navController, scrollBehavior)
  }

  composable(
    route = "youtube_browse/{browseId}?params={params}",
    arguments =
      listOf(
        navArgument("browseId") {
          type = NavType.StringType
          nullable = true
        },
        navArgument("params") {
          type = NavType.StringType
          nullable = true
        },
      ),
  ) {
    YouTubeBrowseScreen(navController)
  }

  composable("settings") { SettingsScreen(navController, scrollBehavior) }
  composable("blocked_artists") { BlockedArtistsScreen(navController) }

  composable(
    route = "settings/update?highlightKey={highlightKey}",
    arguments =
      listOf(
        navArgument("highlightKey") {
          type = NavType.StringType
          nullable = true
        }
      )
  ) { backStackEntry ->
    UpdateSettings(
      navController,
      scrollBehavior,
      highlightKey = backStackEntry.arguments?.getString("highlightKey")
    )
  }

  composable(
    route = "settings/account?highlightKey={highlightKey}",
    arguments =
      listOf(
        navArgument("highlightKey") {
          type = NavType.StringType
          nullable = true
        }
      )
  ) { backStackEntry ->
    AccountSettingsScreen(
      navController,
      scrollBehavior,
      highlightKey = backStackEntry.arguments?.getString("highlightKey")
    )
  }

  composable(
    route = "settings/integrations?highlightKey={highlightKey}",
    arguments =
      listOf(
        navArgument("highlightKey") {
          type = NavType.StringType
          nullable = true
        }
      )
  ) { backStackEntry ->
    AccountSettingsScreen(
      navController,
      scrollBehavior,
      highlightKey = backStackEntry.arguments?.getString("highlightKey") ?: "Integrations"
    )
  }

  composable("ambient_settings") {
    app.music.backend.sonari.ui.screens.settings.AmbientSettingsScreen(navController)
  }

  composable(
    route = "settings/appearance?highlightKey={highlightKey}",
    arguments =
      listOf(
        navArgument("highlightKey") {
          type = NavType.StringType
          nullable = true
        }
      )
  ) { backStackEntry ->
    AppearanceSettings(
      navController,
      scrollBehavior,
      activity,
      snackbarHostState,
      highlightKey = backStackEntry.arguments?.getString("highlightKey")
    )
  }

  composable("settings/appearance/theme") { ThemeScreen(navController) }
  composable(
    route = "settings/appearance/haptics?highlightKey={highlightKey}",
    arguments = listOf(navArgument("highlightKey") { type = NavType.StringType; nullable = true })
  ) { backStackEntry ->
    app.music.backend.sonari.ui.screens.settings.HapticsSettings(
      navController = navController,
      scrollBehavior = scrollBehavior,
      activity = activity,
      snackbarHostState = snackbarHostState,
      highlightKey = backStackEntry.arguments?.getString("highlightKey")
    )
  }

  composable("settings/appearance/app_icon") {
    AppIconSettingsScreen(navController, activity, snackbarHostState)
  }

  composable("settings/appearance/font") {
    app.music.backend.sonari.ui.screens.settings.FontSelectionScreen(navController, scrollBehavior)
  }

  composable("settings/listening_summary") {
    app.music.backend.sonari.ui.screens.ListeningSummaryScreen(navController)
  }

  composable(
    route = "detailed_listening_history/{startTimestamp}",
    arguments =
      listOf(
        navArgument("startTimestamp") { type = NavType.StringType },
      ),
  ) {
    app.music.backend.sonari.ui.screens.DetailedListeningHistoryScreen(navController)
  }

  composable("settings/appearance/liquidglass") {
    GlassEffectSettings(navController, scrollBehavior)
  }

  composable(
    route = "settings/content?highlightKey={highlightKey}",
    arguments =
      listOf(
        navArgument("highlightKey") {
          type = NavType.StringType
          nullable = true
        }
      )
  ) { backStackEntry ->
    ContentSettings(
      navController,
      scrollBehavior,
      highlightKey = backStackEntry.arguments?.getString("highlightKey")
    )
  }

  composable("uptime") { UptimeScreen(navController, scrollBehavior) }

  composable(
    route = "settings/ai?highlightKey={highlightKey}",
    arguments =
      listOf(
        navArgument("highlightKey") {
          type = NavType.StringType
          nullable = true
        }
      )
  ) { backStackEntry ->
    AiSettings(
      navController,
      scrollBehavior,
      highlightKey = backStackEntry.arguments?.getString("highlightKey")
    )
  }

  composable(
    route = "settings/player?highlightKey={highlightKey}",
    arguments =
      listOf(
        navArgument("highlightKey") {
          type = NavType.StringType
          nullable = true
        }
      )
  ) { backStackEntry ->
    PlayerSettings(
      navController,
      scrollBehavior,
      highlightKey = backStackEntry.arguments?.getString("highlightKey")
    )
  }

  composable(
    route =
      "settings/storage?autoOpenExportPicker={autoOpenExportPicker}&highlightKey={highlightKey}",
    arguments =
      listOf(
        navArgument("autoOpenExportPicker") {
          type = NavType.BoolType
          defaultValue = false
        },
        navArgument("highlightKey") {
          type = NavType.StringType
          nullable = true
        }
      )
  ) { backStackEntry ->
    val autoOpenExportPicker = backStackEntry.arguments?.getBoolean("autoOpenExportPicker") ?: false
    StorageSettings(
      navController = navController,
      scrollBehavior = scrollBehavior,
      autoOpenExportPicker = autoOpenExportPicker,
      highlightKey = backStackEntry.arguments?.getString("highlightKey")
    )
  }

  composable("settings/equalizer") { AxionEqScreen(onBackClick = { navController.navigateUp() }) }

  composable(
    route = "settings/privacy?highlightKey={highlightKey}",
    arguments =
      listOf(
        navArgument("highlightKey") {
          type = NavType.StringType
          nullable = true
        }
      )
  ) { backStackEntry ->
    PrivacySettings(
      navController,
      scrollBehavior,
      highlightKey = backStackEntry.arguments?.getString("highlightKey")
    )
  }

  composable(
    route = "settings/backup_restore?highlightKey={highlightKey}",
    arguments =
      listOf(
        navArgument("highlightKey") {
          type = NavType.StringType
          nullable = true
        }
      )
  ) { backStackEntry ->
    BackupAndRestore(
      navController,
      scrollBehavior,
      highlightKey = backStackEntry.arguments?.getString("highlightKey")
    )
  }

  composable("settings/extensions") {
    app.music.backend.sonari.ui.screens.settings.ExtensionSettingsScreen(
        navController = navController,
        scrollBehavior = scrollBehavior
    )
  }
  composable("settings/discord") {
    app.music.backend.sonari.ui.screens.settings.DiscordSettings(navController, scrollBehavior)
  }

  composable("settings/lastfm") {
    app.music.backend.sonari.ui.screens.settings.LastFMSettingsScreen(navController)
  }

  composable("settings/discord/experimental") {
    app.music.backend.sonari.ui.screens.settings.DiscordExperimental(navController)
  }

  composable("settings/spotify_import") { SpotifyImportScreen(navController) }

  composable(
    route = "settings/about?highlightKey={highlightKey}",
    arguments =
      listOf(
        navArgument("highlightKey") {
          type = NavType.StringType
          nullable = true
        }
      )
  ) { backStackEntry ->
    AboutScreen(
      navController,
      scrollBehavior,
      highlightKey = backStackEntry.arguments?.getString("highlightKey")
    )
  }

  composable("update") { UpdateScreen(navController) }

  composable("login") { LoginScreen(navController) }

  dialog("equalizer") { EqScreen(navController = navController) }

  composable("recognition") { RecognitionScreen(navController) }

  composable("recognition_history") { RecognitionHistoryScreen(navController) }
  composable("settings/changelog") { ChangelogScreen(navController, scrollBehavior) }
  composable("settings/commits") { CommitScreen(navController, scrollBehavior) }
  composable("generate") { GenerateScreen(navController) }

  composable("settings/navidrome_login") {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entryPoint =
      dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        app.music.backend.sonari.cloud.CloudEntryPoint::class.java
      )
    app.music.backend.sonari.ui.screens.settings.cloud.NavidromeLoginScreen(navController, entryPoint.navidromeRepository())
  }

  composable("settings/navidrome_dashboard") {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entryPoint =
      dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        app.music.backend.sonari.cloud.CloudEntryPoint::class.java
      )
    app.music.backend.sonari.ui.screens.settings.cloud.NavidromeDashboardScreen(navController, entryPoint.navidromeRepository())
  }

  composable("settings/jellyfin_login") {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entryPoint =
      dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        app.music.backend.sonari.cloud.CloudEntryPoint::class.java
      )
    app.music.backend.sonari.ui.screens.settings.cloud.JellyfinLoginScreen(navController, entryPoint.jellyfinRepository())
  }

  composable("settings/jellyfin_dashboard") {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entryPoint =
      dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        app.music.backend.sonari.cloud.CloudEntryPoint::class.java
      )
    app.music.backend.sonari.ui.screens.settings.cloud.JellyfinDashboardScreen(navController, entryPoint.jellyfinRepository())
  }
}
