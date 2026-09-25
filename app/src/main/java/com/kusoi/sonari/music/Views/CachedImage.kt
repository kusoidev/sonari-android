package com.kusoi.sonari.music.Views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kusoi.sonari.music.Services.ThumbnailCache
import java.io.File

/**
 * Thumbnail that checks the local disk cache first, then falls back to remote download.
 * Mirrors the Swift CachedAsyncImage: offline-but-cached still shows, online-but-uncached
 * downloads and caches for next time.
 */
@Composable
fun CachedImage(
    youtubeID: String?,
    remoteURL: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
    size: Dp
) {
    var cachedFile by remember(youtubeID) { mutableStateOf<File?>(null) }

    LaunchedEffect(youtubeID, remoteURL) {
        if (youtubeID == null) return@LaunchedEffect
        ThumbnailCache.cachedURL(youtubeID)?.let {
            cachedFile = it
            return@LaunchedEffect
        }
        if (remoteURL != null) {
            cachedFile = ThumbnailCache.ensureCached(youtubeID, remoteURL)
        }
    }

    val model: Any? = cachedFile ?: remoteURL

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(corner)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(size * 0.35f)
                )
            }
        }
    }
}