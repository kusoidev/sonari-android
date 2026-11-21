package app.music.backend.sonari.innertube.pages

import app.music.backend.sonari.innertube.models.YTItem

data class ArtistItemsContinuationPage(
  val items: List<YTItem>,
  val continuation: String?,
)
