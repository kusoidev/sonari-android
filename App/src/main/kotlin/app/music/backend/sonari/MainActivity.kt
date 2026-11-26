package app.music.backend.sonari

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import app.music.backend.sonari.sonari.AudioDeviceBottomSheet
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.music.backend.sonari.ui.utils.handOffToLaunchIntro
import app.music.backend.sonari.ui.launch.LaunchGate
import app.music.backend.sonari.ui.launch.LaunchIntroOverlay
import app.music.backend.sonari.ui.launch.LocalLaunchIntro
import app.music.backend.sonari.ui.launch.ReportLaunchHomeComposed
import app.music.backend.sonari.ui.launch.launchMarkTarget
import app.music.backend.sonari.ui.launch.rememberLaunchIntroState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import app.music.backend.sonari.utils.HapticType
import app.music.backend.sonari.utils.rememberHapticHelper
import app.music.backend.sonari.innertube.YouTube
import app.music.backend.sonari.innertube.models.SongItem
import app.music.backend.sonari.innertube.models.WatchEndpoint
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import app.music.backend.sonari.constants.*
import app.music.backend.sonari.constants.AiRecommendationsKey
import app.music.backend.sonari.constants.AppBarHeight
import app.music.backend.sonari.constants.AppLanguageKey
import app.music.backend.sonari.constants.DarkModeKey
import app.music.backend.sonari.constants.DefaultOpenTabKey
import app.music.backend.sonari.constants.DisableScreenshotKey
import app.music.backend.sonari.constants.DynamicThemeKey
import app.music.backend.sonari.constants.EnableHighRefreshRateKey
import app.music.backend.sonari.constants.FloatingToolbarBottomPadding
import app.music.backend.sonari.constants.FloatingToolbarHorizontalPadding
import app.music.backend.sonari.constants.LiquidGlassBlurRadiusKey
import app.music.backend.sonari.constants.LiquidGlassChromaticAberrationKey
import app.music.backend.sonari.constants.LiquidGlassDepthEffectKey
import app.music.backend.sonari.constants.LiquidGlassGlobalEnabledKey
import app.music.backend.sonari.constants.LiquidGlassLensAmountKey
import app.music.backend.sonari.constants.LiquidGlassLensHeightKey
import app.music.backend.sonari.constants.LiquidGlassMiniPlayerEnabledKey
import app.music.backend.sonari.constants.LiquidGlassNavBarEnabledKey
import app.music.backend.sonari.constants.LiquidGlassPlayerEnabledKey
import app.music.backend.sonari.constants.LiquidGlassSurfaceOpacityKey
import app.music.backend.sonari.constants.LiquidGlassSurfaceTintColorKey
import app.music.backend.sonari.constants.LiquidGlassTextColorKey
import app.music.backend.sonari.constants.LiquidGlassVibrancyKey
import app.music.backend.sonari.constants.ListenTogetherInTopBarKey
import app.music.backend.sonari.constants.ListenTogetherUsernameKey
import app.music.backend.sonari.constants.MiniPlayerBottomSpacing
import app.music.backend.sonari.constants.MiniPlayerHeight
import app.music.backend.sonari.constants.NavigationBarAnimationSpec
import app.music.backend.sonari.constants.NavigationBarHeight
import app.music.backend.sonari.constants.PauseListenHistoryKey
import app.music.backend.sonari.constants.PauseSearchHistoryKey
import app.music.backend.sonari.constants.PureBlackKey
import app.music.backend.sonari.constants.IsMonochromeThemeKey
import app.music.backend.sonari.constants.SYSTEM_DEFAULT
import app.music.backend.sonari.constants.SelectedThemeColorKey
import app.music.backend.sonari.constants.StopMusicOnTaskClearKey
import app.music.backend.sonari.constants.UseFloatingNavBarKey
import app.music.backend.sonari.constants.UseNewMiniPlayerDesignKey
import app.music.backend.sonari.db.MusicDatabase
import app.music.backend.sonari.db.entities.SearchHistory
import app.music.backend.sonari.sonari.UpdateNotificationHelper
import app.music.backend.sonari.sonari.updater.checkForUpdate
import app.music.backend.sonari.sonari.updater.getAutoUpdateCheckSetting
import app.music.backend.sonari.sonari.updater.getUpdateNotificationsSetting
import app.music.backend.sonari.sonari.updater.saveUpdateAvailableState
import app.music.backend.sonari.extensions.toEnum
import app.music.backend.sonari.models.toMediaMetadata
import app.music.backend.sonari.playback.DownloadUtil
import app.music.backend.sonari.playback.MusicService
import app.music.backend.sonari.playback.MusicService.MusicBinder
import app.music.backend.sonari.playback.PlayerConnection
import app.music.backend.sonari.playback.queues.YouTubeQueue
import app.music.backend.sonari.ui.component.*
import app.music.backend.sonari.ui.component.AppFloatingNavBar
import app.music.backend.sonari.ui.component.GlassEffectConfig
import app.music.backend.sonari.ui.component.LocalAppBackdrop
import app.music.backend.sonari.ui.component.LocalLiquidState
import app.music.backend.sonari.ui.component.isGlassLowEnd
import app.music.backend.sonari.ui.component.isGlassSupported
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState
import app.music.backend.sonari.ui.component.LocalGlassEffectConfig
import app.music.backend.sonari.ui.component.RingtoneProgressDialog
import app.music.backend.sonari.ui.component.RingtoneTrimmerDialog
import app.music.backend.sonari.ui.component.backdrop.backdrops.layerBackdrop
import app.music.backend.sonari.ui.component.backdrop.backdrops.rememberLayerBackdrop
import app.music.backend.sonari.ui.component.floatingtabbar.rememberFloatingTabBarScrollConnection
import app.music.backend.sonari.ui.component.shimmer.getShimmerTheme
import app.music.backend.sonari.ui.menu.YouTubeSongMenu
import app.music.backend.sonari.ui.player.BottomSheetPlayer
import app.music.backend.sonari.ui.screens.Screens
import app.music.backend.sonari.ui.screens.SettingDialoge
import app.music.backend.sonari.ui.screens.WelcomeDialog
import app.music.backend.sonari.ui.screens.navigationBuilder
import app.music.backend.sonari.ui.screens.settings.DarkMode
import app.music.backend.sonari.ui.screens.settings.NavigationTab
import app.music.backend.sonari.ui.screens.settings.RingtoneViewModel
import app.music.backend.sonari.ui.theme.ColorSaver
import app.music.backend.sonari.ui.theme.DefaultThemeColor
import app.music.backend.sonari.ui.theme.PlayerColorExtractor
import app.music.backend.sonari.ui.theme.sonariTheme
import app.music.backend.sonari.ui.utils.appBarScrollBehavior
import app.music.backend.sonari.ui.utils.resetHeightOffset
import app.music.backend.sonari.utils.SyncUtils
import app.music.backend.sonari.utils.dataStore
import app.music.backend.sonari.ai.AiRecommendationHelper
import app.music.backend.sonari.constants.AiRecommendationsKey
import app.music.backend.sonari.constants.LastAiRecommendationUpdateDayKey
import app.music.backend.sonari.constants.CreateFromTasteDailyKey
import app.music.backend.sonari.constants.LastCreateFromTasteUpdateDayKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import app.music.backend.sonari.utils.get
import app.music.backend.sonari.utils.onlinePlaylistRoute
import app.music.backend.sonari.utils.validOnlinePlaylistId
import app.music.backend.sonari.utils.rememberEnumPreference
import app.music.backend.sonari.utils.rememberPreference
import app.music.backend.sonari.utils.reportException
import app.music.backend.sonari.utils.setAppLocale
import app.music.backend.sonari.viewmodels.HomeViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.compose.OnParticleSystemUpdateListener
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import timber.log.Timber

