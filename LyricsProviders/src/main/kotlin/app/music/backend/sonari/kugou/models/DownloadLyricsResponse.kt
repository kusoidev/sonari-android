package app.music.backend.sonari.kugou.models

import kotlinx.serialization.Serializable

@Serializable
data class DownloadLyricsResponse(
  val content: String,
)
