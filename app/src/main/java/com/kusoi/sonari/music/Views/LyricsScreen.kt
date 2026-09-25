package com.kusoi.sonari.music.Views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kusoi.sonari.music.Services.LyricLine
import com.kusoi.sonari.music.ViewModels.PlayerViewModel

@Composable
fun LyricsScreen(isActive: Boolean) {
    val track by PlayerViewModel.currentTrack.collectAsState()
    val isPlaying by PlayerViewModel.isPlaying.collectAsState()
    val currentTime by PlayerViewModel.currentTime.collectAsState()
    val duration by PlayerViewModel.duration.collectAsState()
    val lyrics by PlayerViewModel.lyrics.collectAsState()
    val currentIndex by PlayerViewModel.currentLyricIndex.collectAsState()
    val isFetching by PlayerViewModel.isFetchingLyrics.collectAsState()
    val searchStatus by PlayerViewModel.lyricsSearchStatus.collectAsState()
    val theme by PlayerViewModel.theme.collectAsState()

    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(track?.id) {
        if (track != null && lyrics.isEmpty()) PlayerViewModel.fetchLyricsForCurrentTrack()
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (track == null) {
                NoTrack()
                return@Column
            }

            val t = track!!
            MiniPlayer(
                track = t,
                isPlaying = isPlaying,
                expanded = expanded,
                currentTime = currentTime,
                duration = if (duration > 0) duration else (t.duration ?: 0.0),
                accent = theme.accent,
                onToggleExpand = { expanded = !expanded },
                onTogglePlay = { PlayerViewModel.togglePlayPause() },
                onNext = { PlayerViewModel.nextTrack() },
                onSeek = { PlayerViewModel.seek(it) },
                onClose = { expanded = false }
            )

            when {
                isFetching -> LoadingLyrics(searchStatus)
                lyrics.isEmpty() -> EmptyLyrics { PlayerViewModel.refreshLyrics() }
                else -> LyricsList(
                    lyrics = lyrics,
                    currentIndex = currentIndex,
                    isActive = isActive,
                    onTapLine = { PlayerViewModel.seekToLyric(it) }
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    track: com.kusoi.sonari.music.Models.Track,
    isPlaying: Boolean,
    expanded: Boolean,
    currentTime: Double,
    duration: Double,
    accent: Color,
    onToggleExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Double) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CachedImage(
                youtubeID = track.youtubeID,
                remoteURL = track.thumbnailURL,
                size = 36.dp,
                corner = 6.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleDisplay(track),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artistDisplay(track),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(26.dp).clickable { onTogglePlay() }
            )
            Spacer(Modifier.width(18.dp))
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(24.dp).clickable { onNext() }
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Slider(
                    value = currentTime.toFloat(),
                    onValueChange = { onSeek(it.toDouble()) },
                    valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(formatTime(currentTime), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(duration), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                val p = if (duration > 0) (currentTime / duration).coerceIn(0.0, 1.0) else 0.0
                Box(
                    modifier = Modifier
                        .fillMaxWidth(p.toFloat())
                        .height(2.dp)
                        .background(accent)
                )
            }
        }
    }
}

@Composable
private fun LyricsList(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    isActive: Boolean,
    onTapLine: (LyricLine) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex in lyrics.indices) {
            listState.animateScrollToItem(currentIndex)
        }
    }
    LaunchedEffect(isActive) {
        if (isActive && currentIndex in lyrics.indices) {
            listState.scrollToItem(currentIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 120.dp)
    ) {
        items(lyrics.size) { i ->
            val line = lyrics[i]
            val offset = i - currentIndex
            val isCurrent = offset == 0
            val isNear = kotlin.math.abs(offset) <= 1
            val isFar = kotlin.math.abs(offset) >= 3

            val scale by animateFloatAsState(
                targetValue = if (isCurrent) 1f else if (isNear) 0.97f else 0.93f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
                label = "lyricScale"
            )

            Text(
                text = line.text,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale)
                    .clickable { onTapLine(line) }
                    .padding(horizontal = 28.dp, vertical = if (isCurrent) 18.dp else 14.dp)
            )
        }
    }
}

@Composable
private fun LoadingLyrics(status: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        Spacer(Modifier.height(16.dp))
        Text("Finding lyrics...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(status, color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun EmptyLyrics(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No synced lyrics found", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable { onRetry() }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Retry", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun NoTrack() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No track playing", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Play a song to see lyrics here.", color = Color.White.copy(alpha = 0.25f), fontSize = 14.sp)
    }
}

private fun formatTime(t: Double): String {
    if (!t.isFinite() || t < 0) return "0:00"
    val total = t.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}