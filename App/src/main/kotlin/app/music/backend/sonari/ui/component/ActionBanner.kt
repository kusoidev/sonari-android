package app.music.backend.sonari.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BannerMessage(
  val id: Long,
  val text: String,
  val iconRes: Int? = null,
)

/**
 * One short line about what just happened, such as a queue being shuffled. Shown for a moment and
 * gone: it should not sit in the way of the player.
 */
object ActionBanner {
  private val _message = MutableStateFlow<BannerMessage?>(null)
  val message: StateFlow<BannerMessage?> = _message.asStateFlow()

  private var counter = 0L

  fun show(text: String, iconRes: Int? = null) {
    counter += 1
    _message.value = BannerMessage(id = counter, text = text, iconRes = iconRes)
  }

  fun dismiss() {
    _message.value = null
  }
}

private const val RevealMillis = 240
private const val RewindMillis = 210
private const val SplitMillis = 340
private const val MergeMillis = 540
private const val ExitMillis = 200
private const val SettleMillis = 90L
private const val HoldMillis = 1300L

/** How far the cell has to divide before the pill behind it has faded out. */
private const val PillGoneAt = 0.34f

/** How far the cell has to divide before it is fully opaque. */
private const val CellSolidAt = 0.32f

private val CellSize = 32.dp
private val CellTravel = 26.dp
private val CellBarWidth = 15.dp
private val CellBarHeight = 4.dp
private val CellBarAngle = 24f

/**
 * A line about the last thing the player did. The notification divides into two cells and pulls
 * itself back together into the pill again, so a tap is announced without stealing the screen.
 */
@Composable
fun ActionBannerHost(modifier: Modifier = Modifier) {
  val message by ActionBanner.message.collectAsState()
  val split = remember { Animatable(0f) }
  val reveal = remember { Animatable(0f) }
  var shown by remember { mutableStateOf<BannerMessage?>(null) }

  LaunchedEffect(message?.id) {
    val next = message
    if (next == null) {
      split.snapTo(0f)
      reveal.snapTo(0f)
      return@LaunchedEffect
    }
    shown = next
    // Tapped again while it was still dividing: pull it back together, then divide once more.
    if (split.value > 0f) {
      split.animateTo(0f, tween(RewindMillis, easing = FastOutSlowInEasing))
    }
    reveal.animateTo(1f, tween(RevealMillis, easing = FastOutSlowInEasing))
    delay(SettleMillis)
    split.animateTo(1f, tween(SplitMillis, easing = FastOutSlowInEasing))
    split.animateTo(0f, tween(MergeMillis, easing = FastOutSlowInEasing))
    delay(HoldMillis)
    reveal.animateTo(0f, tween(ExitMillis, easing = FastOutSlowInEasing))
    ActionBanner.dismiss()
  }

  val current = shown ?: return

  val travel = with(LocalDensity.current) { CellTravel.toPx() }
  val progress = { cellProgress(split.value) }

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    DividingCell(
      progress = progress,
      travelPx = travel,
      direction = -1f,
      barAngle = CellBarAngle,
    )
    DividingCell(
      progress = progress,
      travelPx = travel,
      direction = 1f,
      barAngle = -CellBarAngle,
    )
    Surface(
      shape = RoundedCornerShape(26.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      contentColor = MaterialTheme.colorScheme.onSurface,
      tonalElevation = 3.dp,
      shadowElevation = 2.dp,
      modifier =
        Modifier.animateContentSize(tween(260, easing = FastOutSlowInEasing)).graphicsLayer {
          val pinch = (split.value / PillGoneAt).coerceIn(0f, 1f)
          val appear = 0.94f + 0.06f * reveal.value
          alpha = reveal.value * (1f - pinch)
          scaleX = appear * (1f - 0.28f * pinch)
          scaleY = appear * (1f - 0.42f * pinch)
          transformOrigin = TransformOrigin.Center
        },
    ) {
      Crossfade(
        targetState = current,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
      ) { banner ->
        Row(
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val icon = banner.iconRes
          if (icon != null) {
            Icon(
              painter = painterResource(icon),
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
          }
          Text(text = banner.text, style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
  }
}

/**
 * One half of the dividing cell: a ball that grows out of the pill and drifts away from it, carrying
 * a single rounded bar the way a dividing cell carries its chromosomes.
 */
@Composable
private fun DividingCell(
  progress: () -> Float,
  travelPx: Float,
  direction: Float,
  barAngle: Float,
  modifier: Modifier = Modifier,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .offset { IntOffset(0, (travelPx * progress() * direction).roundToInt()) }
        .graphicsLayer {
          val p = progress()
          val scale = 0.42f + 0.58f * p
          scaleX = scale
          scaleY = scale
          alpha = (p / CellSolidAt).coerceIn(0f, 1f)
        }
        .size(CellSize)
        .shadow(4.dp, CircleShape)
        .background(MaterialTheme.colorScheme.primary, CircleShape),
  ) {
    Box(
      modifier =
        Modifier.rotate(barAngle)
          .size(width = CellBarWidth, height = CellBarHeight)
          .background(
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            RoundedCornerShape(percent = 50),
          )
    )
  }
}

private fun cellProgress(split: Float): Float = ((split - 0.05f) / 0.95f).coerceIn(0f, 1f)
