package app.music.backend.sonari.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One short line about what just happened, such as a queue being shuffled. [anchor] is where the
 * button that caused it is drawn, in root pixels: the line is not pushed in from off screen, it is
 * born out of that button.
 */
data class BannerMessage(
  val id: Long,
  val text: String,
  val iconRes: Int? = null,
  val anchor: Rect? = null,
)

object ActionBanner {
  private val _message = MutableStateFlow<BannerMessage?>(null)
  val message: StateFlow<BannerMessage?> = _message.asStateFlow()

  private var counter = 0L

  fun show(text: String, iconRes: Int? = null, anchor: Rect? = null) {
    counter += 1
    _message.value = BannerMessage(id = counter, text = text, iconRes = iconRes, anchor = anchor)
  }

  fun dismiss() {
    _message.value = null
  }
}

/** How long one line lives, button to button. */
private const val ShowMillis = 2000

/** How fast an interrupted line is wound back into its button before the next one divides. */
private const val RewindMillis = 170

// One keyframed timeline, in fractions of ShowMillis. The beats never overlap in a way that fights:
// the cell blooms on the button, splits there, flies as two clean cells, and only then does the
// membrane refill into the capsule the pill fades in over. The exit is the same road backwards.

/** The cell grows out of the button. */
private const val BloomEnd = 0.03f

/** Two cells separate on the button, the membrane between them pinching away. */
private const val SplitStart = 0.03f
private const val SplitEnd = 0.12f

/** The two cells rise and part, out to the ends of the banner. */
private const val FlyStart = 0.12f
private const val FlyEnd = 0.32f

/** The membrane refills between them: the capsule the banner is. */
private const val SealStart = 0.32f
private const val SealEnd = 0.45f

/** The banner itself fades in over that capsule, and its words a moment after. */
private const val PillInStart = 0.34f
private const val PillInEnd = 0.46f
private const val TextInStart = 0.40f
private const val TextInEnd = 0.50f

/** It reads itself out, then goes back the way it came. */
private const val HoldEnd = 0.83f
private const val PillOutEnd = 0.87f
private const val UnsealStart = 0.835f
private const val UnsealEnd = 0.905f
private const val FlyBackStart = 0.87f
private const val FlyBackEnd = 0.975f
private const val ResealStart = 0.96f
private const val FadeOutStart = 0.978f

/** How far the two cells part while they are still sitting on the button. */
private val ButtonSpread = 17.dp

/**
 * The banner, grown out of the button that caused it.
 *
 * Pressing shuffle or loop does not slide a pill in: the button itself divides. A cell sitting
 * exactly on it blooms, pinches into two, and the pair rises to where the ends of the banner will
 * be; the membrane that refills between them is the capsule, and the pill fades in over it, so the
 * hand-over cannot be seen. Pressing again winds the whole thing back into the button and divides
 * it again with the new line.
 *
 * One number drives all of it, and it is only ever read from draw and layer lambdas, so no frame
 * of the animation recomposes the banner or lays the words out again.
 */
