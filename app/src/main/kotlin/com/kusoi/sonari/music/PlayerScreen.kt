package com.kusoi.sonari.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.min

/**
 * Port of Sonari/Views/PlayerView.swift (phoneBody). Full-screen artwork with
 * title below, or a 56pt thumbnail top-left with title beside it once the
 * queue opens. One continuous artwork view so the resize interpolates.
 */
@Composable
fun PlayerScreen(engine: PlayerEngine) {
    val track by engine.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by engine.isPlaying.collectAsStateWithLifecycle()
    val currentTime by engine.currentTime.collectAsStateWithLifecycle()
    val duration by engine.duration.collectAsStateWithLifecycle()
    val theme by engine.theme.collectAsStateWithLifecycle()
    val isDownloading by engine.isDownloading.collectAsStateWithLifecycle()
    val downloadProgress by engine.downloadProgress.collectAsStateWithLifecycle()
    val queue by engine.queue.collectAsStateWithLifecycle()
    val queueIndex by engine.queueIndex.collectAsStateWithLifecycle()
    val loopMode by engine.loopMode.collectAsStateWithLifecycle()

    var showingQueue by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        val current = track
        if (current == null) {
            EmptyPlayer()
            return@Box
        }

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (!showingQueue) Spacer(Modifier.weight(0.35f))

            PlayerHeader(
                track = current,
                accent = theme.accent,
                compact = showingQueue,
                onToggleQueue = { showingQueue = !showingQueue }
            )

            if (showingQueue) {
                UpNextList(
                    queue = queue,
                    queueIndex = queueIndex,
                    onSelect = { engine.playTrackAt(it) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            Column(Modifier.padding(bottom = 24.dp)) {
                if (isDownloading) {
                    DownloadProgress(progress = downloadProgress, modifier = Modifier.padding(horizontal = 28.dp))
                } else {
                    SeekBar(
                        current = currentTime,
                        duration = duration,
                        accent = theme.accent,
                        onSeek = { engine.seek(it) },
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                TransportControls(
                    isPlaying = isPlaying,
                    loopMode = loopMode,
                    accent = theme.accent,
                    onAccent = theme.onAccent,
                    onPlayPause = { engine.togglePlayPause() },
                    onNext = { engine.nextTrack() },
                    onPrevious = { engine.previousTrack() },
                    onLoop = { engine.toggleLoop() },
                    onShuffle = { engine.shuffleQueue() }
                )
            }
        }
    }
}

/**
 * The morph. Compact: artwork 56dp top-left, title/artist beside it.
 * Full: artwork sized to the screen width minus 64dp, capped at 380dp, centred
 * with title/artist stacked below. The artwork frame and the text positions
 * animate on the same spring so nothing races.
 */
@Composable
private fun PlayerHeader(
    track: Track,
    accent: Color,
    compact: Boolean,
    onToggleQueue: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val fullSide = min(screenWidth - 64, 380).dp
    val side by animateDpAsState(if (compact) 56.dp else fullSide, Motion.header(), label = "artSide")
    val corner by animateDpAsState(if (compact) 8.dp else 12.dp, Motion.header(), label = "artCorner")
    val horizontal by animateDpAsState(if (compact) 20.dp else 32.dp, Motion.header(), label = "hpad")
    val topPad by animateDpAsState(if (compact) 12.dp else 0.dp, Motion.header(), label = "toppad")

    Column(Modifier.padding(horizontal = horizontal).padding(top = topPad)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!compact) Spacer(Modifier.weight(1f))
            Box(
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggleQueue() }
            ) {
                Artwork(
                    youtubeID = track.youtubeID,
                    remoteURL = track.thumbnailURL,
                    size = side,
                    corner = corner
                )
            }
            if (compact) {
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        TrackText.titleDisplay(track),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        TrackText.artistDisplay(track),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        AnimatedVisibility(
            visible = !compact,
            enter = fadeIn(Motion.header()) + slideInVertically(Motion.header()) { it / 3 },
            exit = fadeOut(Motion.quickFade())
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    TrackText.titleDisplay(track),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    TrackText.artistDisplay(track),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 15.sp,
                    maxLines = 1
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
    val safeDuration = if (duration.isFinite() && duration > 0) duration else 1.0
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0.0) }
    val shown = if (isDragging) dragValue else current
    val fraction = min(shown / safeDuration, 1.0).toFloat()

    val knobSize by animateDpAsState(if (isDragging) 16.dp else 12.dp, Motion.knob(), label = "knob")
    var trackWidth by remember { mutableStateOf(1) }

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(safeDuration) {
                    trackWidth = size.width
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragValue = (offset.x / size.width) * safeDuration
                        },
                        onDrag = { change, _ ->
                            dragValue = (change.position.x / size.width).coerceIn(0f, 1f) * safeDuration
                        },
                        onDragEnd = {
                            onSeek(dragValue)
                            isDragging = false
                        },
                        onDragCancel = { isDragging = false }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            )
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((fraction * trackWidth - knobSize.toPx() / 2).toInt(), 0) }
                    .size(knobSize)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shown), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(formatTime(safeDuration), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    loopMode: LoopMode,
    accent: Color,
    onAccent: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onLoop: () -> Unit,
    onShuffle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(1f).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onLoop() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val active = loopMode != LoopMode.None
            Icon(
                if (loopMode == LoopMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Loop",
                tint = if (active) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                when (loopMode) { LoopMode.None -> "Off"; LoopMode.One -> "One"; LoopMode.All -> "All" },
                color = if (active) Color.White else Color.White.copy(alpha = 0.3f),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Icon(
            Icons.Rounded.SkipPrevious,
            contentDescription = "Previous",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f).size(26.dp).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onPrevious() }
        )

        Box(
            Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onPlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(68.dp)
                    .shadow(12.dp, CircleShape, ambientColor = accent.copy(alpha = 0.35f), spotColor = accent.copy(alpha = 0.35f))
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = onAccent,
                    modifier = Modifier.size(30.dp).offset(x = if (isPlaying) 0.dp else 2.dp)
                )
            }
        }

        Icon(
            Icons.Rounded.SkipNext,
            contentDescription = "Next",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f).size(26.dp).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onNext() }
        )

        Column(
            Modifier.weight(1f).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onShuffle() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Text("Shuffle", color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DownloadProgress(progress: Double, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Preparing download...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("${(progress * 100).toInt()}%", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                Modifier.fillMaxWidth(progress.toFloat().coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(Color.White)
            )
        }
    }
}

@Composable
private fun UpNextList(
    queue: List<Track>,
    queueIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (queue.size <= 1) return
    val upcoming = (1 until queue.size).map { (queueIndex + it) % queue.size }
    LazyColumn(modifier.padding(top = 20.dp)) {
        item {
            Text(
                "Up Next",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)
            )
        }
        itemsIndexed(upcoming) { offset, idx ->
            val track = queue[idx]
            val isNext = offset == 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(idx) }
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .background(if (isNext) Color.White.copy(alpha = 0.04f) else Color.Transparent, RoundedCornerShape(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${offset + 1}",
                    color = if (isNext) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.35f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(18.dp)
                )
                Spacer(Modifier.width(12.dp))
                Artwork(track.youtubeID, track.thumbnailURL, 42.dp, 8.dp, iconSize = 11.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        TrackText.titleDisplay(track),
                        color = if (isNext) Color.White else Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                    Row {
                        Text(TrackText.artistDisplay(track), color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, maxLines = 1)
                        track.duration?.let {
                            Text("  ·  ${formatTime(it)}", color = Color.White.copy(alpha = 0.25f), fontSize = 11.sp)
                        }
                    }
                }
                if (isNext) {
                    Text(
                        "NEXT",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlayer() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Nothing playing", color = Color.White.copy(alpha = 0.4f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("Pick a track from your library.", color = Color.White.copy(alpha = 0.2f), fontSize = 14.sp)
    }
}
