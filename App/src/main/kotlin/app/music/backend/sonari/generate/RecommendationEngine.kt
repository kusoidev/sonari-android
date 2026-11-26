package app.music.backend.sonari.generate

import app.music.backend.sonari.db.MusicDatabase
import app.music.backend.sonari.db.daos.RecommendationExclusionDao
import app.music.backend.sonari.db.daos.SongPlayStatsDao
import app.music.backend.sonari.utils.lastfm.LastFmTasteApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

const val SIMILAR_TRACK_BONUS = 2.0f
const val SIMILAR_ARTIST_BONUS = 1.0f
const val TAG_MATCH_BONUS = 1.5f
const val GENRE_MATCH_BONUS = 1.0f

private const val SOURCE_TIMEOUT_MILLIS = 20_000L

fun scoreCandidateWithExternals(
  baseScore: Float,
  isSimilarTrack: Boolean = false,
  isSimilarArtist: Boolean = false,
  matchesTopGenre: Boolean = false,
  matchesGenrePreference: Boolean = false,
): Float {
  var score = baseScore
  if (isSimilarTrack) score += SIMILAR_TRACK_BONUS
  if (isSimilarArtist) score += SIMILAR_ARTIST_BONUS
  if (matchesTopGenre) score += TAG_MATCH_BONUS
  if (matchesGenrePreference) score += GENRE_MATCH_BONUS
  return score
}

/**
 * Widens the base taste mix with other providers: Last.fm says what sounds like it and who sounds
 * like the artists the user plays, SoundCloud says what floats around the same tracks.
 */
@Singleton
open class RecommendationEngine @Inject constructor(
  private val songPlayStatsDao: SongPlayStatsDao,
  private val recommendationExclusionDao: RecommendationExclusionDao,
  private val tasteProfileProvider: TasteProfileProvider,
  private val lastFmTasteApi: LastFmTasteApi,
  private val genresRepository: GenresRepository? = null,
  database: MusicDatabase? = null,
  private val soundCloudTasteSource: SoundCloudTasteSource? = null,
) : LocalTasteEngine(songPlayStatsDao, recommendationExclusionDao, database) {

  override suspend fun collectSuggestions(
    seeds: List<TasteSeed>,
    exclusions: Set<String>,
    count: Int,
  ): List<TasteSuggestion> = withContext(Dispatchers.IO) {
    val base = runCatching { super.collectSuggestions(seeds, exclusions, count) }
      .getOrDefault(emptyList())

    val tasteProfile = runCatching { tasteProfileProvider.get() }.getOrNull()

    val external = coroutineScope {
      val jobs = mutableListOf<Deferred<List<TasteSuggestion>>>()

      jobs += async { collectLastFmSuggestions(seeds, exclusions, tasteProfile) }

      soundCloudTasteSource?.let { source ->
        jobs +=
          async {
            withTimeoutOrNull(SOURCE_TIMEOUT_MILLIS) { source.suggest(seeds, limit = count / 3) }
              .orEmpty()
          }
      }

      jobs.map { job -> runCatching { job.await() }.getOrDefault(emptyList()) }.flatten()
    }

    Timber.d("Taste mix sources: ${base.size} local, ${external.size} external")
    base + external
  }

  /** Last.fm knows what sounds like a track and who sounds like an artist. */
  private suspend fun collectLastFmSuggestions(
    seeds: List<TasteSeed>,
    exclusions: Set<String>,
    tasteProfile: TasteProfile?,
  ): List<TasteSuggestion> = coroutineScope {
    val topSeeds = seeds.take(3)
    val similarArtistNames = mutableSetOf<String>()

    val similarTracks = topSeeds.map { seed ->
      async(Dispatchers.IO) {
        val tracks = runCatching { lastFmTasteApi.getSimilarTracks(seed.artist, seed.title) }
          .getOrNull()
          ?.getOrNull()
          .orEmpty()

        val out = mutableListOf<TasteSuggestion>()
        for (track in tracks.take(4)) {
          if (trackKeyOf(track.name, track.artist) in exclusions) continue
          val song = resolvePlayableSong(track.name, track.artist, hideExplicit = false) ?: continue
          out +=
            TasteSuggestion(
              song = song,
              source = TasteSource.SOUNDS_LIKE,
              reason = "Sounds like ${seed.title}",
              score = seed.score * SEED_WEIGHT + sourceBonusFor(TasteSource.SOUNDS_LIKE),
            )
        }
        out
      }
    }

    val similarArtists = topSeeds.map { seed ->
      async(Dispatchers.IO) {
        runCatching { lastFmTasteApi.getSimilarArtists(seed.artist) }
          .getOrNull()
          ?.getOrNull()
          .orEmpty()
          .take(2)
          .map { it.name.trim() }
          .filter { it.isNotBlank() }
      }
    }

    val trackSuggestions = similarTracks.map { job -> runCatching { job.await() }.getOrDefault(emptyList()) }.flatten()
    similarArtists.map { job -> runCatching { job.await() }.getOrDefault(emptyList()) }
      .flatten()
      .forEach { similarArtistNames.add(it) }

    // The user's own long term artists are the strongest "more from" signal there is.
    tasteProfile?.topArtistNames.orEmpty().take(2).forEach { similarArtistNames.add(it) }

    val artistSuggestions =
      if (similarArtistNames.isEmpty()) emptyList()
      else
        runCatching {
            collectArtistSuggestions(
              seeds = seeds,
              exclusions = exclusions,
              extraArtists = similarArtistNames.toList(),
              perArtist = 2,
              maxArtists = 4,
            )
          }
          .getOrDefault(emptyList())

    trackSuggestions + artistSuggestions
  }

  /** A genre the user keeps playing lifts a candidate above the rest of its source. */
  override suspend fun adjustScores(
    suggestions: List<TasteSuggestion>,
  ): List<TasteSuggestion> {
    val topGenres = runCatching {
        genresRepository?.topGenres(12).orEmpty().map { it.genre.lowercase() }
      }
      .getOrDefault(emptyList())
    if (topGenres.isEmpty()) return suggestions

    return suggestions.map { suggestion ->
      val title = suggestion.song.title.lowercase()
      val artist = suggestion.song.artists.joinToString(" ") { it.name }.lowercase()
      val matches =
        topGenres.any { genre ->
          genre.length > 2 && (title.contains(genre) || artist.contains(genre))
        }
      if (matches) suggestion.copy(score = suggestion.score + GENRE_MATCH_BONUS)
      else suggestion
    }
  }
}
