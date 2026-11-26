package app.music.backend.sonari.ui.menu

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import app.music.backend.sonari.LocalListenTogetherManager
import app.music.backend.sonari.R
import app.music.backend.sonari.db.entities.Playlist
import app.music.backend.sonari.db.entities.PlaylistSong
import app.music.backend.sonari.ui.component.Material3MenuGroup
import app.music.backend.sonari.ui.component.Material3MenuItemData

@Composable
fun LocalPlaylistMenu(
  playlist: Playlist,
  songs: List<PlaylistSong>,
  context: Context,
  downloadState: Int,
  onEdit: () -> Unit,
  onSync: () -> Unit,
  onDelete: () -> Unit,
  onDownload: () -> Unit,
  onExport: () -> Unit,
  onModifyWithAi: () -> Unit,
  onQueue: () -> Unit,
  onDismiss: () -> Unit,
  onNotInterested: (() -> Unit)? = null
) {
  val listenTogetherManager = LocalListenTogetherManager.current
  val isGuest = listenTogetherManager?.isGuestPlaybackRestricted == true

  val downloadMenuItem: Material3MenuItemData? =
    when (downloadState) {
      Download.STATE_COMPLETED ->
        Material3MenuItemData(
          title = { Text(stringResource(R.string.remove_download)) },
          description = { Text(stringResource(R.string.remove_download_playlist_desc)) },
          icon = { Icon(painter = painterResource(R.drawable.offline), contentDescription = null) },
          onClick = {
            onDownload()
            onDismiss()
          }
        )
      Download.STATE_QUEUED,
      Download.STATE_DOWNLOADING ->
        Material3MenuItemData(
          title = { Text(stringResource(R.string.downloading)) },
          description = { Text(stringResource(R.string.download_in_progress_desc)) },
          icon = { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) },
          onClick = {
            onDownload()
            onDismiss()
          }
        )
      else -> null
    }

  val isYouTubePlaylist = playlist.playlist.browseId != null

  val menuItems = buildList {
    if (onNotInterested != null) {
      add(
        Material3MenuItemData(
          title = { Text("Not interested") },
          description = { Text("Never suggest these songs again") },
          icon = { Icon(painter = painterResource(R.drawable.close), contentDescription = null) },
          onClick = {
            onNotInterested.invoke()
            onDismiss()
          }
        )
      )
    }

    add(
      Material3MenuItemData(
        title = { Text(stringResource(R.string.edit)) },
        description = { Text(stringResource(R.string.edit_playlist)) },
        icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
        onClick = {
          onEdit()
          onDismiss()
        }
      )
    )

    add(
      Material3MenuItemData(
        title = { Text(stringResource(R.string.export_as_csv)) },
        description = { Text(stringResource(R.string.export_playlist_desc)) },
        icon = {
          Icon(painter = painterResource(R.drawable.file_export), contentDescription = null)
        },
        onClick = {
          onExport()
          onDismiss()
        }
      )
    )

    if (isYouTubePlaylist) {
      add(
        Material3MenuItemData(
          title = { Text(stringResource(R.string.action_sync)) },
          description = { Text(stringResource(R.string.sync_playlist_desc)) },
          icon = { Icon(painter = painterResource(R.drawable.sync), contentDescription = null) },
          onClick = {
            onSync()
            onDismiss()
          }
        )
      )
    }

    if (!isGuest) {
      add(
        Material3MenuItemData(
          title = { Text(stringResource(R.string.add_to_queue)) },
          description = { Text(stringResource(R.string.add_to_queue_desc)) },
          icon = {
            Icon(painter = painterResource(R.drawable.queue_music), contentDescription = null)
          },
          onClick = {
            onQueue()
            onDismiss()
          }
        )
      )
    }

    downloadMenuItem?.let(::add)

    add(
      Material3MenuItemData(
        title = { Text(stringResource(R.string.delete)) },
        description = { Text(stringResource(R.string.delete_playlist_desc)) },
        icon = { Icon(painter = painterResource(R.drawable.delete), contentDescription = null) },
        onClick = {
          onDelete()
          onDismiss()
        }
      )
    )
  }

  Material3MenuGroup(items = menuItems)
}

