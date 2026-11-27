package app.music.backend.sonari.sonari.updater

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil3.compose.AsyncImage
import app.music.backend.sonari.BuildConfig
import app.music.backend.sonari.R
import app.music.backend.sonari.sonari.updater.downloadmanager.DownloadNotificationManager
import app.music.backend.sonari.sonari.updater.downloadmanager.UpdateDownloadWorker
import app.music.backend.sonari.ui.component.AnimatedActionButton
import app.music.backend.sonari.ui.component.ChangelogItem
import app.music.backend.sonari.ui.component.DefaultDialog
import app.music.backend.sonari.ui.component.ErrorSnackbar
import app.music.backend.sonari.ui.component.ExpressiveIconButton
import app.music.backend.sonari.ui.component.detachedItemShape
import app.music.backend.sonari.ui.component.endItemShape
import app.music.backend.sonari.ui.component.leadingItemShape
import app.music.backend.sonari.ui.component.middleItemShape
import app.music.backend.sonari.ui.component.parseMarkdown
import app.music.backend.sonari.ui.utils.parseMarkdownToSections
import app.music.backend.sonari.ui.utils.parseSimpleMarkdown
import app.music.backend.sonari.ui.utils.withoutTables
import java.io.File
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ChangelogSection(val title: String, val items: List<String>)

sealed class SonariUpdateStatus {
  object Idle : SonariUpdateStatus()

  object Checking : SonariUpdateStatus()

  data class Available(
    val version: String,
    val changelog: List<ChangelogSection>,
    val size: String,
    val releaseDate: String,
    val description: String?,
    val imageUrl: String?,
    val apkUrl: String?
  ) : SonariUpdateStatus()

  data class NoUpdate(val version: String) : SonariUpdateStatus()

  data class Error(val message: String) : SonariUpdateStatus()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateScreen(navController: NavHostController) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var status by remember {
    mutableStateOf<SonariUpdateStatus>(SonariUpdateStatus.NoUpdate(BuildConfig.VERSION_NAME))
  }
  var isDownloading by remember { mutableStateOf(false) }
  var downloadProgress by remember { mutableStateOf(0f) }
  var isDownloadComplete by remember { mutableStateOf(false) }
  var downloadedFile by remember { mutableStateOf<File?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }

  val currentVersion = BuildConfig.VERSION_NAME
  val autoUpdateCheckEnabled = getAutoUpdateCheckSetting(context)

  LaunchedEffect(Unit) { DownloadNotificationManager.initialize(context) }

  LaunchedEffect(Unit) {
    WorkManager.getInstance(context)
      .getWorkInfosForUniqueWorkLiveData("update_download")
      .observeForever { workInfos ->
        val workInfo = workInfos?.firstOrNull() ?: return@observeForever

        when (workInfo.state) {
          WorkInfo.State.RUNNING -> {
            isDownloading = true
            downloadProgress = workInfo.progress.getFloat("progress", 0f)
          }
          WorkInfo.State.SUCCEEDED -> {
            isDownloading = false
            // Only a whole, newer APK becomes the one offered: a file an earlier download left
            // behind is dropped here instead of being handed to the installer as an update.
            val file = workInfo.outputData.getString("file_path")?.let { File(it) }
            if (file != null && isInstallableApk(context, file) && isNewerApk(context, file)) {
              isDownloadComplete = true
              downloadedFile = file
            } else {
              file?.delete()
              isDownloadComplete = false
              downloadedFile = null
              downloadProgress = 0f
            }
          }
          WorkInfo.State.FAILED -> {
            isDownloading = false
            scope.launch {
              snackbarHostState.showSnackbar(context.getString(R.string.download_failed))
            }
          }
          WorkInfo.State.CANCELLED -> {
            isDownloading = false
            downloadProgress = 0f
          }
          else -> {}
        }
      }
  }

