package app.music.backend.sonari.generate

import app.music.backend.sonari.innertube.YouTube
import app.music.backend.sonari.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Where a suggestion was found. Used to balance the mix and to explain it on screen. */
enum class TasteSource {
  /** Last.fm says this track sounds like the seed: the strongest signal there is. */
  SIMILAR_TRACK,

  /** The seed's own "up next" on YouTube Music. */
  SOUNDS_LIKE,

  /** Another song by an artist the user plays, or by one Last.fm calls similar. */
  ARTIST,

  SOUNDCLOUD,

  /** YouTube's radio around a seed. */
  RADIO,

  HISTORY,
}

/** A song the user clearly likes, taken from their listening history and downloads. */
data class TasteSeed(
  val key: String,
  val title: String,
  val artist: String,
  val videoId: String?,
  val downloaded: Boolean,
  val score: Float,
  val artworkUrl: String? = null,
  /** Played within the last week, so the artist is still on the user's mind. */
  val recent: Boolean = false,
)

/**
 * One proposed song, with the reason it was proposed and the score it earned. [reason] is what the
 * user reads on the mix screen, so every source phrases its own.
 */
data class TasteSuggestion(
  val song: SongItem,
  val source: TasteSource,
  val reason: String,
  val score: Float,
) {
  val key: String
    get() = songKeyOf(song)
}

data class TasteMix(
  val playlistId: String,
  val playlistName: String,
  val suggestions: List<TasteSuggestion>,
  /**
   * What the last build left, kept with the mix while it is read back from disk. The songs are the
   * playlist's own, so a card is there before this session's build has finished, reading its size
   * and cover from here.
   */
  val builtSongCount: Int = 0,
  val builtCoverUrl: String? = null,
) {
  /** How many songs the card says the mix holds. */
  val songCount: Int
    get() = suggestions.size.takeIf { it > 0 } ?: builtSongCount

  /** The cover the card shows: the first song's artwork, or the one the last build kept. */
  val artworkUrl: String?
    get() = suggestions.firstOrNull()?.song?.thumbnail ?: builtCoverUrl
}

const val DOWNLOADED_BONUS = 1.2f
const val SEED_WEIGHT = 0.6f

/** How much a known artist lifts a candidate, at full share of the listening. */
const val ARTIST_AFFINITY_BONUS = 3.0f

/** Every source returns its results in relevance order, so the nth one is worth this much less. */
const val RANK_DECAY = 0.35f

/** What each further provider agreeing on the same track adds, so a consensus beats one opinion. */
const val AGREEMENT_BONUS = 1.6f

/** What it adds when the song is by an artist the user played this week. */
const val FRESH_ARTIST_BONUS = 1.2f

/** Below this a "song" is a jingle; above it, a mix or a podcast episode. */
const val MIN_SUGGESTION_SECONDS = 45
const val MAX_SUGGESTION_SECONDS = 15 * 60

/** Excluded artists are stored in the same table as excluded tracks, under this prefix. */
const val ARTIST_KEY_PREFIX = "artist|"

/** How much a source's own claim is worth, on top of the seed score it grew from. */
fun sourceBonusFor(source: TasteSource): Float =
  when (source) {
    TasteSource.SIMILAR_TRACK -> 2.6f
    TasteSource.SOUNDS_LIKE -> 1.8f
    TasteSource.ARTIST -> 1.3f
    TasteSource.SOUNDCLOUD -> 1.1f
    TasteSource.RADIO -> 1.0f
    TasteSource.HISTORY -> 0f
  }

/** How many songs one source may add, so a single provider cannot flood the mix. */
internal fun sourceShare(source: TasteSource, count: Int): Int {
  val base = (count / 3).coerceAtLeast(3)
  return when (source) {
    TasteSource.SIMILAR_TRACK,
    TasteSource.SOUNDS_LIKE -> (count / 2).coerceAtLeast(base)
    TasteSource.ARTIST -> base + 1
    else -> base
  }
}

internal fun songKeyOf(song: SongItem): String =
  trackKeyOf(song.title, song.artists.joinToString(", ") { it.name })

internal fun artistKeyOf(artist: String): String = ARTIST_KEY_PREFIX + artist.trim().lowercase()

/** One line of artists ("A, B") split into the names it is made of, lowercased. */
internal fun artistNamesOf(artistLine: String): List<String> =
  artistLine.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

/**
 * Titles that almost never belong in a mix: sped up edits, nightcore, karaoke, "1 hour" loops.
 * A user who does listen to these keeps them, because the markers their own seeds carry are let
 * through (see [allowedMarkers] in [assembleTasteMix]).
 */
private val JUNK_TITLE_MARKERS =
  listOf(
    "sped up",
    "speed up",
    "slowed",
    "nightcore",
    "8d audio",
    "8d version",
    "bass boosted",
    "karaoke",
    "instrumental",
    "reaction",
    "mashup",
    "1 hour",
    "10 hours",
    "ringtone",
    "tutorial",
    "challenge",
  )

internal fun junkMarkersIn(text: String): Set<String> {
  val lower = text.lowercase()
  return JUNK_TITLE_MARKERS.filterTo(mutableSetOf()) { lower.contains(it) }
}

