package app.music.backend.sonari.playback

import app.music.backend.sonari.db.entities.SongEntity

interface ISyncUtils {
  fun likeSong(song: SongEntity)
}
