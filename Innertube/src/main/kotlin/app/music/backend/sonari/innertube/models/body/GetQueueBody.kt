package app.music.backend.sonari.innertube.models.body

import app.music.backend.sonari.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetQueueBody(
  val context: Context,
  val videoIds: List<String>?,
  val playlistId: String?,
)
