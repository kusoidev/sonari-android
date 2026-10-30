package com.kusoi.sonari.music

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * Mirrors MacShareView.swift. Pick downloaded tracks, tick Select all, choose a
 * peer, send. Incoming offers arrive at the top with Accept and Decline.
 *
 * The share service is a singleton, so start() is idempotent and stopping is
 * left to the service lifetime rather than this screen's composition.
 */
@Composable
fun ShareScreen(engine: PlayerEngine, onAutoPlay: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme by engine.theme.collectAsStateWithLifecycle()
    val accent = theme.accent

    val peers by LocalShareService.peers.collectAsStateWithLifecycle()
    val offers by LocalShareService.offers.collectAsStateWithLifecycle()
    val transfer by LocalShareService.transfer.collectAsStateWithLifecycle()
    val advertising by LocalShareService.isAdvertising.collectAsStateWithLifecycle()
    val lastReceived by LocalShareService.lastReceived.collectAsStateWithLifecycle()

    val library by LibraryStore.tracks.collectAsStateWithLifecycle()
    val downloaded = remember(library) { library.filter { it.isDownloaded } }

    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var choosingPeer by remember { mutableStateOf(false) }

    val visible = remember(downloaded, query) {
        if (query.isBlank()) downloaded
        else downloaded.filter {
            it.title.contains(query, ignoreCase = true) ||
                (it.artist?.contains(query, ignoreCase = true) == true)
        }
    }
    val allSelected = visible.isNotEmpty() && visible.all { it.id in selected }
    val selectedTracks = downloaded.filter { it.id in selected }
    val selectedBytes = selectedTracks.sumOf { it.fileSize ?: 0L }

    LaunchedEffect(Unit) { LocalShareService.start(context) }

    LaunchedEffect(lastReceived) {
        if (lastReceived.isNotEmpty()) {
            engine.setQueue(lastReceived, 0)
            engine.play(lastReceived.first())
            onAutoPlay()
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Share", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !advertising -> "Off"
                            peers.isEmpty() -> "Looking for phones"
                            else -> "${peers.size} found"
                        },
                        color = Ink.dim, fontSize = 12.sp
                    )
                }
                PillButton(if (advertising) "Stop" else "Start", accent = accent) {
                    if (advertising) LocalShareService.stop()
                    else LocalShareService.start(context)
                }
            }
        }

        if (offers.isNotEmpty()) {
            item { SectionLabel("INCOMING") }
            items(offers, key = { it.id }) { offer ->
                OfferRow(
                    offer = offer,
                    accent = accent,
                    onAccept = { scope.launch { LocalShareService.accept(offer) } },
                    onDecline = { LocalShareService.decline(offer) }
                )
            }
        }

        if (choosingPeer) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(Ink.ghost, CircleShape)
                            .clickable { choosingPeer = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.ArrowBack, null, tint = Ink.dim, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (selectedTracks.size == 1) selectedTracks.first().title else "${selectedTracks.size} tracks",
                            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                        )
                        Text(formatBytes(selectedBytes), color = Ink.dim, fontSize = 12.sp)
                    }
                }
            }

            if (peers.isEmpty()) {
                item { EmptyNote("No phones nearby", "Open Share in Sonari on the other phone, on the same Wi-Fi.", Icons.Rounded.Wifi) }
            } else {
                items(peers, key = { it.name }) { peer ->
                    PeerRow(peer, accent) {
                        val batch = selectedTracks
                        scope.launch { LocalShareService.share(batch, peer) }
                    }
                }
            }

            if (transfer.isActive) {
                item { Text(transfer.label, color = Ink.dim, fontSize = 12.sp) }
            }
        } else {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Ink.ghost, RoundedCornerShape(22.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Find a downloaded track", color = Ink.faint, fontSize = 13.sp) },
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = accent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (downloaded.isEmpty()) {
                item { EmptyNote("Nothing to share", "Only downloaded tracks can be sent.", Icons.Rounded.Wifi) }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                selected = if (allSelected) emptySet() else visible.map { it.id }.toSet()
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CheckBox(
                                state = when {
                                    allSelected -> 2
                                    selected.isEmpty() -> 0
                                    else -> 1
                                },
                                accent = accent
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Select all", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.weight(1f))
                        if (selected.isNotEmpty()) {
                            Text(
                                "${selectedTracks.size} selected - ${formatBytes(selectedBytes)}",
                                color = Ink.dim, fontSize = 11.sp
                            )
                        }
                    }
                }

                items(visible, key = { it.id }) { track ->
                    TrackPickRow(
                        track = track,
                        checked = track.id in selected,
                        accent = accent
                    ) {
                        selected = if (track.id in selected) selected - track.id else selected + track.id
                    }
                }

                if (selected.isNotEmpty()) {
                    item {
                        PillButton(
                            if (selectedTracks.size == 1) "Send track" else "Send ${selectedTracks.size} tracks",
                            accent = accent, filled = true
                        ) { choosingPeer = true }
                    }
                }
            }
        }

        if (transfer.isFailed) {
            item { Text(transfer.label, color = accent, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Ink.dim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp)
    )
}

@Composable
private fun PillButton(label: String, accent: Color, filled: Boolean = false, onClick: () -> Unit) {
    val bg = if (filled) accent else Ink.ghost
    val fg = if (filled) Color.Black else Color.White
    Text(
        label,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

@Composable
private fun CheckBox(state: Int, accent: Color) {
    val on = state == 2
    val mixed = state == 1
    Box(
        Modifier
            .size(18.dp)
            .background(if (state == 0) Color.Transparent else accent, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (on) {
            Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(13.dp))
        } else if (mixed) {
            Box(
                Modifier
                    .size(width = 8.dp, height = 2.dp)
                    .background(Color.Black, RoundedCornerShape(1.dp))
            )
        } else {
            Box(
                Modifier
                    .size(18.dp)
                    .background(Ink.hairline, RoundedCornerShape(5.dp))
            )
        }
    }
}

@Composable
private fun TrackPickRow(track: Track, checked: Boolean, accent: Color, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink.ghost, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckBox(if (checked) 2 else 0, accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(track.artist ?: "Unknown artist", color = Ink.dim, fontSize = 11.sp, maxLines = 1)
        }
        track.fileSize?.let {
            Text(formatBytes(it), color = Ink.dim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PeerRow(peer: LocalShareService.Peer, accent: Color, onSend: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink.ghost, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(Ink.hairline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PhoneIphone, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(peer.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
        PillButton("Send", accent = accent, filled = true, onClick = onSend)
    }
}

@Composable
private fun OfferRow(offer: LocalShareService.Offer, accent: Color, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ink.ghost, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(offer.summary, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text("From ${offer.peerName} - ${formatBytes(offer.sizeBytes)}", color = Ink.dim, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Decline", accent = accent, onClick = onDecline)
            PillButton("Accept", accent = accent, filled = true, onClick = onAccept)
        }
    }
}

@Composable
private fun EmptyNote(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Ink.faint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = Ink.dim, fontSize = 12.sp)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000.0)
    else -> "$bytes B"
}
