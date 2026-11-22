package app.music.backend.sonari.sonari.updater

import android.content.Context
import android.os.Build
import app.music.backend.sonari.BuildConfig
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Every update fact Sonari needs comes from its own public GitHub repository: the newest release,
 * its notes, and the APK it carries. There is no update server of our own and no domain to keep up.
 */
object UpdateSource {
  const val REPO = "kusoidev/sonari-android"
  const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"
  const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO/releases/latest"
  const val RELEASES_API = "https://api.github.com/repos/$REPO/releases"
  const val RELEASE_DOWNLOAD_URL = "https://github.com/$REPO/releases/download"

  /**
   * Release tags read "sonari-ver.1.5.0", but an older release may say "v1.5.0" or "b1.5.0". Every
   * prefix is dropped so a tag and [BuildConfig.VERSION_NAME] always compare as plain versions.
   */
  private val TAG_PREFIXES = listOf("sonari-ver.", "sonari-", "ver.", "v", "b")

  /** "sonari-ver.1.5.0" and "v1.5.0" both become "1.5.0". */
  fun cleanTag(tag: String): String {
    var version = tag.trim()
    var stripped = true
    while (stripped) {
      val prefix = TAG_PREFIXES.firstOrNull { version.startsWith(it, ignoreCase = true) }
      if (prefix == null) {
        stripped = false
      } else {
        version = version.substring(prefix.length).trim()
      }
    }
    return version.trimStart('.', '-', '_')
  }

  /**
   * The numbers a version leads with, e.g. "1.5.0" -> [1, 5, 0]. A suffix such as "-beta1" is not
   * compared, so it can never make a released version look newer or older than it is.
   */
  fun versionNumbers(version: String): List<Int> {
    val clean = cleanTag(version)
    val numbers = mutableListOf<Int>()
    for (part in clean.split(".")) {
      val number = part.takeWhile { it.isDigit() }.toIntOrNull() ?: break
      numbers.add(number)
    }
    return numbers
  }

  /**
   * Opens a GitHub address with timeouts (URLConnection waits forever without them, which can hang
   * an update check on a stalled connection) and with the User-Agent the API asks for.
   */
  fun open(url: String, timeoutMillis: Int = 15_000): InputStream {
    val connection =
      (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = timeoutMillis
        readTimeout = timeoutMillis
        setRequestProperty("User-Agent", "Sonari-Android")
        setRequestProperty("Accept", "application/vnd.github+json")
      }
    return connection.inputStream
  }

  /**
   * The APK in a release to install: the smaller arm64 build on an arm64 phone, the universal one
   * anywhere else, never a debug build. The phone's own ABIs decide, since Sonari's build flavours
   * reuse the same APK names.
   */
  fun pickApkAsset(
    assetNames: List<String>,
    downloadUrls: Map<String, String>,
  ): String? {
    val installable =
      assetNames.filter {
        it.endsWith(".apk", ignoreCase = true) && !it.contains("debug", ignoreCase = true)
      }
    if (installable.isEmpty()) return null

    val isArm64 = Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }
    val wanted =
      if (isArm64) {
        installable.firstOrNull {
          !it.contains("universal", ignoreCase = true) &&
            (it.contains("arm64", ignoreCase = true) || it.contains("release", ignoreCase = true))
        }
      } else {
        installable.firstOrNull { it.contains("universal", ignoreCase = true) }
      }
    val chosen =
      wanted
        ?: installable.firstOrNull { it.contains("universal", ignoreCase = true) }
        ?: installable.first()

    return downloadUrls[chosen]
  }
}

private const val UPDATE_PREFS_KEY_LATEST_KNOWN = "latest_known_version"
private const val UPDATE_PREFS_KEY_DISMISSED = "dismissed_update_version"
private const val UPDATE_PREFS_KEY_NOTIFIED = "notified_update_version"

private fun updatePrefs(context: Context) =
  context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

/** The newest release the last successful check saw. */
fun saveLatestKnownVersion(context: Context, version: String) {
  if (version.isEmpty()) return
  updatePrefs(context).edit().putString(UPDATE_PREFS_KEY_LATEST_KNOWN, version).apply()
}

fun getLatestKnownVersion(context: Context): String =
  updatePrefs(context).getString(UPDATE_PREFS_KEY_LATEST_KNOWN, "").orEmpty()

/** A version the user put off, so the banner leaves them alone until the next one ships. */
fun saveDismissedUpdateVersion(context: Context, version: String) {
  updatePrefs(context).edit().putString(UPDATE_PREFS_KEY_DISMISSED, version).apply()
}

fun getDismissedUpdateVersion(context: Context): String =
  updatePrefs(context).getString(UPDATE_PREFS_KEY_DISMISSED, "").orEmpty()

/** The version the notification already announced, so nobody is told twice about one release. */
fun saveNotifiedUpdateVersion(context: Context, version: String) {
  updatePrefs(context).edit().putString(UPDATE_PREFS_KEY_NOTIFIED, version).apply()
}

fun getNotifiedUpdateVersion(context: Context): String =
  updatePrefs(context).getString(UPDATE_PREFS_KEY_NOTIFIED, "").orEmpty()

/** The version to offer in the update banner, or "" when there is nothing to offer. */
fun pendingUpdateVersion(context: Context): String {
  val latest = getLatestKnownVersion(context)
  if (latest.isEmpty()) return ""
  if (!isNewerVersion(latest, BuildConfig.VERSION_NAME)) return ""
  return latest.takeIf { it != getDismissedUpdateVersion(context) }.orEmpty()
}
