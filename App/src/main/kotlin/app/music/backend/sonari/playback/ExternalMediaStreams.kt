package app.music.backend.sonari.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.music.backend.sonari.constants.SoundCloudClientIdKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

const val SOUNDCLOUD_MEDIA_PREFIX = "soundcloud:"

fun String.isProviderMediaId(): Boolean = startsWith(SOUNDCLOUD_MEDIA_PREFIX)

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val USER_AGENT = "Sonari/1.7"

/**
 * Audio that does not come from YouTube Music: a SoundCloud progressive stream. The media id names
 * the service and the url is fetched when playback reaches the track, then kept until it expires.
 */
@Singleton
class ExternalMediaStreams
@Inject
constructor(
  private val dataStore: DataStore<Preferences>,
) {
  private val client =
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .build()

  private val cache = ConcurrentHashMap<String, ResolvedStream>()

  fun isExternal(mediaId: String): Boolean = mediaId.isProviderMediaId()

  internal fun cached(mediaId: String): ResolvedStream? =
    cache[mediaId]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }

  internal suspend fun resolve(mediaId: String): ResolvedStream? = withContext(Dispatchers.IO) {
    cached(mediaId)?.let { return@withContext it }

    val stream =
      if (mediaId.startsWith(SOUNDCLOUD_MEDIA_PREFIX)) {
        soundCloudStream(mediaId.removePrefix(SOUNDCLOUD_MEDIA_PREFIX))
      } else {
        null
      }

    if (stream != null) {
      cache[mediaId] = stream
    } else {
      Timber.d("No audio from the provider for $mediaId")
    }
    stream
  }

  private fun soundCloudStream(trackId: String): ResolvedStream? {
    if (trackId.isBlank()) return null
    val clientId =
      runCatching { kotlinx.coroutines.runBlocking { dataStore.data.first()[SoundCloudClientIdKey] } }
        .getOrNull()
        ?.trim()
        .orEmpty()
    if (clientId.isBlank()) return null

    val transcodings =
      getJson("https://api-v2.soundcloud.com/tracks/$trackId/media/transcodings?client_id=$clientId")
        ?.optJSONArray("collection")
        ?: return null

    var progressive: String? = null
    for (index in 0 until transcodings.length()) {
      val transcoding = transcodings.optJSONObject(index) ?: continue
      if (transcoding.optString("protocol") != "progressive") continue
      if (!transcoding.optBoolean("is_legacy_transcoding", false)) continue
      progressive = transcoding.optString("url").takeIf { it.isNotBlank() }
      break
    }
    if (progressive == null) {
      for (index in 0 until transcodings.length()) {
        val transcoding = transcodings.optJSONObject(index) ?: continue
        if (transcoding.optString("protocol") != "progressive") continue
        progressive = transcoding.optString("url").takeIf { it.isNotBlank() }
        break
      }
    }

    val transcodingUrl = progressive ?: return null
    val streamUrl =
      getJson("$transcodingUrl?client_id=$clientId")
        ?.optString("url")
        ?.takeIf { it.isNotBlank() }
        ?: return null

    return ResolvedStream(
      url = streamUrl,
      expiresAtMs = System.currentTimeMillis() + DAY_MILLIS,
      headers = emptyMap(),
    )
  }

  private fun getJson(url: String): JSONObject? =
    runCatching {
        val response =
          client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute()
        response.use { result ->
          if (!result.isSuccessful) return null
          JSONObject(result.body?.string().orEmpty())
        }
      }
      .onFailure { Timber.d(it, "Could not read $url") }
      .getOrNull()
}
