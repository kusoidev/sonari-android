package app.music.backend.sonari.playback.queues

import app.music.backend.sonari.innertube.models.SongItem

internal class RadioQueuePageFilter {
  private val seenSongIds = mutableSetOf<String>()

  fun reset() = seenSongIds.clear()

  fun filter(songs: List<SongItem>): List<SongItem> =
    songs.filter { seenSongIds.add(it.id) }
}
