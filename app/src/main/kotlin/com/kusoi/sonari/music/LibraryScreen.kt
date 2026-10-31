package com.kusoi.sonari.music

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class SortOrder(val label: String) {
    DateAdded("Recent"),
    Title("Title"),
    Duration("Length")
}

/**
 * Library. A greeting header with a search field under it, the Top Played
 * shelf as wide cards, playlist chips, a quiet sort row, then the list. All
 * of the sorting, filtering and playlist selection is unchanged; only the
 * surface did.
 */
@Composable
fun LibraryScreen(engine: PlayerEngine, onPlay: () -> Unit) {
    val tracks by LibraryStore.tracks.collectAsStateWithLifecycle()
    val playlists by LibraryStore.playlists.collectAsStateWithLifecycle()
    val currentTrack by engine.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by engine.isPlaying.collectAsStateWithLifecycle()
    val theme by engine.theme.collectAsStateWithLifecycle()
    val liveDuration by engine.duration.collectAsStateWithLifecycle()

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
    val display = if (query.isBlank()) sorted else sorted.filter {
        it.title.contains(query, ignoreCase = true) || TrackText.artistDisplay(it).contains(query, ignoreCase = true)
    }

    val topPlayed = remember(tracks) {
        tracks.filter { it.playCount > 0 }
            .sortedWith(compareByDescending<Track> { it.playCount }.thenByDescending { it.addedAt })
            .take(6)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        HeaderRow(
            title = selectedPlaylist?.name ?: "Library",
            subtitle = if (tracks.isEmpty()) "Nothing saved yet" else "${tracks.size} tracks",
            onBack = if (selectedPlaylist != null) {
                { selectedPlaylist = null }
            } else null
        )

        SearchField(query = query, onQueryChange = { query = it })

        if (topPlayed.isNotEmpty() && query.isBlank() && selectedPlaylist == null) {
            Spacer(Modifier.height(20.dp))
            ShelfHeader(title = "Top played")
            Spacer(Modifier.height(12.dp))
            TopPlayedShelf(
                tracks = topPlayed,
                accent = theme.accent,
                onAccent = theme.onAccent,
                onSelect = { track ->
                    val idx = tracks.indexOfFirst { it.id == track.id }
                    engine.setQueue(tracks, if (idx >= 0) idx else 0)
                    engine.play(track)
                    onPlay()
                }
            )
        }

        if (playlists.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Chip("All", selectedPlaylist == null, theme.accent) { selectedPlaylist = null }
                }
                items(playlists) { playlist ->
                    Chip(playlist.name, selectedPlaylist?.id == playlist.id, theme.accent) { selectedPlaylist = playlist }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SortBar(sort = sort, onSelect = { sort = it })
        Spacer(Modifier.height(6.dp))

        if (display.isEmpty()) {
            EmptyLibrary(hasTracks = tracks.isNotEmpty())
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
            ) {
                itemsIndexed(display) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = currentTrack?.id == track.id,
                        isPlaying = currentTrack?.id == track.id && isPlaying,
                        accent = theme.accent,
                        onAccent = theme.onAccent,
                        engineDuration = liveDuration,
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
private fun HeaderRow(title: String, subtitle: String, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = SonariText.primary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = SonariText.tertiary, fontSize = 13.sp)
        }
        if (onBack != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SonariSurface.raised)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ArrowForward, contentDescription = "Back to all", tint = SonariText.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Songs, artists, albums", color = SonariText.tertiary, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = SonariText.tertiary, modifier = Modifier.size(18.dp)) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SonariSurface.raised,
            unfocusedContainerColor = SonariSurface.raised,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = SonariText.primary,
            unfocusedTextColor = SonariText.primary,
            cursorColor = SonariSurface.accent
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    )
}

@Composable
private fun ShelfHeader(title: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = SonariText.primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TopPlayedShelf(
    tracks: List<Track>,
    accent: Color,
    onAccent: Color,
    onSelect: (Track) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(tracks) { index, track ->
            Column(
                Modifier
                    .width(132.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(track) }
            ) {
                Box {
                    Box(
                        Modifier
                            .size(132.dp)
                            .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        Artwork(track.youtubeID, track.thumbnailURL, 132.dp, 20.dp, iconSize = 28.dp)
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = onAccent, modifier = Modifier.size(16.dp))
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text("${index + 1}", color = SonariText.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    TrackText.titleDisplay(track),
                    color = SonariText.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    TrackText.artistDisplay(track),
                    color = SonariText.tertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val bg = if (selected) accent else SonariSurface.raised
    val fg = if (selected) SonariText.onAccent else SonariText.secondary
    Box(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

/** Sort row. The active label gets a small dot instead of an underline bar. */
@Composable
private fun SortBar(sort: SortOrder, onSelect: (SortOrder) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SortOrder.entries.forEach { order ->
            val isActive = sort == order
            val dot by animateDpAsState(if (isActive) 5.dp else 0.dp, Motion.chip(), label = "sortDot")
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(order) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(dot)
                        .clip(CircleShape)
                        .background(SonariSurface.accent)
                )
                if (isActive) Spacer(Modifier.width(6.dp))
                Text(
                    order.label,
                    color = if (isActive) SonariText.primary else SonariText.tertiary,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/** One row. Artwork, title, artist and length, with the play state inline. */
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    accent: Color,
    onAccent: Color,
    engineDuration: Double,
    onPlay: () -> Unit
) {
    val scale by animateFloatAsState(if (isCurrent) 1f else 0.98f, Motion.chip(), label = "rowScale")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCurrent) SonariSurface.card else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPlay() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track.youtubeID, track.thumbnailURL, 52.dp, 14.dp, iconSize = 15.dp, modifier = Modifier.scale(scale))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                TrackText.titleDisplay(track),
                color = if (isCurrent) SonariText.primary else SonariText.primary.copy(alpha = 0.88f),
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TrackText.artistDisplay(track),
                    color = SonariText.tertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                track.duration?.let {
                    Text("  ·  ${formatTime(it)}", color = SonariText.tertiary, fontSize = 12.sp)
                } ?: run {
                    val live = engineDuration
                    if (isCurrent && live > 0) {
                        Text("  ·  ${formatTime(live)}", color = SonariText.tertiary, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isCurrent) accent else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
                tint = if (isCurrent) onAccent else SonariText.tertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EmptyLibrary(hasTracks: Boolean) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(SonariSurface.raised),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = SonariText.tertiary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (hasTracks) "No matches" else "Your library is empty",
            color = SonariText.primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasTracks) "Try a different search." else "Find a track and download it to start.",
            color = SonariText.tertiary,
            fontSize = 13.sp
        )
    }
}
