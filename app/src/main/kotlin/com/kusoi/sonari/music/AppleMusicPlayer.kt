package com.kusoi.sonari.music

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong

/**
 * Apple Music style player controls, following SimpMusic's approach: an 8.4dp
 * seek track that springs to 16.8dp while touched with no thumb, a tight
 * centered transport cluster, and 1.35x press inflation on every control.
 */

private val AppleMusicSecondaryText = SonariText.tertiary
private val AppleMusicTrackInactive = Color.White.copy(alpha = 0.12f)

/** The press inflation that gives the controls their rubbery feel. */
@Composable
internal fun Modifier.pressInflate(pressedScale: Float = 1.2f): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 380f),
        label = "pressInflate"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
}

/**
 * Track-only seek bar. One clipped container keeps both ends equally round.
 * It stays slim at rest and fattens on touch, no thumb, so the time labels do
 * the pointing.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun AppleMusicThinSlider(
    value: Float,
    activeColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()

    val trackHeight by animateDpAsState(
        targetValue = if (pressed || dragged) 12.dp else 5.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 320f),
        label = "sliderInflate"
    )

    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        interactionSource = interactionSource,
        track = {
            val fraction = value.coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(AppleMusicTrackInactive)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(activeColor)
                )
            }
        },
        thumb = { Spacer(Modifier.size(0.dp)) }
    )
}

/** Elapsed on the left, remaining on the right, in the tertiary tone. */
@Composable
internal fun AppleMusicTimesRow(elapsed: Double, total: Double) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            formatClock(elapsed),
            color = AppleMusicSecondaryText,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Left
        )
        Text(
            if (total > 0) "-" + formatClock(total - elapsed) else "--:--",
            color = AppleMusicSecondaryText,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Right
        )
    }
}

/** Round glyph button with the press inflation. */
@Composable
internal fun GlyphButton(
    icon: ImageVector,
    tint: Color,
    size: Dp,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .pressInflate()
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

/**
 * Compact transport used by the lyrics screen header. Same press language as
 * the player, without the big circle.
 */
@Composable
internal fun AppleMusicTransport(
    isPlaying: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    shuffleOn: Boolean,
    loopMode: LoopMode,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onLoop: () -> Unit,
    onShuffle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphButton(
            icon = Icons.Rounded.Shuffle,
            tint = if (shuffleOn) SonariText.primary else SonariText.tertiary,
            size = 36.dp,
            onClick = onShuffle
        )
        GlyphButton(
            icon = Icons.Rounded.SkipPrevious,
            tint = if (canPrevious) SonariText.primary else SonariText.tertiary,
            size = 40.dp,
            onClick = onPrevious
        )
        Box(
            Modifier
                .pressInflate(1.08f)
                .size(52.dp)
                .clip(CircleShape)
                .background(SonariSurface.accent)
                .clickable { onPlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = isPlaying, label = "playPauseIcon") { playing ->
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = SonariText.onAccent,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        GlyphButton(
            icon = Icons.Rounded.SkipNext,
            tint = if (canNext) SonariText.primary else SonariText.tertiary,
            size = 40.dp,
            onClick = onNext
        )
        GlyphButton(
            icon = if (loopMode == LoopMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            tint = if (loopMode != LoopMode.None) SonariText.primary else SonariText.tertiary,
            size = 36.dp,
            onClick = onLoop
        )
    }
}

internal fun formatClock(seconds: Double): String {
    if (!seconds.isFinite() || seconds < 0) return "0:00"
    val total = seconds.roundToLong()
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
