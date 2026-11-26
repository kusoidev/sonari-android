package app.music.backend.sonari.generate

import app.music.backend.sonari.innertube.models.Album
import app.music.backend.sonari.innertube.models.Artist
import app.music.backend.sonari.innertube.models.SongItem
import app.music.backend.sonari.db.MusicDatabase
import app.music.backend.sonari.db.daos.RecommendationExclusionDao
import app.music.backend.sonari.db.daos.SongPlayStatsDao
import app.music.backend.sonari.db.entities.PlaylistEntity
import app.music.backend.sonari.db.entities.RecommendationExclusionEntity
import app.music.backend.sonari.db.entities.PlaylistSongMap
import app.music.backend.sonari.db.entities.Song
import app.music.backend.sonari.db.entities.SongPlayStatsEntity
import app.music.backend.sonari.models.toMediaMetadata
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
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

/** A play this recent still says something about what the user is into right now. */
private const val RECENT_WINDOW_MILLIS = 7L * 86_400_000L

private const val YOUTUBE_ID_LENGTH = 11

/** The playlist the suggestions live in, built on its own and kept only when the user saves it. */
const val ForYouPlaylistId = "local_for_you"
const val ForYouPlaylistName = "For you"

/** How many mixes the app keeps ready; each grows from a different seed. */
const val ForYouMixCount = 4

/** A mix with fewer songs than this is dropped, so a thin build never leaves the row empty-handed. */
const val MinMixSongs = 6

/** A mix built from the library keeps its card with this few songs; the build tops it up as it can. */
private const val MinLocalMixSongs = 3

/** How far a mix built from the library may grow; anything more is what the network brings. */
private const val MaxLocalMixSongs = 12

/** What the first song of a library mix scores, and every one after it this much less. */
private const val FOR_YOU_TOP_SCORE = 20f

/** How long a mix may grow to; its real size is picked at random inside that range each build. */
const val MaxMixSongs = 75

/** "Recommend more like this" adds this many songs to the mix it was asked about. */
const val MoreLikeThisCount = 8

/** A song already in a kept mix is the best seed there is, so it outweighs any listening stat. */
private const val SimilarSeedScore = 12f

private fun mixPlaylistId(index: Int): String =
  if (index == 0) ForYouPlaylistId else "$ForYouPlaylistId$index"

private fun mixPlaylistName(index: Int, seedTitle: String): String =
  if (index == 0) ForYouPlaylistName else "$ForYouPlaylistName: $seedTitle"

/** The artist keys among the exclusions, as [artistKeyOf] writes them. */
internal fun excludedArtistKeys(entities: List<RecommendationExclusionEntity>): Set<String> =
  entities
    .asSequence()
    .filter { it.trackKey.startsWith(ARTIST_KEY_PREFIX) }
    .map { it.trackKey.removePrefix(ARTIST_KEY_PREFIX).trim().lowercase() }
    .filter { it.isNotEmpty() }
    .toSet()

/**
 * Groups the seeds into clusters of songs by one artist. Each cluster becomes one mix, so a mix
 * sounds like a place in the user's taste instead of a single song's radio, and two mixes never
 * stand on the same artist while others are still free.
 */
