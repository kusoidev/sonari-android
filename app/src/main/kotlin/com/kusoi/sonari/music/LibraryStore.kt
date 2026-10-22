package com.kusoi.sonari.music

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Port of Sonari/Services/LibraryManager.swift. library.json and playlists.json
 * keep the same names and keys, and writes are atomic so a crash mid-save
 * cannot truncate the library.
 */
object LibraryStore {
    private val tracksFile = File(AppPaths.dataDirectory, "library.json")
    private val playlistsFile = File(AppPaths.dataDirectory, "playlists.json")

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    /** Replaces the whole library and persists. Used by the legacy import. */
    fun replaceAll(tracks: List<Track>) {
        _tracks.value = tracks
        saveTracks()
    }

    fun load() {
        runCatching {
            if (tracksFile.exists()) _tracks.value = Json.tracksFromJson(tracksFile.readText())
        }
        runCatching {
            if (playlistsFile.exists()) _playlists.value = Json.playlistsFromJson(playlistsFile.readText())
        }
    }

    fun addTrack(track: Track) {
        val list = _tracks.value.toMutableList()
        val idx = list.indexOfFirst { it.id == track.id }
        if (idx >= 0) list[idx] = track else list.add(track)
        _tracks.value = list
        saveTracks()
    }

    fun renameTrack(track: Track, newTitle: String) {
        val list = _tracks.value.toMutableList()
        val idx = list.indexOfFirst { it.id == track.id }
        if (idx < 0) return
        list[idx] = list[idx].copy(title = newTitle)
        _tracks.value = list
        saveTracks()
    }

    fun removeTrack(track: Track) {
        track.localFileURL?.delete()
        _tracks.value = _tracks.value.filterNot { it.id == track.id }
        _playlists.value = _playlists.value.map { p ->
            p.copy(trackIDs = p.trackIDs.filterNot { it == track.id })
        }
        saveTracks()
        savePlaylists()
    }

    fun recordPlay(track: Track) {
        val list = _tracks.value.toMutableList()
        val idx = list.indexOfFirst { it.id == track.id }
        if (idx < 0) return
        list[idx] = list[idx].copy(playCount = list[idx].playCount + 1)
        _tracks.value = list
        saveTracks()
    }

    fun mostPlayed(limit: Int = 5): List<Track> =
        _tracks.value
            .filter { it.playCount > 0 }
            .sortedWith(compareByDescending<Track> { it.playCount }.thenByDescending { it.addedAt })
            .take(limit)

    fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(name = name)
        _playlists.value = _playlists.value + playlist
        savePlaylists()
        return playlist
    }

    fun deletePlaylist(playlist: Playlist) {
        _playlists.value = _playlists.value.filterNot { it.id == playlist.id }
        _tracks.value = _tracks.value.map { t ->
            t.copy(playlistIDs = t.playlistIDs.filterNot { it == playlist.id })
        }
        savePlaylists()
        saveTracks()
    }

    fun renamePlaylist(playlist: Playlist, newName: String) {
        _playlists.value = _playlists.value.map {
            if (it.id == playlist.id) it.copy(name = newName) else it
        }
        savePlaylists()
    }

    fun addTrackToPlaylist(track: Track, playlist: Playlist) {
        _playlists.value = _playlists.value.map {
            if (it.id == playlist.id && !it.trackIDs.contains(track.id)) {
                it.copy(trackIDs = it.trackIDs + track.id)
            } else it
        }
        _tracks.value = _tracks.value.map {
            if (it.id == track.id && !it.playlistIDs.contains(playlist.id)) {
                it.copy(playlistIDs = it.playlistIDs + playlist.id)
            } else it
        }
        savePlaylists()
        saveTracks()
    }

    fun removeTrackFromPlaylist(track: Track, playlist: Playlist) {
        _playlists.value = _playlists.value.map {
            if (it.id == playlist.id) it.copy(trackIDs = it.trackIDs.filterNot { t -> t == track.id }) else it
        }
        _tracks.value = _tracks.value.map {
            if (it.id == track.id) it.copy(playlistIDs = it.playlistIDs.filterNot { p -> p == playlist.id }) else it
        }
        savePlaylists()
        saveTracks()
    }

    fun tracksInPlaylist(playlist: Playlist): List<Track> {
        val ids = playlist.trackIDs.toSet()
        return _tracks.value.filter { it.id in ids }
    }

    private fun saveTracks() {
        runCatching {
            val temp = File(AppPaths.dataDirectory, "library.json.tmp")
            temp.writeText(Json.tracksToJson(_tracks.value))
            temp.renameTo(tracksFile)
        }
    }

    private fun savePlaylists() {
        runCatching {
            val temp = File(playlistsFile.parentFile, "playlists.json.tmp")
            temp.writeText(Json.playlistsToJson(_playlists.value))
            temp.renameTo(playlistsFile)
        }
    }
}
