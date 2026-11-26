package app.music.backend.sonari.generate

import app.music.backend.sonari.innertube.models.Artist
import app.music.backend.sonari.innertube.models.SongItem
import app.music.backend.sonari.db.MusicDatabase
import app.music.backend.sonari.db.daos.RecommendationExclusionDao
import app.music.backend.sonari.db.daos.SongPlayStatsDao
import app.music.backend.sonari.db.entities.PlaylistEntity
import app.music.backend.sonari.db.entities.PlaylistSongMap
import app.music.backend.sonari.db.entities.Song
import app.music.backend.sonari.db.entities.SongPlayStatsEntity
import app.music.backend.sonari.models.toMediaMetadata
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber

internal const val MAX_SUGGESTIONS = 25
internal const val PLAY_WEIGHT = 2.0f
internal const val SKIP_PENALTY = 3.0f
internal const val LIKED_BONUS = 5.0f
internal const val RECENCY_BONUS = 1.5f
internal const val TIME_OF_DAY_BONUS = 1.3f

private const val YOUTUBE_ID_LENGTH = 11

private val MORNING = 6..11
private val AFTERNOON = 12..17
private val EVENING = 18..21

internal fun currentTimeOfDay(hour: Int = LocalTime.now().hour): String = when (hour) {
  in MORNING -> "morning"
  in AFTERNOON -> "afternoon"
  in EVENING -> "evening"
  else -> "night"
}

internal fun scoreStats(
  stats: SongPlayStatsEntity,
  likedKeys: Set<String>,
  timeOfDay: String,
): Float {
  val isLiked = stats.trackKey in likedKeys
  val isRecent = System.currentTimeMillis() - stats.lastPlayedAtMillis < 7L * 86_400_000L

  val playMinutes = (stats.totalPlayTimeMs.coerceAtLeast(0L) / 60_000.0)
  val normalizedPlayTime = kotlin.math.ln(1.0 + playMinutes).toFloat()
  var score = normalizedPlayTime * PLAY_WEIGHT
  score -= stats.skipCount.toFloat() * SKIP_PENALTY
  if (isLiked) score += LIKED_BONUS
  if (isRecent) score += RECENCY_BONUS

  val hourBonus = when (timeOfDay) {
    "morning" -> if (isRecent) TIME_OF_DAY_BONUS else 1.0f
    "night" -> if (isLiked) TIME_OF_DAY_BONUS else 1.0f
    else -> 1.0f
  }
  score *= hourBonus
  return score.coerceAtLeast(0f)
}

internal fun applyCandidateFilter(stats: SongPlayStatsEntity): Boolean =
  stats.skipCount < 2 || stats.totalPlayTimeMs > 45_000L

/**
 * Turns the listening history into seeds, then asks every source for songs that might follow them.
 * The base engine knows YouTube Music only; [RecommendationEngine] adds Last.fm and SoundCloud
 * on top of it.
 */
