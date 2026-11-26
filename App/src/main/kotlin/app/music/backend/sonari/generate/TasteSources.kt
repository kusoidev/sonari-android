package app.music.backend.sonari.generate

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.music.backend.sonari.constants.HideExplicitKey
import app.music.backend.sonari.constants.SoundCloudClientIdKey
import app.music.backend.sonari.innertube.YouTube
import app.music.backend.sonari.innertube.models.Artist
import app.music.backend.sonari.innertube.models.SongItem
import app.music.backend.sonari.innertube.models.WatchEndpoint
import app.music.backend.sonari.playback.SOUNDCLOUD_MEDIA_PREFIX
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * More songs by an artist the user already plays, plus any artist another provider named as similar.
 * YouTube Music has no "artist songs" endpoint here, so the artist name is searched for songs, and
 * every result is checked to really be by that artist before it is offered.
 */
internal suspend fun collectArtistSuggestions(
  seeds: List<TasteSeed>,
  exclusions: Set<String>,
  extraArtists: List<String> = emptyList(),
  perArtist: Int = 3,
  maxArtists: Int = 4,
): List<TasteSuggestion> = coroutineScope {
  val fromSeeds = seeds.map { it.artist.trim() }.filter { it.isNotBlank() }
  val artists =
    (fromSeeds + extraArtists.map { it.trim() })
      .filter { it.isNotBlank() }
      .distinct()
      .take(maxArtists)

  artists.map { artist ->
    async(Dispatchers.IO) {
      val seed = seeds.firstOrNull { it.artist.trim().equals(artist, ignoreCase = true) }
      val baseScore = (seed?.score ?: 4f) * SEED_WEIGHT + sourceBonusFor(TasteSource.ARTIST)
      runCatching {
        YouTube.search(artist, YouTube.SearchFilter.FILTER_SONG)
          .getOrNull()
          ?.items
          ?.filterIsInstance<SongItem>()
          ?.filter { song ->
            artistMatches(song, artist) &&
              trackKeyOf(song.title, song.artists.joinToString(", ") { it.name }) !in exclusions
          }
          ?.distinctBy { songKeyOf(it) }
          ?.take(perArtist)
          ?.mapIndexed { index, song ->
            TasteSuggestion(
              song = song,
              source = TasteSource.ARTIST,
              reason = "More from $artist",
              score = baseScore - index * RANK_DECAY,
            )
          }
          .orEmpty()
      }.getOrDefault(emptyList())
    }
  }.awaitAll().flatten()
}

/** YouTube's own graph: what gets played next after a seed, and the radios around it. */
internal suspend fun collectRadioSuggestions(
  seeds: List<TasteSeed>,
  exclusions: Set<String>,
  maxSeeds: Int = 5,
): List<TasteSuggestion> {
  val out = mutableListOf<TasteSuggestion>()

  for (seed in seeds.filter { it.videoId != null }.take(maxSeeds)) {
    val videoId = seed.videoId ?: continue
    val next = runCatching { YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull() }.getOrNull()
      ?: continue

    next.items
      .filterIsInstance<SongItem>()
      .filter { song -> songKeyOf(song) !in exclusions }
      .distinctBy { songKeyOf(it) }
      .take(4)
      .forEachIndexed { index, song ->
        out +=
          TasteSuggestion(
            song = song,
            source = TasteSource.SOUNDS_LIKE,
            reason = "Sounds like ${seed.title}",
            score = seed.score * SEED_WEIGHT + sourceBonusFor(TasteSource.SOUNDS_LIKE) - index * RANK_DECAY,
          )
      }

    next.relatedEndpoint?.let { endpoint ->
      runCatching { YouTube.related(endpoint).getOrNull()?.songs.orEmpty() }.getOrDefault(emptyList())
        .filter { song -> songKeyOf(song) !in exclusions }
        .distinctBy { songKeyOf(it) }
        .take(3)
        .forEachIndexed { index, song ->
          out +=
            TasteSuggestion(
              song = song,
              source = TasteSource.RADIO,
              reason = "Radio of ${seed.title}",
              score = seed.score * SEED_WEIGHT + sourceBonusFor(TasteSource.RADIO) - index * RANK_DECAY,
            )
        }
    }
  }

  return out
}

