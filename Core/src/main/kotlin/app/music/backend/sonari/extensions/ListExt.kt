package app.music.backend.sonari.extensions

import app.music.backend.sonari.db.entities.Album
import app.music.backend.sonari.db.entities.Playlist
import app.music.backend.sonari.db.entities.Song

fun <T> List<T>.reversed(reversed: Boolean) = if (reversed) asReversed() else this

fun <T> MutableList<T>.move(
  fromIndex: Int,
  toIndex: Int,
): MutableList<T> {
  add(toIndex, removeAt(fromIndex))
  return this
}

fun <T : Any> List<T>.mergeNearbyElements(
  key: (T) -> Any = { it },
  merge: (first: T, second: T) -> T = { first, _ -> first },
): List<T> {
  if (isEmpty()) return emptyList()

  val mergedList = mutableListOf<T>()
  var currentItem = this[0]

  for (i in 1 until size) {
    val nextItem = this[i]
    if (key(currentItem) == key(nextItem)) {
      currentItem = merge(currentItem, nextItem)
    } else {
      mergedList.add(currentItem)
      currentItem = nextItem
    }
  }
  mergedList.add(currentItem)

  return mergedList
}

fun List<Song>.filterExplicit(enabled: Boolean = true) =
  if (enabled) {
    filter { !it.song.explicit }
  } else {
    this
  }

/**
 * Video songs are never hidden any more: the "Hide video songs" option was removed because anything
 * played from the search VIDEOS tab then vanished from the library even though it had been
 * downloaded. The parameter is kept so existing call sites still compile.
 */
@Suppress("UNUSED_PARAMETER")
fun List<Song>.filterVideoSongs(enabled: Boolean = true) = this

fun List<Album>.filterExplicitAlbums(enabled: Boolean = true) =
  if (enabled) {
    filter { !it.album.explicit }
  } else {
    this
  }

fun List<Playlist>.filterYoutubeShorts(enabled: Boolean = false) =
  if (enabled) {
    filterNot { it.playlist.browseId?.startsWith("SS") == true }
  } else {
    this
  }
