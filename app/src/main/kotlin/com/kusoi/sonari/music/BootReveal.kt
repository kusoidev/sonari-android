package com.kusoi.sonari.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Hand-off from the system splash. The platform draws the animated mark, then
 * this draws the same mark once more over the app while the app fades in, so
 * the two halves read as one continuous launch instead of a cut.
 */
@Composable
fun BootReveal(content: @Composable () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    val markScale = remember { Animatable(0.82f) }
    val markAlpha = remember { Animatable(0f) }
    val backdropAlpha = remember { Animatable(1f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        markAlpha.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        markScale.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        // Hold a beat so the mark is read, then dissolve into the app.
        kotlinx.coroutines.delay(120)
        backdropAlpha.animateTo(0f, tween(340, easing = FastOutSlowInEasing))
        contentAlpha.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        finished = true
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha.value }) {
            content()
        }

        if (!finished) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = backdropAlpha.value }
                    .background(SonariSurface.base),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    Modifier.size(108.dp).graphicsLayer {
                        scaleX = markScale.value
                        scaleY = markScale.value
                        alpha = markAlpha.value
                    }
                ) {
                    drawSonariMark(SonariSurface.accent)
                }
            }
        }
    }
}

/**
 * The Sonari mark: a note head, a stem and a beam. Drawn rather than shipped
 * as a bitmap so it scales with density and uses the same accent token.
 */
private fun DrawScope.drawSonariMark(tint: Color) {
    val w = size.width
    val h = size.height
    val stemWidth = w * 0.085f
    val headRadius = w * 0.175f

    drawOval(
        color = tint,
        topLeft = Offset(w * 0.14f, h * 0.60f),
        size = Size(headRadius * 2.1f, headRadius * 1.75f)
    )

    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.14f + headRadius * 1.92f, h * 0.20f),
        size = Size(stemWidth, h * 0.48f),
        cornerRadius = CornerRadius(stemWidth / 2f, stemWidth / 2f)
    )

    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.30f, h * 0.20f),
        size = Size(w * 0.46f, h * 0.075f),
        cornerRadius = CornerRadius(h * 0.04f, h * 0.04f)
    )
}