@Composable
fun ActionBannerHost(modifier: Modifier = Modifier) {
  val message by ActionBanner.message.collectAsState()
  val phase = remember { Animatable(0f) }
  var line by remember { mutableStateOf<BannerMessage?>(null) }
  var pill by remember { mutableStateOf(Rect.Zero) }

  LaunchedEffect(message?.id) {
    val next = message ?: return@LaunchedEffect
    // A line still in flight is wound back into its own button first, so the next one can grow out
    // of its button rather than tearing in over the top of the old one.
    if (line != null && phase.value > 0.001f) {
      // Only as long as there is something to unwind, so a fast tap-tap-tap stays responsive.
      val unwind = (RewindMillis * phase.value).toInt().coerceIn(60, RewindMillis)
      phase.animateTo(0f, tween(unwind, easing = FastOutSlowInEasing))
    }
    line = next
    phase.animateTo(1f, tween(ShowMillis, easing = LinearEasing))
  }

  val shown = line ?: return
  val pillColor = MaterialTheme.colorScheme.inverseSurface
  val contentColor = MaterialTheme.colorScheme.inverseOnSurface
  val iconColor = MaterialTheme.colorScheme.inversePrimary

  Box(modifier = modifier) {
    // The dividing cell. It is drawn from the button all the way up to the banner, which is outside
    // this box, so it is never clipped to the banner's own rectangle.
    Canvas(modifier = Modifier.matchParentSize()) {
      val p = phase.value
      val alpha = cellAt(p)
      if (alpha <= 0.002f) return@Canvas

      val capsule = pill
      if (capsule.width <= 0f || capsule.height <= 0f) return@Canvas

      val capsuleRadius = capsule.height / 2f
      val anchor = shown.anchor
      val buttonX = (anchor?.center?.x ?: capsule.center.x) - capsule.left
      val buttonY = (anchor?.center?.y ?: capsule.center.y) - capsule.top
      val buttonRadius =
        if (anchor != null && anchor.width > 0f && anchor.height > 0f) {
          minOf(minOf(anchor.width, anchor.height) / 2f, capsuleRadius)
        } else {
          capsuleRadius
        }

      // Three clean steps: bloom on the button, part on the button, then fly as two whole cells.
      val bloom = bloomAt(p)
      val spread = ButtonSpread.toPx() * partAt(p)
      val fly = flyAt(p)

      val radius = buttonRadius * bloom + (capsuleRadius - buttonRadius * bloom) * fly
      val fromLeft = buttonX - spread
      val fromRight = buttonX + spread
      val leftX = fromLeft + (capsuleRadius - fromLeft) * fly
      val rightX = fromRight + (capsule.width - capsuleRadius - fromRight) * fly
      val centerY = buttonY + (capsuleRadius - buttonY) * fly

      drawCell(
        leftX = leftX,
        rightX = rightX,
        centerY = centerY,
        radius = radius,
        neck = neckAt(p),
        color = pillColor.copy(alpha = pillColor.alpha * alpha),
      )
    }

    Surface(
      shape = RoundedCornerShape(percent = 50),
      color = pillColor,
      contentColor = contentColor,
      shadowElevation = 3.dp,
      // No tonal overlay: the capsule under the pill is drawn in exactly this colour, so the
      // banner arriving over it must not tint.
      tonalElevation = 0.dp,
      modifier =
        Modifier.onGloballyPositioned { pill = it.boundsInRoot() }
          .graphicsLayer { alpha = pillAt(phase.value) },
    ) {
      Row(
        modifier =
          Modifier.graphicsLayer { alpha = textAt(phase.value) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val icon = shown.iconRes
        if (icon != null) {
          Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = iconColor,
          )
          Spacer(Modifier.width(10.dp))
        }
        Text(text = shown.text, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

/** 0 on the button, 1 out at the ends of the banner. */
private fun flyAt(p: Float): Float = when {
  p <= FlyStart -> 0f
  p <= FlyEnd -> eased(FlyStart, FlyEnd, p)
  p <= FlyBackStart -> 1f
  else -> 1f - eased(FlyBackStart, FlyBackEnd, p)
}

/** How far the two cells have parted while they are still on the button: 0 together, 1 apart. */
private fun partAt(p: Float): Float = when {
  p <= SplitStart -> 0f
  p <= SplitEnd -> eased(SplitStart, SplitEnd, p)
  p <= FlyBackStart -> 1f
  else -> 1f - eased(FlyBackStart, FlyBackEnd, p)
}

/** The cell growing out of the button's own circle. */
private fun bloomAt(p: Float): Float = 0.52f + 0.48f * eased(0f, BloomEnd, p)

/** How full the membrane is: 1 is one shape, 0 is two whole cells. */
private fun neckAt(p: Float): Float = when {
  p <= SplitStart -> 1f
  p <= SplitEnd -> 1f - eased(SplitStart, SplitEnd, p)
  p <= SealStart -> 0f
  p <= SealEnd -> eased(SealStart, SealEnd, p)
  p <= UnsealStart -> 1f
  p <= UnsealEnd -> 1f - eased(UnsealStart, UnsealEnd, p)
  else -> eased(ResealStart, 1f, p)
}

private fun cellAt(p: Float): Float = when {
  p <= 0f -> 0f
  p <= BloomEnd -> eased(0f, BloomEnd, p)
  p <= FadeOutStart -> 1f
  else -> 1f - eased(FadeOutStart, 1f, p)
}

private fun pillAt(p: Float): Float = when {
  p <= PillInStart -> 0f
  p <= PillInEnd -> eased(PillInStart, PillInEnd, p)
  p <= HoldEnd -> 1f
  p <= PillOutEnd -> 1f - eased(HoldEnd, PillOutEnd, p)
  else -> 0f
}

private fun textAt(p: Float): Float = when {
  p <= TextInStart -> 0f
  p <= TextInEnd -> eased(TextInStart, TextInEnd, p)
  p <= HoldEnd -> 1f
  p <= PillOutEnd -> 1f - eased(HoldEnd, PillOutEnd, p)
  else -> 0f
}

/** A smooth step from 0 at [edge0] to 1 at [edge1], flat at both ends. */
private fun eased(edge0: Float, edge1: Float, value: Float): Float {
  if (edge1 <= edge0) return if (value < edge1) 0f else 1f
  val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

/**
 * A dividing cell: two circles of [radius] at [leftX] and [rightX], joined by a membrane that
 * pinches to [neck]. At 1 the membrane is as thick as the circles, which is a capsule; at 0 they
 * are two cells of their own. This is the same pinch the player's own buttons share.
 */
private fun DrawScope.drawCell(
  leftX: Float,
  rightX: Float,
  centerY: Float,
  radius: Float,
  neck: Float,
  color: Color,
) {
  if (radius <= 0f) return
  if (rightX - leftX <= 0.5f) {
    drawCircle(color, radius, Offset(leftX, centerY))
    return
  }

  val neckRadius = radius * neck.coerceIn(0f, 1f)
  if (neckRadius <= 0.5f) {
    // Two circles once the neck has gone: the pinched path would leave a hairline bridge between
    // them, because of antialiasing at the cusp.
    drawCircle(color, radius, Offset(leftX, centerY))
    drawCircle(color, radius, Offset(rightX, centerY))
    return
  }

  val centerX = (leftX + rightX) / 2f
  val top = centerY - radius
  val bottom = centerY + radius
  val membrane = Path()
  membrane.moveTo(leftX, top)
  membrane.cubicTo(
    leftX + radius * 0.35f,
    top,
    centerX - radius * 0.6f,
    centerY - neckRadius,
    centerX,
    centerY - neckRadius,
  )
  membrane.cubicTo(
    centerX + radius * 0.6f,
    centerY - neckRadius,
    rightX - radius * 0.35f,
    top,
    rightX,
    top,
  )
  membrane.arcTo(Rect(rightX - radius, top, rightX + radius, bottom), -90f, 180f, false)
  membrane.cubicTo(
    rightX - radius * 0.35f,
    bottom,
    centerX + radius * 0.6f,
    centerY + neckRadius,
    centerX,
    centerY + neckRadius,
  )
  membrane.cubicTo(
    centerX - radius * 0.6f,
    centerY + neckRadius,
    leftX + radius * 0.35f,
    bottom,
    leftX,
    bottom,
  )
  membrane.arcTo(Rect(leftX - radius, top, leftX + radius, bottom), 90f, 180f, false)
  membrane.close()
  drawPath(membrane, color)
}
