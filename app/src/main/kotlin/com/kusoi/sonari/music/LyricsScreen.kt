package com.kusoi.sonari.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.min

/**
 * Port of Sonari/Views/LyricsView.swift. Collapsed mini-header with a 2dp
 * progress line, tap to expand a full transport row, and the lyric list that
 * scrolls the current line to center on a spring.
 */
@Composable
fun LyricsScreen(engine: PlayerEngine) {
    val track by engine.currentTrack.collectAsStateWithLifecycle()
    val lyrics by engine.lyrics.collectAsStateWithLifecycle()
    val currentIndex by engine.currentLyricIndex.collectAsStateWithLifecycle()
    val isFetching by engine.isFetchingLyrics.collectAsStateWithLifecycle()
    val searchStatus by engine.lyricsSearchStatus.collectAsStateWithLifecycle()
    val isPlaying by engine.isPlaying.collectAsStateWithLifecycle()
    val currentTime by engine.currentTime.collectAsStateWithLifecycle()
    val duration by engine.duration.collectAsStateWithLifecycle()
    val theme by engine.theme.collectAsStateWithLifecycle()

    var isExpanded by remember { mutableStateOf(false) }
    var lyricsMode by remember { mutableStateOf(LyricsMode.load()) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val current = track
        if (current == null) {
            NoTrackPlaying()
            return@Column
        }

        MiniHeader(
            track = current,
            isPlaying = isPlaying,
            accent = theme.accent,
            expanded = isExpanded,
            progress = if (duration > 0) min(currentTime / duration, 1.0) else 0.0,
            onTap = { isExpanded = !isExpanded },
            onPlayPause = { engine.togglePlayPause() },
            onNext = { engine.nextTrack() },
            onModeChange = { lyricsMode = it; it.save() }
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + slideInVertically { -it / 4 },
            exit = fadeOut() + slideOutVertically { -it / 4 }
        ) {
            ExpandedControls(
                engine = engine,
                duration = duration,
                currentTime = currentTime,
                isPlaying = isPlaying,
                accent = theme.accent,
                onCollapse = { isExpanded = false }
            )
        }

        when {
            isFetching -> LoadingLyrics(status = searchStatus)
            lyrics.isEmpty() -> NoLyrics(onRetry = { engine.refreshLyrics() })
            else -> LyricList(
                engine = engine,
                lyrics = lyrics,
                currentIndex = currentIndex,
                mode = lyricsMode,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MiniHeader(
    track: Track,
    isPlaying: Boolean,
    accent: Color,
    expanded: Boolean,
    progress: Double,
    onTap: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onModeChange: (LyricsMode) -> Unit
) {
    var mode by remember { mutableStateOf(LyricsMode.load()) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTap() }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(track.youtubeID, track.thumbnailURL, 36.dp, 6.dp, iconSize = 11.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    TrackText.titleDisplay(track),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    TrackText.artistDisplay(track),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/pause",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onPlayPause() }
                )
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNext() }
                )
                Box {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        contentDescription = "Lyrics style",
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { menuOpen = true }
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Karaoke Highlight") },
                            onClick = {
                                menuOpen = false
                                mode = LyricsMode.Karaoke
                                mode.save()
                                onModeChange(mode)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Plain") },
                            onClick = {
                                menuOpen = false
                                mode = LyricsMode.Plain
                                mode.save()
                                onModeChange(mode)
                            }
                        )
                    }
                }
            }
        }

        if (!expanded) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.1f))) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.toFloat().coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(accent)
                )
            }
        }
    }
}

