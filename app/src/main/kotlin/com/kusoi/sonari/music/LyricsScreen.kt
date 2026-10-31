package com.kusoi.sonari.music

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
 * Lyrics. A slim now-playing header up top, then the lyric list with the
 * current line centred. Every line is the same composable so its identity
 * never changes when it becomes current; only the animated values move. The
 * list scrolls on a low-stiffness spring so a line arriving in the middle
 * reads as a glide, and the highlight colour crossfades instead of cutting.
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
    val queue by engine.queue.collectAsStateWithLifecycle()
    val queueIndex by engine.queueIndex.collectAsStateWithLifecycle()
    val loopMode by engine.loopMode.collectAsStateWithLifecycle()
    val isShuffled by engine.isShuffled.collectAsStateWithLifecycle()

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
            progress = if (duration > 0) min(currentTime / duration, 1.0) else 0.0,
            expanded = isExpanded,
            onTap = { isExpanded = !isExpanded },
            onPlayPause = { engine.togglePlayPause() },
            onNext = { engine.nextTrack() },
            onModeChange = { lyricsMode = it; it.save() }
        )

        if (isExpanded) {
            ExpandedControls(
                engine = engine,
                duration = duration,
                currentTime = currentTime,
                isPlaying = isPlaying,
                accent = theme.accent,
                queueIndex = queueIndex,
                queueSize = queue.size,
                loopMode = loopMode,
                shuffleOn = isShuffled,
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
    progress: Double,
    expanded: Boolean,
    onTap: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onModeChange: (LyricsMode) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(SonariSurface.card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTap() }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(track.youtubeID, track.thumbnailURL, 40.dp, 10.dp, iconSize = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    TrackText.titleDisplay(track),
                    color = SonariText.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    TrackText.artistDisplay(track),
                    color = SonariText.tertiary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play/pause",
                tint = SonariText.primary,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onPlayPause() }
                    .padding(6.dp)
            )
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = SonariText.secondary,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNext() }
                    .padding(7.dp)
            )
            Box {
                Icon(
                    Icons.Rounded.MoreHoriz,
                    contentDescription = "Lyrics style",
                    tint = SonariText.secondary,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { menuOpen = true }
                        .padding(7.dp)
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Karaoke highlight") },
                        onClick = {
                            menuOpen = false
                            onModeChange(LyricsMode.Karaoke)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Plain") },
                        onClick = {
                            menuOpen = false
                            onModeChange(LyricsMode.Plain)
                        }
                    )
                }
            }
        }

        if (!expanded) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.06f))) {
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
    queueIndex: Int,
    queueSize: Int,
    loopMode: LoopMode,
    shuffleOn: Boolean,
    onCollapse: () -> Unit
) {
    var sliderValue by remember(duration) { mutableStateOf(currentTime.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val safeDuration = if (duration.isFinite() && duration > 0) duration else 1.0

    Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 8.dp)) {
        AppleMusicThinSlider(
            value = if (dragging) sliderValue / safeDuration.toFloat() else (currentTime / safeDuration).toFloat().coerceIn(0f, 1f),
            activeColor = accent,
            onValueChange = { sliderValue = it * safeDuration.toFloat(); dragging = true },
            onValueChangeFinished = {
                engine.seek(sliderValue.toDouble())
                dragging = false
            }
        )
        Spacer(Modifier.height(6.dp))
        AppleMusicTimesRow(
            elapsed = if (dragging) sliderValue.toDouble() else currentTime,
            total = duration
        )
        Spacer(Modifier.height(14.dp))
        AppleMusicTransport(
            isPlaying = isPlaying,
            canPrevious = queueIndex > 0,
            canNext = queueIndex < queueSize - 1,
            shuffleOn = shuffleOn,
            loopMode = loopMode,
            onPlayPause = { engine.togglePlayPause() },
            onNext = { engine.nextTrack() },
            onPrevious = { engine.previousTrack() },
            onLoop = { engine.toggleLoop() },
            onShuffle = { engine.shuffleQueue() }
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(36.dp)
                .clip(CircleShape)
                .background(SonariSurface.raised)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onCollapse() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Collapse", tint = SonariText.secondary, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Lyric list. The scroll target is the current line minus a fixed lookahead so
 * the active line lands slightly above centre, which leaves room for the next
 * line to arrive without a second correction. All per-line values are
 * animated, so a change of current line is a transition rather than a swap.
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
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Centre the active line. Half a viewport of padding above and below means
    // the target index is the line itself, and the springs on each line carry
    // the motion; this scroll just keeps it in the middle.
    LaunchedEffect(currentIndex) {
        listState.animateScrollToItem(currentIndex)
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = screenHeight * 0.34f, bottom = screenHeight * 0.40f)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val offset = index - currentIndex
                val isCurrent = offset == 0
                val isNear = abs(offset) == 1
                val isFar = abs(offset) >= 3

                val targetScale = when {
                    isCurrent -> 1f
                    isNear -> 0.96f
                    else -> 0.9f
                }
                val scale by animateFloatAsState(targetScale, Motion.lyricScale(), label = "lyricScale")
                val alpha by animateFloatAsState(
                    when {
                        isCurrent -> 1f
                        isNear -> 0.55f
                        isFar -> 0.18f
                        else -> 0.34f
                    },
                    Motion.lyricScale(),
                    label = "lyricAlpha"
                )
                val bottomPad by animateDpAsState(
                    when {
                        isCurrent -> 34.dp
                        isNear -> 26.dp
                        else -> 20.dp
                    },
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
                    val textModifier = Modifier.scale(scale).alpha(alpha)
                    if (isCurrent && mode == LyricsMode.Karaoke) {
                        KaraokeLine(
                            engine = engine,
                            index = index,
                            text = line.text,
                            fontSize = 27.sp,
                            modifier = textModifier
                        )
                    } else {
                        val color by animateColorAsState(
                            if (isCurrent) SonariText.primary else SonariText.secondary,
                            Motion.lyricScale(),
                            label = "lyricColor"
                        )
                        Text(
                            line.text,
                            color = color,
                            fontSize = when {
                                isCurrent -> 27.sp
                                isNear -> 21.sp
                                else -> 18.sp
                            },
                            fontWeight = when {
                                isCurrent -> FontWeight.Bold
                                isNear -> FontWeight.SemiBold
                                else -> FontWeight.Medium
                            },
                            textAlign = TextAlign.Center,
                            modifier = textModifier
                        )
                    }
                }
            }
        }

        // Top fade only. The bottom is handled by content padding so the last
        // line can still be tapped when the track ends.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(90.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(SonariSurface.base.copy(alpha = 0.9f), Color.Transparent)
                    )
                )
        )
    }
}

