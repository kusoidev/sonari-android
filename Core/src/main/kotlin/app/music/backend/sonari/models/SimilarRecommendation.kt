package app.music.backend.sonari.models

import app.music.backend.sonari.innertube.models.YTItem
import app.music.backend.sonari.db.entities.LocalItem

data class SimilarRecommendation(
  val title: LocalItem,
  val items: List<YTItem>,
)