@Singleton
open class LocalTasteEngine @Inject constructor(
  private val songPlayStatsDao: SongPlayStatsDao,
  private val recommendationExclusionDao: RecommendationExclusionDao,
  protected val database: MusicDatabase? = null,
) {
  private val _mix = MutableStateFlow<TasteMix?>(null)

  /** The last mix that was built, so the screen can show why each song is in it. */
  val mix: StateFlow<TasteMix?> = _mix.asStateFlow()

  /** Tests build the engine without a database; a real run always has one. */
  open var allowNullDatabaseForTesting: Boolean = false

  open suspend fun generate(count: Int = MAX_SUGGESTIONS): Result<String> =
    withContext(Dispatchers.IO) {
      try {
        if (database == null && !allowNullDatabaseForTesting) {
          return@withContext Result.failure(
            IllegalStateException("Database not available to persist playlist")
          )
        }

        val seeds = loadTasteSeeds()
        if (seeds.isEmpty()) {
          return@withContext Result.failure(
            IllegalStateException("No listening history available yet")
          )
        }

        val exclusions = recommendationExclusionDao.getAll().map { it.trackKey }.toSet()
        val eligibleSeeds = seeds.filter { it.key !in exclusions }.take(8)
        if (eligibleSeeds.isEmpty()) {
          return@withContext Result.failure(
            IllegalStateException("All candidate tracks have been excluded")
          )
        }

        val knownKeys = runCatching { songPlayStatsDao.getAllTrackKeys().toSet() }
          .getOrDefault(emptySet())

        val raw = collectSuggestions(eligibleSeeds, exclusions, count)
        val adjusted = runCatching { adjustScores(raw) }.getOrDefault(raw)
        val suggestions = assembleTasteMix(adjusted, knownKeys, count)
        val finalSuggestions =
          if (suggestions.size >= 5) suggestions
          else topUpWithSeeds(suggestions, eligibleSeeds, count)

        if (finalSuggestions.isEmpty()) {
          return@withContext Result.failure(
            IllegalStateException("Could not generate playlist from seeds")
          )
        }

        val timeOfDay = currentTimeOfDay()
        val playlistId = "local_taste_$timeOfDay"
        val playlistName = "Taste Mix (${timeOfDay.replaceFirstChar { it.uppercase() }})"

        persistPlaylist(playlistId, playlistName, finalSuggestions.map { it.song })
        _mix.value = TasteMix(playlistId, playlistName, finalSuggestions)

        Result.success(playlistId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Taste mix generation failed")
        Result.failure(e)
      }
    }

  /** Everything the user played, liked or kept offline, best first. */
  protected suspend fun loadTasteSeeds(): List<TasteSeed> {
    val timeOfDay = currentTimeOfDay()
    val thirtyDaysAgo = System.currentTimeMillis() - 30L * 86_400_000L

    val recentStats = songPlayStatsDao.mostPlayedSince(thirtyDaysAgo, limit = 100)
      .ifEmpty { songPlayStatsDao.recentlyPlayed(limit = 20) }
      .filter(::applyCandidateFilter)

    val likedKeys = database?.likedSongsByRowIdAsc()?.firstOrNull().orEmpty()
      .map { song -> trackKeyOf(song.song.title, artistLineOf(song)) }
      .toSet()

    val downloads = database?.downloadedSongsByPlayTimeAsc()?.firstOrNull().orEmpty()
    val downloadedKeys = downloads
      .map { song -> trackKeyOf(song.song.title, artistLineOf(song)) }
      .toSet()

    val seen = mutableSetOf<String>()
    val seeds = mutableListOf<TasteSeed>()

    recentStats.forEach { stats ->
      if (!seen.add(stats.trackKey)) return@forEach
      val downloaded = stats.trackKey in downloadedKeys
      seeds +=
        TasteSeed(
          key = stats.trackKey,
          title = stats.title,
          artist = stats.artist,
          videoId = stats.videoId,
          downloaded = downloaded,
          score = scoreStats(stats, likedKeys, timeOfDay) + if (downloaded) DOWNLOADED_BONUS else 0f,
          artworkUrl = stats.artworkUrl,
        )
    }

    // A download is a choice too: songs kept offline count even before they are played much.
    downloads.forEach { song ->
      val key = trackKeyOf(song.song.title, artistLineOf(song))
      if (!seen.add(key)) return@forEach
      val playedMinutes = (song.song.totalPlayTime / 60_000.0).coerceAtMost(120.0)
      seeds +=
        TasteSeed(
          key = key,
          title = song.song.title,
          artist = artistLineOf(song),
          videoId = song.song.id.takeIf { !song.song.isLocal && it.length == YOUTUBE_ID_LENGTH },
          downloaded = true,
          score = DOWNLOADED_BONUS + playedMinutes.toFloat() * 0.05f,
          artworkUrl = song.song.thumbnailUrl,
        )
    }

    return seeds.sortedByDescending { it.score }
  }

  /** The sources the base engine knows: the artists themselves, "sounds like" and the radio. */
  protected open suspend fun collectSuggestions(
    seeds: List<TasteSeed>,
    exclusions: Set<String>,
    count: Int,
  ): List<TasteSuggestion> {
    val similar = runCatching { collectRadioSuggestions(seeds, exclusions) }
      .getOrDefault(emptyList())
    val artists = runCatching { collectArtistSuggestions(seeds, exclusions) }
      .getOrDefault(emptyList())
    return similar + artists
  }

  /** A subclass may reweigh suggestions here before the mix is assembled and cut to size. */
  protected open suspend fun adjustScores(
    suggestions: List<TasteSuggestion>,
  ): List<TasteSuggestion> = suggestions

  private fun topUpWithSeeds(
    suggestions: List<TasteSuggestion>,
    seeds: List<TasteSeed>,
    count: Int,
  ): List<TasteSuggestion> {
    val picked = suggestions.toMutableList()
    val seen = picked.map { it.key }.toMutableSet()

    for (seed in seeds) {
      if (picked.size >= count) break
      val videoId = seed.videoId ?: continue
      if (!seen.add(seed.key)) continue
      picked +=
        TasteSuggestion(
          song =
            SongItem(
              id = videoId,
              title = seed.title,
              artists = listOf(Artist(name = seed.artist, id = null)),
              album = null,
              duration = null,
              thumbnail = seed.artworkUrl.orEmpty(),
            ),
          source = TasteSource.HISTORY,
          reason = "You keep coming back to this",
          score = seed.score,
        )
    }

    return picked
  }

  private fun artistLineOf(song: Song): String =
    song.artists.joinToString(", ") { it.name }.trim()

  protected suspend fun persistPlaylist(
    playlistId: String,
    playlistName: String,
    songs: List<SongItem>,
  ) {
    database?.withTransaction {
      clearPlaylist(playlistId)
      insert(
        PlaylistEntity(
          id = playlistId,
          name = playlistName,
          browseId = null,
          bookmarkedAt = java.time.LocalDateTime.now(),
          isLocal = true,
          isEditable = true
        )
      )

      songs.forEachIndexed { index, songItem ->
        insert(songItem.toMediaMetadata())
        insert(
          PlaylistSongMap(
            playlistId = playlistId,
            songId = songItem.id,
            position = index,
          )
        )
      }
    }
  }
}
