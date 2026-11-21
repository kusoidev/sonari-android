package app.music.backend.sonari.domain.repositories

import app.music.backend.sonari.domain.models.Playlist
import app.music.backend.sonari.domain.models.Song
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
  fun getAllPlaylists(): Flow<List<Playlist>>
  fun getPlaylistSongs(playlistId: String): Flow<List<Song>>
  suspend fun addSongToPlaylist(playlistId: String, songId: String)
  suspend fun removeSongFromPlaylist(playlistId: String, songId: String)
}
