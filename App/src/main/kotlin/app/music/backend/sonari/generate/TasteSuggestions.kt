package app.music.backend.sonari.generate

import app.music.backend.sonari.innertube.YouTube
import app.music.backend.sonari.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Where a suggestion was found. Used to balance the mix and to explain it on screen. */
enum class TasteSource {
  SOUNDS_LIKE,
  ARTIST,
  SOUNDCLOUD,
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
    get() = trackKeyOf(song.title, song.artists.joinToString(", ") { it.name })
}

data class TasteMix(
  val playlistId: String,
  val playlistName: String,
  val suggestions: List<TasteSuggestion>,
)

const val DOWNLOADED_BONUS = 1.2f
const val SEED_WEIGHT = 0.6f

/** How much a source's own claim is worth, on top of the seed score it grew from. */
fun sourceBonusFor(source: TasteSource): Float =
  when (source) {
    TasteSource.SOUNDS_LIKE -> 1.8f
    TasteSource.ARTIST -> 1.2f
    TasteSource.SOUNDCLOUD -> 1.1f
    TasteSource.RADIO -> 1.0f
    TasteSource.HISTORY -> 0f
  }

/** How many songs one source may add, so a single provider cannot flood the mix. */
internal fun sourceShare(source: TasteSource, count: Int): Int {
  val base = (count / 3).coerceAtLeast(3)
  return when (source) {
    TasteSource.SOUNDS_LIKE -> (count / 2).coerceAtLeast(base)
    TasteSource.ARTIST -> base + 1
    else -> base
  }
}

/**
 * Merges what every source proposed: drops what the user has already played, keeps the best scored
 * copy of each track, limits how much one source may add, then orders the rest by score.
 */
internal fun assembleTasteMix(
  raw: List<TasteSuggestion>,
  knownKeys: Set<String>,
  count: Int,
): List<TasteSuggestion> {
  val seen = mutableSetOf<String>()
  val usedPerSource = mutableMapOf<TasteSource, Int>()
  val kept = mutableListOf<TasteSuggestion>()

  for (suggestion in raw.sortedByDescending { it.score }) {
    if (suggestion.key in knownKeys) continue
    if (!seen.add(suggestion.key)) continue

    val used = usedPerSource[suggestion.source] ?: 0
    if (used >= sourceShare(suggestion.source, count)) continue
    usedPerSource[suggestion.source] = used + 1
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
      ?.firstOrNull { !hideExplicit || !it.explicit }
  }.onFailure { Timber.d(it, "Could not resolve $query") }.getOrNull()
}
