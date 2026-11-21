package app.music.backend.sonari.innertube.pages

import app.music.backend.sonari.metadata.cleaner.ApiText
import app.music.backend.sonari.innertube.models.AlbumItem
import app.music.backend.sonari.innertube.models.Artist
import app.music.backend.sonari.innertube.models.MusicTwoRowItemRenderer
import app.music.backend.sonari.innertube.models.oddElements
import app.music.backend.sonari.innertube.models.splitBySeparator

object NewReleaseAlbumPage {
  fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
    return AlbumItem(
      browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
      playlistId =
        renderer.thumbnailOverlay
          ?.musicItemThumbnailOverlayRenderer
          ?.content
          ?.musicPlayButtonRenderer
          ?.playNavigationEndpoint
          ?.watchPlaylistEndpoint
          ?.playlistId ?: return null,
      title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
      artists =
        renderer.subtitle?.runs?.splitBySeparator()?.getOrNull(1)?.oddElements()?.map {
          Artist(
            name = ApiText.clean(it.text),
            id = it.navigationEndpoint?.browseEndpoint?.browseId,
          )
        } ?: return null,
      year = renderer.subtitle.runs.lastOrNull()?.text?.toIntOrNull(),
      thumbnail =
        renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
      explicit =
        renderer.subtitleBadges?.find {
          it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } != null,
    )
  }
}