  LaunchedEffect(isDownloadComplete, downloadedFile) {
    if (isDownloadComplete && downloadedFile != null) {
      if (!downloadedFile!!.exists()) {
        isDownloadComplete = false
        downloadedFile = null
        downloadProgress = 0f
      }
    }
  }

  fun triggerUpdateCheck() {
    status = SonariUpdateStatus.Checking
    scope.launch {
      delay(1000L)
      checkForUpdate(
        context = context,
        onSuccess = { tag, isAvailable, changelog, size, date, description, imageUrl, apkUrl ->
          saveLastCheckedTime(
            context,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a"))
          )
          saveUpdateAvailableState(context, isAvailable)
          status =
            if (isAvailable) {
              SonariUpdateStatus.Available(
                version = tag,
                changelog = changelog,
                size = size,
                releaseDate = date,
                description = description,
                imageUrl = imageUrl,
                apkUrl = apkUrl
              )
            } else {
              SonariUpdateStatus.NoUpdate(tag)
            }
        },
        onError = {
          status = SonariUpdateStatus.Error(context.getString(R.string.cant_check_updates))
        }
      )
    }
  }

  LaunchedEffect(Unit) {
    if (autoUpdateCheckEnabled) {
      triggerUpdateCheck()
    }
  }

  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  Scaffold(
    topBar = {
      LargeTopAppBar(
        title = {
          val titleText =
            if (status is SonariUpdateStatus.Available) {
              buildAnnotatedString {
                append(stringResource(R.string.new_update) + " ")
                withStyle(
                  SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                ) {
                  append((status as SonariUpdateStatus.Available).version)
                }
              }
            } else {
              AnnotatedString(stringResource(R.string.settings_check_updates_title))
            }
          Text(text = titleText, maxLines = 1)
        },
        navigationIcon = {
          Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
            ExpressiveIconButton(
              onClick = { navController.navigateUp() },
              painter = painterResource(R.drawable.arrow_back),
              contentDescription = stringResource(R.string.cancel),
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              contentColor = MaterialTheme.colorScheme.onSurface
            )
          }
        },
        scrollBehavior = scrollBehavior,
        colors =
          TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
          )
      )
    },
    bottomBar = {
      Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .padding(WindowInsets.navigationBars.asPaddingValues())
              .padding(horizontal = 24.dp, vertical = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          when (val currentStatus = status) {
            is SonariUpdateStatus.Idle,
            is SonariUpdateStatus.Checking,
            is SonariUpdateStatus.NoUpdate,
            is SonariUpdateStatus.Error -> {
              AnimatedActionButton(
                text = stringResource(R.string.check_for_update),
                onClick = { triggerUpdateCheck() },
                enabled = currentStatus !is SonariUpdateStatus.Checking && !isDownloading,
                modifier = Modifier.fillMaxWidth()
              )
            }
            is SonariUpdateStatus.Available -> {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                AnimatedActionButton(
                  text = stringResource(R.string.later),
                  onClick = { navController.navigateUp() },
                  modifier = Modifier.weight(1f),
                  isOutlined = true,
                  enabled = !isDownloading
                )
                AnimatedActionButton(
                  text =
                    if (isDownloading) "${(downloadProgress * 100).toInt()}%"
                    else if (isDownloadComplete) stringResource(R.string.install)
                    else stringResource(R.string.update_available),
                  onClick = {
                    if (isDownloadComplete) {
                      val file = downloadedFile
                      if (file == null || !file.exists()) {
                        isDownloadComplete = false
                        downloadedFile = null
                        downloadProgress = 0f
                        return@AnimatedActionButton
                      }
                      file.let { f ->
                        // A file that is not whole, or not newer than what is installed, is not
                        // sent to the installer: it is dropped and downloaded again instead.
                        if (!isInstallableApk(context, f) || !isNewerApk(context, f)) {
                          f.delete()
                          isDownloadComplete = false
                          downloadedFile = null
                          downloadProgress = 0f
                          scope.launch {
                            snackbarHostState.showSnackbar(
                              context.getString(R.string.download_incomplete)
                            )
                          }
                          return@AnimatedActionButton
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                          if (!context.packageManager.canRequestPackageInstalls()) {
                            val intent =
                              Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                .apply { data = Uri.parse("package:${context.packageName}") }
                            context.startActivity(intent)
                            return@let
                          }
                        }
                        val uri =
                          FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.FileProvider",
                            file
                          )
                        val installIntent =
                          Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            // Some installers only keep the read grant the intent carries in its
                            // clip data, and then cannot read the APK they were handed.
                            clipData = ClipData.newRawUri("APK", uri)
                          }
                        ContextCompat.startActivity(context, installIntent, null)
                      }
                    } else {
                      val urlToDownload =
                        currentStatus.apkUrl ?: UpdateSource.RELEASES_PAGE

                      val constraints =
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

                      val downloadRequest =
                        OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                          .setInputData(
                            workDataOf(
                              "apk_url" to urlToDownload,
                              "version" to currentStatus.version,
                              "file_size" to currentStatus.size
                            )
                          )
                          .setConstraints(constraints)
                          .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            10,
                            java.util.concurrent.TimeUnit.SECONDS
                          )
                          .addTag("update_download")
                          .build()
                      WorkManager.getInstance(context)
                        .enqueueUniqueWork(
                          "update_download",
                          ExistingWorkPolicy.REPLACE,
                          downloadRequest
                        )
                      isDownloading = true
                    }
                  },
                  modifier = Modifier.weight(1f),
                  enabled = !isDownloading || isDownloadComplete
                )
              }
            }
          }
        }
      }
    },
    snackbarHost = { ErrorSnackbar(hostState = snackbarHostState) },
    containerColor = MaterialTheme.colorScheme.background,
    modifier =
      Modifier.widthIn(max = 700.dp)
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection)
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        item {
          val contentModifier =
            if (status is SonariUpdateStatus.Available) {
              Modifier.fillMaxWidth()
            } else {
              Modifier.fillParentMaxSize()
            }

          Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
            when (val currentStatus = status) {
              is SonariUpdateStatus.Checking -> {
                androidx.compose.material3.ContainedLoadingIndicator(
                  modifier = Modifier.size(64.dp)
                )
              }
              is SonariUpdateStatus.NoUpdate -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    painter = painterResource(R.drawable.deployed_app_update),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                  )
                  Spacer(modifier = Modifier.height(24.dp))
                  Text(
                    text = stringResource(R.string.on_latest_version),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    text = stringResource(R.string.current_version_v, currentStatus.version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                  )
                }
              }
              is SonariUpdateStatus.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    painter = painterResource(R.drawable.error),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.error
                  )
                  Spacer(modifier = Modifier.height(24.dp))
                  Text(
                    text = currentStatus.message,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
              is SonariUpdateStatus.Available -> {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                  Spacer(modifier = Modifier.height(12.dp))
                  Text(
                    text = stringResource(R.string.release_date_v, currentStatus.releaseDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    text = stringResource(R.string.update_size_v, currentStatus.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Spacer(modifier = Modifier.height(24.dp))
                  if (!currentStatus.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                      model = currentStatus.imageUrl,
                      contentDescription = null,
                      modifier =
                        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp)),
                      contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                  }
                  if (!currentStatus.description.isNullOrBlank()) {
                    val annotatedText = currentStatus.description.parseMarkdown()

                    ClickableText(
                      text = annotatedText,
                      onClick = { offset ->
                        annotatedText
                          .getStringAnnotations("URL", offset, offset)
                          .firstOrNull()
                          ?.let {
                            ContextCompat.startActivity(
                              context,
                              Intent(Intent.ACTION_VIEW, Uri.parse(it.item)),
                              null
                            )
                          }
                      },
                      style =
                        MaterialTheme.typography.bodyMedium.copy(
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          lineHeight = 20.sp
                        ),
                      modifier = Modifier.padding(bottom = 24.dp)
                    )
                  }

                  currentStatus.changelog.forEach { section ->
                    Text(
                      text = section.title,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onBackground,
                      modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    section.items.forEachIndexed { index, item ->
                      val shape =
                        when {
                          section.items.size == 1 -> detachedItemShape()
                          index == 0 -> leadingItemShape()
                          index == section.items.size - 1 -> endItemShape()
                          else -> middleItemShape()
                        }
                      ChangelogItem(text = item, shape = shape)
                      if (index != section.items.size - 1) {
                        Spacer(modifier = Modifier.height(2.dp))
                      }
                    }
                  }

                  if (currentStatus.changelog.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                  }

                  if (isDownloading) {
                    if (downloadProgress > 0f) {
                      androidx.compose.material3.LinearProgressIndicator(
                        progress = downloadProgress,
                        modifier =
                          Modifier.fillMaxWidth()
                            .height(8.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                      )
                    } else {
                      androidx.compose.material3.LinearProgressIndicator(
                        modifier =
                          Modifier.fillMaxWidth()
                            .height(8.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                      )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                  }
                }
              }
              else -> {}
            }
          }
        }
      }
    }
  }
}

