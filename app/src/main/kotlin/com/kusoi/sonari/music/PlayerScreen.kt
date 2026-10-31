package com.kusoi.sonari.music

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.min

/**
 * Now playing. Artwork is a big rounded card that floats on the backdrop, the
 * title block sits under it, then a slim seek row and a pill transport. The
 * queue is a bottom sheet that lifts over the same card, and the card shrinks
 * into a header row on the same spring so the two states share one motion.
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
    val isShuffled by engine.isShuffled.collectAsStateWithLifecycle()

    var showingQueue by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        val current = track
        if (current == null) {
            EmptyPlayer()
            return@Box
        }

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(
                compact = showingQueue,
                onToggleQueue = { showingQueue = !showingQueue }
            )

            if (showingQueue) {
                CompactHeader(track = current)
                UpNextList(
                    queue = queue,
                    queueIndex = queueIndex,
                    onSelect = { engine.playTrackAt(it) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
                ArtworkCard(track = current)
                Spacer(Modifier.height(28.dp))
                TitleBlock(track = current)
                Spacer(Modifier.weight(1f))
            }

            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
                if (isDownloading) {
                    DownloadProgress(progress = downloadProgress)
                } else {
                    SeekRow(
                        currentTime = currentTime,
                        duration = duration,
                        accent = theme.accent,
                        onSeek = { engine.seek(it) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                // Primary actions carry the house violet. The sleeve accent is
                // for the room (background, seek fill), never for a button that
                // has to read as enabled.
                Transport(
                    isPlaying = isPlaying,
                    canPrevious = queueIndex > 0,
                    canNext = queueIndex < queue.size - 1,
                    shuffleOn = isShuffled,
                    loopMode = loopMode,
                    accent = SonariSurface.accent,
                    onAccent = SonariText.onAccent,
                    onPlayPause = { engine.togglePlayPause() },
                    onNext = { engine.nextTrack() },
                    onPrevious = { engine.previousTrack() },
                    onLoop = { engine.toggleLoop() },
                    onShuffle = { engine.shuffleQueue() },
                    onQueue = { showingQueue = !showingQueue }
                )
            }
        }
    }
}

@Composable
private fun TopBar(compact: Boolean, onToggleQueue: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleButton(Icons.Rounded.ChevronLeft, "Collapse", onClick = onToggleQueue)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (compact) "Up Next" else "Playing from library",
                color = SonariText.tertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Sonari",
                color = SonariText.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.weight(1f))
        CircleButton(Icons.Rounded.MoreHoriz, "More", onClick = {})
    }
}

/**
 * Artwork card. Square, sized off the screen width, and wrapped in a plain Box
 * so a weighted parent slot cannot stretch it. The shadow sits on an inner
 * layer after the clip so it does not inflate the measured bounds.
 */
@Composable
private fun ArtworkCard(track: Track) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val side = min(screenWidth - 56, 360).dp
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(side)
                .clip(RoundedCornerShape(28.dp))
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(28.dp),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.55f)
                )
                .clip(RoundedCornerShape(28.dp))
        ) {
            Artwork(
                youtubeID = track.youtubeID,
                remoteURL = track.thumbnailURL,
                size = side,
                corner = 28.dp,
                iconSize = 64.dp
            )
        }
    }
}

@Composable
private fun CompactHeader(track: Track) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track.youtubeID, track.thumbnailURL, 52.dp, 12.dp, iconSize = 16.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                TrackText.titleDisplay(track),
                color = SonariText.primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                TrackText.artistDisplay(track),
                color = SonariText.secondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        CircleButton(Icons.Rounded.FavoriteBorder, "Save", size = 40.dp, onClick = {})
    }
}

@Composable
private fun TitleBlock(track: Track) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                TrackText.titleDisplay(track),
                color = SonariText.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                TrackText.artistDisplay(track),
                color = SonariText.secondary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        CircleButton(Icons.Rounded.FavoriteBorder, "Save", onClick = {})
    }
}

