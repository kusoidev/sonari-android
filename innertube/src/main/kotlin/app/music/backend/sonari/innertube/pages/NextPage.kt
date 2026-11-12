package app.music.backend.sonari.innertube.pages

import app.music.backend.sonari.metadata.cleaner.ApiText
import app.music.backend.sonari.innertube.models.Album
import app.music.backend.sonari.innertube.models.Artist
import app.music.backend.sonari.innertube.models.BrowseEndpoint
import app.music.backend.sonari.innertube.models.PlaylistPanelVideoRenderer
import app.music.backend.sonari.innertube.models.SongItem
import app.music.backend.sonari.innertube.models.WatchEndpoint
import app.music.backend.sonari.innertube.models.oddElements
import app.music.backend.sonari.innertube.models.splitBySeparator
import app.music.backend.sonari.innertube.utils.parseTime

data class NextResult(
  val title: String? = null,
  val items: List<SongItem>,
  val currentIndex: Int? = null,
  val lyricsEndpoint: BrowseEndpoint? = null,
  val relatedEndpoint: BrowseEndpoint? = null,
  val continuation: String?,
  val endpoint: WatchEndpoint, // current or continuation next endpoint
)

object NextPage {
  fun fromPlaylistPanelVideoRenderer(renderer: PlaylistPanelVideoRenderer): SongItem? {
    val longByLineRuns = renderer.longBylineText?.runs?.splitBySeparator() ?: return null

    // Extract library tokens using the new method that properly handles multiple toggle items
    val libraryTokens =
      PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

    return SongItem(
      id = renderer.videoId ?: return null,
      title = renderer.title?.runs?.firstOrNull()?.text ?: return null,
      artists =
        longByLineRuns.firstOrNull()?.oddElements()?.map {
          Artist(
            name = ApiText.clean(it.text),
            id = it.navigationEndpoint?.browseEndpoint?.browseId,
          )
        } ?: return null,
      album =
        longByLineRuns
          .getOrNull(1)
          ?.firstOrNull()
          ?.takeIf { it.navigationEndpoint?.browseEndpoint != null }
          ?.let {
            Album(
              name = ApiText.clean(it.text),
              id = it.navigationEndpoint?.browseEndpoint?.browseId!!,
            )
          },
      duration = renderer.lengthText?.runs?.firstOrNull()?.text?.parseTime() ?: return null,
      musicVideoType = renderer.navigationEndpoint.musicVideoType,
      thumbnail = renderer.thumbnail.thumbnails.lastOrNull()?.url ?: return null,
      explicit =
        renderer.badges?.find {
          it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } != null,
      libraryAddToken = libraryTokens.addToken,
      libraryRemoveToken = libraryTokens.removeToken
    )
  }
}
