package app.music.backend.sonari.innertube.models.body

import app.music.backend.sonari.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class FeedbackBody(
  val context: Context,
  val feedbackTokens: List<String>,
  val isFeedbackTokenUnencrypted: Boolean = false,
  val shouldMerge: Boolean = false,
)
