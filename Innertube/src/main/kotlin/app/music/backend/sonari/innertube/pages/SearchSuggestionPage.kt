package app.music.backend.sonari.innertube.pages

import app.music.backend.sonari.metadata.cleaner.ApiText
import app.music.backend.sonari.innertube.models.Album
import app.music.backend.sonari.innertube.models.AlbumItem
import app.music.backend.sonari.innertube.models.Artist
import app.music.backend.sonari.innertube.models.ArtistItem
import app.music.backend.sonari.innertube.models.MusicResponsiveListItemRenderer
import app.music.backend.sonari.innertube.models.SongItem
import app.music.backend.sonari.innertube.models.YTItem
import app.music.backend.sonari.innertube.models.clean
import app.music.backend.sonari.innertube.models.oddElements
import app.music.backend.sonari.innertube.models.splitBySeparator

object SearchSuggestionPage {
  fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): YTItem? {
    return when {
      renderer.isSong -> {
        val secondaryLine =
          renderer.flexColumns
            .getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.splitBySeparator()
            ?.clean()
        SongItem(
          id =
            renderer.playlistItemData?.videoId
              ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
              ?: renderer.overlay
                ?.musicItemThumbnailOverlayRenderer
                ?.content
                ?.musicPlayButtonRenderer
                ?.playNavigationEndpoint
                ?.watchEndpoint
                ?.videoId
              ?: renderer.flexColumns
                .firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.firstOrNull()
                ?.navigationEndpoint
                ?.watchEndpoint
                ?.videoId
              ?: return null,
          title =
            renderer.flexColumns
              .firstOrNull()
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.text?.let(ApiText::clean) ?: return null,
          artists =
            secondaryLine?.firstOrNull()?.oddElements()?.map {
              Artist(
                name = ApiText.clean(it.text),
                id = it.navigationEndpoint?.browseEndpoint?.browseId,
              )
            } ?: return null,
          album =
            renderer.flexColumns
              .getOrNull(
                2,
              )
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.let {
                Album(
                  name = ApiText.clean(it.text),
                  id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                )
              },
          duration = null,
          musicVideoType = renderer.musicVideoType,
          thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
          explicit =
            renderer.badges?.find {
              it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
            } != null,
        )
      }
      renderer.isArtist -> {
        ArtistItem(
          id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
          title =
            renderer.flexColumns
              .firstOrNull()
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.text?.let(ApiText::clean) ?: return null,
          thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
          shuffleEndpoint =
            renderer.menu
              ?.menuRenderer
              ?.items
              ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
              ?.menuNavigationItemRenderer
              ?.navigationEndpoint
              ?.watchPlaylistEndpoint,
          radioEndpoint =
            renderer.menu
              ?.menuRenderer
              ?.items
              ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
              ?.menuNavigationItemRenderer
              ?.navigationEndpoint
              ?.watchPlaylistEndpoint,
        )
      }
      renderer.isAlbum -> {
        val secondaryLine =
          renderer.flexColumns
            .getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.splitBySeparator() ?: return null
        AlbumItem(
          browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
          playlistId =
            renderer.menu
              ?.menuRenderer
              ?.items
              ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
              ?.menuNavigationItemRenderer
              ?.navigationEndpoint
              ?.watchPlaylistEndpoint
              ?.playlistId ?: return null,
          title =
            renderer.flexColumns
              .firstOrNull()
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.text?.let(ApiText::clean) ?: return null,
          artists =
            secondaryLine.getOrNull(1)?.oddElements()?.map {
              Artist(
                name = ApiText.clean(it.text),
                id = it.navigationEndpoint?.browseEndpoint?.browseId,
              )
            } ?: return null,
          year = secondaryLine.lastOrNull()?.firstOrNull()?.text?.toIntOrNull(),
          thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
          explicit =
            renderer.badges?.find {
              it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
            } != null,
        )
      }
      else -> null
    }
  }
}
