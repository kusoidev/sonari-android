package app.music.backend.sonari.innertube.models.response

import app.music.backend.sonari.innertube.models.PlaylistPanelRenderer
import kotlinx.serialization.Serializable

@Serializable
data class GetQueueResponse(
  val queueDatas: List<QueueData>,
) {
  @Serializable
  data class QueueData(
    val content: PlaylistPanelRenderer.Content,
  )
}