const val PREFS_NAME = "settings"
const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"
const val KEY_LAST_CHECKED_TIME = "last_checked_time"
const val KEY_BETA_UPDATES = "beta_updates"
const val KEY_UPDATE_AVAILABLE = "update_available"

fun getUpdateAvailableState(context: Context): Boolean {
  val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  return sharedPrefs.getBoolean(KEY_UPDATE_AVAILABLE, false)
}

fun saveUpdateAvailableState(context: Context, available: Boolean) {
  val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  sharedPrefs.edit().putBoolean(KEY_UPDATE_AVAILABLE, available).apply()
}

fun getAutoUpdateCheckSetting(context: Context): Boolean {
  val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  return sharedPrefs.getBoolean(KEY_AUTO_UPDATE_CHECK, true)
}

fun saveAutoUpdateCheckSetting(context: Context, enabled: Boolean) {
  val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  sharedPrefs.edit().putBoolean(KEY_AUTO_UPDATE_CHECK, enabled).apply()
}

const val KEY_UPDATE_NOTIFICATIONS = "update_notifications"

fun getUpdateNotificationsSetting(context: Context): Boolean {
  val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  return sharedPrefs.getBoolean(KEY_UPDATE_NOTIFICATIONS, true)
}

fun saveUpdateNotificationsSetting(context: Context, enabled: Boolean) {
  val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  sharedPrefs.edit().putBoolean(KEY_UPDATE_NOTIFICATIONS, enabled).apply()
}

