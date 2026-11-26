package app.music.backend.sonari.ui.component

import androidx.compose.animation.core.Animatable
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

  fun shownForMillis(): Long = ShowMillis.toLong()
}

/** How long one banner lives, from the button dividing to its last cell going back in. */
private const val ShowMillis = 2400

/** How fast an interrupted banner unwinds, before the new line divides all over again. */
private const val RewindMillis = 200

/** The button's cell is one blob until here, having faded in over it. */
private const val BlobInEnd = 0.04f
private const val DivideStart = 0.05f
private const val NeckStart = 0.13f

/** The two cells have parted and are on their way up to where the banner will be. */
private const val DivideEnd = 0.28f

/** The cells have merged again, so the capsule the banner sits in is whole. */
private const val MergeEnd = 0.38f

/** The banner fades in over its own capsule, which is why nothing is seen to change. */
private const val PillInStart = 0.38f
private const val PillInEnd = 0.47f

/** The banner goes the way it came: fades out over the capsule, then divides again. */
private const val PillOutStart = 0.84f
private const val PillOutEnd = 0.89f
private const val SplitEnd = 0.94f
private const val BlobOutStart = 0.97f

/**
 * The banner, grown out of the button that caused it.
 *
 * Pressing shuffle or loop does not slide a pill in: the button itself divides. A blob sitting
 * exactly on it pinches in the middle, its two cells drift apart to where the ends of the banner
 * will be, and the capsule they leave behind is the banner. Pressing again unwinds the blob back
 * into the button and divides it again with the new line, so nothing is ever cut off.
 *
 * The whole animation is one number, read only from draw and layer lambdas, so no frame of it
 * recomposes the banner: the shape and the fades are drawn afresh each frame and nothing else moves.
 */
@Composable
fun ActionBannerHost(modifier: Modifier = Modifier) {
  val message by ActionBanner.message.collectAsState()
  val phase = remember { Animatable(0f) }
  var line by remember { mutableStateOf<BannerMessage?>(null) }
  var pillBounds by remember { mutableStateOf(Rect.Zero) }

  LaunchedEffect(message?.id) {
    val next = message ?: return@LaunchedEffect
    // Nothing is on screen once a banner has finished, so only one still in flight is unwound.
    if (line != null && phase.value < 0.999f) {
      phase.animateTo(0f, tween(RewindMillis, easing = LinearEasing))
    }
    line = next
    phase.animateTo(1f, tween(ShowMillis, easing = LinearEasing))
  }

  val shown = line ?: return
  val pillColor = MaterialTheme.colorScheme.inverseSurface
  val contentColor = MaterialTheme.colorScheme.inverseOnSurface
  val iconColor = MaterialTheme.colorScheme.inversePrimary

  Box(modifier = modifier) {
    // The dividing blob, under the banner it becomes. It is drawn from the button all the way up,
    // which is outside this box, so it is not clipped to the banner's own rectangle.
    Canvas(modifier = Modifier.matchParentSize()) {
      val p = phase.value
      val blob = blobAt(p)
      if (blob <= 0.001f) return@Canvas

      val pill = pillBounds
      if (pill.width <= 0f || pill.height <= 0f) return@Canvas

      val separation = separationAt(p)
      val pillRadius = pill.height / 2f
      val anchor = shown.anchor
      val startX = (anchor?.center?.x ?: pill.center.x) - pill.left
      val startY = (anchor?.center?.y ?: pill.center.y) - pill.top
      val startRadius =
        if (anchor != null) {
          minOf(anchor.width, anchor.height) / 2f
        } else {
          pillRadius * 0.55f
        }

      // One cell on the button becomes two at the ends of the banner, as they travel.
      val radius = startRadius + (pillRadius - startRadius) * separation
      val leftX = startX + (pillRadius - startX) * separation
      val rightX = startX + (pill.width - pillRadius - startX) * separation
      val centerY = startY + (pill.height / 2f - startY) * separation

      drawCells(
        leftX = leftX,
        rightX = rightX,
        centerY = centerY,
        radius = radius,
        neck = neckAt(p),
        color = pillColor.copy(alpha = pillColor.alpha * blob),
      )
    }

    Surface(
      shape = RoundedCornerShape(percent = 50),
      color = pillColor,
      contentColor = contentColor,
      tonalElevation = 3.dp,
      shadowElevation = 2.dp,
      modifier =
        Modifier.onGloballyPositioned { pillBounds = it.boundsInRoot() }
          .graphicsLayer { alpha = pillAt(phase.value) },
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
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

/** How far apart the two cells are: 0 is both on the button, 1 is the ends of the banner. */
private fun separationAt(p: Float): Float = when {
  p <= DivideStart -> 0f
  p <= DivideEnd -> eased(DivideStart, DivideEnd, p)
  p <= SplitEnd -> 1f
  p <= 1f -> 1f - eased(SplitEnd, 1f, p)
  else -> 0f
}

/** How full the membrane between the cells is: 1 is one shape, 0 is two separate cells. */
private fun neckAt(p: Float): Float = when {
  p <= NeckStart -> 1f
  p <= DivideEnd -> 1f - eased(NeckStart, DivideEnd, p)
  p <= MergeEnd -> eased(DivideEnd, MergeEnd, p)
  p <= PillOutEnd -> 1f
  p <= SplitEnd -> 1f - eased(PillOutEnd, SplitEnd, p)
  else -> eased(SplitEnd, 1f, p)
}

private fun blobAt(p: Float): Float = when {
  p <= 0f -> 0f
  p <= BlobInEnd -> eased(0f, BlobInEnd, p)
  p <= BlobOutStart -> 1f
  else -> 1f - eased(BlobOutStart, 1f, p)
}

private fun pillAt(p: Float): Float = when {
  p <= PillInStart -> 0f
  p <= PillInEnd -> eased(PillInStart, PillInEnd, p)
  p <= PillOutStart -> 1f
  p <= PillOutEnd -> 1f - eased(PillOutStart, PillOutEnd, p)
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
 * are two cells of their own. This is the same pinch the player's buttons share.
 */
private fun DrawScope.drawCells(
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