/**
 * One Text with per-character colour. The reveal fraction is read from the
 * engine every frame and glides across ~1.2 chars so the leading edge is
 * smooth. Same curve as iOS; do not add holds or beat terms here.
 */
@Composable
private fun KaraokeLine(
    engine: PlayerEngine,
    index: Int,
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    val isPlaying by engine.isPlaying.collectAsStateWithLifecycle()
    var fraction by remember { mutableStateOf(0.0) }
    LaunchedEffect(index) {
        while (true) {
            withFrameNanos {
                // Paused playback holds the line fully lit. A frozen half-swept
                // line reads as a rendering bug, not as a pause.
                fraction = if (isPlaying) {
                    engine.revealedFraction(index, engine.smoothTime())
                } else {
                    1.0
                }
            }
        }
    }

    val chars = text.toList()
    val brightCount = chars.size * fraction
    val annotated = buildAnnotatedString {
        chars.forEachIndexed { i, ch ->
            val distance = brightCount - i
            val brightness = (distance / 1.2).coerceIn(0.0, 1.0)
            val alpha = 0.34f + (1f - 0.34f) * brightness.toFloat()
            withStyle(SpanStyle(color = SonariText.primary.copy(alpha = alpha))) {
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
        CircularProgressIndicator(color = SonariSurface.accent, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(16.dp))
        Text("Finding lyrics", color = SonariText.secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                status,
                color = SonariText.tertiary,
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
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(SonariSurface.raised),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.TextFields, contentDescription = null, tint = SonariText.tertiary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("No synced lyrics", color = SonariText.primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Try a different search or add them later.", color = SonariText.tertiary, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(SonariSurface.accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onRetry() }
                .padding(horizontal = 26.dp, vertical = 12.dp)
        ) {
            Text("Retry", color = SonariText.onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(SonariSurface.raised),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = SonariText.tertiary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("No track playing", color = SonariText.primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Play a song to see its lyrics here.", color = SonariText.tertiary, fontSize = 13.sp)
    }
}