val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  private var introPlays = false
  companion object {
    const val ACTION_SEARCH = "app.music.backend.sonari.action.SEARCH"
    const val ACTION_LIBRARY = "app.music.backend.sonari.action.LIBRARY"
    const val ACTION_UPDATE = "app.music.backend.sonari.action.UPDATE"
    const val ACTION_RECOGNITION = "app.music.backend.sonari.action.RECOGNITION"
    const val ACTION_NOW_PLAYING = "app.music.backend.sonari.action.NOW_PLAYING"
    const val ACTION_QUEUE = "app.music.backend.sonari.action.QUEUE"
    const val ACTION_OUTPUT_SWITCHER = "app.music.backend.sonari.action.OUTPUT_SWITCHER"
    const val ACTION_SONG_OPTIONS = "app.music.backend.sonari.action.SONG_OPTIONS"
    const val ACTION_LYRICS = "app.music.backend.sonari.action.LYRICS"
    const val ACTION_MEDIA_OUTPUT = "com.android.settings.panel.action.MEDIA_OUTPUT"
    const val EXTRA_MEDIA_OUTPUT_PACKAGE_NAME = "com.android.settings.panel.extra.PACKAGE_NAME"
    const val EXTRA_AUTO_START_RECOGNITION = "auto_start_recognition"
  }

  @Inject lateinit var database: MusicDatabase
  @Inject lateinit var localTasteEngine: app.music.backend.sonari.generate.LocalTasteEngine

  @Inject lateinit var downloadUtil: DownloadUtil

  @Inject lateinit var syncUtils: SyncUtils
  @Inject lateinit var localSongScanner: app.music.backend.sonari.localmedia.LocalSongScanner

  @Inject
  lateinit var listenTogetherManager: app.music.backend.sonari.listentogether.ListenTogetherManager
  private lateinit var navController: NavHostController
  private var pendingIntent: Intent? = null

  private var playerConnection by mutableStateOf<PlayerConnection?>(null)

  private val serviceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        if (service is MusicBinder) {
          try {
            playerConnection =
              PlayerConnection(this@MainActivity, service, database, lifecycleScope)
            Timber.tag("MainActivity").d("PlayerConnection created successfully")

            listenTogetherManager.setPlayerConnection(playerConnection)
          } catch (e: Exception) {
            Timber.tag("MainActivity").e(e, "Failed to create PlayerConnection")

            lifecycleScope.launch {
              delay(500)
              try {
                playerConnection =
                  PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                listenTogetherManager.setPlayerConnection(playerConnection)
              } catch (e2: Exception) {
                Timber.tag("MainActivity").e(e2, "Failed to create PlayerConnection on retry")
              }
            }
          }
        }
      }

      override fun onServiceDisconnected(name: ComponentName?) {

        listenTogetherManager.setPlayerConnection(null)
        playerConnection?.dispose()
        playerConnection = null
      }
    }

  override fun onStart() {
    super.onStart()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
      ) {
        ActivityCompat.requestPermissions(
          this,
          arrayOf(Manifest.permission.POST_NOTIFICATIONS),
          1000
        )
      }
    }

    bindService(Intent(this, MusicService::class.java), serviceConnection, BIND_AUTO_CREATE)
  }

  override fun onStop() {
    unbindService(serviceConnection)
    super.onStop()
  }

  override fun onDestroy() {
    super.onDestroy()
    val wasPlaying = playerConnection?.isPlaying?.value == true
    listenTogetherManager.setPlayerConnection(null)
    playerConnection?.dispose()
    if (dataStore.get(StopMusicOnTaskClearKey, false) && wasPlaying && isFinishing) {
      stopService(Intent(this, MusicService::class.java))
      playerConnection = null
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (::navController.isInitialized) {
      handleDeepLinkIntent(intent, navController)
      handleRecognitionIntent(intent, navController)
      handleAssistantSearchIntent(intent, navController)
    } else {
      pendingIntent = intent
    }
  }

  private var isPlaying = false

  override fun startForegroundService(service: Intent): android.content.ComponentName? {
    return try {
      super.startForegroundService(service)
    } catch (e: Exception) {
      if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
          e is android.app.ForegroundServiceStartNotAllowedException
      ) {
        Timber.e(e, "Suppressed ForegroundServiceStartNotAllowedException in MainActivity")
        null
      } else {
        throw e
      }
    }
  }

  private suspend fun enrichLegacyLocalMusic() {
    val entries =
      runCatching {
          org.json.JSONArray(
            assets.open("legacy_library.json").bufferedReader().use { it.readText() }
          )
        }
        .getOrNull() ?: return
    val byName = HashMap<String, org.json.JSONObject>()
    for (i in 0 until entries.length()) {
      val entry = entries.optJSONObject(i) ?: continue
      val fileName = entry.optString("fileName")
      if (fileName.isNotBlank()) byName[fileName] = entry
    }
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection =
      arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
    val selection =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
      } else {
        "${MediaStore.MediaColumns.DATA} LIKE ?"
      }
    val selectionArgs =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf("Music/Sonari/%")
      } else {
        arrayOf("%/Music/Sonari/%")
      }

    data class LegacyRow(val uri: String, val meta: org.json.JSONObject)

    val rows = mutableListOf<LegacyRow>()
    contentResolver
      .query(collection, projection, selection, selectionArgs, null)
      ?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        while (cursor.moveToNext()) {
          val name = cursor.getString(nameColumn) ?: continue
          val meta = byName[name] ?: continue
          val uri =
            ContentUris.withAppendedId(collection, cursor.getLong(idColumn)).toString()
          rows += LegacyRow(uri, meta)
        }
      }
    if (rows.isEmpty()) return

    val songs = database.getSongsByIds(rows.map { it.uri }).associateBy { it.song.id }
    for (row in rows) {
      val song = songs[row.uri] ?: continue
      val title = fixLegacyText(row.meta.optString("title")).ifBlank { song.song.title }
      val artist = fixLegacyText(row.meta.optString("artist"))
      val thumbnail =
        row.meta.optString("thumbnailURL").takeIf { it.startsWith("http") }
          ?: song.song.thumbnailUrl
      val duration =
        row.meta.optDouble("duration", 0.0).let { if (it > 0) it.toInt() else song.song.duration }
      database.updateSongMetadata(row.uri, title, duration, thumbnail)
      if (artist.isNotBlank()) {
        song.artists.firstOrNull()?.let { songArtist ->
          val entity = database.getArtistById(songArtist.id) ?: return@let
          if (entity.name != artist) database.update(entity.copy(name = artist))
        }
      }
    }
  }

  private fun fixLegacyText(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return runCatching {
        val repaired = value.toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8)
        if (repaired.contains('\uFFFD')) value else repaired
      }
      .getOrDefault(value)
  }

  @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    introPlays = savedInstanceState == null

    // Below Android 12 the compat splash inflates splash_screen_view, and that layout sizes its
    // icon with ?attr/splashScreenIconSize, an attribute only a Theme.SplashScreen parent
    // defines. Theme.sonari is not one, so on some devices (a Huawei on Android 10 among them)
    // the inflate throws UnsupportedOperationException and the activity dies before it starts.
    // On Android 12 and newer the platform draws the splash and no layout is inflated at all,
    // which is why only older phones ever hit this. The launch intro is the real boot screen
    // there anyway, so the splash is set up on 12+ only, and a splash that fails for any other
    // reason must never be allowed to take the activity down with it.
    val splashScreen =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            installSplashScreen().also { splash -> splash.handOffToLaunchIntro { introPlays } }
          }
          .onFailure { timber.log.Timber.tag("LaunchIntro").e(it, "splash screen unavailable") }
          .getOrNull()
      } else {
        null
      }

    if (introPlays) LaunchGate.begin() else LaunchGate.skip()
    // With no splash waiting to be dismissed, the intro takes off at once instead of idling out
    // its hand-off timeout.
    if (splashScreen == null) LaunchGate.onSplashGone()
    super.onCreate(savedInstanceState)

    try {
      startService(Intent(this, app.music.backend.sonari.playback.MusicService::class.java))
    } catch (e: Exception) {
      timber.log.Timber.e(e, "Failed to pre-start MusicService for warmup")
    }

    window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
    WindowCompat.setDecorFitsSystemWindows(window, false)

    listenTogetherManager.initialize()

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      val locale =
        dataStore[AppLanguageKey]
          ?.takeUnless { it == SYSTEM_DEFAULT }
          ?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
      setAppLocale(this, locale)
    }

    if (java.io.File(filesDir, "clear_export_state").exists()) {
      lifecycleScope.launch {
        dataStore.edit { preferences ->
          preferences.remove(app.music.backend.sonari.constants.ExportingSongIdsKey)
          preferences.remove(app.music.backend.sonari.constants.ExportedSongIdsKey)
          preferences.remove(app.music.backend.sonari.constants.ExportProgressKey)
        }
        java.io.File(filesDir, "clear_export_state").delete()
      }
    }

    // One-time import of music copied over from the legacy Sonari app.
    val legacyImportMarker = java.io.File(filesDir, "legacy_local_import_done_v4")
    val legacyThumbsMarker = java.io.File(filesDir, "legacy_local_thumbs_import_done")
    val audioPermission =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
      } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
      }
    if (
      (!legacyImportMarker.exists() || !legacyThumbsMarker.exists()) &&
        ContextCompat.checkSelfPermission(this, audioPermission) ==
          PackageManager.PERMISSION_GRANTED
    ) {
      lifecycleScope.launch(Dispatchers.IO) {
        runCatching {
            if (!legacyImportMarker.exists()) {
              localSongScanner.scanDevice(
                app.music.backend.sonari.localmedia.LocalSongScanConfig(
                  includedFolders = setOf("Music/Sonari"),
                  filterMode = app.music.backend.sonari.localmedia.LocalFolderFilterMode.INCLUDE,
                )
              )
              legacyImportMarker.createNewFile()
            }
          }
          .onFailure { timber.log.Timber.e(it, "Legacy local scan failed") }
        runCatching {
            if (!legacyThumbsMarker.exists()) {
              enrichLegacyLocalMusic()
              legacyThumbsMarker.createNewFile()
            }
          }
          .onFailure { timber.log.Timber.e(it, "Legacy local metadata import failed") }
      }
    }

    lifecycleScope.launch {
      val aiRecommendationsEnabled = dataStore.data.map { it[AiRecommendationsKey] ?: false }.first()
      if (aiRecommendationsEnabled) {
        val lastUpdate = dataStore.data.map { it[LastAiRecommendationUpdateDayKey] ?: 0L }.first()
        val currentDay = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
        if (lastUpdate != currentDay) {
          dataStore.edit { it[LastAiRecommendationUpdateDayKey] = currentDay }
          AiRecommendationHelper.generateRecommendations(this@MainActivity)
        }
      }

      dataStore.data
        .map {
          (try {
            it[DisableScreenshotKey]
          } catch (e: Exception) {
            null
          }) ?: false
        }
        .distinctUntilChanged()
        .collectLatest {
          if (it) {
            window.setFlags(
              WindowManager.LayoutParams.FLAG_SECURE,
              WindowManager.LayoutParams.FLAG_SECURE,
            )
          } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
          }
        }
    }

    lifecycleScope.launch {
      val aiRecommendationsEnabled = dataStore.data.map { it[AiRecommendationsKey] ?: false }.first()
      if (aiRecommendationsEnabled) {
        val lastUpdate = dataStore.data.map { it[LastAiRecommendationUpdateDayKey] ?: 0L }.first()
        val currentDay = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
        if (lastUpdate != currentDay) {
          dataStore.edit { it[LastAiRecommendationUpdateDayKey] = currentDay }
          AiRecommendationHelper.generateRecommendations(this@MainActivity)
        }
      }

      dataStore.data
        .map {
          (try {
            it[AiRecommendationsKey]
          } catch (e: Exception) {
            null
          }) ?: false
        }
        .distinctUntilChanged()
        .collectLatest { enabled ->
          val workManager = androidx.work.WorkManager.getInstance(this@MainActivity)
          if (enabled) {
            val request =
              androidx.work
                .PeriodicWorkRequestBuilder<app.music.backend.sonari.ai.AiRecommendationWorker>(
                  1,
                  java.util.concurrent.TimeUnit.DAYS
                )
                .setConstraints(
                  androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
              "AiRecommendationWorker",
              androidx.work.ExistingPeriodicWorkPolicy.KEEP,
              request
            )
          } else {
            workManager.cancelUniqueWork("AiRecommendationWorker")
          }
        }
    }

    setContent {
      sonariApp(
        playerConnection = playerConnection,
        database = database,
        downloadUtil = downloadUtil,
        syncUtils = syncUtils,
      )
    }
  }

  @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  private fun sonariApp(
    playerConnection: PlayerConnection?,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    syncUtils: SyncUtils,
  ) {
    val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
    val enableHighRefreshRate by rememberPreference(EnableHighRefreshRateKey, defaultValue = true)
    val context = LocalContext.current
    // The newest release we know of, kept from the last check so the banner can appear at once.
    var availableUpdateVersion by remember {
      androidx.compose.runtime.mutableStateOf(
        app.music.backend.sonari.sonari.updater.pendingUpdateVersion(context)
      )
    }
    var dismissedUpdateVersion by remember {
      androidx.compose.runtime.mutableStateOf(
        app.music.backend.sonari.sonari.updater.getDismissedUpdateVersion(context)
      )
    }
    var whatsNewInfo by remember {
      androidx.compose.runtime.mutableStateOf<app.music.backend.sonari.sonari.updater.WhatsNewInfo?>(
        null
      )
    }

    LaunchedEffect(Unit) {
      val currentVersion = BuildConfig.VERSION_NAME
      val lastSeenVersion =
        app.music.backend.sonari.sonari.updater.getLastSeenChangelogVersion(context)

      if (lastSeenVersion.isEmpty() || lastSeenVersion != currentVersion) {
        // Run migrations for fresh installs and updates
        context.dataStore.edit { preferences ->
          preferences[app.music.backend.sonari.constants.ForceOpusKey] = true
          preferences[app.music.backend.sonari.constants.EnableCronetKey] = true
        }
      }

      if (lastSeenVersion.isEmpty()) {
        // Fresh install, not an update — nothing "new" to show, so mark this
        // version seen right away rather than waiting on a dialog dismissal.
        app.music.backend.sonari.sonari.updater.saveLastSeenChangelogVersion(context, currentVersion)
      } else if (lastSeenVersion != currentVersion) {
        // Only mark the version seen once its changelog is actually shown (see
        // onDismiss below) — if the fetch fails here, retry on the next launch
        // instead of losing that version's release notes forever.
        whatsNewInfo = app.music.backend.sonari.sonari.updater.fetchChangelogForVersion(currentVersion)
      }
    }

    LaunchedEffect(Unit) {
      val prefs = context.dataStore.data.first()

      if (getAutoUpdateCheckSetting(context)) {

        delay(2000L)
        checkForUpdate(
          context = context,
          onSuccess = { latestVersion, isAvailable, _, _, _, _, _, _ ->
            Log.d(
              "UpdateCheck",
              "Startup check: latest=$latestVersion, current=${BuildConfig.VERSION_NAME}, available=$isAvailable"
            )
            saveUpdateAvailableState(context, isAvailable)
            app.music.backend.sonari.sonari.updater.saveLatestKnownVersion(context, latestVersion)

            if (isAvailable) {
              availableUpdateVersion = latestVersion

              if (getUpdateNotificationsSetting(context)) {
                UpdateNotificationHelper.showUpdateNotification(context, latestVersion)
              }
            }
          },
          onError = { Log.e("UpdateCheck", "Startup check failed") }
        )
      }
    }

    val HasShownBirthdayNotificationKey = remember {
      androidx.datastore.preferences.core.booleanPreferencesKey("has_shown_birthday_notification")
    }
    LaunchedEffect(Unit) {
      val today = java.time.LocalDate.now()
      if (today.month == java.time.Month.OCTOBER && today.dayOfMonth == 14) {
        val prefs = context.dataStore.data.first()
        val hasShown = prefs[HasShownBirthdayNotificationKey] ?: false
        if (!hasShown) {
          if (
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
              androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
              ) == android.content.pm.PackageManager.PERMISSION_GRANTED
          ) {

            val channelId = "birthday_channel"
            val notificationManager =
              context.getSystemService(android.app.NotificationManager::class.java)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
              val channel =
                android.app.NotificationChannel(
                  channelId,
                  "Birthday",
                  android.app.NotificationManager.IMPORTANCE_HIGH
                )
              notificationManager.createNotificationChannel(channel)
            }

            val bitmap =
              android.graphics.BitmapFactory.decodeResource(
                context.resources,
                R.drawable.birthday_image
              )

            val notification =
              androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.sonarinotification) // Use correct transparent sonari icon
                .setContentTitle("Happy Birthday Aditya!")
                .setContentText("Today is the developer's birthday!")
                .setLargeIcon(bitmap)
                .setStyle(
                  androidx.core.app.NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .bigLargeIcon(null as android.graphics.Bitmap?)
                )
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(1014, notification)

            context.dataStore.edit { preferences ->
              preferences[HasShownBirthdayNotificationKey] = true
            }
          }
        }
      }
    }

    LaunchedEffect(enableHighRefreshRate) {
      val window = this@MainActivity.window
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val layoutParams = window.attributes
        if (enableHighRefreshRate) {
          layoutParams.preferredDisplayModeId = 0
        } else {
          val modes = window.windowManager.defaultDisplay.supportedModes
          val mode60 =
            modes.firstOrNull { kotlin.math.abs(it.refreshRate - 60f) < 1f }
              ?: modes.minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }

          if (mode60 != null) {
            layoutParams.preferredDisplayModeId = mode60.modeId
          }
        }
        window.attributes = layoutParams
      } else {
        val params = window.attributes
        if (enableHighRefreshRate) {
          params.preferredRefreshRate = 0f
        } else {
          params.preferredRefreshRate = 60f
        }
        window.attributes = params
      }
    }

    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme =
      remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
      }

    LaunchedEffect(useDarkTheme) { setSystemBarAppearance(useDarkTheme) }

    val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
    val pureBlack = remember(pureBlackEnabled, useDarkTheme) { pureBlackEnabled && useDarkTheme }
    val isMonochrome by rememberPreference(IsMonochromeThemeKey, defaultValue = false)

    val (selectedThemeColorInt) =
      rememberPreference(SelectedThemeColorKey, defaultValue = DefaultThemeColor.toArgb())
    val selectedThemeColor = Color(selectedThemeColorInt)

    var themeColor by
      rememberSaveable(stateSaver = ColorSaver) { mutableStateOf(selectedThemeColor) }
    var appGradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(selectedThemeColor) {
      if (!enableDynamicTheme) {
        themeColor = selectedThemeColor
        appGradientColors = emptyList()
      }
    }

    LaunchedEffect(playerConnection, enableDynamicTheme, selectedThemeColor) {
      val playerConnection = playerConnection
      if (!enableDynamicTheme || playerConnection == null) {
        themeColor = selectedThemeColor
        appGradientColors = emptyList()
        return@LaunchedEffect
      }

      playerConnection.service.currentMediaMetadata.collectLatest { song ->
        if (song?.thumbnailUrl != null) {
          withContext(Dispatchers.IO) {
            try {
              val result =
                imageLoader.execute(
                  ImageRequest.Builder(this@MainActivity)
                    .data(song.thumbnailUrl)
                    .size(100, 100)
                    .allowHardware(false)
                    .memoryCacheKey("gradient_${song.id}")
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false)
                    .build()
                )
              val bitmap = result.image?.toBitmap()
              if (bitmap != null) {
                // Use the same extractor as the player so the app-wide dynamic theme
                // and the player gradient are seeded from the exact same artwork color.
                val palette =
                  withContext(Dispatchers.Default) {
                    Palette.from(bitmap).maximumColorCount(8).resizeBitmapArea(100 * 100).generate()
                  }
                val extractedColors =
                  PlayerColorExtractor.extractGradientColors(
                    palette = palette,
                    fallbackColor = selectedThemeColor.toArgb(),
                  )
                themeColor = extractedColors.first()
                appGradientColors = extractedColors
              } else {
                themeColor = selectedThemeColor
                appGradientColors = emptyList()
              }
            } catch (e: Exception) {
              themeColor = selectedThemeColor
              appGradientColors = emptyList()
            }
          }
        } else {
          themeColor = selectedThemeColor
          appGradientColors = emptyList()
        }
      }
    }

    val appGradientTop by
      animateColorAsState(
        targetValue =
          appGradientColors.getOrNull(0)?.copy(alpha = 0.55f) ?: Color.Transparent,
        animationSpec = tween(durationMillis = 900),
        label = "appGradientTop",
      )
    val appGradientBottom by
      animateColorAsState(
        targetValue =
          appGradientColors.getOrNull(1)?.copy(alpha = 0.35f) ?: Color.Transparent,
        animationSpec = tween(durationMillis = 900),
        label = "appGradientBottom",
      )

    sonariTheme(
      darkTheme = useDarkTheme,
      pureBlack = pureBlack,
      isMonochrome = isMonochrome,
      themeColor = themeColor,
    ) {
      val hapticHelper = rememberHapticHelper()

      whatsNewInfo?.let { info ->
        app.music.backend.sonari.sonari.updater.WhatsNewDialog(
          version = BuildConfig.VERSION_NAME,
          info = info,
          onDismiss = {
            app.music.backend.sonari.sonari.updater.saveLastSeenChangelogVersion(
              context,
              BuildConfig.VERSION_NAME,
            )
            whatsNewInfo = null
          }
        )
      }
      BoxWithConstraints(
        modifier =
          Modifier.fillMaxSize()
            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
            .pointerInput(hapticHelper) {
              if (hapticHelper.masterEnabled && hapticHelper.clickEnabled) {
                awaitPointerEventScope {
                  while (true) {
                    val event =
                      awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (event.changes.any { it.changedToDown() }) {
                      hapticHelper.performHaptic(HapticType.CLICK)
                    }
                  }
                }
              }
            }
      ) {
        val focusManager = LocalFocusManager.current
        val density = LocalDensity.current
        val configuration = LocalWindowInfo.current
        val cutoutInsets = WindowInsets.displayCutout
        val windowsInsets = WindowInsets.systemBars
        val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
        val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

        val navController = rememberNavController()
        val blockedArtists by
          dataStore.data
            .map { it[BlockedArtistsKey] ?: emptySet() }
            .collectAsState(initial = emptySet())
        LaunchedEffect(blockedArtists) {
          app.music.backend.sonari.innertube.YouTube.blockedArtists = blockedArtists
        }
        val homeViewModel: HomeViewModel = hiltViewModel()
        val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val onRailSearchLongClick: () -> Unit =
          remember(navController) {
            {} // User requested to disable opening the recognize music page on long press
          }

        val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }

        val (listenTogetherInTopBar) =
          rememberPreference(ListenTogetherInTopBarKey, defaultValue = true)
        val navigationItems =
          remember(listenTogetherInTopBar) {
            if (listenTogetherInTopBar) {
              Screens.MainScreens.filter { it != Screens.ListenTogether }
            } else {
              Screens.MainScreens
            }
          }
        val (useNewMiniPlayerDesign) =
          rememberPreference(UseNewMiniPlayerDesignKey, defaultValue = true)
        val defaultOpenTab = remember {
          dataStore[DefaultOpenTabKey].toEnum(defaultValue = NavigationTab.LIBRARY)
        }
        val tabOpenedFromShortcut = remember {
          when (intent?.action) {
            ACTION_SEARCH -> NavigationTab.LIBRARY
            ACTION_LIBRARY -> NavigationTab.SEARCH
            else -> null
          }
        }

        val topLevelScreens = remember {
          listOf(
            Screens.Home.route,
            Screens.Library.route,
            Screens.ListenTogether.route,
            "settings",
          )
        }

        val (query, onQueryChange) =
          rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

        val onSearch: (String) -> Unit = remember {
          { searchQuery ->
            if (searchQuery.isNotEmpty()) {
              navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")

              if (dataStore[PauseSearchHistoryKey] != true) {
                lifecycleScope.launch(Dispatchers.IO) {
                  database.query { insert(SearchHistory(query = searchQuery)) }
                }
              }
            }
          }
        }

        val currentRoute by remember { derivedStateOf { navBackStackEntry?.destination?.route } }

        val inSearchScreen by remember {
          derivedStateOf { currentRoute?.startsWith("search/") == true }
        }
        val navigationItemRoutes =
          remember(navigationItems) { navigationItems.map { it.route }.toSet() }

        val shouldShowNavigationBar =
          remember(currentRoute, navigationItemRoutes) {
            currentRoute == null ||
              navigationItemRoutes.contains(currentRoute) ||
              currentRoute!!.startsWith("search/") ||
              currentRoute!!.startsWith("album/") ||
              currentRoute!!.startsWith("online_playlist/") ||
              currentRoute!!.startsWith("local_playlist/") ||
              currentRoute!!.startsWith("spotify_playlist/") ||
              currentRoute!!.startsWith("artist/")
          }

        val isLandscape = configuration.containerDpSize.width > configuration.containerDpSize.height

        val showRail =
          isLandscape && !inSearchScreen && currentRoute != "ambient_mode" && currentRoute != "music_flow" && currentRoute != "wrapped" && currentRoute != "podcast_video"

        val navPadding =
          if (shouldShowNavigationBar && !showRail) {
            NavigationBarHeight + FloatingToolbarBottomPadding
          } else {
            0.dp
          }

        val navigationBarHeight by
          animateDpAsState(
            targetValue = if (shouldShowNavigationBar && !showRail) NavigationBarHeight else 0.dp,
            animationSpec = NavigationBarAnimationSpec,
            label = "navBarHeight",
          )

        val (useFloatingNavBar) = rememberPreference(UseFloatingNavBarKey, defaultValue = true)
        val floatingNavBarScrollConnection = rememberFloatingTabBarScrollConnection()

        val playerBottomSheetState =
          rememberBottomSheetState(
            dismissedBound = 0.dp,
            collapsedBound =
              if (useFloatingNavBar && !showRail && shouldShowNavigationBar) {
                0.dp
              } else {
                bottomInset +
                  (if (!showRail && shouldShowNavigationBar) navPadding else 0.dp) +
                  (if (useNewMiniPlayerDesign) MiniPlayerBottomSpacing else 0.dp) +
                  MiniPlayerHeight
              },
            expandedBound = maxHeight,
          )

        var expandQueueRequested by remember { mutableStateOf(false) }
        var showPlayerMenuRequested by remember { mutableStateOf(false) }
        var showAudioDeviceBottomSheet by remember { mutableStateOf(false) }
        var showLyricsRequested by remember { mutableStateOf(false) }

        val handleWidgetAction: (Intent) -> Unit = remember {
          { targetIntent ->
            when (targetIntent.action) {
              ACTION_NOW_PLAYING -> {
                playerBottomSheetState.expandSoft()
              }
              ACTION_QUEUE -> {
                playerBottomSheetState.expandSoft()
                expandQueueRequested = true
              }
              ACTION_SONG_OPTIONS -> {
                playerBottomSheetState.expandSoft()
                showPlayerMenuRequested = true
              }
              ACTION_OUTPUT_SWITCHER -> {
                var launchedSystemPanel = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                  try {
                    val panelIntent =
                      Intent(ACTION_MEDIA_OUTPUT).apply {
                        putExtra(EXTRA_MEDIA_OUTPUT_PACKAGE_NAME, packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                      }
                    if (packageManager.resolveActivity(panelIntent, 0) != null) {
                      startActivity(panelIntent)
                      launchedSystemPanel = true
                    }
                  } catch (_: Exception) {}
                }
                if (!launchedSystemPanel) {
                  showAudioDeviceBottomSheet = true
                }
              }
              ACTION_LYRICS -> {
                playerBottomSheetState.expandSoft()
                showLyricsRequested = true
              }
              ACTION_UPDATE -> {
                navController.navigate("update") { launchSingleTop = true }
              }
            }
          }
        }

        val onShuffleClick: (() -> Unit)? =
          remember(playerConnection, playerBottomSheetState) {
            playerConnection?.let { connection ->
              {
                if (playerBottomSheetState.isExpanded) {
                  playerBottomSheetState.collapseSoft()
                }
                val enabled = !connection.player.shuffleModeEnabled
                connection.player.shuffleModeEnabled = enabled
                ActionBanner.show(
                  text = if (enabled) "Shuffled the queue" else "Shuffle off",
                  iconRes = R.drawable.shuffle,
                )
              }
            }
          }
        val shuffleEnabled by
          playerConnection?.shuffleModeEnabled?.collectAsState()
            ?: remember { mutableStateOf(false) }

        val onMusicRecognitionClick: (() -> Unit) =
          remember(navController, playerBottomSheetState) {
            {
              if (playerBottomSheetState.isExpanded) {
                playerBottomSheetState.collapseSoft()
              }
              navController.navigate("recognition") { launchSingleTop = true }
            }
          }

        val mediaMetadata by
          playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) }
        val hasDockedPlayerAccessory =
          useFloatingNavBar && mediaMetadata != null && !showRail && shouldShowNavigationBar

        val playerAwareWindowInsets =
          remember(
            bottomInset,
            shouldShowNavigationBar,
            playerBottomSheetState.isDismissed,
            showRail,
            hasDockedPlayerAccessory,
          ) {
            var bottom = bottomInset
            if (shouldShowNavigationBar && !showRail) {
              bottom += NavigationBarHeight
            }
            if (!playerBottomSheetState.isDismissed || hasDockedPlayerAccessory)
              bottom += MiniPlayerHeight
            windowsInsets
              .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
              .add(WindowInsets(top = AppBarHeight, bottom = bottom))
          }
        appBarScrollBehavior(
          canScroll = {
            !inSearchScreen &&
              (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
          }
        )

        val topAppBarScrollBehavior =
          appBarScrollBehavior(
            canScroll = {
              !inSearchScreen &&
                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
            },
          )

        LaunchedEffect(navBackStackEntry) {
          if (inSearchScreen) {
            val searchQuery =
              withContext(Dispatchers.IO) {
                val rawQuery = navBackStackEntry?.arguments?.getString("query")!!
                try {
                  URLDecoder.decode(rawQuery, "UTF-8")
                } catch (e: IllegalArgumentException) {
                  rawQuery
                }
              }
            onQueryChange(TextFieldValue(searchQuery, TextRange(searchQuery.length)))
          } else if (
            navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }
          ) {
            onQueryChange(TextFieldValue())
          }

          if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
            if (navigationItems.fastAny { it.route == previousTab }) {
              topAppBarScrollBehavior.state.resetHeightOffset()
            }
          }

          topAppBarScrollBehavior.state.resetHeightOffset()

          navController.currentBackStackEntry?.destination?.route?.let { setPreviousTab(it) }
        }

        LaunchedEffect(mediaMetadata) {
          if (mediaMetadata == null) {
            if (!playerBottomSheetState.isDismissed) {
              playerBottomSheetState.dismiss()
            }
          } else {
            if (playerBottomSheetState.isDismissed) {
              playerBottomSheetState.collapseSoft()
            }
          }
        }

        var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(navBackStackEntry, listenTogetherInTopBar) {
          val currentRoute = navBackStackEntry?.destination?.route
          val isListenTogetherScreen =
            currentRoute == Screens.ListenTogether.route ||
              currentRoute == "listen_together_from_topbar"
          shouldShowTopBar =
            currentRoute in topLevelScreens &&
              currentRoute != "settings" &&
              !(isListenTogetherScreen && listenTogetherInTopBar)
        }

        val coroutineScope = rememberCoroutineScope()
        var sharedSong: SongItem? by remember { mutableStateOf(null) }
        val snackbarHostState = remember { SnackbarHostState() }
        var showSettingDialoge by remember { mutableStateOf(false) }

        var showWelcomeDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
          val activeIntent = pendingIntent ?: intent
          if (activeIntent != null) {
            handleWidgetAction(activeIntent)
          }
          if (pendingIntent != null) {
            handleDeepLinkIntent(pendingIntent!!, navController)
            handleRecognitionIntent(pendingIntent!!, navController)
            handleAssistantSearchIntent(pendingIntent!!, navController)
            pendingIntent = null
          } else if (
            intent != null &&
              (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND)
          ) {
            handleDeepLinkIntent(intent, navController)
          } else if (intent != null && intent.action == ACTION_RECOGNITION) {
            handleRecognitionIntent(intent, navController)
          } else if (
            intent != null &&
              intent.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
          ) {
            handleAssistantSearchIntent(intent, navController)
          }
        }

        DisposableEffect(Unit) {
          val listener =
            Consumer<Intent> { newIntent ->
              handleWidgetAction(newIntent)
              if (newIntent.action == Intent.ACTION_VIEW || newIntent.action == Intent.ACTION_SEND) {
                handleDeepLinkIntent(newIntent, navController)
              } else if (newIntent.action == ACTION_RECOGNITION) {
                handleRecognitionIntent(newIntent, navController)
              } else if (
                newIntent.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
              ) {
                handleAssistantSearchIntent(newIntent, navController)
              }
            }

          addOnNewIntentListener(listener)
          onDispose { removeOnNewIntentListener(listener) }
        }

        val currentTitle =
          when (navBackStackEntry?.destination?.route) {
            Screens.Home.route -> "Sonari"
            Screens.Search.route -> stringResource(R.string.search)
            Screens.Library.route -> stringResource(R.string.filter_library)
            Screens.ListenTogether.route -> stringResource(R.string.together)
            else -> ""
          }

        val baseBg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer

        val (liquidGlassGlobalEnabled) =
          rememberPreference(LiquidGlassGlobalEnabledKey, defaultValue = true)
        val (liquidGlassVibrancy) = rememberPreference(LiquidGlassVibrancyKey, defaultValue = 1f)
        val (liquidGlassBlurRadius) =
          rememberPreference(LiquidGlassBlurRadiusKey, defaultValue = 8f)
        val (liquidGlassLensHeight) =
          rememberPreference(LiquidGlassLensHeightKey, defaultValue = 0.5f)
        val (liquidGlassLensAmount) =
          rememberPreference(LiquidGlassLensAmountKey, defaultValue = 0.5f)
        val (liquidGlassChromaticAberration) =
          rememberPreference(LiquidGlassChromaticAberrationKey, defaultValue = true)
        val (liquidGlassDepthEffect) =
          rememberPreference(LiquidGlassDepthEffectKey, defaultValue = true)
        val (liquidGlassSurfaceTintColorInt) =
          rememberPreference(LiquidGlassSurfaceTintColorKey, defaultValue = 0)
        val (liquidGlassSurfaceOpacity) =
          rememberPreference(LiquidGlassSurfaceOpacityKey, defaultValue = 0.4f)
        val (liquidGlassTextColorInt) =
          rememberPreference(LiquidGlassTextColorKey, defaultValue = 0)
        val (liquidGlassPlayerEnabled) =
          rememberPreference(LiquidGlassPlayerEnabledKey, defaultValue = true)
        val (liquidGlassMiniPlayerEnabled) =
          rememberPreference(LiquidGlassMiniPlayerEnabledKey, defaultValue = true)
        val (liquidGlassNavBarEnabled) =
          rememberPreference(LiquidGlassNavBarEnabledKey, defaultValue = true)

        val glassEffectConfig =
          remember(
            liquidGlassGlobalEnabled,
            useFloatingNavBar,
            liquidGlassVibrancy,
            liquidGlassBlurRadius,
            liquidGlassLensHeight,
            liquidGlassLensAmount,
            liquidGlassChromaticAberration,
            liquidGlassDepthEffect,
            liquidGlassSurfaceTintColorInt,
            liquidGlassSurfaceOpacity,
            liquidGlassTextColorInt,
            liquidGlassPlayerEnabled,
            liquidGlassMiniPlayerEnabled,
            liquidGlassNavBarEnabled,
          ) {
            GlassEffectConfig(
              globalEnabled = liquidGlassGlobalEnabled,
              vibrancy = liquidGlassVibrancy,
              blurRadius = liquidGlassBlurRadius,
              lensHeight = liquidGlassLensHeight,
              lensAmount = liquidGlassLensAmount,
              chromaticAberration = liquidGlassChromaticAberration,
              depthEffect = liquidGlassDepthEffect,
              surfaceTintColor =
                if (liquidGlassSurfaceTintColorInt == 0) Color.Unspecified
                else Color(liquidGlassSurfaceTintColorInt),
              surfaceOpacity = liquidGlassSurfaceOpacity,
              textColor =
                if (liquidGlassTextColorInt == 0) Color.Unspecified
                else Color(liquidGlassTextColorInt),
              playerEnabled = liquidGlassPlayerEnabled,
              miniPlayerEnabled = liquidGlassMiniPlayerEnabled,
              navBarEnabled = liquidGlassNavBarEnabled,
            )
          }

        val appBackdrop = rememberLayerBackdrop {
          drawRect(baseBg)
          drawContent()
        }

        // The liquid glass source. The screen content is tagged liquefiable below and the chrome
        // (nav bar, mini player) samples it through the liquid shaders; when the effect is off, or
        // the platform is too old for it, nothing is tagged and the chrome keeps its own pipeline.
        val liquidState = rememberLiquidState()
        // Sampling the screen into a second layer costs real GPU time, so a low-RAM phone or a
        // phone in power saving keeps the cheaper pipeline it already had instead.
        val liquidSampling =
          glassEffectConfig.globalEnabled && isGlassSupported() &&
            !remember { isGlassLowEnd(this@MainActivity) }

        val ringtoneViewModel: RingtoneViewModel = viewModel()
        val ringtoneUiState by ringtoneViewModel.uiState.collectAsState()

        val customHapticFeedback =
          remember(hapticHelper) {
            object : androidx.compose.ui.hapticfeedback.HapticFeedback {
              override fun performHapticFeedback(
                hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType
              ) {
                when (hapticFeedbackType) {
                  androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress ->
                    hapticHelper.performHaptic(HapticType.LONG_PRESS)
                  androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove -> {
                    // Do not invoke hapticHelper for TextHandleMove
                  }
                  else -> hapticHelper.performHaptic(HapticType.CLICK)
                }
              }
            }
          }

        val expandPlayer = remember(playerBottomSheetState) { { playerBottomSheetState.expandSoft() } }
        CompositionLocalProvider(
          LocalHapticFeedback provides customHapticFeedback,
          LocalGlassEffectConfig provides glassEffectConfig,
          LocalAppBackdrop provides appBackdrop,
          LocalLiquidState provides if (liquidSampling) liquidState else null,
          LocalRingtoneViewModel provides ringtoneViewModel,
          LocalDatabase provides database,
          LocalContentColor provides
            if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
          LocalPlayerConnection provides playerConnection,
          LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
          LocalDownloadUtil provides downloadUtil,
          LocalShimmerTheme provides getShimmerTheme(),
          LocalSyncUtils provides syncUtils,
          LocalListenTogetherManager provides listenTogetherManager,
          LocalExpandPlayer provides expandPlayer,
        ) {
          val launchIntroState = rememberLaunchIntroState(introPlays)
          val useAppGradient =
            currentRoute == null ||
              currentRoute == "home" ||
              currentRoute == "library" ||
              currentRoute == "search_input"

          // Sonari launch intro - Mochy-style boot flow
          CompositionLocalProvider(LocalLaunchIntro provides launchIntroState) {
            Box(modifier = Modifier.fillMaxSize()) {
              Scaffold(
                  snackbarHost = { SnackbarHost(snackbarHostState) },
                  containerColor = Color.Transparent,
              topBar = {
                AnimatedVisibility(
                  visible = shouldShowTopBar,
                  enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                  exit = fadeOut(animationSpec = tween(durationMillis = 200))
                ) {
                  Row {
                    TopAppBar(
                      title = {
                        val isLibrary = currentRoute == "library" || currentRoute?.startsWith("library/") == true
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          if (isLibrary) {
                            Image(
                              painter = painterResource(R.mipmap.ic_launcher_foreground),
                              contentDescription = null,
                              modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .launchMarkTarget()
                            )
                            Spacer(Modifier.width(8.dp))
                          }
                          Text(
                            text = currentTitle,
                            style =
                              MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                              ),
                          )
                        }
                      },
                      actions = {
                        IconButton(onClick = { showSettingDialoge = true }) {
                          val profileRingTransition = rememberInfiniteTransition(label = "TopBarProfileRing")
                          val profileRingAngle by profileRingTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                              animation = tween(12000, easing = LinearEasing),
                              repeatMode = RepeatMode.Restart
                            ),
                            label = "profileRingAngle"
                          )
                          val profileRingAlpha by profileRingTransition.animateFloat(
                            initialValue = 0.65f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                              animation = tween(2400, easing = FastOutSlowInEasing),
                              repeatMode = RepeatMode.Reverse
                            ),
                            label = "profileRingAlpha"
                          )
                          Box(
                            modifier = Modifier.size(34.dp),
                            contentAlignment = Alignment.Center
                          ) {
                            // Light Red Story / Wrapped Ring with slight gentle animation
                            Box(
                              modifier = Modifier
                                .fillMaxSize()
                                .rotate(profileRingAngle)
                                .border(
                                  width = 2.dp,
                                  brush = Brush.sweepGradient(
                                    listOf(
                                      Color(0xFFFF5252).copy(alpha = profileRingAlpha),
                                      Color(0xFFFF7A8A).copy(alpha = profileRingAlpha * 0.85f),
                                      Color(0xFFFF8E9E).copy(alpha = profileRingAlpha),
                                      Color(0xFFFF6B6B).copy(alpha = profileRingAlpha * 0.9f),
                                      Color(0xFFFF5252).copy(alpha = profileRingAlpha)
                                    )
                                  ),
                                  shape = CircleShape
                                )
                            )
                            if (accountImageUrl != null) {
                              AsyncImage(
                                model = accountImageUrl,
                                contentDescription = stringResource(R.string.account),
                                modifier = Modifier.size(24.dp).clip(CircleShape)
                              )
                            } else {
                              Icon(
                                painter = painterResource(R.drawable.settings),
                                contentDescription = stringResource(R.string.account),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                              )
                            }
                          }
                        }
                      },
                      scrollBehavior = topAppBarScrollBehavior,
                      colors =
                        TopAppBarDefaults.topAppBarColors(
                          containerColor = Color.Transparent,
                          scrolledContainerColor =
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                          titleContentColor = MaterialTheme.colorScheme.onSurface,
                          actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                          navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                      windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top),
                      modifier =
                        Modifier.windowInsetsPadding(
                          if (showRail) {
                            WindowInsets(left = NavigationBarHeight)
                              .add(cutoutInsets.only(WindowInsetsSides.Start))
                          } else {
                            cutoutInsets.only(WindowInsetsSides.Start + WindowInsetsSides.End)
                          }
                        )
                    )
                  }
                }
              },
              bottomBar = {
                val onNavItemClick: (Screens, Boolean) -> Unit =
                  remember(
                    navController,
                    coroutineScope,
                    topAppBarScrollBehavior,
                    playerBottomSheetState
                  ) {
                    { screen: Screens, isSelected: Boolean ->
                      if (playerBottomSheetState.isExpanded) {
                        playerBottomSheetState.collapseSoft()
                      }

                      if (isSelected) {
                        navController.currentBackStackEntry
                          ?.savedStateHandle
                          ?.set("scrollToTop", true)
                        coroutineScope.launch { topAppBarScrollBehavior.state.resetHeightOffset() }
                      } else {
                        navController.navigate(screen.route) {
                          popUpTo(navController.graph.startDestinationId) { saveState = true }
                          launchSingleTop = true
                          restoreState = true
                        }
                      }
                    }
                  }

                if (
                  !showRail &&
                    currentRoute != "update" &&
                    currentRoute != "listen_together/chat" &&
                    currentRoute != "ambient_mode" &&
                    currentRoute != "music_flow" &&
                    currentRoute != "podcast_video" &&
                    currentRoute != "uptime" &&
                    currentRoute != "wrapped" &&
                    currentRoute?.startsWith("settings") != true
                ) {
                  Box {
                    BottomSheetPlayer(
                      state = playerBottomSheetState,
                      navController = navController,
                      pureBlack = pureBlack,
                      expandQueueRequested = expandQueueRequested,
                      onQueueExpanded = { expandQueueRequested = false },
                      showPlayerMenuRequested = showPlayerMenuRequested,
                      onPlayerMenuShown = { showPlayerMenuRequested = false },
                      showLyricsRequested = showLyricsRequested,
                      onLyricsShown = { showLyricsRequested = false },
                    )

                    val navSlideDistance =
                      bottomInset + FloatingToolbarBottomPadding + NavigationBarHeight

                    val navOffsetY =
                      if (navigationBarHeight == 0.dp) {
                        navSlideDistance
                      } else {
                        val slideOffset =
                          navSlideDistance * playerBottomSheetState.progress.coerceIn(0f, 1f)
                        val hideOffset =
                          navSlideDistance *
                            (1 -
                              navigationBarHeight.coerceAtMost(NavigationBarHeight) /
                                NavigationBarHeight)
                        slideOffset + hideOffset
                      }

                    if (useFloatingNavBar) {
                      AppFloatingNavBar(
                        navigationItems = navigationItems,
                        currentRoute = currentRoute,
                        onItemClick = onNavItemClick,
                        scrollConnection = floatingNavBarScrollConnection,
                        pureBlack = pureBlack,
                        showPlayerAccessory = hasDockedPlayerAccessory,
                        onAccessoryClick = { playerBottomSheetState.expandSoft() },
                        onSearchLongClick = onRailSearchLongClick,
                        modifier =
                          Modifier.align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = bottomInset + 8.dp)
                            .graphicsLayer {
                              val hiddenOffset = size.height + (bottomInset + 8.dp).toPx()
                              val navBarHeightPx = navigationBarHeight.toPx()
                              translationY =
                                if (navBarHeightPx == 0f) {
                                  hiddenOffset
                                } else {
                                  val progress = playerBottomSheetState.progress.coerceIn(0f, 1f)
                                  val slideOffset = hiddenOffset * progress
                                  val hideOffset =
                                    hiddenOffset * (1 - navBarHeightPx / NavigationBarHeight.toPx())
                                  slideOffset + hideOffset
                                }
                            }
                      )
                    } else {
                      Box(
                        modifier =
                          Modifier.align(Alignment.BottomCenter)
                            .height(navSlideDistance)
                            .offset(y = navOffsetY),
                      ) {
                        FloatingNavigationToolbar(
                          items = navigationItems,
                          pureBlack = pureBlack,
                          onShuffleClick = onShuffleClick,
                          shuffleEnabled = shuffleEnabled,
                          shuffleIconRes = R.drawable.shuffle,
                          shuffleContentDescription = stringResource(R.string.shuffle),
                          onSearchLongClick = onRailSearchLongClick,
                          isSelected = { screen ->
                            currentRoute == screen.route ||
                              currentRoute?.startsWith("${screen.route}/") == true
                          },
                          onItemClick = onNavItemClick,
                          modifier =
                            Modifier.align(Alignment.BottomCenter)
                              .padding(
                                start = FloatingToolbarHorizontalPadding,
                                end = FloatingToolbarHorizontalPadding,
                                bottom = bottomInset + FloatingToolbarBottomPadding,
                              )
                              .height(NavigationBarHeight)
                        )
                      }

                      Box(
                        modifier =
                          Modifier.fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .height(bottomInsetDp)
                            .graphicsLayer {
                              val progress = playerBottomSheetState.progress
                              alpha =
                                if (
                                  progress > 0f ||
                                    useFloatingNavBar ||
                                    !shouldShowNavigationBar ||
                                    (useNewMiniPlayerDesign && !shouldShowNavigationBar)
                                )
                                  0f
                                else 1f
                            }
                            .background(baseBg)
                      )
                    }
                  }
                } else {
                  if (
                    currentRoute != "update" &&
                      currentRoute != "listen_together/chat" &&
                      currentRoute != "ambient_mode" &&
                      currentRoute != "music_flow" &&
                      currentRoute != "podcast_video" &&
                      currentRoute != "uptime" &&
                      currentRoute != "wrapped" &&
                      currentRoute?.startsWith("settings") != true
                  ) {
                    BottomSheetPlayer(
                      state = playerBottomSheetState,
                      navController = navController,
                      pureBlack = pureBlack,
                      expandQueueRequested = expandQueueRequested,
                      onQueueExpanded = { expandQueueRequested = false },
                      showPlayerMenuRequested = showPlayerMenuRequested,
                      onPlayerMenuShown = { showPlayerMenuRequested = false },
                      showLyricsRequested = showLyricsRequested,
                      onLyricsShown = { showLyricsRequested = false },
                    )
                  }

                  Box(
                    modifier =
                      Modifier.fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(bottomInsetDp)
                        .graphicsLayer {
                          val progress = playerBottomSheetState.progress
                          alpha =
                            if (
                              progress > 0f ||
                                useFloatingNavBar ||
                                !shouldShowNavigationBar ||
                                (useNewMiniPlayerDesign && !shouldShowNavigationBar)
                            )
                              0f
                            else 1f
                        }
                        .background(baseBg)
                  )
                }
              },
              modifier =
                Modifier.fillMaxSize()
                  .background(MaterialTheme.colorScheme.background)
                  .background(
                    if (useAppGradient) {
                      Brush.verticalGradient(
                        0f to appGradientTop,
                        0.55f to appGradientBottom,
                        1f to Color.Transparent,
                      )
                    } else {
                      Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                  )
                  .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                  .then(
                    if (useFloatingNavBar) {
                      Modifier.nestedScroll(floatingNavBarScrollConnection)
                    } else {
                      Modifier
                    }
                  )
            ) {
              Row(Modifier.fillMaxSize()) {
                val onRailItemClick: (Screens, Boolean) -> Unit =
                  remember(
                    navController,
                    coroutineScope,
                    topAppBarScrollBehavior,
                    playerBottomSheetState
                  ) {
                    { screen: Screens, isSelected: Boolean ->
                      if (playerBottomSheetState.isExpanded) {
                        playerBottomSheetState.collapseSoft()
                      }

                      if (isSelected) {
                        navController.currentBackStackEntry
                          ?.savedStateHandle
                          ?.set("scrollToTop", true)
                        coroutineScope.launch { topAppBarScrollBehavior.state.resetHeightOffset() }
                      } else {
                        navController.navigate(screen.route) {
                          popUpTo(navController.graph.startDestinationId) { saveState = true }
                          launchSingleTop = true
                          restoreState = true
                        }
                      }
                    }
                  }

                if (showRail && currentRoute != "update") {
                  AppNavigationRail(
                    navigationItems = navigationItems,
                    currentRoute = currentRoute,
                    onItemClick = onRailItemClick,
                    pureBlack = pureBlack,
                    onSearchLongClick = onRailSearchLongClick,
                    onShuffleClick = onShuffleClick,
                    shuffleEnabled = shuffleEnabled,
                    shuffleIconRes = R.drawable.shuffle,
                    shuffleContentDescription = stringResource(R.string.shuffle),
                  )
                }
                Box(Modifier.weight(1f)) {
                  NavHost(
                    navController = navController,
                    startDestination =
                      when (tabOpenedFromShortcut ?: defaultOpenTab) {
                        NavigationTab.LIBRARY -> Screens.Library
                        NavigationTab.SEARCH -> Screens.Search
                        else -> Screens.Library
                      }.route,
                    enterTransition = {
                      val currentRouteIndex =
                        navigationItems.indexOfFirst { it.route == targetState.destination.route }
                      val previousRouteIndex =
                        navigationItems.indexOfFirst { it.route == initialState.destination.route }

                      if (currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex)
                        slideInHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                          it / 8
                        } + fadeIn(tween(400, easing = EmphasizedEasing))
                      else
                        slideInHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                          -it / 8
                        } + fadeIn(tween(400, easing = EmphasizedEasing))
                    },
                    exitTransition = {
                      val currentRouteIndex =
                        navigationItems.indexOfFirst { it.route == initialState.destination.route }
                      val targetRouteIndex =
                        navigationItems.indexOfFirst { it.route == targetState.destination.route }

                      if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex)
                        slideOutHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                          -it / 8
                        } + fadeOut(tween(400, easing = EmphasizedEasing))
                      else
                        slideOutHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                          it / 8
                        } + fadeOut(tween(400, easing = EmphasizedEasing))
                    },
                    popEnterTransition = {
                      val currentRouteIndex =
                        navigationItems.indexOfFirst { it.route == targetState.destination.route }
                      val previousRouteIndex =
                        navigationItems.indexOfFirst { it.route == initialState.destination.route }

                      if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex)
                        slideInHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                          it / 8
                        } + fadeIn(tween(400, easing = EmphasizedEasing))
                      else
                        slideInHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                          -it / 8
                        } + fadeIn(tween(400, easing = EmphasizedEasing))
                    },
                    popExitTransition = {
                      val currentRouteIndex =
                        navigationItems.indexOfFirst { it.route == initialState.destination.route }
                      val targetRouteIndex =
                        navigationItems.indexOfFirst { it.route == targetState.destination.route }

                      if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex)
                        slideOutHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                          -it / 8
                        } + fadeOut(tween(400, easing = EmphasizedEasing))
                      else
                        slideOutHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                          it / 8
                        } + fadeOut(tween(400, easing = EmphasizedEasing))
                    },
                    modifier =
                      Modifier.layerBackdrop(appBackdrop)
                        .then(if (liquidSampling) Modifier.liquefiable(liquidState) else Modifier)
                        .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                  ) {
                    navigationBuilder(
                      navController = navController,
                      scrollBehavior = topAppBarScrollBehavior,
                      activity = this@MainActivity,
                      snackbarHostState = snackbarHostState
                    )
                  }
                }
              }
            }

          }

          // Launch intro overlay (Mochy-style boot flow)
          LaunchIntroOverlay(
              state = launchIntroState,
              essentialsReady = true,
          )

          // Mark home/library as composed for launch intro
          ReportLaunchHomeComposed()

          // One short line about what the player just did, such as a queue being shuffled.
          ActionBannerHost(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(),
            bottomPadding = MiniPlayerHeight + 16.dp,
          )

          BottomSheetMenu(
            state = LocalMenuState.current,
            modifier = Modifier.align(Alignment.BottomCenter)
          )
            }

          BottomSheetPage(
            state = LocalBottomSheetPageState.current,
            modifier = Modifier.align(Alignment.BottomCenter)
          )

          if (showAudioDeviceBottomSheet) {
            AudioDeviceBottomSheet(
              onDismiss = { showAudioDeviceBottomSheet = false }
            )
          }

          sharedSong?.let { song ->
            playerConnection?.let {
              Dialog(
                onDismissRequest = { sharedSong = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
              ) {
                Surface(
                  modifier = Modifier.padding(24.dp),
                  shape = RoundedCornerShape(16.dp),
                  color = AlertDialogDefaults.containerColor,
                  tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                  ) {
                    YouTubeSongMenu(
                      song = song,
                      navController = navController,
                      onDismiss = { sharedSong = null },
                    )
                  }
                }
              }
            }
          }

          RingtoneTrimmerDialog(
            isVisible = ringtoneUiState.showTrimmer,
            songId = ringtoneUiState.targetSongId,
            songTitle = ringtoneUiState.targetSongTitle,
            duration = ringtoneUiState.targetSongDuration,
            onDismiss = { ringtoneViewModel.hideTrimmer() },
            onResolveStreamUrl = { ringtoneViewModel.getStreamUrl(this@MainActivity, it) },
            onConfirm = { start, end ->
              ringtoneViewModel.setAsRingtone(this@MainActivity, start, end)
            }
          )

          if (ringtoneUiState.showProgress) {
            RingtoneProgressDialog(
              isVisible = ringtoneUiState.showProgress,
              progress = ringtoneUiState.progress,
              statusMessage = ringtoneUiState.statusMessage,
              isComplete = ringtoneUiState.isComplete,
              isSuccess = ringtoneUiState.isSuccess,
              onDismiss = { ringtoneViewModel.dismissProgress() },
              onOpenSettings = { ringtoneViewModel.openRingtoneSettings(this@MainActivity) }
            )
          }

          // A newer release waiting on GitHub: the prompt from the reference, once per release.
          if (
            availableUpdateVersion.isNotEmpty() &&
              availableUpdateVersion != dismissedUpdateVersion &&
              currentRoute != "update"
          ) {
            app.music.backend.sonari.sonari.component.UpdateAvailableDialog(
              currentVersion = BuildConfig.VERSION_NAME,
              latestVersion = availableUpdateVersion,
              onUpdate = {
                app.music.backend.sonari.sonari.updater.saveDismissedUpdateVersion(
                  context,
                  availableUpdateVersion,
                )
                dismissedUpdateVersion = availableUpdateVersion
                if (playerBottomSheetState.isExpanded) playerBottomSheetState.collapseSoft()
                navController.navigate("update") { launchSingleTop = true }
              },
              onDismiss = {
                app.music.backend.sonari.sonari.updater.saveDismissedUpdateVersion(
                  context,
                  availableUpdateVersion,
                )
                dismissedUpdateVersion = availableUpdateVersion
              },
            )
          }

          if (showSettingDialoge) {
            SettingDialoge(
              onDismissRequest = { showSettingDialoge = false },
              onNavigate = { route ->
                showSettingDialoge = false
                navController.navigate(route)
              },
              homeViewModel = homeViewModel
            )
          }

          if (showWelcomeDialog) {
            WelcomeDialog(
              onDismissRequest = {
                showWelcomeDialog = false
                coroutineScope.launch {
                  context.dataStore.edit {
                    it[app.music.backend.sonari.constants.LastOpenedVersionCodeKey] =
                      BuildConfig.VERSION_CODE
                  }
                }
              }
            )
          }

          var showPartyBomb by remember {
            val today = java.time.LocalDate.now()
            mutableStateOf(today.month == java.time.Month.OCTOBER && today.dayOfMonth == 14)
          }
          if (showPartyBomb) {
            val parties = remember {
              listOf(
                Party(
                  speed = 30f,
                  maxSpeed = 70f,
                  damping = 0.9f,
                  angle = 270,
                  spread = 60,
                  colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                  emitter = Emitter(duration = 300, TimeUnit.MILLISECONDS).max(300),
                  position = Position.Relative(0.5, 1.0)
                ),
                Party(
                  speed = 30f,
                  maxSpeed = 70f,
                  damping = 0.9f,
                  angle = 315,
                  spread = 45,
                  colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                  emitter = Emitter(duration = 300, TimeUnit.MILLISECONDS).max(300),
                  position = Position.Relative(0.0, 1.0)
                ),
                Party(
                  speed = 30f,
                  maxSpeed = 70f,
                  damping = 0.9f,
                  angle = 225,
                  spread = 45,
                  colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                  emitter = Emitter(duration = 300, TimeUnit.MILLISECONDS).max(300),
                  position = Position.Relative(1.0, 1.0)
                )
              )
            }
            KonfettiView(
              modifier = Modifier.fillMaxSize(),
              parties = parties,
              updateListener =
                object : OnParticleSystemUpdateListener {
                  override fun onParticleSystemEnded(
                    system: nl.dionsegijn.konfetti.core.PartySystem,
                    activeSystems: Int
                  ) {
                    if (activeSystems == 0) showPartyBomb = false
                  }
                }
            )
          }
        }
      }
    }
  }

  private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
    var uri = intent.data
    if (uri == null) {
      val extraText = intent.extras?.getString(Intent.EXTRA_TEXT)
      if (extraText != null) {
        val urlRegex = "(https?://[^\\s]+)".toRegex()
        val match = urlRegex.find(extraText)
        if (match != null) {
          uri = match.value.toUri()
        }
      }
    }
    if (uri == null) return

    intent.data = null
    intent.removeExtra(Intent.EXTRA_TEXT)
    val coroutineScope = lifecycle.coroutineScope

    val listenCode =
      uri.getQueryParameter("code")
        ?: uri.getQueryParameter("room")
        ?: uri.pathSegments.getOrNull(1)
    val isListenLink =
      uri.pathSegments.firstOrNull() == "listen" ||
        uri.host?.equals("listen", ignoreCase = true) == true
    if (!listenCode.isNullOrBlank() && isListenLink) {
      val username = dataStore.get(ListenTogetherUsernameKey, "").ifBlank { "Guest" }
      listenTogetherManager.joinRoom(listenCode, username)
      return
    }

    when (val path = uri.pathSegments.firstOrNull()) {
      "playlist" -> {
        val playlistId = validOnlinePlaylistId(uri.getQueryParameter("list"))
        if (playlistId == null) {
          android.widget.Toast.makeText(
              this,
              R.string.invalid_playlist_url,
              android.widget.Toast.LENGTH_SHORT
            )
            .show()
        } else {
          if (playlistId.startsWith("OLAK5uy_")) {
            coroutineScope.launch(Dispatchers.IO) {
              YouTube.albumSongs(playlistId)
                .onSuccess { songs ->
                  songs.firstOrNull()?.album?.id?.let { browseId ->
                    withContext(Dispatchers.Main) { navController.navigate("album/${URLEncoder.encode(browseId, "UTF-8")}") }
                  }
                }
                .onFailure { reportException(it) }
            }
          } else {
            onlinePlaylistRoute(playlistId)?.let { navController.navigate(it) }
          }
        }
      }
      "browse" -> uri.lastPathSegment?.let { browseId -> navController.navigate("album/${URLEncoder.encode(browseId, "UTF-8")}") }
      "channel",
      "c" -> uri.lastPathSegment?.let { artistId -> navController.navigate("artist/${URLEncoder.encode(artistId, "UTF-8")}") }
      "search" -> {
        uri.getQueryParameter("q")?.let {
          navController.navigate("search/${URLEncoder.encode(it, "UTF-8")}")
        }
      }
      else -> {
        val videoId =
          when {
            path == "watch" -> uri.getQueryParameter("v")
            uri.host == "youtu.be" || uri.host == "share.sonari.fun" ->
              uri.pathSegments.firstOrNull()
            else -> null
          }

        val playlistId = uri.getQueryParameter("list")

        if (videoId != null) {
          coroutineScope.launch(Dispatchers.IO) {
            YouTube.queue(listOf(videoId), playlistId)
              .onSuccess { queue ->
                withContext(Dispatchers.Main) {
                  var attempts = 0
                  while (playerConnection == null && attempts < 20) {
                    delay(100)
                    attempts++
                  }
                  playerConnection?.playQueue(
                    YouTubeQueue(
                      WatchEndpoint(videoId = queue.firstOrNull()?.id, playlistId = playlistId),
                      queue.firstOrNull()?.toMediaMetadata()
                    )
                  )
                }
              }
              .onFailure { reportException(it) }
          }
        } else if (playlistId != null) {
          coroutineScope.launch(Dispatchers.IO) {
            YouTube.queue(null, playlistId)
              .onSuccess { queue ->
                val firstItem = queue.firstOrNull()
                withContext(Dispatchers.Main) {
                  var attempts = 0
                  while (playerConnection == null && attempts < 20) {
                    delay(100)
                    attempts++
                  }
                  playerConnection?.playQueue(
                    YouTubeQueue(
                      WatchEndpoint(videoId = firstItem?.id, playlistId = playlistId),
                      firstItem?.toMediaMetadata()
                    )
                  )
                }
              }
              .onFailure { reportException(it) }
          }
        }
      }
    }
  }

  @SuppressLint("ObsoleteSdkInt")
  private fun setSystemBarAppearance(isDark: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
      isAppearanceLightStatusBars = !isDark
      isAppearanceLightNavigationBars = !isDark
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      window.statusBarColor =
        (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      window.navigationBarColor =
        (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
    }
  }

  private fun handleRecognitionIntent(
    intent: Intent,
    navController: NavHostController,
  ) {
    if (intent.action != ACTION_RECOGNITION) return
    val autoStart = intent.getBooleanExtra(EXTRA_AUTO_START_RECOGNITION, false)

    intent.removeExtra(EXTRA_AUTO_START_RECOGNITION)
    navController.navigate(if (autoStart) "recognition?autoStart=true" else "recognition") {
      launchSingleTop = true
    }
  }

  private fun handleAssistantSearchIntent(
    intent: Intent,
    navController: NavHostController,
  ) {
    if (intent.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
      val query = intent.getStringExtra(android.app.SearchManager.QUERY) ?: return
      navController.navigate("search/${URLEncoder.encode(query, "UTF-8")}")
    }
  }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalRingtoneViewModel =
  compositionLocalOf<RingtoneViewModel> { error("No RingtoneViewModel provided") }

val LocalPlayerConnection =
  staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }

val LocalPlayerAwareWindowInsets =
  compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalListenTogetherManager =
  staticCompositionLocalOf<app.music.backend.sonari.listentogether.ListenTogetherManager?> { null }
val LocalIsPlayerExpanded = compositionLocalOf { false }
val LocalExpandPlayer = staticCompositionLocalOf<() -> Unit> { {} }


@Composable
private fun SonariBootAnimation(onFinished: () -> Unit) {
  val iconScale = remember { Animatable(0.3f) }
  val iconAlpha = remember { Animatable(0f) }
  val iconLift = remember { Animatable(24f) }
  val glowAlpha = remember { Animatable(0f) }
  val scrimAlpha = remember { Animatable(1f) }

  LaunchedEffect(Unit) {
    // Smooth entrance - icon fades in and springs up
    launch { iconAlpha.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    launch {
      iconScale.animateTo(
        1f,
        spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMediumLow)
      )
    }
    launch {
      iconLift.animateTo(
        0f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
      )
    }
    launch { glowAlpha.animateTo(0.18f, tween(400, easing = FastOutSlowInEasing)) }

    // Settle with a gentle breath
    delay(500)
    launch {
      iconScale.animateTo(0.96f, tween(180, easing = FastOutSlowInEasing))
      iconScale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
    }
    launch { glowAlpha.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }

    // Graceful reveal into the app
    delay(350)
    scrimAlpha.animateTo(0f, tween(450, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
    onFinished()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .graphicsLayer { alpha = scrimAlpha.value }
      .background(Color.Black),
    contentAlignment = Alignment.Center
  ) {
    // Ambient glow
    Box(
      modifier = Modifier
        .size(220.dp)
        .graphicsLayer { alpha = glowAlpha.value }
        .clip(CircleShape)
        .background(
          Brush.radialGradient(
            colors = listOf(
              Color.White.copy(alpha = 0.22f),
              Color.White.copy(alpha = 0.06f),
              Color.Transparent
            )
          )
        )
    )

    // App icon mark
    Image(
      painter = painterResource(R.mipmap.ic_launcher_foreground),
      contentDescription = null,
      modifier = Modifier
        .size(128.dp)
        .graphicsLayer {
          scaleX = iconScale.value
          scaleY = iconScale.value
          alpha = iconAlpha.value
          translationY = iconLift.value
        }
    )
  }
}