/** How much the user's own play history leans on each artist: 1.0 for the first, less for the rest. */
internal fun artistAffinityOf(seeds: List<TasteSeed>): Map<String, Float> {
  val totals = mutableMapOf<String, Float>()
  seeds.forEach { seed ->
    val key = seed.artist.trim().lowercase()
    if (key.isNotEmpty()) totals[key] = (totals[key] ?: 0f) + seed.score.coerceAtLeast(0f)
  }
  val top = totals.values.maxOrNull() ?: return emptyMap()
  if (top <= 0f) return emptyMap()
  return totals.mapValues { (_, value) -> (value / top).coerceIn(0f, 1f) }
}

/** The artist a suggestion is filed under, for the per-artist cap and the affinity lookup. */
internal fun primaryArtistOf(suggestion: TasteSuggestion): String =
  suggestion.song.artists.firstOrNull()?.name?.trim()?.lowercase().orEmpty()

/** How many providers proposed each track, before the copies are merged into one. */
internal fun sourceAgreementOf(raw: List<TasteSuggestion>): Map<String, Int> =
  raw.groupBy { it.key }.mapValues { (_, copies) -> copies.mapTo(mutableSetOf()) { it.source }.size }

/** True when the result really is by the artist another provider named. */
internal fun artistMatches(song: SongItem, artist: String): Boolean {
  val wanted = artist.trim().lowercase()
  if (wanted.isEmpty()) return true
  return song.artists.any { candidate ->
    val name = candidate.name.trim().lowercase()
    name.isNotEmpty() &&
      (name == wanted || name.contains(wanted) || (name.length >= 4 && wanted.contains(name)))
  }
}

/** Junk titles, banned artists and absurd lengths: anything that should never reach the mix. */
internal fun isOffTaste(
  suggestion: TasteSuggestion,
  excludedArtists: Set<String>,
  allowedMarkers: Set<String>,
): Boolean {
  if (suggestion.song.artists.any { it.name.trim().lowercase() in excludedArtists }) return true

  val duration = suggestion.song.duration
  if (duration != null && duration !in MIN_SUGGESTION_SECONDS..MAX_SUGGESTION_SECONDS) return true

  return (junkMarkersIn(suggestion.song.title) - allowedMarkers).isNotEmpty()
}

/**
 * Merges what every source proposed: lifts the artists the user actually plays, trusts a track more
 * when several providers named it, follows the artists played this week, drops what was already played
 * or is plainly not a song, keeps the best scored copy of each track, and caps both how much one
 * source may add and how often one artist may repeat.
 */
internal fun assembleTasteMix(
  raw: List<TasteSuggestion>,
  knownKeys: Set<String>,
  count: Int,
  artistAffinity: Map<String, Float> = emptyMap(),
  excludedArtists: Set<String> = emptySet(),
  allowedMarkers: Set<String> = emptySet(),
  maxPerArtist: Int = 3,
  freshArtists: Set<String> = emptySet(),
): List<TasteSuggestion> {
  val seen = mutableSetOf<String>()
  val usedPerSource = mutableMapOf<TasteSource, Int>()
  val usedPerArtist = mutableMapOf<String, Int>()
  val kept = mutableListOf<TasteSuggestion>()

  // How many providers named each track: YouTube Music and Last.fm agreeing is the best sign there
  // is, and it is counted before the copies are merged below.
  val agreement = sourceAgreementOf(raw)

  val ranked =
    raw
      .map { suggestion ->
        var score = suggestion.score
        val affinity = artistAffinity[primaryArtistOf(suggestion)] ?: 0f
        if (affinity > 0f) score += affinity * ARTIST_AFFINITY_BONUS
        val agreeing = agreement[suggestion.key] ?: 1
        if (agreeing > 1) score += (agreeing - 1) * AGREEMENT_BONUS
        if (primaryArtistOf(suggestion) in freshArtists) score += FRESH_ARTIST_BONUS
        if (score != suggestion.score) suggestion.copy(score = score) else suggestion
      }
      .sortedByDescending { it.score }

  for (suggestion in ranked) {
    if (suggestion.key in knownKeys) continue
    if (!seen.add(suggestion.key)) continue
    if (isOffTaste(suggestion, excludedArtists, allowedMarkers)) continue

    val used = usedPerSource[suggestion.source] ?: 0
    if (used >= sourceShare(suggestion.source, count)) continue

    val artist = primaryArtistOf(suggestion)
    if (artist.isNotEmpty() && (usedPerArtist[artist] ?: 0) >= maxPerArtist) continue

    usedPerSource[suggestion.source] = used + 1
    if (artist.isNotEmpty()) usedPerArtist[artist] = (usedPerArtist[artist] ?: 0) + 1
    kept += suggestion
  }

  return kept.sortedByDescending { it.score }.take(count)
}

/** Searches YouTube Music for a track another provider named, so the mix can actually play it. */
internal suspend fun resolvePlayableSong(
  title: String,
  artist: String,
  hideExplicit: Boolean,
): SongItem? = withContext(Dispatchers.IO) {
  if (title.isBlank()) return@withContext null
  val query = "$title $artist".trim()
  runCatching {
    YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
      .getOrNull()
      ?.items
      ?.filterIsInstance<SongItem>()
      ?.filter { !hideExplicit || !it.explicit }
      ?.firstOrNull { artistMatches(it, artist) }
  }.onFailure { Timber.d(it, "Could not resolve $query") }.getOrNull()
}
