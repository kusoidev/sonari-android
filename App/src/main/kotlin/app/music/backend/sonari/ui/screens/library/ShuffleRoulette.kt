package app.music.backend.sonari.ui.screens.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.music.backend.sonari.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Top speed of the wheel in pixels per second — fast enough that the rows smear into a blur. */
private const val SPIN_SPEED = 16_000f

/** How long the wheel races for before it starts slowing down onto the winner. */
private const val SPIN_MS = 950L

/** How many rows above the winner the wheel begins its final approach. */
private const val RUN_UP_ROWS = 8

/**
 * Shared between the library screen's shuffle FAB and the songs list. The FAB only calls [begin];
 * the songs screen watches [tick] and spins its own list: the rows race past the needle at the
 * centre of the screen with a motion blur, slow down, and land on the winner, which is then played
 * first with the rest of the library shuffled in behind it.
 */
class ShuffleRouletteState {
  var tick: Int by mutableIntStateOf(0)
    private set

  var running: Boolean by mutableStateOf(false)
    private set

  /**
   * The row the wheel is aiming at. Reported up front so the list can attach a position probe to
   * that one row, which is what lets the wheel stop dead centre whatever the row heights are.
   */
  var targetSongId: String? by mutableStateOf(null)
    private set

  /** The row the wheel settled on; drives the winner pulse. */
  var winnerSongId: String? by mutableStateOf(null)
    private set

  /** 0..1 — how strongly the top and bottom of the screen are frosted. */
  var blurProgress: Float by mutableFloatStateOf(0f)
    private set

  /** 0..1 — the current scroll speed: drives the motion blur and how hard the needle rattles. */
  var speed: Float by mutableFloatStateOf(0f)
    private set

  /** Called by the shuffle FAB. Ignored while a spin is already running. */
  fun begin() {
    if (!running) tick++
  }

  /**
   * Spins [listState] and stops on the row at [winnerRow] — its index among the rows, not counting
   * the [headerRows] header items at the top of the list.
   */
  internal suspend fun run(
    listState: LazyListState,
    probe: ShuffleNeedleProbe,
    rowCount: Int,
    winnerRow: Int,
    headerRows: Int,
    winnerId: String,
  ) {
    if (running || rowCount <= 0) return
    running = true
    targetSongId = winnerId
    probe.reset()
    val frost = Animatable(0f)

    try {
      // Frost the top and bottom in as the wheel starts.
      frost.animateTo(1f, tween(280)) { blurProgress = value }

      // The wheel itself: race the rows past the needle, wrapping round to the top whenever the
      // list runs out so a small library spins just as hard as a big one.
      var lastFrame = withFrameNanos { it }
      val stopAt = lastFrame + SPIN_MS * 1_000_000L
      var velocity = 0f
      while (true) {
        val now = withFrameNanos { it }
        val elapsed = ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrame = now
        velocity = minOf(velocity + SPIN_SPEED * 2.4f * elapsed, SPIN_SPEED)
        speed = velocity / SPIN_SPEED
        listState.scrollBy(velocity * elapsed)
        if (!listState.canScrollForward) listState.scrollToItem(0)
        if (now >= stopAt) break
      }

      // Line the winner up for the final approach: back to the top, then a sweep down to just
      // above it. Both jumps happen while the rows are still smeared past recognition.
      probe.reset()
      listState.scrollToItem(0)
      listState.animateScrollToItem((winnerRow - RUN_UP_ROWS).coerceAtLeast(0) + headerRows)

      // Final approach: decelerate onto the needle using that row's measured position, so the
      // wheel stops exactly on it however tall the rows happen to be.
      var approach = SPIN_SPEED * 0.45f
      var frames = 0
      var stuck = 0
      var previous = withFrameNanos { it }
      while (frames++ < 240) {
        val now = withFrameNanos { it }
        val elapsed = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
        previous = now
        val error = probe.errorPx()
        if (error != null && abs(error) <= 0.75f) break
        val step =
          if (error == null) {
            // The winner is not on screen yet: keep sweeping towards it.
            approach * elapsed
          } else {
            (error * 0.22f).coerceIn(-approach * elapsed, approach * elapsed)
          }
        val consumed = listState.scrollBy(step)
        if (abs(step) > 1.5f && abs(consumed) < 0.5f) {
          // The list has nothing left to give: the winner is as close as it can get.
          if (++stuck > 2) break
        } else {
          stuck = 0
        }
        approach = maxOf(approach * 0.985f, 1_200f)
        speed = (approach / SPIN_SPEED).coerceIn(0f, 1f)
      }

      speed = 0f
      winnerSongId = winnerId
      delay(420) // let the winner pulse read before the frost lifts

      frost.animateTo(0f, tween(380)) { blurProgress = value }
    } finally {
      running = false
      speed = 0f
      blurProgress = 0f
      targetSongId = null
      winnerSongId = null
    }
  }
}

