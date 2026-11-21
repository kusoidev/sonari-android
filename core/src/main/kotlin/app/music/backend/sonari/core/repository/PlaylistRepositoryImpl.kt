package app.music.backend.sonari.core.repository

import app.music.backend.sonari.core.mapping.toDomain
import app.music.backend.sonari.domain.models.Playlist as DomainPlaylist
import app.music.backend.sonari.domain.models.Song as DomainSong
import app.music.backend.sonari.domain.repositories.PlaylistRepository
import app.music.backend.sonari.db.MusicDatabase
import app.music.backend.sonari.db.entities.PlaylistSongMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
  private val database: MusicDatabase
) : PlaylistRepository {

  override fun getAllPlaylists(): Flow<List<DomainPlaylist>> {
    return database.playlistsByNameAsc().map { list ->
      list.map { it.toDomain() }
    }
  }

  override fun getPlaylistSongs(playlistId: String): Flow<List<DomainSong>> {
    return database.playlistSongs(playlistId).map { list ->
      list.map { it.song.toDomain() }
    }
  }

  override suspend fun addSongToPlaylist(playlistId: String, songId: String) {
    database.query {
      val existingMaps = playlistSongMaps(playlistId, 0)
      val nextPos = (existingMaps.maxOfOrNull { it.position } ?: -1) + 1
      insert(PlaylistSongMap(songId = songId, playlistId = playlistId, position = nextPos))
    }
  }

  override suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
    database.query {
      val maps = playlistSongMaps(songId).filter { it.playlistId == playlistId }
      maps.forEach { delete(it) }
    }
  }
}
