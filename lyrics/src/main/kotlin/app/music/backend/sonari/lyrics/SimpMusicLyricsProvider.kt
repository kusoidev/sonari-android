package app.music.backend.sonari.lyrics

import android.content.Context
import app.music.backend.sonari.simpmusic.SimpMusicLyrics
import app.music.backend.sonari.constants.EnableSimpMusicKey
import app.music.backend.sonari.utils.dataStore
import app.music.backend.sonari.utils.get

object SimpMusicLyricsProvider : LyricsProvider {
  override val name = "SimpMusic"

  override fun isEnabled(context: Context): Boolean = context.dataStore[EnableSimpMusicKey] ?: true

  override suspend fun getLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): Result<String> = SimpMusicLyrics.getLyrics(id, duration)

  override suspend fun getAllLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
    callback: (String) -> Unit,
  ) {
    SimpMusicLyrics.getAllLyrics(id, duration, callback)
  }
}
