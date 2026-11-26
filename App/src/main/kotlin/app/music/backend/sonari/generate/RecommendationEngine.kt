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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

const val SIMILAR_TRACK_BONUS = 2.0f
const val SIMILAR_ARTIST_BONUS = 1.0f
const val TAG_MATCH_BONUS = 1.5f
const val GENRE_MATCH_BONUS = 1.0f

private const val SOURCE_TIMEOUT_MILLIS = 8_000L

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
    deep: Boolean,
  ): List<TasteSuggestion> = withContext(Dispatchers.IO) {
    // The engine's own sources and the other providers are asked at once: one behind the other,
    // the first mix waited for the radio to answer and then for Last.fm on top of it.
    val local = async { runCatching { localSuggestions(seeds, exclusions, count, deep) } }

    val tasteProfile = runCatching { tasteProfileProvider.get() }.getOrNull()

    val external = coroutineScope {
      val jobs = mutableListOf<Deferred<List<TasteSuggestion>>>()

      jobs += async { collectLastFmSuggestions(seeds, exclusions, tasteProfile, deep) }

      // SoundCloud answers last and slowly, and only ever adds a handful of songs: it is asked for
      // the mix the row leads with, not for every one of the four.
      if (deep) {
        soundCloudTasteSource?.let { source ->
          jobs +=
            async {
              withTimeoutOrNull(SOURCE_TIMEOUT_MILLIS) { source.suggest(seeds, limit = count / 3) }
                .orEmpty()
            }
        }
      }

      jobs.map { job -> runCatching { job.await() }.getOrDefault(emptyList()) }.flatten()
    }

    val base = local.await().getOrDefault(emptyList())
    Timber.d("Taste mix sources: ${base.size} local, ${external.size} external")
    base + external
  }

  /**
   * The engine's own sources, as [LocalTasteEngine] runs them. They are asked from a method of
   * their own so they can run beside Last.fm instead of before it.
   */
  private suspend fun localSuggestions(
    seeds: List<TasteSeed>,
    exclusions: Set<String>,
    count: Int,
    deep: Boolean,
  ): List<TasteSuggestion> = super.collectSuggestions(seeds, exclusions, count, deep)

  /** Last.fm knows what sounds like a track and who sounds like an artist. */
  private suspend fun collectLastFmSuggestions(
    seeds: List<TasteSeed>,
    exclusions: Set<String>,
    tasteProfile: TasteProfile?,
    deep: Boolean,
  ): List<TasteSuggestion> = coroutineScope {
    val topSeeds = seeds.take(if (deep) 3 else 1)
    val similarArtistNames = mutableSetOf<String>()

    val similarTracks = topSeeds.map { seed ->
      async(Dispatchers.IO) {
        val tracks = runCatching { lastFmTasteApi.getSimilarTracks(seed.artist, seed.title) }
          .getOrNull()
          ?.getOrNull()
          .orEmpty()

        // Every track is searched for at once: one after another, four of them held this seed up
        // for four searches in a row, and three seeds meant twelve of them before the mix could
        // even be assembled.
        tracks
          .take(if (deep) 4 else 2)
          .mapIndexed { index, track ->
            async(Dispatchers.IO) {
              if (trackKeyOf(track.name, track.artist) in exclusions) return@async null
              // The search must land on that artist: a different act with the same title is no use.
              val song =
                resolvePlayableSong(track.name, track.artist, hideExplicit = false)
                  ?: return@async null
              index to
                TasteSuggestion(
                  song = song,
                  source = TasteSource.SIMILAR_TRACK,
                  reason = "Sounds like ${seed.title}",
                  score =
                    seed.score * SEED_WEIGHT + sourceBonusFor(TasteSource.SIMILAR_TRACK) -
                      index * RANK_DECAY,
                )
            }
          }
          .awaitAll()
          .filterNotNull()
          .sortedBy { (index, _) -> index }
          .map { (_, suggestion) -> suggestion }
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
              maxArtists = if (deep) 4 else 2,
            )
          }
          .getOrDefault(emptyList())

    trackSuggestions + artistSuggestions
  }

  /** A genre the user keeps playing, or a long term artist, lifts a candidate above the rest. */
  override suspend fun adjustScores(
    suggestions: List<TasteSuggestion>,
  ): List<TasteSuggestion> {
    val topGenres = runCatching {
        genresRepository?.topGenres(12).orEmpty().map { it.genre.lowercase() }
      }
      .getOrDefault(emptyList())

    val topArtists = runCatching { tasteProfileProvider.get()?.topArtistNames.orEmpty() }
      .getOrDefault(emptyList())
      .map { it.trim().lowercase() }
      .filter { it.isNotEmpty() }

    if (topGenres.isEmpty() && topArtists.isEmpty()) return suggestions

    return suggestions.map { suggestion ->
      val title = suggestion.song.title.lowercase()
      val artist = suggestion.song.artists.joinToString(" ") { it.name }.lowercase()
      val artists = suggestion.song.artists.map { it.name.trim().lowercase() }

      var score = suggestion.score
      if (topGenres.any { genre -> genre.length > 2 && (title.contains(genre) || artist.contains(genre)) }) {
        score += GENRE_MATCH_BONUS
      }
      if (topArtists.any { loved -> artists.any { it.isNotEmpty() && (it == loved || it.contains(loved)) } }) {
        score += SIMILAR_ARTIST_BONUS
      }

      if (score != suggestion.score) suggestion.copy(score = score) else suggestion
    }
  }
}
