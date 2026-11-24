package app.music.backend.sonari.ui.screens.library

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import app.music.backend.sonari.LocalDatabase
import app.music.backend.sonari.LocalPlayerConnection
import app.music.backend.sonari.R
import app.music.backend.sonari.db.MusicDatabase
import app.music.backend.sonari.db.entities.Song
import app.music.backend.sonari.extensions.toMediaItem
import app.music.backend.sonari.playback.queues.ListQueue
import app.music.backend.sonari.ui.utils.formatFileSize
import java.util.Locale
import kotlin.math.abs

/** How far two copies of a song may differ in length and still count as the same recording. */
private const val DurationToleranceSeconds = 3

private data class DuplicateGroup(
  val title: String,
  val artist: String,
  val songs: List<Song>,
)

/**
 * Finds local songs that exist more than once: same title and artist, and a length within
 * [DurationToleranceSeconds] of each other. The same name at a wildly different length is a
 * different recording (a live take, a remix), so it is left out.
 */
private fun findDuplicateGroups(songs: List<Song>): List<DuplicateGroup> {
  val byName =
    songs.groupBy { song ->
      val title = song.song.title.trim().lowercase(Locale.ROOT)
      val artist = song.artists.joinToString(", ") { it.name.trim().lowercase(Locale.ROOT) }
      "$title|$artist"
    }

  val groups = mutableListOf<DuplicateGroup>()
  byName.values.forEach { copies ->
    if (copies.size < 2) return@forEach

    val clusters = mutableListOf<MutableList<Song>>()
    copies.sortedBy { it.song.duration }.forEach { copy ->
      val cluster =
        clusters.firstOrNull { existing ->
          abs(existing.first().song.duration - copy.song.duration) <= DurationToleranceSeconds
        }
      if (cluster != null) cluster.add(copy) else clusters.add(mutableListOf(copy))
    }

    clusters.filter { it.size > 1 }.forEach { cluster ->
      val first = cluster.first()
      groups.add(
        DuplicateGroup(
          title = first.song.title,
          artist = first.artists.joinToString(", ") { it.name },
          songs = cluster,
        )
      )
    }
  }

  return groups.sortedBy { it.title.lowercase(Locale.ROOT) }
}

private fun removeLocalSongs(database: MusicDatabase, songIds: List<String>) {
  database.query { deleteSongsCompletely(songIds) }
}

private fun durationText(seconds: Int): String {
  if (seconds <= 0) return "--:--"
  return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(navController: NavController) {
  val context = LocalContext.current
  val database = LocalDatabase.current
  val playerConnection = LocalPlayerConnection.current

  val songs by database.localSongs().collectAsState(initial = emptyList())
  val groups = remember(songs) { findDuplicateGroups(songs) }

  var pendingDeleteId by remember { mutableStateOf<String?>(null) }

  val deleteLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
      val songId = pendingDeleteId
      pendingDeleteId = null
      if (songId != null && result.resultCode == Activity.RESULT_OK) {
        removeLocalSongs(database, listOf(songId))
        Toast.makeText(context, R.string.dup_deleted, Toast.LENGTH_SHORT).show()
      }
    }

  val onDelete: (Song) -> Unit = { song ->
    val uri = song.id.toUri()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      runCatching {
          pendingDeleteId = song.id
          val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
          deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
        .onFailure {
          pendingDeleteId = null
          Toast.makeText(context, R.string.dup_delete_failed, Toast.LENGTH_SHORT).show()
        }
    } else {
      runCatching { context.contentResolver.delete(uri, null, null) }
        .onSuccess {
          removeLocalSongs(database, listOf(song.id))
          Toast.makeText(context, R.string.dup_deleted, Toast.LENGTH_SHORT).show()
        }
        .onFailure {
          Toast.makeText(context, R.string.dup_delete_failed, Toast.LENGTH_SHORT).show()
        }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.duplicates_title)) },
        navigationIcon = {
          IconButton(onClick = navController::navigateUp) {
            Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
          }
        },
      )
    },
    contentWindowInsets = WindowInsets.systemBars,
  ) { innerPadding ->
    if (groups.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(R.string.duplicates_none),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Text(
            text = stringResource(R.string.duplicates_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        items(items = groups, key = { group -> group.songs.first().id }) { group ->
          DuplicateGroupCard(
            group = group,
            onPlay = { index ->
              playerConnection?.playQueue(
                ListQueue(
                  title = group.title,
                  items = group.songs.map { it.toMediaItem() },
                  startIndex = index,
                )
              )
            },
            onDelete = onDelete,
          )
        }
      }
    }
  }
}

@Composable
private fun DuplicateGroupCard(
  group: DuplicateGroup,
  onPlay: (Int) -> Unit,
  onDelete: (Song) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
        text = group.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )

      Spacer(modifier = Modifier.height(2.dp))

      val copies = stringResource(R.string.duplicates_copies, group.songs.size)
      Text(
        text = if (group.artist.isBlank()) copies else "${group.artist} · $copies",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Spacer(modifier = Modifier.height(8.dp))

      group.songs.forEachIndexed { index, song ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            val albumName = song.song.albumName
            if (!albumName.isNullOrBlank()) {
              Text(
                text = albumName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Text(
              text =
                "${durationText(song.song.duration)} · ${
                  formatFileSize(song.format?.contentLength ?: 0L)
                }",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          IconButton(onClick = { onPlay(index) }) {
            Icon(
              painter = painterResource(R.drawable.play),
              contentDescription = stringResource(R.string.play),
            )
          }
          IconButton(onClick = { onDelete(song) }) {
            Icon(
              painter = painterResource(R.drawable.delete),
              contentDescription = stringResource(R.string.dup_delete_file),
            )
          }
        }
      }
    }
  }
}
