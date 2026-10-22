package com.kusoi.sonari.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Full-screen gradient that re-tints when the artwork theme changes. */
@Composable
fun ThemeBackground(theme: AppTheme, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(theme.backgroundTop, theme.backgroundBottom)))
    )
}

/** Thumbnail with the same placeholder treatment as CachedAsyncImage.swift. */
@Composable
fun Artwork(
    youtubeID: String,
    remoteURL: String?,
    size: Dp,
    corner: Dp,
    modifier: Modifier = Modifier,
    iconSize: Dp = if (size > 100.dp) 56.dp else 20.dp
) {
    val context = LocalContext.current
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        if (remoteURL != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(remoteURL).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/** Fixed white-alpha tokens used across the iOS views. */
object Ink {
    val hairline = Color.White.copy(alpha = 0.08f)
    val divider = Color.White.copy(alpha = 0.05f)
    val dim = Color.White.copy(alpha = 0.4f)
    val faint = Color.White.copy(alpha = 0.2f)
    val ghost = Color.White.copy(alpha = 0.08f)
}