fun saveLastCheckedTime(context: Context, timestamp: String) {
  val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  sharedPrefs.edit().putString(KEY_LAST_CHECKED_TIME, timestamp).apply()
}

fun getLastCheckedTime(context: Context): String {
  val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  return sharedPrefs.getString(KEY_LAST_CHECKED_TIME, "") ?: ""
}

fun getBetaUpdatesSetting(context: Context): Boolean {
  val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  return sharedPrefs.getBoolean(KEY_BETA_UPDATES, false)
}

fun saveBetaUpdatesSetting(context: Context, enabled: Boolean) {
  val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  sharedPrefs.edit().putBoolean(KEY_BETA_UPDATES, enabled).apply()
}

private fun formatGitHubDate(githubDate: String): String =
  try {
    val githubFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    val displayFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a")
    val dateTime = LocalDateTime.parse(githubDate, githubFormatter)
    dateTime.format(displayFormatter)
  } catch (e: Exception) {
    githubDate
  }

/**
 * Whether [latestVersion] is newer than [currentVersion]. Either may arrive as a release tag
 * ("sonari-ver.1.5.0") or as a plain version ("1.5.0"), so both are reduced to their leading
 * numbers first; an equal pair counts as up to date, so a newer build the user already runs is
 * never offered as an "update".
 */
fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
  val latestParts = UpdateSource.versionNumbers(latestVersion)
  val currentParts = UpdateSource.versionNumbers(currentVersion)

  for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
    val latest = latestParts.getOrElse(i) { 0 }
    val current = currentParts.getOrElse(i) { 0 }
    when {
      latest > current -> return true
      latest < current -> return false
    }
  }

  return false
}

suspend fun checkForUpdate(
  context: Context,
  onSuccess:
    (
      tag: String,
      isAvailable: Boolean,
      changelog: List<ChangelogSection>,
      size: String,
      date: String,
      description: String?,
      imageUrl: String?,
      apkUrl: String?
    ) -> Unit,
  onError: () -> Unit,
) {
  withContext(Dispatchers.IO) {
    try {
      val json =
        UpdateSource.open(UpdateSource.LATEST_RELEASE_API).bufferedReader().use { it.readText() }
      val targetRelease = JSONObject(json)

      val currentVersion = UpdateSource.cleanTag(BuildConfig.VERSION_NAME)
      val tagWithPrefix = targetRelease.getString("tag_name")
      val latestVersion = UpdateSource.cleanTag(tagWithPrefix)

      // Only a genuinely newer release is offered, so a build ahead of the newest release (a local
      // debug build, say) is never told to "update" down to an older version.
      if (isNewerVersion(latestVersion, currentVersion)) {
        val displayTag = latestVersion

        val changelogList = mutableListOf<ChangelogSection>()
        var description: String? = null
        var imageUrl: String? = null
        try {
          val changelogJson =
            UpdateSource
              .open("${UpdateSource.RELEASE_DOWNLOAD_URL}/$tagWithPrefix/changelog.json")
              .bufferedReader()
              .use { it.readText() }
          val changelogData = JSONObject(changelogJson)

          description = changelogData.optString("description").takeIf { it.isNotEmpty() }
          imageUrl = changelogData.optString("image").takeIf { it.isNotEmpty() }

          val changelogArray = changelogData.getJSONArray("changelog")
          for (j in 0 until changelogArray.length()) {
            val sectionObj = changelogArray.getJSONObject(j)
            val title = sectionObj.getString("title")
            val itemsArray = sectionObj.getJSONArray("items")
            val itemsList = mutableListOf<String>()
            for (k in 0 until itemsArray.length()) {
              itemsList.add(itemsArray.getString(k))
            }
            changelogList.add(ChangelogSection(title, itemsList))
          }
        } catch (e: Exception) {
          var body =
            targetRelease.optString("body", context.getString(R.string.no_changelog_available))

          val imageRegex = Regex("!\\[(.*?)\\]\\((.*?)\\)")
          val match = imageRegex.find(body)
          if (match != null) {
            imageUrl = match.groupValues[2]
            body = body.replace(match.value, "").trim()
          }
          val (parsedDesc, parsedSections) = parseMarkdownToSections(body)
          description = parsedDesc ?: body.takeIf { parsedSections.isEmpty() }
          changelogList.addAll(parsedSections)
        }

        val publishedAt = targetRelease.getString("published_at")
        val formattedReleaseDate = formatGitHubDate(publishedAt)
        val assets = targetRelease.getJSONArray("assets")
        val assetUrls = mutableMapOf<String, String>()
        val assetSizes = mutableMapOf<String, Long>()
        for (j in 0 until assets.length()) {
          val asset = assets.getJSONObject(j)
          val assetName = asset.getString("name")
          assetUrls[assetName] = asset.getString("browser_download_url")
          assetSizes[assetName] = asset.getLong("size")
        }

        val apkDownloadUrl =
          UpdateSource.pickApkAsset(assetUrls.keys.toList(), assetUrls).orEmpty()
        val apkSizeInMB =
          if (apkDownloadUrl.isEmpty()) {
            ""
          } else {
            val sizeInBytes = assetUrls.entries.first { it.value == apkDownloadUrl }.key
            String.format("%.1f", (assetSizes[sizeInBytes] ?: 0L) / (1024.0 * 1024.0))
          }

        if (apkDownloadUrl.isNotEmpty()) {
          withContext(Dispatchers.Main) {
            onSuccess(
              displayTag,
              true,
              changelogList.withoutTables(),
              apkSizeInMB,
              formattedReleaseDate,
              description,
              imageUrl,
              apkDownloadUrl
            )
          }
          return@withContext
        }
      }

      withContext(Dispatchers.Main) {
        onSuccess(currentVersion, false, emptyList(), "", "", null, null, null)
      }
    } catch (e: Exception) {
      Log.e("UpdateCheck", "Error checking for updates: ${e.message}", e)
      withContext(Dispatchers.Main) { onError() }
    }
  }
}

