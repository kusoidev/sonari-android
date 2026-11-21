package app.music.backend.sonari.innertube.pages

import app.music.backend.sonari.innertube.models.YTItem
import app.music.backend.sonari.innertube.models.filterBlockedArtists
import app.music.backend.sonari.innertube.models.filterExplicit
import app.music.backend.sonari.innertube.models.filterVideoSongs
import app.music.backend.sonari.innertube.models.filterYoutubeShorts

data class BrowseResult(
  val title: String?,
  val items: List<Item>,
) {
  data class Item(
    val title: String?,
    val items: List<YTItem>,
  )

  fun filterBlockedArtists() =
    copy(
      items =
        items.mapNotNull {
          it.copy(
            items =
              it.items.filterBlockedArtists().ifEmpty {
                return@mapNotNull null
              }
          )
        }
    )

  fun filterExplicit(enabled: Boolean = true) =
    if (enabled) {
      copy(
        items =
          items.mapNotNull {
            it.copy(
              items =
                it.items.filterExplicit().ifEmpty {
                  return@mapNotNull null
                },
            )
          },
      )
    } else {
      this
    }

  fun filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
      copy(
        items =
          items.mapNotNull {
            it.copy(
              items =
                it.items.filterVideoSongs(true).ifEmpty {
                  return@mapNotNull null
                },
            )
          },
      )
    } else {
      this
    }

  fun filterYoutubeShorts(enabled: Boolean = false) =
    if (enabled) {
      copy(
        items =
          items.mapNotNull {
            it.copy(
              items =
                it.items.filterYoutubeShorts(true).ifEmpty {
                  return@mapNotNull null
                },
            )
          },
      )
    } else {
      this
    }
}
