package com.kusoi.sonari.music.ViewModels

import com.kusoi.sonari.music.Models.Playlist
import com.kusoi.sonari.music.Models.Track
import com.kusoi.sonari.music.Services.LibraryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SortOrder(val label: String) {
    DATE_ADDED("Date Added"),
    TITLE("Title"),
    DURATION("Duration")
}

/** Library state holder. Mirrors the Swift LibraryViewModel. */
object LibraryViewModel {
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _showingNewPlaylistSheet = MutableStateFlow(false)
    val showingNewPlaylistSheet: StateFlow<Boolean> = _showingNewPlaylistSheet.asStateFlow()

    private val _newPlaylistName = MutableStateFlow("")
    val newPlaylistName: StateFlow<String> = _newPlaylistName.asStateFlow()

    private val _trackToDelete = MutableStateFlow<Track?>(null)
    val trackToDelete: StateFlow<Track?> = _trackToDelete.asStateFlow()

    private val _showingDeleteConfirm = MutableStateFlow(false)
    val showingDeleteConfirm: StateFlow<Boolean> = _showingDeleteConfirm.asStateFlow()

    val tracks: StateFlow<List<Track>> get() = LibraryManager.tracks
    val playlists: StateFlow<List<Playlist>> get() = LibraryManager.playlists

    fun setSearchText(t: String) { _searchText.value = t }
    fun setSortOrder(o: SortOrder) { _sortOrder.value = o }
    fun selectPlaylist(p: Playlist?) { _selectedPlaylist.value = p }
    fun setNewPlaylistName(n: String) { _newPlaylistName.value = n }
    fun setShowingNewPlaylistSheet(v: Boolean) { _showingNewPlaylistSheet.value = v }

    /** Tracks after playlist filter, sort, and search, matching displayTracks. */
    fun displayTracks(): List<Track> {
        val selected = _selectedPlaylist.value
        val base = if (selected != null) LibraryManager.tracksInPlaylist(selected) else tracks.value

        val sorted = when (_sortOrder.value) {
            SortOrder.DATE_ADDED -> base.sortedByDescending { it.addedAt }
            SortOrder.TITLE -> base.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            SortOrder.DURATION -> base.sortedBy { it.duration ?: 0.0 }
        }

        val search = _searchText.value
        if (search.isEmpty()) return sorted
        return sorted.filter { it.title.contains(search, ignoreCase = true) }
    }

    fun topPlayed(): List<Track> = LibraryManager.mostPlayed(5)

    fun createPlaylist(name: String) {
        LibraryManager.createPlaylist(name)
    }

    fun deletePlaylist(p: Playlist) {
        LibraryManager.deletePlaylist(p)
        if (_selectedPlaylist.value?.id == p.id) _selectedPlaylist.value = null
    }

    fun renamePlaylist(p: Playlist, name: String) {
        LibraryManager.renamePlaylist(p, name)
    }

    fun addToPlaylist(track: Track, p: Playlist) {
        LibraryManager.addTrackToPlaylist(track, p)
    }

    fun removeFromPlaylist(track: Track, p: Playlist) {
        LibraryManager.removeTrackFromPlaylist(track, p)
    }

    fun rename(track: Track, name: String) {
        LibraryManager.renameTrack(track, name)
    }

    fun delete(track: Track) {
        LibraryManager.removeTrack(track)
    }

    fun confirmDelete(track: Track) {
        _trackToDelete.value = track
        _showingDeleteConfirm.value = true
    }

    fun executeDelete() {
        _trackToDelete.value?.let { LibraryManager.removeTrack(it) }
        _trackToDelete.value = null
        _showingDeleteConfirm.value = false
    }
}