/**
 * Measures the two things the wheel needs to land a row on the needle exactly: where the needle sits
 * on screen, and where the winning row currently is. Both are reported in root coordinates by the
 * composables that draw them, so nothing is assumed about the list's content padding.
 */
class ShuffleNeedleProbe {
  var needleCenterY: Float = Float.NaN
  var rowTopY: Float = Float.NaN
  var rowHeight: Float = 0f

  /** How far the winning row still has to travel up to sit under the needle; null until it is drawn. */
  fun errorPx(): Float? {
    if (needleCenterY.isNaN() || rowTopY.isNaN() || rowHeight <= 0f) return null
    return (rowTopY + rowHeight / 2f) - needleCenterY
  }

  fun reset() {
    rowTopY = Float.NaN
    rowHeight = 0f
  }
}

/** Frosted bands over the top and bottom of the song list, fading with [ShuffleRouletteState.blurProgress]. */
@Composable
fun BoxScope.ShuffleFrostOverlay(state: ShuffleRouletteState, modifier: Modifier = Modifier) {
  if (state.blurProgress <= 0.01f) return
  val surface = MaterialTheme.colorScheme.surface
  Box(modifier.graphicsLayer { alpha = state.blurProgress }) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(170.dp)
          .align(Alignment.TopCenter)
          .background(
            Brush.verticalGradient(
              listOf(surface.copy(alpha = 0.8f), surface.copy(alpha = 0.45f), Color.Transparent),
            )
          ),
    )
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(210.dp)
          .align(Alignment.BottomCenter)
          .background(
            Brush.verticalGradient(
              listOf(Color.Transparent, surface.copy(alpha = 0.5f), surface.copy(alpha = 0.85f)),
            )
          ),
    )
  }
}

/**
 * The "> song <" brackets the wheel lands between. They rattle like a real spinning wheel being hit
 * by the rows — hard while it is at speed, settling to still as it stops.
 */
@Composable
fun ShuffleNeedleOverlay(
  state: ShuffleRouletteState,
  probe: ShuffleNeedleProbe,
  modifier: Modifier = Modifier,
) {
  if (!state.running) return
  val jiggle by
    rememberInfiniteTransition(label = "shuffleNeedle").animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(durationMillis = 110, easing = LinearEasing)),
      label = "shuffleNeedleJiggle",
    )
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)
        .onGloballyPositioned { coords ->
          probe.needleCenterY = coords.positionInRoot().y + coords.size.height / 2f
        }
        .graphicsLayer { alpha = state.blurProgress },
  ) {
    NeedleArrow(speed = state.speed, phase = jiggle, pointsRight = true)
    NeedleArrow(speed = state.speed, phase = jiggle + 0.37f, pointsRight = false)
  }
}

@Composable
private fun NeedleArrow(speed: Float, phase: Float, pointsRight: Boolean) {
  val wobble = sin(phase * 2f * PI.toFloat())
  Icon(
    painter =
      painterResource(
        if (pointsRight) R.drawable.ic_nav_chevron_right else R.drawable.ic_nav_chevron_left
      ),
    contentDescription = null,
    tint = MaterialTheme.colorScheme.primary,
    modifier =
      Modifier.size(30.dp).graphicsLayer {
        rotationZ = wobble * 11f * speed
        translationX = wobble * 4.dp.toPx() * speed
        translationY = sin(phase * 4f * PI.toFloat()) * 3.dp.toPx() * speed
        val punch = 1f + 0.1f * speed * abs(wobble)
        scaleX = punch
        scaleY = punch
      },
  )
}