const val KEY_LAST_SEEN_CHANGELOG_VERSION = "last_seen_changelog_version"

fun getLastSeenChangelogVersion(context: Context): String {
  val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  return sharedPrefs.getString(KEY_LAST_SEEN_CHANGELOG_VERSION, "") ?: ""
}

fun saveLastSeenChangelogVersion(context: Context, version: String) {
  val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  sharedPrefs.edit().putString(KEY_LAST_SEEN_CHANGELOG_VERSION, version).apply()
}

data class WhatsNewInfo(
  val changelog: List<ChangelogSection>,
  val description: String?,
)

/**
 * URLConnection defaults both connect and read timeouts to 0 (infinite) — opening a stream without
 * setting them can hang this Dispatchers.IO call indefinitely on a stalled request. Every update
 * request goes through [UpdateSource.open], which also sends the User-Agent GitHub asks for.
 */
private fun openTimedStream(url: String): java.io.InputStream = UpdateSource.open(url)

/**
 * Looks up the release whose tag matches [currentVersion] (not necessarily the latest — the user
 * may be a version or two behind "latest" right after installing an update) and returns its
 * changelog, mirroring the parsing [checkForUpdate] does for the newest release.
 */
suspend fun fetchChangelogForVersion(currentVersion: String): WhatsNewInfo? =
  withContext(Dispatchers.IO) {
    try {
      val cleanCurrent = UpdateSource.cleanTag(currentVersion)
      val releasesJson =
        openTimedStream(UpdateSource.RELEASES_API).bufferedReader().use { it.readText() }
      val releases = JSONArray(releasesJson)

      var matchedRelease: JSONObject? = null
      for (i in 0 until releases.length()) {
        val release = releases.getJSONObject(i)
        val tagClean = UpdateSource.cleanTag(release.getString("tag_name"))
        if (tagClean == cleanCurrent) {
          matchedRelease = release
          break
        }
      }
      val release = matchedRelease ?: return@withContext null
      val tag = release.getString("tag_name")

      val changelogList = mutableListOf<ChangelogSection>()
      var description: String? = null
      try {
        val changelogJson =
          openTimedStream("${UpdateSource.RELEASE_DOWNLOAD_URL}/$tag/changelog.json")
            .bufferedReader()
            .use { it.readText() }
        val changelogData = JSONObject(changelogJson)
        description = changelogData.optString("description").takeIf { it.isNotEmpty() }
        val changelogArray = changelogData.getJSONArray("changelog")
        for (j in 0 until changelogArray.length()) {
          val sectionObj = changelogArray.getJSONObject(j)
          val title = sectionObj.getString("title")
          val itemsArray = sectionObj.getJSONArray("items")
          val itemsList = mutableListOf<String>()
          for (k in 0 until itemsArray.length()) {
            itemsList.add(itemsArray.getString(k))
          }
          changelogList.add(ChangelogSection(title, itemsList))
        }
      } catch (e: Exception) {
        var body = release.optString("body", "")
        val imageRegex = Regex("!\\[(.*?)\\]\\((.*?)\\)")
        imageRegex.find(body)?.let { match -> body = body.replace(match.value, "").trim() }
        val (parsedDesc, parsedSections) = parseMarkdownToSections(body)
        description = parsedDesc ?: body.takeIf { parsedSections.isEmpty() }
        changelogList.addAll(parsedSections)
      }

      val cleanSections = changelogList.withoutTables()
      if (cleanSections.isEmpty() && description.isNullOrBlank()) return@withContext null
      WhatsNewInfo(cleanSections, description)
    } catch (e: Exception) {
      Log.e("WhatsNew", "Failed to fetch changelog for version $currentVersion: ${e.message}", e)
      null
    }
  }

