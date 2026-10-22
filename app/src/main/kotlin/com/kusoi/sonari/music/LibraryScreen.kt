package com.kusoi.sonari.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class SortOrder(val label: String) {
    DateAdded("Date Added"),
    Title("Title"),
    Duration("Duration")
}

/**
 * Port of Sonari/Views/LibraryView.swift. Top Played strip, playlist chips,
 * animated sort underline, then the rows. Search filters the same list.
 */
@Composable
fun LibraryScreen(engine: PlayerEngine, onPlay: () -> Unit) {
    val tracks by LibraryStore.tracks.collectAsStateWithLifecycle()
    val playlists by LibraryStore.playlists.collectAsStateWithLifecycle()
    val currentTrack by engine.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by engine.isPlaying.collectAsStateWithLifecycle()
    val theme by engine.theme.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SortOrder.DateAdded) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val base = selectedPlaylist?.let { p ->
        val ids = p.trackIDs.toSet()
        tracks.filter { it.id in ids }
    } ?: tracks

    val sorted = when (sort) {
        SortOrder.DateAdded -> base.sortedByDescending { it.addedAt }
        SortOrder.Title -> base.sortedBy { it.title.lowercase() }
        SortOrder.Duration -> base.sortedBy { it.duration ?: 0.0 }
    }
    val display = if (query.isBlank()) sorted else sorted.filter { it.title.contains(query, ignoreCase = true) }

    val topPlayed = remember(tracks) {
        tracks.filter { it.playCount > 0 }
            .sortedWith(compareByDescending<Track> { it.playCount }.thenByDescending { it.addedAt })
            .take(5)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            selectedPlaylist?.name ?: "Library",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search library", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                focusedBorderColor = Color.White.copy(alpha = 0.2f),
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = theme.accent
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        if (topPlayed.isNotEmpty()) {
            TopPlayedStrip(
                tracks = topPlayed,
                accent = theme.accent,
                onSelect = { track ->
                    val all = display
                    val idx = all.indexOfFirst { it.id == track.id }
                    engine.setQueue(if (idx >= 0) all else topPlayed, if (idx >= 0) idx else 0)
                    engine.play(track)
                    onPlay()
                }
            )
        }

        if (playlists.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Chip("All", selectedPlaylist == null) { selectedPlaylist = null }
                }
                itemsIndexed(playlists) { _, playlist ->
                    Chip(playlist.name, selectedPlaylist?.id == playlist.id) { selectedPlaylist = playlist }
                }
            }
        }

        SortBar(sort = sort, onSelect = { sort = it })
        Spacer(Modifier.height(1.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.07f)))

        if (display.isEmpty()) {
            EmptyLibrary(hasTracks = tracks.isNotEmpty())
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(display) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = currentTrack?.id == track.id,
                        isPlaying = currentTrack?.id == track.id && isPlaying,
                        accent = theme.accent,
                        onPlay = {
                            engine.setQueue(display, index)
                            engine.play(track)
                            onPlay()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color.White else Color.White.copy(alpha = 0.1f)
    val fg = if (selected) Color.Black else Color.White.copy(alpha = 0.65f)
    Box(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun TopPlayedStrip(tracks: List<Track>, accent: Color, onSelect: (Track) -> Unit) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            "Top Played",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 6.dp)
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(tracks) { index, track ->
                Row(
                    Modifier
                        .width(210.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(track) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Artwork(track.youtubeID, track.thumbnailURL, 34.dp, 6.dp, iconSize = 10.dp)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            TrackText.titleDisplay(track),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("${track.playCount} plays", color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

/** Sort bar with a 1.5dp underline that slides between the active labels. */
@Composable
private fun SortBar(sort: SortOrder, onSelect: (SortOrder) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SortOrder.entries.forEach { order ->
            val isActive = sort == order
            val weight by animateFloatAsState(if (isActive) 1f else 0f, Motion.chip(), label = "sortWeight")
            Column(
                Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(order) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    order.label,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Box(
                    Modifier
                        .fillMaxWidth(weight)
                        .height(1.5.dp)
                        .background(Color.White)
                )
            }
        }
    }
}

/** Port of TrackRowView.swift, including the playing overlay on the artwork. */
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    accent: Color,
    onPlay: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPlay() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Artwork(track.youtubeID, track.thumbnailURL, 48.dp, 8.dp, iconSize = 14.dp)
            if (isCurrent) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                )
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                TrackText.titleDisplay(track),
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TrackText.artistDisplay(track),
                    color = Color.White.copy(alpha = if (isCurrent) 0.5f else 0.35f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                track.duration?.let {
                    Text("  ·  ${formatTime(it)}", color = Color.White.copy(alpha = 0.25f), fontSize = 12.sp)
                }
            }
        }
        Icon(
            if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = "Play",
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun EmptyLibrary(hasTracks: Boolean) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (hasTracks) "No results" else "No tracks yet",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (!hasTracks) {
            Text(
                "Head to Download to grab some music.",
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 6.dp)
            )
        }
    }
}
