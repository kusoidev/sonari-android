package com.kusoi.sonari.music.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kusoi.sonari.music.Models.Track

/** Library row. Mirrors the Swift TrackRowView, including the now-playing overlay. */
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            CachedImage(
                youtubeID = track.youtubeID,
                remoteURL = track.thumbnailURL,
                size = 48.dp,
                corner = 8.dp
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titleDisplay(track),
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = artistDisplay(track),
                    color = Color.White.copy(alpha = if (isCurrent) 0.5f else 0.35f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val dur = track.duration
                if (dur != null && dur > 0) {
                    Text("  ·  ", color = Color.White.copy(alpha = 0.2f), fontSize = 12.sp)
                    Text(formatDuration(dur), color = Color.White.copy(alpha = 0.25f), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.width(4.dp))

        Icon(
            imageVector = if (isCurrent && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = "Play",
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(26.dp)
        )
    }
}

fun titleDisplay(track: Track): String {
    val parts = track.title.split(" - ")
    val titlePart = if (parts.size >= 2) parts.drop(1).joinToString(" - ") else track.title
    return titlePart.replace(Regex("""\(.*?\)"""), "").trim()
}

fun artistDisplay(track: Track): String {
    track.artist?.takeIf { it.isNotEmpty() }?.let { return it }
    val parts = track.title.split(" - ")
    if (parts.size < 2) return "Unknown"
    return parts[0].replace(Regex("""\(.*?\)"""), "").trim()
}

fun formatDuration(t: Double): String {
    if (!t.isFinite() || t <= 0) return ""
    val m = t.toInt() / 60
    val s = t.toInt() % 60
    return "%d:%02d".format(m, s)
}