package app.music.backend.sonari.innertube.models.body

import app.music.backend.sonari.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
  val context: Context,
  val browseId: String?,
  val params: String?,
  val continuation: String?
)
