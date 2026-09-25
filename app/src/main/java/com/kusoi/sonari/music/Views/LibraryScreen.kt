package com.kusoi.sonari.music.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kusoi.sonari.music.Models.Playlist
import com.kusoi.sonari.music.Models.Track
import com.kusoi.sonari.music.ViewModels.LibraryViewModel
import com.kusoi.sonari.music.ViewModels.PlayerViewModel
import com.kusoi.sonari.music.ViewModels.SortOrder

@Composable
fun LibraryScreen() {
    val searchText by LibraryViewModel.searchText.collectAsState()
    val sortOrder by LibraryViewModel.sortOrder.collectAsState()
    val selectedPlaylist by LibraryViewModel.selectedPlaylist.collectAsState()
    val playlists by LibraryViewModel.playlists.collectAsState()
    val allTracks by LibraryViewModel.tracks.collectAsState()
    val currentTrack by PlayerViewModel.currentTrack.collectAsState()
    val isPlaying by PlayerViewModel.isPlaying.collectAsState()

    val displayTracks = LibraryViewModel.displayTracks()
    val topPlayed = remember(allTracks) { LibraryViewModel.topPlayed() }

    var showingNewPlaylist by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Track?>(null) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            text = selectedPlaylist?.name ?: "Library",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (allTracks.isEmpty()) {
            EmptyState()
            return@Column
        }

        LibrarySearchBar(searchText) { LibraryViewModel.setSearchText(it) }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (topPlayed.isNotEmpty()) {
                item { TopPlayedStrip(topPlayed) }
            }

            if (playlists.isNotEmpty()) {
                item {
                    PlaylistChips(playlists, selectedPlaylist) { LibraryViewModel.selectPlaylist(it) }
                }
            }

            item { SortBar(sortOrder) { LibraryViewModel.setSortOrder(it) } }

            if (displayTracks.isEmpty()) {
                item { NoResults() }
            }

            items(displayTracks, key = { it.id }) { track ->
                Box {
                    var menuOpen by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        TrackRow(
                            track = track,
                            isCurrent = currentTrack?.id == track.id,
                            isPlaying = isPlaying,
                            onPlay = {
                                val queue = displayTracks
                                val index = queue.indexOfFirst { it.id == track.id }
                                PlayerViewModel.setQueue(queue, index)
                                PlayerViewModel.play(track)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { menuOpen = true }
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Reset Lyrics") },
                                onClick = { PlayerViewModel.resetLyrics(track); menuOpen = false }
                            )
                            playlists.forEach { playlist ->
                                val inPlaylist = track.playlistIDs.contains(playlist.id)
                                DropdownMenuItem(
                                    text = { Text((if (inPlaylist) "Remove from " else "Add to ") + playlist.name) },
                                    onClick = {
                                        if (inPlaylist) LibraryViewModel.removeFromPlaylist(track, playlist)
                                        else LibraryViewModel.addToPlaylist(track, playlist)
                                        menuOpen = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFE57373)) },
                                onClick = { deleteTarget = track; menuOpen = false }
                            )
                        }
                    }
                }
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Track?") },
            text = { Text("This will permanently remove \"${deleteTarget!!.title}\" and its file.") },
            confirmButton = {
                TextButton(onClick = {
                    LibraryViewModel.delete(deleteTarget!!)
                    deleteTarget = null
                }) { Text("Delete", color = Color(0xFFE57373)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LibrarySearchBar(text: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.TextField(
            value = text,
            onValueChange = onChange,
            placeholder = { Text("Search library", color = Color.White.copy(alpha = 0.3f)) },
            singleLine = true,
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TopPlayedStrip(tracks: List<Track>) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = "Top Played",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            tracks.forEachIndexed { index, track ->
                Row(
                    modifier = Modifier
                        .width(210.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable {
                            PlayerViewModel.setQueue(tracks, index)
                            PlayerViewModel.play(track)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    CachedImage(
                        youtubeID = track.youtubeID,
                        remoteURL = track.thumbnailURL,
                        size = 34.dp,
                        corner = 6.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = titleDisplay(track),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${track.playCount} plays",
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistChips(
    playlists: List<Playlist>,
    selected: Playlist?,
    onSelect: (Playlist?) -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Chip("All", selected == null) { onSelect(null) }
        playlists.forEach { playlist ->
            Chip(playlist.name, selected?.id == playlist.id) { onSelect(playlist) }
        }
    }
}

@Composable
private fun Chip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.65f),
        fontSize = 13.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.1f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
private fun SortBar(selected: SortOrder, onSelect: (SortOrder) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            SortOrder.entries.forEach { order ->
                val isSel = selected == order
                Column(
                    modifier = Modifier.weight(1f).clickable { onSelect(order) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = order.label,
                        color = if (isSel) Color.White else Color.White.copy(alpha = 0.35f),
                        fontSize = 12.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .background(if (isSel) Color.White else Color.Transparent)
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.07f)))
    }
}

@Composable
private fun NoResults() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("No results", color = Color.White.copy(alpha = 0.35f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text("No tracks yet", color = Color.White.copy(alpha = 0.4f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Head to Download to grab some music.",
            color = Color.White.copy(alpha = 0.2f),
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}