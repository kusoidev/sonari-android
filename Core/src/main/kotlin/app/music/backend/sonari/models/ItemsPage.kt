package app.music.backend.sonari.models

import app.music.backend.sonari.innertube.models.YTItem

data class ItemsPage(
  val items: List<YTItem>,
  val continuation: String?,
)
