package app.music.backend.sonari.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BannerMessage(
  val id: Long,
  val text: String,
  val iconRes: Int? = null,
  val anchor: Rect? = null,
  val sourceColor: Color? = null,
)

object ActionBanner {
  private val _message = MutableStateFlow<BannerMessage?>(null)
  val message: StateFlow<BannerMessage?> = _message.asStateFlow()

  private var counter = 0L

  fun show(
    text: String,
    iconRes: Int? = null,
    anchor: Rect? = null,
    sourceColor: Color? = null,
  ) {
    counter += 1
    _message.value =
      BannerMessage(
        id = counter,
        text = text,
        iconRes = iconRes,
        anchor = anchor,
        sourceColor = sourceColor,
      )
  }

  fun dismiss() {
    _message.value = null
  }
}

private const val ShowMillis = 2300
private const val TravelMillis = 380f
private const val ExitStart = ShowMillis - TravelMillis

private const val SplitStart = 0.10f
private const val SplitEnd = 0.39f
private const val TravelStart = 0.28f
private const val TravelEnd = 0.87f
private const val RefillStart = 0.64f
private const val RefillEnd = 0.82f
private const val PillInStart = 0.78f
private const val PillInEnd = 0.96f
private const val TextInStart = 0.88f

private val RewindSpring =
  spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
private val ButtonSpread = 1.15f

@Composable
fun ActionBannerHost(
  modifier: Modifier = Modifier,
  bottomPadding: Dp = 0.dp,
) {
  val message by ActionBanner.message.collectAsState()
  val clock = remember { Animatable(0f) }
  var line by remember { mutableStateOf<BannerMessage?>(null) }
  var hostBounds by remember { mutableStateOf(Rect.Zero) }
  var pillBounds by remember { mutableStateOf(Rect.Zero) }

  LaunchedEffect(message?.id) {
    val next = message
    if (line != null && clock.value > 0f) {
      clock.animateTo(0f, RewindSpring)
    }
    clock.snapTo(0f)
    line = next
    if (next != null) {
      clock.animateTo(ShowMillis.toFloat(), tween(ShowMillis, easing = LinearEasing))
      line = null
      ActionBanner.dismiss()
    }
  }

  val shown = line ?: return
  val pillColor = MaterialTheme.colorScheme.inverseSurface
  val contentColor = MaterialTheme.colorScheme.inverseOnSurface
  val iconColor = MaterialTheme.colorScheme.inversePrimary

  Box(
    modifier = modifier.fillMaxSize().onGloballyPositioned { hostBounds = it.boundsInRoot() }
  ) {
    Canvas(
      modifier =
        Modifier.matchParentSize().graphicsLayer {
          compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
      val anchor = shown.anchor
      val t = clock.value
      val motion = motionAt(t)
      val pillAlpha = if (anchor == null) fallbackPillAt(t) else pillAt(motion)
      val cellAlpha = if (anchor == null) 0f else 1f - pillAlpha
      if (cellAlpha <= 0.002f) return@Canvas

      val host = hostBounds
      val pill = pillBounds
      if (host.width <= 0f || host.height <= 0f || pill.width <= 0f || pill.height <= 0f) {
        return@Canvas
      }
      val localPill =
        Rect(
          left = pill.left - host.left,
          top = pill.top - host.top,
          right = pill.right - host.left,
          bottom = pill.bottom - host.top,
        )
      val pillRadius = localPill.height / 2f
      val buttonX = (anchor?.center?.x ?: pill.center.x) - host.left
      val buttonY = (anchor?.center?.y ?: pill.center.y) - host.top
      val buttonRadius =
        if (anchor != null && anchor.width > 0f && anchor.height > 0f) {
          minOf(minOf(anchor.width, anchor.height) / 2f, pillRadius)
        } else {
          pillRadius
        }

      val travel = if (anchor == null) 1f else travelAt(motion)
      val spread = if (anchor == null) 0f else buttonRadius * ButtonSpread * splitAt(motion)
      val radius = mix(buttonRadius, pillRadius, travel)
      val leftX = mix(buttonX - spread, localPill.left + pillRadius, travel)
      val rightX = mix(buttonX + spread, localPill.right - pillRadius, travel)
      val centerY = mix(buttonY, localPill.center.y, travel)
      val color = lerp(shown.sourceColor ?: pillColor, pillColor, travel)

      drawCell(
        leftX = leftX,
        rightX = rightX,
        centerY = centerY,
        radius = radius,
        neck = if (anchor == null) 1f else neckAt(motion),
        color = color,
        alpha = cellAlpha,
      )
    }

    Surface(
      shape = RoundedCornerShape(percent = 50),
      color = pillColor,
      contentColor = contentColor,
      shadowElevation = 3.dp,
      tonalElevation = 0.dp,
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .padding(bottom = bottomPadding)
          .onGloballyPositioned { pillBounds = it.boundsInRoot() }
          .graphicsLayer {
            val t = clock.value
            alpha = if (shown.anchor == null) fallbackPillAt(t) else pillAt(motionAt(t))
          },
    ) {
      Row(
        modifier =
          Modifier.graphicsLayer {
              val t = clock.value
              alpha = if (shown.anchor == null) fallbackPillAt(t) else textAt(motionAt(t))
              val scale = 0.96f + 0.04f * textSettle(motionAt(t))
              scaleX = scale
              scaleY = scale
            }
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

private fun motionAt(t: Float): Float {
  val entering = smooth(0f, TravelMillis, t)
  val leaving = 1f - smooth(ExitStart, ShowMillis.toFloat(), t)
  return minOf(entering, leaving).coerceIn(0f, 1f)
}

private fun travelAt(motion: Float): Float = smooth(TravelStart, TravelEnd, motion)

private fun splitAt(motion: Float): Float = smooth(SplitStart, SplitEnd, motion)

private fun neckAt(motion: Float): Float =
  (1f - smooth(SplitStart, SplitEnd, motion) + smooth(RefillStart, RefillEnd, motion))
    .coerceIn(0f, 1f)

private fun pillAt(motion: Float): Float = smooth(PillInStart, PillInEnd, motion)

private fun textAt(motion: Float): Float = smooth(TextInStart, 1f, motion)

private fun textSettle(motion: Float): Float = smooth(TextInStart, 1f, motion)

private fun fallbackPillAt(t: Float): Float =
  smooth(0f, 180f, t) * (1f - smooth(ShowMillis - 180f, ShowMillis.toFloat(), t))

private fun mix(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

private fun smooth(start: Float, end: Float, t: Float): Float {
  val x = ((t - start) / (end - start)).coerceIn(0f, 1f)
  return x * x * x * (x * (x * 6f - 15f) + 10f)
}

private fun DrawScope.drawCell(
  leftX: Float,
  rightX: Float,
  centerY: Float,
  radius: Float,
  neck: Float,
  color: Color,
  alpha: Float,
) {
  if (radius <= 0f) return
  val drawColor = color.copy(alpha = color.alpha * alpha)
  if (rightX - leftX <= 0.5f) {
    drawCircle(drawColor, radius, Offset(leftX, centerY))
    return
  }

  val neckRadius = radius * neck.coerceIn(0f, 1f)
  if (neckRadius <= 0.5f) {
    drawCircle(drawColor, radius, Offset(leftX, centerY))
    drawCircle(drawColor, radius, Offset(rightX, centerY))
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
  drawPath(membrane, drawColor)
}
