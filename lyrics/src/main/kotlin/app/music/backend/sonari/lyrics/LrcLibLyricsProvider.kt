package app.music.backend.sonari.lyrics

import android.content.Context
import app.music.backend.sonari.lrclib.LrcLib
import app.music.backend.sonari.constants.EnableLrcLibKey
import app.music.backend.sonari.utils.dataStore
import app.music.backend.sonari.utils.get

object LrcLibLyricsProvider : LyricsProvider {
  override val name = "LrcLib"

  override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLrcLibKey] ?: true

  override suspend fun getLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): Result<String> = LrcLib.getLyrics(title, artist, duration, album)

  override suspend fun getAllLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
    callback: (String) -> Unit,
  ) {
    LrcLib.getAllLyrics(title, artist, duration, album, callback)
  }
}
