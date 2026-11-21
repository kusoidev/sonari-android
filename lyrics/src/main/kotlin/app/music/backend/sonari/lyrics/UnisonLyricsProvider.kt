package app.music.backend.sonari.lyrics

import android.content.Context
import app.music.backend.sonari.unison.Unison
import app.music.backend.sonari.constants.UnisonLyricsEnabledKey
import app.music.backend.sonari.utils.dataStore
import app.music.backend.sonari.utils.get

object UnisonLyricsProvider : LyricsProvider {
  override val name: String = "Unison"

  override fun isEnabled(context: Context): Boolean =
    context.dataStore[UnisonLyricsEnabledKey] ?: true

  override suspend fun getLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): Result<String> =
    Unison.getLyrics(
        videoId = id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = duration
      )
      .map { convertIfTTML(it) }

  override suspend fun getAllLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
    callback: (String) -> Unit,
  ) {
    Unison.getAllLyrics(
      videoId = id,
      title = title,
      artist = artist,
      album = album,
      durationSeconds = duration,
      callback = { callback(convertIfTTML(it)) }
    )
  }

  private fun convertIfTTML(content: String): String {
    return if (content.trimStart().startsWith("<tt", ignoreCase = true)) {
      val parsedLines = app.music.backend.sonari.betterlyrics.TTMLParser.parseTTML(content)
      app.music.backend.sonari.betterlyrics.TTMLParser.toLRC(parsedLines)
    } else {
      content
    }
  }
}