@Composable
private fun ExpandedControls(
    engine: PlayerEngine,
    duration: Double,
    currentTime: Double,
    isPlaying: Boolean,
    accent: Color,
    onCollapse: () -> Unit
) {
    var sliderValue by remember(duration) { mutableStateOf(currentTime.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val safeDuration = if (duration.isFinite() && duration > 0) duration else 1.0

    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)) {
        Slider(
            value = if (dragging) sliderValue else currentTime.toFloat().coerceIn(0f, safeDuration.toFloat()),
            onValueChange = { sliderValue = it; dragging = true },
            onValueChangeFinished = {
                engine.seek(sliderValue.toDouble())
                dragging = false
            },
            valueRange = 0f..safeDuration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(currentTime), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
            Text(formatTime(safeDuration), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play/pause",
                tint = Color.White,
                modifier = Modifier.size(44.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { engine.togglePlayPause() }
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Collapse",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(30.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    .padding(8.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCollapse() }
            )
        }
    }
}

/**
 * The lyric list. Every line is the same composable, so identity never changes
 * when a line becomes current. Only the current line animates its scale and,
 * in karaoke mode, recomputes per-character color every frame.
 */
@Composable
private fun LyricList(
    engine: PlayerEngine,
    lyrics: List<LyricLine>,
    currentIndex: Int,
    mode: LyricsMode,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        val target = (currentIndex - 3).coerceAtLeast(0)
        listState.animateScrollToItem(target)
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 120.dp, bottom = 240.dp)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val offset = index - currentIndex
                val isCurrent = offset == 0
                val isNear = abs(offset) == 1
                val isFar = abs(offset) >= 3

                val scale by animateFloatAsState(
                    if (isCurrent) 1f else if (isNear) 0.97f else 0.93f,
                    Motion.lyricScale(),
                    label = "lyricScale"
                )
                val bottomPad by animateDpAsState(
                    if (isCurrent) 36.dp else if (isNear) 28.dp else 20.dp,
                    Motion.lyricScale(),
                    label = "lyricPad"
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .padding(bottom = bottomPad)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { engine.seekToLyric(line) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrent && mode == LyricsMode.Karaoke) {
                        KaraokeLine(
                            engine = engine,
                            index = index,
                            text = line.text,
                            fontSize = 26.sp,
                            modifier = Modifier.scale(scale)
                        )
                    } else {
                        Text(
                            line.text,
                            color = when {
                                isCurrent -> Color.White
                                isFar -> Color.White.copy(alpha = 0.2f)
                                else -> Color.White.copy(alpha = 0.4f)
                            },
                            fontSize = when {
                                isCurrent -> 26.sp
                                isNear -> 20.sp
                                else -> 17.sp
                            },
                            fontWeight = when {
                                isCurrent -> FontWeight.Bold
                                isNear -> FontWeight.SemiBold
                                else -> FontWeight.Medium
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.scale(scale)
                        )
                    }
                }
            }
        }

        // Top and bottom fades so lines dissolve into the gradient.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                    )
                )
        )
    }
}

/**
 * One Text with per-character color, exactly like KaraokeText.build. The reveal
 * fraction is read from the engine every frame and glides across ~1.2 chars so
 * the leading edge is smooth, not stepped.
 */
@Composable
private fun KaraokeLine(
    engine: PlayerEngine,
    index: Int,
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    var fraction by remember { mutableStateOf(0.0) }
    LaunchedEffect(index) {
        while (true) {
            withFrameNanos {
                fraction = engine.revealedFraction(index, engine.smoothTime())
            }
        }
    }

    val chars = text.toList()
    val brightCount = chars.size * fraction
    val annotated = buildAnnotatedString {
        chars.forEachIndexed { i, ch ->
            val distance = brightCount - i
            val brightness = ((distance / 1.2).coerceIn(0.0, 1.0))
            val opacity = 0.32f + (1f - 0.32f) * brightness.toFloat()
            withStyle(SpanStyle(color = Color.White.copy(alpha = opacity))) {
                append(ch.toString())
            }
        }
    }

    Text(
        annotated,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
private fun LoadingLyrics(status: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(16.dp))
        Text("Finding lyrics...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                status,
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun NoLyrics(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.TextFields,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("No synced lyrics found", color = Color.White.copy(alpha = 0.5f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onRetry() }
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text("Retry", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun NoTrackPlaying() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No track playing", color = Color.White.copy(alpha = 0.5f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("Play a song to see lyrics here.", color = Color.White.copy(alpha = 0.25f), fontSize = 14.sp)
    }
}
