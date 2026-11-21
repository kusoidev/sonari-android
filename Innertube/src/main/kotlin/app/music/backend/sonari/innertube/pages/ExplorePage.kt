package app.music.backend.sonari.innertube.pages

import app.music.backend.sonari.innertube.models.AlbumItem

data class ExplorePage(
  val newReleaseAlbums: List<AlbumItem>,
  val moodAndGenres: List<MoodAndGenres.Item>,
)
