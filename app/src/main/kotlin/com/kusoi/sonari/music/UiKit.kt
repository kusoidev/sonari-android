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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Backdrop for the whole app. Three soft violet orbs on a near-black base,
 * heavily blurred so they read as light rather than shapes. The orbs drift
 * with the theme accent so a warm sleeve still warms the page.
 */
@Composable
fun ThemeBackground(theme: AppTheme, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(SonariSurface.base)) {
        Box(
            Modifier
                .fillMaxSize()
                .blur(120.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            theme.backgroundTop.copy(alpha = 0.95f),
                            theme.backgroundBottom.copy(alpha = 0.85f),
                            SonariSurface.base
                        )
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(SonariSurface.halo.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 900f
                    )
                )
        )
    }
}

/** Thumbnail with a soft placeholder and one shared corner language. */
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
            .background(SonariSurface.raised),
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
                tint = SonariText.tertiary,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Fixed tokens. The mockup is violet-led, so the surfaces are a neutral with a
 * hair of magenta chroma instead of pure black, and text steps are measured
 * against that base rather than against white.
 */
object SonariSurface {
    val base = Color(0xFF0B0912)
    val raised = Color(0xFF16121F)
    val raisedHigh = Color(0xFF1F1A2B)
    val card = Color(0xFF171320)
    val glass = Color(0xFF1B1726)
    val halo = Color(0xFF7B4DFF)
    val accent = Color(0xFF8B5CF6)
    val accentSoft = Color(0xFFB79CFF)
    val pillUnselected = Color(0xFF2A2436)
}

object SonariText {
    val primary = Color(0xFFF4F1FA)
    val secondary = Color(0xFFA79FB8)
    val tertiary = Color(0xFF6E667F)
    val onAccent = Color(0xFF140F20)
}

/** Legacy white-alpha tokens. ShareScreen still reads these. */
object Ink {
    val hairline = Color.White.copy(alpha = 0.08f)
    val divider = Color.White.copy(alpha = 0.05f)
    val dim = SonariText.secondary
    val faint = SonariText.tertiary
    val ghost = Color.White.copy(alpha = 0.08f)
}
