package app.music.backend.sonari.innertube.models.body

import app.music.backend.sonari.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetSearchSuggestionsBody(
  val context: Context,
  val input: String,
)