@Composable
fun AutoPlaylistMenu(
  downloadState: Int,
  onQueue: () -> Unit,
  onDownload: () -> Unit,
  onDismiss: () -> Unit
) {
  val listenTogetherManager = LocalListenTogetherManager.current
  val isGuest = listenTogetherManager?.isGuestPlaybackRestricted == true

  val downloadMenuItem: Material3MenuItemData? =
    when (downloadState) {
      Download.STATE_COMPLETED ->
        Material3MenuItemData(
          title = { Text(stringResource(R.string.remove_download)) },
          description = { Text(stringResource(R.string.remove_download_playlist_desc)) },
          icon = { Icon(painter = painterResource(R.drawable.offline), contentDescription = null) },
          onClick = {
            onDownload()
            onDismiss()
          }
        )
      Download.STATE_QUEUED,
      Download.STATE_DOWNLOADING ->
        Material3MenuItemData(
          title = { Text(stringResource(R.string.downloading)) },
          description = { Text(stringResource(R.string.download_in_progress_desc)) },
          icon = { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) },
          onClick = {
            onDownload()
            onDismiss()
          }
        )
      else -> null
    }

  Material3MenuGroup(
    items =
      listOfNotNull(
        if (!isGuest) {
          Material3MenuItemData(
            title = { Text(stringResource(R.string.add_to_queue)) },
            description = { Text(stringResource(R.string.add_to_queue_desc)) },
            icon = {
              Icon(painter = painterResource(R.drawable.queue_music), contentDescription = null)
            },
            onClick = {
              onQueue()
              onDismiss()
            }
          )
        } else null,
        downloadMenuItem
      )
  )
}

@Composable
fun TopPlaylistMenu(
  downloadState: Int,
  onQueue: () -> Unit,
  onDownload: () -> Unit,
  onDismiss: () -> Unit
) {
  val listenTogetherManager = LocalListenTogetherManager.current
  val isGuest = listenTogetherManager?.isGuestPlaybackRestricted == true

  val downloadMenuItem: Material3MenuItemData? =
    when (downloadState) {
      Download.STATE_COMPLETED ->
        Material3MenuItemData(
          title = { Text(stringResource(R.string.remove_download)) },
          description = { Text(stringResource(R.string.remove_download_playlist_desc)) },
          icon = { Icon(painter = painterResource(R.drawable.offline), contentDescription = null) },
          onClick = {
            onDownload()
            onDismiss()
          }
        )
      Download.STATE_QUEUED,
      Download.STATE_DOWNLOADING ->
        Material3MenuItemData(
          title = { Text(stringResource(R.string.downloading)) },
          description = { Text(stringResource(R.string.download_in_progress_desc)) },
          icon = { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) },
          onClick = {
            onDownload()
            onDismiss()
          }
        )
      else -> null
    }

  Material3MenuGroup(
    items =
      listOfNotNull(
        if (!isGuest) {
          Material3MenuItemData(
            title = { Text(stringResource(R.string.add_to_queue)) },
            description = { Text(stringResource(R.string.add_to_queue_desc)) },
            icon = {
              Icon(painter = painterResource(R.drawable.queue_music), contentDescription = null)
            },
            onClick = {
              onQueue()
              onDismiss()
            }
          )
        } else null,
        downloadMenuItem
      )
  )
}

@Composable
fun CachePlaylistMenu(
  downloadState: Int,
  onQueue: () -> Unit,
  onDownload: () -> Unit,
  onDismiss: () -> Unit
) {
  val listenTogetherManager = LocalListenTogetherManager.current
  val isGuest = listenTogetherManager?.isGuestPlaybackRestricted == true

  val downloadMenuItem: Material3MenuItemData? =
    when (downloadState) {
      Download.STATE_COMPLETED ->
        Material3MenuItemData(
          title = { Text(stringResource(R.string.remove_download)) },
          description = { Text(stringResource(R.string.remove_download_playlist_desc)) },
          icon = { Icon(painter = painterResource(R.drawable.offline), contentDescription = null) },
          onClick = {
            onDownload()
            onDismiss()
          }
        )
      Download.STATE_QUEUED,
      Download.STATE_DOWNLOADING ->
        Material3MenuItemData(
          title = { Text(stringResource(R.string.downloading)) },
          description = { Text(stringResource(R.string.download_in_progress_desc)) },
          icon = { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) },
          onClick = {
            onDownload()
            onDismiss()
          }
        )
      else -> null
    }

  Material3MenuGroup(
    items =
      listOfNotNull(
        if (!isGuest) {
          Material3MenuItemData(
            title = { Text(stringResource(R.string.add_to_queue)) },
            description = { Text(stringResource(R.string.add_to_queue_desc)) },
            icon = {
              Icon(painter = painterResource(R.drawable.queue_music), contentDescription = null)
            },
            onClick = {
              onQueue()
              onDismiss()
            }
          )
        } else null,
        downloadMenuItem
      )
  )
}
