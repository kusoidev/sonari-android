package app.music.backend.sonari.innertube.pages

import app.music.backend.sonari.innertube.models.SongItem

data class PlaylistContinuationPage(
  val songs: List<SongItem>,
  val continuation: String?,
)
