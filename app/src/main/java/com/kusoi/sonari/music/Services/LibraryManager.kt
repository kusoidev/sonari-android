package com.kusoi.sonari.music.Services

import com.kusoi.sonari.music.Models.Playlist
import com.kusoi.sonari.music.Models.SonariPaths
import com.kusoi.sonari.music.Models.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistent track + playlist store. Mirrors the Swift LibraryManager: two JSON files
 * in the app's documents directory, rewritten on every mutation.
 */
object LibraryManager {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val tracksFile: File get() = File(SonariPaths.documents, "library.json")
    private val playlistsFile: File get() = File(SonariPaths.documents, "playlists.json")

    fun load() {
        loadTracks()
        loadPlaylists()
    }

    private fun loadTracks() {
        val file = tracksFile
        if (!file.exists()) return
        _tracks.value = runCatching {
            json.decodeFromString<List<Track>>(file.readText())
        }.getOrDefault(emptyList())
    }

    private fun loadPlaylists() {
        val file = playlistsFile
        if (!file.exists()) return
        _playlists.value = runCatching {
            json.decodeFromString<List<Playlist>>(file.readText())
        }.getOrDefault(emptyList())
    }

    fun addTrack(track: Track) {
        val current = _tracks.value.toMutableList()
        val idx = current.indexOfFirst { it.id == track.id }
        if (idx >= 0) current[idx] = track else current.add(track)
        _tracks.value = current
        saveTracks()
    }

    fun renameTrack(track: Track, newTitle: String) {
        val current = _tracks.value.toMutableList()
        val idx = current.indexOfFirst { it.id == track.id }
        if (idx < 0) return
        current[idx] = current[idx].copy(title = newTitle)
        _tracks.value = current
        saveTracks()
    }

    fun removeTrack(track: Track) {
        track.localFileURL?.takeIf { it.exists() }?.delete()
        _tracks.value = _tracks.value.filterNot { it.id == track.id }

        _playlists.value = _playlists.value.map { it.removeTrack(track.id) }
        saveTracks()
        savePlaylists()
    }

    fun trackFor(id: String): Track? = _tracks.value.firstOrNull { it.id == id }

    fun recordPlay(track: Track) {
        val current = _tracks.value.toMutableList()
        val idx = current.indexOfFirst { it.id == track.id }
        if (idx < 0) return
        current[idx] = current[idx].copy(playCount = current[idx].playCount + 1)
        _tracks.value = current
        saveTracks()
    }

    /** Top N most played, sorted by play count desc then addedAt desc. */
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
            if (it.id == playlist.id) it.addTrack(track.id) else it
        }
        _tracks.value = _tracks.value.map { t ->
            if (t.id == track.id && !t.playlistIDs.contains(playlist.id)) {
                t.copy(playlistIDs = t.playlistIDs + playlist.id)
            } else t
        }
        savePlaylists()
        saveTracks()
    }

    fun removeTrackFromPlaylist(track: Track, playlist: Playlist) {
        _playlists.value = _playlists.value.map {
            if (it.id == playlist.id) it.removeTrack(track.id) else it
        }
        _tracks.value = _tracks.value.map { t ->
            if (t.id == track.id) t.copy(playlistIDs = t.playlistIDs.filterNot { it == playlist.id }) else t
        }
        savePlaylists()
        saveTracks()
    }

    fun tracksInPlaylist(playlist: Playlist): List<Track> =
        _tracks.value.filter { playlist.trackIDs.contains(it.id) }

    private fun saveTracks() {
        runCatching { tracksFile.writeText(json.encodeToString<List<Track>>(_tracks.value)) }
    }

    private fun savePlaylists() {
        runCatching { playlistsFile.writeText(json.encodeToString<List<Playlist>>(_playlists.value)) }
    }
}