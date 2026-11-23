package app.music.backend.sonari.ui.screens.library

import android.app.Activity
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import app.music.backend.sonari.LocalDatabase
import app.music.backend.sonari.R

/**
 * Edits the tags of a local file: the values are written into the file itself through MediaStore,
 * then the library row is updated so the screens follow without a rescan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorScreen(navController: NavController, songId: String) {
  val context = LocalContext.current
  val database = LocalDatabase.current
  val song by database.song(songId).collectAsState(initial = null)
  val current = song

  var title by rememberSaveable(current?.id) { mutableStateOf(current?.song?.title.orEmpty()) }
  var artist by
    rememberSaveable(current?.id) { mutableStateOf(current?.artists?.firstOrNull()?.name.orEmpty()) }
  var album by rememberSaveable(current?.id) { mutableStateOf(current?.song?.albumName.orEmpty()) }
  var year by
    rememberSaveable(current?.id) { mutableStateOf(current?.song?.year?.toString().orEmpty()) }

  var saving by remember { mutableStateOf(false) }
  var pendingWrite by remember { mutableStateOf<(() -> Unit)?>(null) }

  val writeLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
      val write = pendingWrite
      pendingWrite = null
      if (result.resultCode == Activity.RESULT_OK) {
        write?.invoke()
      } else {
        saving = false
      }
    }

  val onSave: () -> Unit = {
    val target = current
    if (target != null) {
      val newTitle = title.trim()
      val newArtist = artist.trim()
      val newAlbum = album.trim()
      val newYear = year.trim().toIntOrNull()
      val uri = target.id.toUri()

      val applyWrite: () -> Unit = {
        val values =
          ContentValues().apply {
            if (newTitle.isNotEmpty()) put(MediaStore.Audio.Media.TITLE, newTitle)
            if (newArtist.isNotEmpty()) put(MediaStore.Audio.Media.ARTIST, newArtist)
            put(MediaStore.Audio.Media.ALBUM, newAlbum)
            if (newYear != null) put(MediaStore.Audio.Media.YEAR, newYear)
          }

        runCatching { context.contentResolver.update(uri, values, null, null) }
          .onSuccess {
            database.query {
              if (newTitle.isNotEmpty()) {
                update(
                  target.song.copy(
                    title = newTitle,
                    albumName = newAlbum.ifBlank { null },
                    year = newYear,
                  )
                )
              } else {
                update(target.song.copy(albumName = newAlbum.ifBlank { null }, year = newYear))
              }
              if (newArtist.isNotEmpty()) {
                target.artists.firstOrNull()?.let { existing ->
                  update(existing.copy(name = newArtist))
                }
              }
            }
            saving = false
            Toast.makeText(context, R.string.tag_saved, Toast.LENGTH_SHORT).show()
            navController.navigateUp()
          }
          .onFailure {
            saving = false
            Toast.makeText(context, R.string.tag_failed, Toast.LENGTH_SHORT).show()
          }
      }

      saving = true
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            pendingWrite = applyWrite
            val request = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
            writeLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
          }
          .onFailure {
            pendingWrite = null
            applyWrite()
          }
      } else {
        applyWrite()
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.tag_editor_title)) },
        navigationIcon = {
          IconButton(onClick = navController::navigateUp) {
            Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
          }
        },
      )
    },
    contentWindowInsets = WindowInsets.systemBars,
  ) { innerPadding ->
    if (current == null) {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    } else {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = stringResource(R.string.tag_editor_desc),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text(stringResource(R.string.song_title)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = artist,
          onValueChange = { artist = it },
          label = { Text(stringResource(R.string.artist_name)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = album,
          onValueChange = { album = it },
          label = { Text(stringResource(R.string.tag_album)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = year,
          onValueChange = { value -> year = value.filter { it.isDigit() }.take(4) },
          label = { Text(stringResource(R.string.tag_year)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
          onClick = onSave,
          enabled = !saving,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(stringResource(R.string.save))
        }
      }
    }
  }
}
