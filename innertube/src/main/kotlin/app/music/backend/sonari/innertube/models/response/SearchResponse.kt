package app.music.backend.sonari.innertube.models.response

import app.music.backend.sonari.innertube.models.Continuation
import app.music.backend.sonari.innertube.models.MusicResponsiveListItemRenderer
import app.music.backend.sonari.innertube.models.Tabs
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
  val contents: Contents?,
  val continuationContents: ContinuationContents?,
) {
  @Serializable
  data class Contents(
    val tabbedSearchResultsRenderer: Tabs?,
  )

  @Serializable
  data class ContinuationContents(
    val musicShelfContinuation: MusicShelfContinuation,
  ) {
    @Serializable
    data class MusicShelfContinuation(
      val contents: List<Content>,
      val continuations: List<Continuation>?,
    ) {
      @Serializable
      data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer,
      )
    }
  }
}
