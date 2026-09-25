package com.kusoi.sonari.music.Views

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kusoi.sonari.music.Models.Track
import com.kusoi.sonari.music.ViewModels.LoopMode
import com.kusoi.sonari.music.ViewModels.PlayerViewModel

@Composable
fun PlayerScreen() {
    val track by PlayerViewModel.currentTrack.collectAsState()
    val isPlaying by PlayerViewModel.isPlaying.collectAsState()
    val currentTime by PlayerViewModel.currentTime.collectAsState()
    val duration by PlayerViewModel.duration.collectAsState()
    val isDownloading by PlayerViewModel.isDownloading.collectAsState()
    val downloadProgress by PlayerViewModel.downloadProgress.collectAsState()
    val theme by PlayerViewModel.theme.collectAsState()

    var showingQueue by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        if (track == null) {
            EmptyState()
            return@Box
        }

        val t = track!!

        Column(modifier = Modifier.fillMaxSize()) {
            if (!showingQueue) Spacer(Modifier.weight(1f, fill = true))

            PlayerHeader(
                track = t,
                showingQueue = showingQueue,
                onToggleQueue = { showingQueue = !showingQueue }
            )

            if (showingQueue) {
                QueueList(modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f, fill = true))
            }

            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                if (isDownloading) {
                    DownloadProgressBar(downloadProgress, Modifier.padding(horizontal = 28.dp))
                } else {
                    SeekBar(
                        current = currentTime,
                        duration = duration,
                        accent = theme.accent,
                        onSeek = { PlayerViewModel.seek(it) },
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))
                TransportControls(
                    isPlaying = isPlaying,
                    accent = theme.accent,
                    onAccent = theme.onAccent
                )
            }
        }
    }
}

@Composable
private fun PlayerHeader(track: Track, showingQueue: Boolean, onToggleQueue: () -> Unit) {
    val configuration = LocalConfiguration.current
    val fullSide = (configuration.screenWidthDp - 64).coerceAtMost(380).dp

    val artworkSize by animateDpAsState(
        targetValue = if (showingQueue) 56.dp else fullSide,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "artworkSize"
    )
    val corner by animateDpAsState(
        targetValue = if (showingQueue) 8.dp else 12.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "artworkCorner"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (showingQueue) 20.dp else 32.dp)
            .padding(top = if (showingQueue) 12.dp else 0.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.clickable { onToggleQueue() }
            ) {
                CachedImage(
                    youtubeID = track.youtubeID,
                    remoteURL = track.thumbnailURL,
                    size = artworkSize,
                    corner = corner
                )
            }
            if (showingQueue) {
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleDisplay(track),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artistDisplay(track),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (!showingQueue) {
            Spacer(Modifier.height(28.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = titleDisplay(track),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = artistDisplay(track),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SeekBar(
    current: Double,
    duration: Double,
    accent: Color,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val dur = if (duration > 0) duration else 1.0
    var dragValue by remember { mutableStateOf<Double?>(null) }
    val shown = dragValue ?: current
    val fraction = (shown / dur).coerceIn(0.0, 1.0).toFloat()

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(dur) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragValue = ((offset.x / size.width) * dur).coerceIn(0.0, dur)
                        },
                        onDrag = { change, _ ->
                            dragValue = ((change.position.x / size.width) * dur).coerceIn(0.0, dur)
                        },
                        onDragEnd = {
                            dragValue?.let { onSeek(it) }
                            dragValue = null
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(formatTime(current), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(formatTime(dur), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DownloadProgressBar(progress: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "Preparing download... ${(progress * 100).toInt()}%",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0.0, 1.0).toFloat())
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun TransportControls(isPlaying: Boolean, accent: Color, onAccent: Color) {
    val loopMode by PlayerViewModel.loopMode.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { PlayerViewModel.toggleLoop() }
        ) {
            Icon(
                imageVector = Icons.Filled.Repeat,
                contentDescription = "Loop",
                tint = if (loopMode != LoopMode.NONE) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = when (loopMode) { LoopMode.NONE -> "Off"; LoopMode.ONE -> "One"; LoopMode.ALL -> "All" },
                color = if (loopMode != LoopMode.NONE) Color.White else Color.White.copy(alpha = 0.3f),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Icon(
            imageVector = Icons.Filled.SkipPrevious,
            contentDescription = "Previous",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(30.dp).clickable { PlayerViewModel.previousTrack() }
        )

        Box(
            modifier = Modifier
                .size(68.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(accent)
                .clickable { PlayerViewModel.togglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play/Pause",
                tint = onAccent,
                modifier = Modifier.size(30.dp)
            )
        }

        Icon(
            imageVector = Icons.Filled.SkipNext,
            contentDescription = "Next",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(30.dp).clickable { PlayerViewModel.nextTrack() }
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { PlayerViewModel.shuffleQueue() }
        ) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = "Shuffle",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Text("Shuffle", color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QueueList(modifier: Modifier = Modifier) {
    val queue by PlayerViewModel.queue.collectAsState()
    val queueIndex by PlayerViewModel.queueIndex.collectAsState()

    if (queue.size <= 1) return

    val upcoming = (1 until queue.size).map { (queueIndex + it) % queue.size }

    LazyColumn(modifier = modifier.padding(top = 20.dp)) {
        item {
            Text(
                text = "Up Next",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
        items(upcoming.size) { offset ->
            val idx = upcoming[offset]
            QueueRow(
                track = queue[idx],
                number = offset + 1,
                isNext = offset == 0,
                onClick = { PlayerViewModel.playTrack(at = idx) }
            )
        }
    }
}

@Composable
private fun QueueRow(track: Track, number: Int, isNext: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$number",
            color = if (isNext) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.35f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        CachedImage(
            youtubeID = track.youtubeID,
            remoteURL = track.thumbnailURL,
            size = 42.dp,
            corner = 8.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titleDisplay(track),
                color = if (isNext) Color.White else Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artistDisplay(track),
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isNext) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "NEXT",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Nothing playing", color = Color.White.copy(alpha = 0.4f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Pick a track from your library.", color = Color.White.copy(alpha = 0.2f), fontSize = 14.sp)
    }
}

private fun formatTime(t: Double): String {
    if (!t.isFinite() || t < 0) return "0:00"
    val total = t.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}