package app.music.backend.sonari.playback

import app.music.backend.sonari.models.MediaMetadata

data class LyricsWithProvider(val lyrics: String?, val providerName: String)

interface ILyricsHelper {
  suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider
}
