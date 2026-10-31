package com.kusoi.sonari.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Caveat, OFL. Bundled at res/font/caveat.ttf with its licence in assets. */
private val Handwritten = FontFamily(Font(R.font.caveat, FontWeight.Normal))

/**
 * Cold-start hand-off from the system splash.
 *
 * The platform draws the animated mark first, so the first Compose frame
 * continues from it rather than cutting. From there the wordmark writes on in
 * a handwritten face, a soft violet bloom rises behind it, and the whole lockup
 * lifts and dissolves into the app.
 */
@Composable
fun BootReveal(content: @Composable () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    val markScale = remember { Animatable(0.86f) }
    val markAlpha = remember { Animatable(0f) }
    val wordReveal = remember { Animatable(0f) }
    val bloom = remember { Animatable(0f) }
    val backdropAlpha = remember { Animatable(1f) }
    val contentAlpha = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        markAlpha.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        markScale.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        bloom.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        wordReveal.animateTo(1f, tween(760, easing = LinearEasing))
        delay(260)
        lift.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        backdropAlpha.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        contentAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = bloom.value * 0.55f }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(SonariSurface.accent.copy(alpha = 0.42f), Color.Transparent),
                                radius = 620f
                            )
                        )
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.graphicsLayer {
                        translationY = -lift.value * 22f
                        alpha = 1f - lift.value
                    }
                ) {
                    Canvas(
                        Modifier.size(86.dp).graphicsLayer {
                            scaleX = markScale.value
                            scaleY = markScale.value
                            alpha = markAlpha.value
                        }
                    ) {
                        drawSonariMark(SonariSurface.accent)
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sonari",
                        fontFamily = Handwritten,
                        fontSize = 46.sp,
                        color = SonariText.primary,
                        modifier = Modifier.graphicsLayer {
                            alpha = wordReveal.value
                            translationX = (1f - wordReveal.value) * -28f
                        }
                    )
                }
            }
        }
    }
}

/**
 * "Sonari" clipped to an animating width so the letters appear in sequence.
 * The text is laid out once at full size and measured, then the container is
 * narrowed to reveal fraction times that width. Width is a real layout clip;
 * scaling the text instead would squash the glyphs rather than write them on.
 */
/**
 * The Sonari mark: a note head, a stem and a beam. Drawn rather than shipped
 * as a bitmap so it scales with density and uses the same accent token.
 */
private fun DrawScope.drawSonariMark(tint: Color) {
    val w = size.width
    val h = size.height

    // A single eighth note: filled oval head bottom-left, stem rising from its
    // right edge, and a flag sweeping down-right off the stem top.
    val headW = w * 0.30f
    val headH = h * 0.22f
    val headLeft = w * 0.14f
    val headTop = h * 0.64f
    val stemX = headLeft + headW * 0.86f
    val stemW = w * 0.075f
    val stemTop = h * 0.14f

    drawOval(
        color = tint,
        topLeft = Offset(headLeft, headTop),
        size = Size(headW, headH)
    )

    drawRoundRect(
        color = tint,
        topLeft = Offset(stemX, stemTop),
        size = Size(stemW, headTop - stemTop + headH * 0.45f),
        cornerRadius = CornerRadius(stemW / 2f, stemW / 2f)
    )

    // Flag: a curved wedge from the stem top sweeping right and down.
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(stemX + stemW, stemTop)
        cubicTo(
            stemX + w * 0.42f, stemTop + h * 0.02f,
            stemX + w * 0.34f, stemTop + h * 0.16f,
            stemX + w * 0.18f, stemTop + h * 0.26f
        )
        lineTo(stemX + w * 0.13f, stemTop + h * 0.17f)
        cubicTo(
            stemX + w * 0.26f, stemTop + h * 0.10f,
            stemX + w * 0.20f, stemTop + h * 0.04f,
            stemX + stemW, stemTop + h * 0.05f
        )
        close()
    }
    drawPath(path, tint)
}
