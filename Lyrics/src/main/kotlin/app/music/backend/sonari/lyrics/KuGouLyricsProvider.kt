package app.music.backend.sonari.lyrics

import android.content.Context
import app.music.backend.sonari.kugou.KuGou
import app.music.backend.sonari.constants.EnableKugouKey
import app.music.backend.sonari.utils.dataStore
import app.music.backend.sonari.utils.get

object KuGouLyricsProvider : LyricsProvider {
  override val name = "Kugou"

  override fun isEnabled(context: Context): Boolean = context.dataStore[EnableKugouKey] ?: true

  override suspend fun getLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): Result<String> = KuGou.getLyrics(title, artist, duration, album)

  override suspend fun getAllLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
    callback: (String) -> Unit,
  ) {
    KuGou.getAllPossibleLyricsOptions(title, artist, duration, album, callback)
  }
}