internal fun seedClusters(
  seeds: List<TasteSeed>,
  maxSeedsPerCluster: Int = 3,
  limit: Int = ForYouMixCount * 2,
): List<List<TasteSeed>> {
  val used = mutableSetOf<String>()
  val usedArtists = mutableSetOf<String>()
  val clusters = mutableListOf<List<TasteSeed>>()

  fun addCluster(seed: TasteSeed) {
    val artist = seed.artist.trim().lowercase()
    val companions =
      seeds
        .filter { other ->
          other.key != seed.key &&
            other.key !in used &&
            other.artist.trim().lowercase() == artist
        }
        .take(maxSeedsPerCluster - 1)
    val cluster = listOf(seed) + companions
    cluster.forEach { used += it.key }
    usedArtists += artist
    clusters += cluster
  }

  seeds.forEach { seed ->
    if (clusters.size >= limit || seed.key in used) return@forEach
    if (seed.artist.trim().lowercase() in usedArtists) return@forEach
    addCluster(seed)
  }

  // Few artists in the history: fill the remaining slots even though an artist repeats.
  seeds.forEach { seed ->
    if (clusters.size >= limit || seed.key in used) return@forEach
    addCluster(seed)
  }

  return clusters
}

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
  val isRecent = System.currentTimeMillis() - stats.lastPlayedAtMillis < RECENT_WINDOW_MILLIS

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

        val stored = recommendationExclusionDao.getAll()
        val exclusions = stored.mapTo(mutableSetOf()) { it.trackKey }
        val excludedArtists = excludedArtistKeys(stored)
        val eligibleSeeds =
          seeds
            .filter { seed ->
              seed.key !in exclusions && artistNamesOf(seed.artist).none { it in excludedArtists }
            }
            .take(8)
        if (eligibleSeeds.isEmpty()) {
          return@withContext Result.failure(
            IllegalStateException("All candidate tracks have been excluded")
          )
        }

        val suggestions = buildSuggestions(eligibleSeeds, exclusions, count)
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
        val playlistName = "For you (${timeOfDay.replaceFirstChar { it.uppercase() }})"

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

  /**
   * Builds several mixes and saves each as its own playlist. Every mix grows from a cluster of
   * songs by one artist the user plays, so each one is coherent on its own instead of being a
   * single track's radio, and a song used by one mix is not repeated in the next.
   */
  open suspend fun buildMixes(count: Int = 0): List<TasteMix> =
    withContext(Dispatchers.IO) {
      if (database == null) return@withContext emptyList()

      val seeds = loadTasteSeeds()
      if (seeds.isEmpty()) return@withContext emptyList()

      val stored = recommendationExclusionDao.getAll()
      val exclusions = stored.mapTo(mutableSetOf()) { it.trackKey }
      val excludedArtists = excludedArtistKeys(stored)
      val affinity = artistAffinityOf(seeds)
      val allowedMarkers = seeds.flatMapTo(mutableSetOf()) { junkMarkersIn(it.title) }
      val knownKeys = loadKnownKeys()

      // The artists played this week: their other songs are the next thing the user wants to hear.
      val freshArtists =
        seeds.filter { it.recent }.mapTo(mutableSetOf()) { it.artist.trim().lowercase() }

      val usable =
        seeds.filter { seed ->
          seed.key !in exclusions && artistNamesOf(seed.artist).none { it in excludedArtists }
        }
      if (usable.isEmpty()) return@withContext emptyList()

      val mixes = mutableListOf<TasteMix>()
      for (cluster in seedClusters(usable)) {
        if (mixes.size >= ForYouMixCount) break

        // Each mix is a different size, so the cards do not all read the same.
        val target = if (count > 0) count else Random.nextInt(MinMixSongs, MaxMixSongs + 1)

        val suggestions =
          buildSuggestions(
            eligibleSeeds = cluster,
            exclusions = exclusions,
            count = target,
            artistAffinity = affinity,
            excludedArtists = excludedArtists,
            allowedMarkers = allowedMarkers,
            knownKeys = knownKeys,
            freshArtists = freshArtists,
          )
        val filled =
          if (suggestions.size >= MinMixSongs) suggestions
          else topUpWithSeeds(suggestions, cluster, MinMixSongs)
        if (filled.size < MinMixSongs) continue

        // What one mix already used is not offered again by the next.
        exclusions += filled.map { it.key }

        val index = mixes.size
        val playlistId = mixPlaylistId(index)
        val playlistName = mixPlaylistName(index, cluster.first().title)
        persistPlaylist(playlistId, playlistName, filled.map { it.song })
        mixes += TasteMix(playlistId, playlistName, filled)
      }

      mixes
    }

  /**
   * The row as the app opens, built from what is already on the phone: the songs in the library, by
   * the artists the user plays. Nothing is fetched for it, so it stands within a moment, and the
   * mixes the network finds replace it as soon as they are ready.
   */
  open suspend fun buildLocalMixes(count: Int = 0): List<TasteMix> =
    withContext(Dispatchers.IO) {
      if (database == null) return@withContext emptyList()

      val library =
        runCatching { database?.allSongs()?.firstOrNull().orEmpty() }.getOrDefault(emptyList())
      if (library.isEmpty()) return@withContext emptyList()

      val seeds = runCatching { loadTasteSeeds() }.getOrDefault(emptyList())
      val stored = runCatching { recommendationExclusionDao.getAll() }.getOrDefault(emptyList())
      val exclusions = stored.mapTo(mutableSetOf()) { it.trackKey }
      val excludedArtists = excludedArtistKeys(stored)
      val playedArtists = seeds.mapTo(mutableSetOf()) { it.artist.trim().lowercase() }
      val recentKeys = seeds.filter { it.recent }.mapTo(mutableSetOf()) { it.key }

      // The artists the user plays come first, then the fullest ones fill the row out.
      val byArtist =
        library
          .filter { song ->
            trackKeyOf(song.song.title, artistLineOf(song)) !in exclusions &&
              artistNamesOf(artistLineOf(song)).none { it in excludedArtists }
          }
          .groupBy { artistLineOf(it).trim().lowercase() }
          .entries
          .sortedWith(
            compareByDescending<Map.Entry<String, List<Song>>> { entry ->
                entry.key in playedArtists
              }
              .thenByDescending { entry -> entry.value.size }
          )

      val target = if (count > 0) count else Random.nextInt(MinLocalMixSongs, MaxLocalMixSongs + 1)

      val mixes = mutableListOf<TasteMix>()
      val used = mutableSetOf<String>()

      for ((_, songs) in byArtist) {
        if (mixes.size >= ForYouMixCount) break

        val picked =
          songs
            .filter { song -> trackKeyOf(song.song.title, artistLineOf(song)) !in used }
            // What was played this week is not what the mix opens with.
            .sortedBy { song ->
              if (trackKeyOf(song.song.title, artistLineOf(song)) in recentKeys) 1 else 0
            }
            .take(target)
        if (picked.size < MinLocalMixSongs) continue
        picked.forEach { song -> used += trackKeyOf(song.song.title, artistLineOf(song)) }

        val index = mixes.size
        val playlistId = mixPlaylistId(index)
        val playlistName = mixPlaylistName(index, picked.first().song.title)
        val suggestions =
          picked.mapIndexed { position, song ->
            TasteSuggestion(
              song = tasteSongItemOf(song),
              source = TasteSource.ARTIST,
              reason = "More from ${artistLineOf(song)}",
              score = FOR_YOU_TOP_SCORE - position * RANK_DECAY,
            )
          }
        runCatching { persistPlaylist(playlistId, playlistName, suggestions.map { it.song }) }
        mixes += TasteMix(playlistId, playlistName, suggestions)
      }

      mixes
    }

  /** A song of the library as the mix screen plays it, so a mix can be opened and heard. */
  private fun tasteSongItemOf(song: Song): SongItem =
    SongItem(
      id = song.song.id,
      title = song.song.title,
      artists = song.artists.map { artist -> Artist(name = artist.name, id = artist.id) },
      album = song.album?.let { album -> Album(name = album.title, id = album.id) },
      duration = song.song.duration.takeIf { it > 0 },
      thumbnail = song.thumbnailUrl.orEmpty(),
    )

  /**
   * "Recommend more like this": grows a mix in place with songs that follow from what is already in
   * it, leaving its own songs untouched.
   */
  open suspend fun moreLikeThis(
    playlistId: String,
    count: Int = MoreLikeThisCount,
  ): List<TasteSuggestion> =
    withContext(Dispatchers.IO) {
      val db = database ?: return@withContext emptyList()
      val existing =
        runCatching { db.playlistSongs(playlistId).firstOrNull() }.getOrNull().orEmpty()
      if (existing.isEmpty()) return@withContext emptyList()

      val stored = recommendationExclusionDao.getAll()
      val exclusions = stored.mapTo(mutableSetOf()) { it.trackKey }
      val excludedArtists = excludedArtistKeys(stored)

      val seeds =
        existing.mapNotNull { row ->
          val song = row.song.song
          val artist = artistLineOf(row.song)
          if (artist.isBlank()) return@mapNotNull null
          TasteSeed(
            key = trackKeyOf(song.title, artist),
            title = song.title,
            artist = artist,
            videoId = song.id.takeIf { !song.isLocal && it.length == YOUTUBE_ID_LENGTH },
            downloaded = song.isDownloaded,
            score = SimilarSeedScore,
            artworkUrl = song.thumbnailUrl,
          )
        }
      if (seeds.isEmpty()) return@withContext emptyList()

      // Everything already in the mix is off the table, or the "more" would be the same songs.
      seeds.forEach { exclusions += it.key }

      val suggestions =
        buildSuggestions(
            eligibleSeeds = seeds.take(6),
            exclusions = exclusions,
            count = count,
            artistAffinity = artistAffinityOf(seeds),
            excludedArtists = excludedArtists,
            allowedMarkers = seeds.flatMapTo(mutableSetOf()) { junkMarkersIn(it.title) },
          )
          .filterNot { suggestion -> existing.any { it.song.id == suggestion.song.id } }
      if (suggestions.isEmpty()) return@withContext emptyList()

      db.withTransaction {
        var position = (existing.maxOfOrNull { it.map.position } ?: -1) + 1
        suggestions.forEach { suggestion ->
          insert(suggestion.song.toMediaMetadata())
          insert(
            PlaylistSongMap(
              playlistId = playlistId,
              songId = suggestion.song.id,
              position = position++,
            )
          )
        }
      }

      suggestions
    }

  /** Remembers that these songs are not wanted, so no mix offers them again. */
  open suspend fun exclude(
    tracks: List<Pair<String, String>>,
    excludeArtists: Boolean = false,
  ) = withContext(Dispatchers.IO) {
    if (tracks.isEmpty()) return@withContext
    val now = System.currentTimeMillis()

    val rows =
      tracks.flatMap { (title, artist) ->
        buildList {
          add(
            RecommendationExclusionEntity(
              trackKey = trackKeyOf(title, artist),
              excludedAtMillis = now,
              trackName = title,
              artistName = artist,
            )
          )
          // "Never anything like this" also covers the artist, who would otherwise come straight
          // back through "more from" on the next build. Each name is banned on its own, so a
          // duet's second artist is not let through by accident.
          if (excludeArtists) {
            artistNamesOf(artist).forEach { name ->
              add(
                RecommendationExclusionEntity(
                  trackKey = artistKeyOf(name),
                  excludedAtMillis = now,
                  trackName = name,
                  artistName = name,
                )
              )
            }
          }
        }
      }

    recommendationExclusionDao.upsertAll(rows.distinctBy { it.trackKey })
  }

  /** Drops a saved mix from the library when the user says it is not for them. */
  open suspend fun forget(playlistId: String) = withContext(Dispatchers.IO) {
    val db = database ?: return@withContext
    val playlist = runCatching { db.playlist(playlistId).firstOrNull() }.getOrNull()
      ?: return@withContext
    db.withTransaction { delete(playlist.playlist) }
  }

  /** Keeps the built playlist in the playlists list; before this it only exists on its own. */
  open suspend fun keep(playlistId: String): Boolean = withContext(Dispatchers.IO) {
    val db = database ?: return@withContext false
    val playlist = runCatching { db.playlist(playlistId).firstOrNull() }.getOrNull()
      ?: return@withContext false
    db.withTransaction {
      update(playlist.playlist.copy(bookmarkedAt = java.time.LocalDateTime.now()))
    }
    true
  }

  /**
   * The mix without saving a playlist: what the home row and the library show as soon as the app
   * opens, so the suggestions are there before the user asks for them.
   */
  open suspend fun suggest(count: Int = MAX_SUGGESTIONS): List<TasteSuggestion> =
    withContext(Dispatchers.IO) {
      try {
        val seeds = loadTasteSeeds()
        if (seeds.isEmpty()) return@withContext emptyList()

        val stored = recommendationExclusionDao.getAll()
        val exclusions = stored.mapTo(mutableSetOf()) { it.trackKey }
        val excludedArtists = excludedArtistKeys(stored)
        val eligibleSeeds =
          seeds
            .filter { seed ->
              seed.key !in exclusions && artistNamesOf(seed.artist).none { it in excludedArtists }
            }
            .take(8)
        if (eligibleSeeds.isEmpty()) return@withContext emptyList()

        buildSuggestions(eligibleSeeds, exclusions, count)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Taste mix suggestions failed")
        emptyList()
      }
    }

  private suspend fun buildSuggestions(
    eligibleSeeds: List<TasteSeed>,
    exclusions: Set<String>,
    count: Int,
    artistAffinity: Map<String, Float> = emptyMap(),
    excludedArtists: Set<String> = emptySet(),
    allowedMarkers: Set<String> = emptySet(),
    knownKeys: Set<String>? = null,
    freshArtists: Set<String> = emptySet(),
  ): List<TasteSuggestion> {
    // Anything already played or kept (downloaded, liked, in the library) is not a suggestion.
    val known = knownKeys ?: loadKnownKeys()

    val raw = collectSuggestions(eligibleSeeds, exclusions, count)
    val adjusted = runCatching { adjustScores(raw) }.getOrDefault(raw)
    return assembleTasteMix(
      raw = adjusted,
      knownKeys = known,
      count = count,
      artistAffinity = artistAffinity,
      excludedArtists = excludedArtists,
      allowedMarkers = allowedMarkers,
      freshArtists = freshArtists,
    )
  }

  /** Every song the user already has, as a set of track keys. Read once per build, not per mix. */
  private suspend fun loadKnownKeys(): Set<String> {
    val knownKeys = mutableSetOf<String>()
    knownKeys += runCatching { songPlayStatsDao.getAllTrackKeys() }.getOrDefault(emptyList())
    runCatching { database?.allSongs()?.firstOrNull().orEmpty() }
      .getOrDefault(emptyList())
      .forEach { song -> knownKeys += trackKeyOf(song.song.title, artistLineOf(song)) }
    runCatching { database?.downloadedSongsByCreateDateAsc()?.firstOrNull().orEmpty() }
      .getOrDefault(emptyList())
      .forEach { song -> knownKeys += trackKeyOf(song.song.title, artistLineOf(song)) }
    return knownKeys
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
          recent = System.currentTimeMillis() - stats.lastPlayedAtMillis < RECENT_WINDOW_MILLIS,
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
          bookmarkedAt = null,
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
