package app.music.backend.sonari.innertube.models.body

import app.music.backend.sonari.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(
  val context: Context,
  val query: String?,
  val params: String?,
)
