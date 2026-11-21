package app.music.backend.sonari.lyrics

import android.content.Context
import app.music.backend.sonari.innertube.YouTube

object YouTubeSubtitleLyricsProvider : LyricsProvider {
  override val name = "YouTube Subtitle"

  override fun isEnabled(context: Context) = true

  override suspend fun getLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): Result<String> = YouTube.transcript(id)
}