@Composable
fun WhatsNewDialog(
  version: String,
  info: WhatsNewInfo,
  onDismiss: () -> Unit,
) {
  DefaultDialog(
    onDismiss = onDismiss,
    title = { Text(stringResource(R.string.whats_new_title, version)) },
    buttons = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } }
  ) {
    val (effectiveDescription, effectiveSections) =
      remember(info) {
        if (info.changelog.isNotEmpty()) {
          Pair(info.description?.takeIf { it.isNotBlank() }, info.changelog)
        } else if (!info.description.isNullOrBlank()) {
          parseMarkdownToSections(info.description)
        } else {
          Pair(null, emptyList())
        }
      }

    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
      effectiveDescription?.let { description ->
        item {
          Text(
            text = parseSimpleMarkdown(description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
          )
        }
      }
      effectiveSections.forEach { section ->
        if (section.title.isNotBlank()) {
          item {
            Text(
              text = section.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
          }
        }
        itemsIndexed(section.items) { index, changelogItem ->
          val shape =
            when {
              section.items.size == 1 -> detachedItemShape()
              index == 0 -> leadingItemShape()
              index == section.items.size - 1 -> endItemShape()
              else -> middleItemShape()
            }
          ChangelogItem(text = changelogItem, shape = shape)
          if (index != section.items.size - 1) {
            Spacer(modifier = Modifier.height(2.dp))
          }
        }
      }
    }
  }
}

/**
 * Whether the installer can parse this file at all. A download the connection cut short leaves a
 * file that is no APK, and the installer only ever says it "appears to be invalid".
 */
private fun isInstallableApk(context: Context, file: File): Boolean =
  file.length() > 0L &&
    runCatching { context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) != null }
      .getOrDefault(false)

/** The version code an APK carries, or null when it cannot be read. */
private fun apkVersionCode(context: Context, file: File): Long? =
  runCatching {
      val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
      if (info == null) null
      else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
      else info.versionCode.toLong()
    }
    .getOrNull()

/** Whether this APK is a newer build than the one on the phone, so the installer takes it. */
private fun isNewerApk(context: Context, file: File): Boolean =
  (apkVersionCode(context, file) ?: return false) > BuildConfig.VERSION_CODE.toLong()

fun String.extractUrls(): List<Pair<IntRange, String>> {
  val urlPattern =
    Pattern.compile("(?:^|[\\s])((https?://|www\\.|pic\\.)[\\w-]+(\\.[\\w-]+)+([/?].*)?)")
  val matcher = urlPattern.matcher(this)
  val urlList = mutableListOf<Pair<IntRange, String>>()

  while (matcher.find()) {
    val url = matcher.group(1)?.trim() ?: continue
    val range = IntRange(matcher.start(1), matcher.end(1) - 1)

    val fullUrl = if (url.startsWith("http")) url else "https://$url"
    urlList.add(range to fullUrl)
  }

  return urlList
}