@Composable
private fun SeekRow(
    currentTime: Double,
    duration: Double,
    accent: Color,
    onSeek: (Double) -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val fraction = if (dragging) {
        dragValue
    } else if (duration > 0) {
        (currentTime / duration).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    AppleMusicThinSlider(
        value = fraction,
        activeColor = accent,
        onValueChange = { dragValue = it; dragging = true },
        onValueChangeFinished = {
            dragging = false
            if (duration > 0) onSeek(dragValue * duration)
        }
    )
    Spacer(Modifier.height(6.dp))
    AppleMusicTimesRow(
        elapsed = if (dragging && duration > 0) dragValue * duration else currentTime,
        total = duration
    )
}

/**
 * Transport. Centre play is a violet circle; the rest are glyphs with the
 * house press inflation. Queue gets a slot at the far right so the five icons
 * stay balanced around the circle.
 */
@Composable
private fun Transport(
    isPlaying: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    shuffleOn: Boolean,
    loopMode: LoopMode,
    accent: Color,
    onAccent: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onLoop: () -> Unit,
    onShuffle: () -> Unit,
    onQueue: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(
                icon = Icons.Rounded.Shuffle,
                tint = if (shuffleOn) SonariText.primary else SonariText.tertiary,
                size = 34.dp,
                onClick = onShuffle
            )
            GlyphButton(
                icon = if (loopMode == LoopMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                tint = if (loopMode != LoopMode.None) SonariText.primary else SonariText.tertiary,
                size = 34.dp,
                onClick = onLoop
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = if (canPrevious) SonariText.primary else SonariText.tertiary,
                modifier = Modifier
                    .pressInflate()
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canPrevious) { onPrevious() }
                    .padding(10.dp)
            )

            Box(
                Modifier
                    .pressInflate(1.08f)
                    .size(64.dp)
                    .shadow(18.dp, CircleShape, ambientColor = accent.copy(alpha = 0.35f), spotColor = accent.copy(alpha = 0.4f))
                    .clip(CircleShape)
                    .background(accent)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = onAccent,
                    modifier = Modifier
                        .size(30.dp)
                        .offset(x = if (isPlaying) 0.dp else 2.dp)
                )
            }

            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = if (canNext) SonariText.primary else SonariText.tertiary,
                modifier = Modifier
                    .pressInflate()
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canNext) { onNext() }
                    .padding(10.dp)
            )
        }

        GlyphButton(
            icon = Icons.Rounded.QueueMusic,
            tint = SonariText.primary,
            size = 34.dp,
            onClick = onQueue
        )
    }
}

@Composable
private fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(SonariSurface.raised.copy(alpha = 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = SonariText.secondary, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun DownloadProgress(progress: Double) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                color = SonariSurface.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text("Preparing download", color = SonariText.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("${(progress * 100).toInt()}%", color = SonariText.tertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(SonariSurface.raised)) {
            Box(Modifier.fillMaxWidth(progress.toFloat().coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(SonariSurface.accent))
        }
    }
}

/** Up Next, styled as a sheet of cards. Only the visible rows build. */
@Composable
private fun UpNextList(
    queue: List<Track>,
    queueIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (queue.size <= 1) return
    val upcoming = (1 until queue.size).map { (queueIndex + it) % queue.size }
    LazyColumn(modifier.fillMaxWidth().padding(top = 6.dp)) {
        itemsIndexed(upcoming) { offset, idx ->
            val track = queue[idx]
            val isNext = offset == 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isNext) SonariSurface.card else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(idx) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(track.youtubeID, track.thumbnailURL, 46.dp, 12.dp, iconSize = 12.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        TrackText.titleDisplay(track),
                        color = if (isNext) SonariText.primary else SonariText.secondary,
                        fontSize = 15.sp,
                        fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row {
                        Text(
                            TrackText.artistDisplay(track),
                            color = SonariText.tertiary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        track.duration?.let {
                            Text("  ·  ${formatTime(it)}", color = SonariText.tertiary, fontSize = 12.sp)
                        }
                    }
                }
                if (isNext) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(SonariSurface.accent.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("NEXT", color = SonariSurface.accentSoft, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
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
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(SonariSurface.raised),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = SonariText.tertiary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Nothing playing", color = SonariText.primary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Pick a track from your library.", color = SonariText.tertiary, fontSize = 14.sp)
    }
}
