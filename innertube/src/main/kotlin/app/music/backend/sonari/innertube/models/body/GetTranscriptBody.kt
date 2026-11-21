package app.music.backend.sonari.innertube.models.body

import app.music.backend.sonari.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptBody(
  val context: Context,
  val params: String,
)