data class SoundCloudTrack(
  val id: String,
  val title: String,
  val artist: String,
  val durationMs: Long = 0L,
  val artworkUrl: String? = null,
)

/**
 * SoundCloud's related tracks, played straight from SoundCloud's progressive stream. It stays off
 * until a client ID is filled in, since the API answers nothing without one.
 */
@Singleton
class SoundCloudTasteSource
@Inject
constructor(
  private val dataStore: DataStore<Preferences>,
) {
  private val client =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .build()

  suspend fun suggest(seeds: List<TasteSeed>, limit: Int = 6): List<TasteSuggestion> =
    withContext(Dispatchers.IO) {
      val clientId =
        runCatching { dataStore.data.first()[SoundCloudClientIdKey] }.getOrNull()?.trim().orEmpty()
      if (clientId.isBlank()) return@withContext emptyList()

      val out = mutableListOf<TasteSuggestion>()

      for (seed in seeds.take(2)) {
        if (out.size >= limit) break
        val anchor =
          search(clientId, "${seed.artist} ${seed.title}".trim(), limit = 3).firstOrNull() ?: continue
        val related = related(clientId, anchor.id, limit = 8)
        related.forEachIndexed { index, track ->
          if (out.size >= limit) return@forEachIndexed
          if (track.id.isBlank() || track.title.isBlank()) return@forEachIndexed
          out +=
            TasteSuggestion(
              song =
                SongItem(
                  id = SOUNDCLOUD_MEDIA_PREFIX + track.id,
                  title = track.title,
                  artists = listOf(Artist(name = track.artist, id = null)),
                  album = null,
                  duration = (track.durationMs / 1000).toInt().takeIf { it > 0 },
                  thumbnail = track.artworkUrl.orEmpty(),
                ),
              source = TasteSource.SOUNDCLOUD,
              reason = "Found on SoundCloud near ${seed.title}",
              score = seed.score * SEED_WEIGHT + sourceBonusFor(TasteSource.SOUNDCLOUD) - index * RANK_DECAY,
            )
        }
      }

      out
    }

  private fun search(clientId: String, query: String, limit: Int): List<SoundCloudTrack> {
    if (query.isBlank()) return emptyList()
    val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrDefault(query)
    return request("https://api-v2.soundcloud.com/search/tracks?q=$encoded&limit=$limit&client_id=$clientId")
  }

  private fun related(clientId: String, trackId: String, limit: Int): List<SoundCloudTrack> =
    request("https://api-v2.soundcloud.com/tracks/$trackId/related?limit=$limit&client_id=$clientId")

  private fun request(url: String): List<SoundCloudTrack> =
    runCatching {
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { result ->
        if (!result.isSuccessful) return emptyList()
        parse(result.body?.string().orEmpty())
      }
    }.onFailure { Timber.d(it, "SoundCloud request failed") }.getOrDefault(emptyList())

  private fun parse(body: String): List<SoundCloudTrack> {
    val array =
      runCatching { JSONArray(body) }.getOrNull()
        ?: runCatching { JSONObject(body).optJSONArray("collection") }.getOrNull()
        ?: return emptyList()

    return (0 until array.length()).mapNotNull { index ->
      val track = array.optJSONObject(index) ?: return@mapNotNull null
      val id = track.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val title = track.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
      SoundCloudTrack(
        id = id,
        title = title,
        artist = track.optJSONObject("user")?.optString("username").orEmpty(),
        durationMs = track.optLong("full_duration").takeIf { it > 0 } ?: track.optLong("duration"),
        artworkUrl = track.optString("artwork_url").takeIf { it.isNotBlank() },
      )
    }
  }
}
