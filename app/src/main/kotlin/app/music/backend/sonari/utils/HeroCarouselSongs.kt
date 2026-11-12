package app.music.backend.sonari.utils

import app.music.backend.sonari.innertube.models.Album
import app.music.backend.sonari.innertube.models.Artist
import app.music.backend.sonari.innertube.models.SongItem
import app.music.backend.sonari.innertube.models.filterExplicit
import app.music.backend.sonari.innertube.models.filterBlockedArtists
import app.music.backend.sonari.innertube.models.filterVideoSongs
import app.music.backend.sonari.db.entities.Song

fun Song.toCarouselSong() =
  SongItem(
    id = id,
    title = title,
    artists = artists.map { Artist(name = it.name, id = it.id) },
    album = album?.let { Album(name = it.title, id = it.id) },
    duration = song.duration,
    thumbnail = thumbnailUrl.orEmpty(),
    explicit = song.explicit,
    musicVideoType = if (song.isVideo) "MUSIC_VIDEO_TYPE_OMV" else null,
    libraryAddToken = song.libraryAddToken,
    libraryRemoveToken = song.libraryRemoveToken,
  )

fun selectHeroCarouselSongs(
  recentlyPlayed: List<SongItem>,
  library: List<SongItem>,
  recommendations: List<SongItem>,
  account: List<SongItem> = emptyList(),
  excludedSongIds: Set<String> = emptySet(),
  blockedArtists: Set<String> = emptySet(),
  hideExplicit: Boolean = false,
  hideVideoSongs: Boolean = false,
  limit: Int = 20,
): List<SongItem> {
  if (limit <= 0) return emptyList()

  val sources =
    listOf(recentlyPlayed, interleaveSongs(listOf(library, account)), recommendations).map { songs ->
      songs.filterNot { it.id in excludedSongIds }
        .filterBlockedArtists(blockedArtists)
        .filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).distinctBy { it.id }
    }
  val candidates = interleaveSongs(sources).distinctBy { it.id }

  val artwork = mutableSetOf<String>()
  val selected = mutableListOf<SongItem>()
  val repeatedArtwork = mutableListOf<SongItem>()
  for (song in candidates) {
    val artworkKeys = listOfNotNull(
      song.album?.id?.let { "album:$it" },
      song.thumbnail.substringBefore('?').substringBefore('=')
        .takeIf { it.isNotBlank() }?.let { "art:$it" },
    )
    if (artworkKeys.none { it in artwork }) {
      artwork.addAll(artworkKeys)
      selected.add(song)
      if (selected.size == limit) return selected
    } else {
      repeatedArtwork.add(song)
    }
  }
  return (selected + repeatedArtwork).take(limit)
}

private fun interleaveSongs(sources: List<List<SongItem>>) = buildList {
  for (index in 0 until (sources.maxOfOrNull { it.size } ?: 0)) {
    sources.forEach { source -> source.getOrNull(index)?.let { add(it) } }
  }
}
