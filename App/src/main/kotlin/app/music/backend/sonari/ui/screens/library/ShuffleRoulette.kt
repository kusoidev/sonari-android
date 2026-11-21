package app.music.backend.sonari.ui.screens.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.music.backend.sonari.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Top speed of the wheel in pixels per second — fast enough that the rows smear into a blur. */
private const val SPIN_SPEED = 16_000f

/** How many rows above the winner the wheel begins its final approach. */
private const val RUN_UP_ROWS = 8

/**
 * Shared between the library screen's shuffle FAB and the songs list. The FAB only calls [begin];
 * the songs screen watches [tick] and spins its own list: the rows race past the needle at the
 * centre of the screen with a motion blur, slow down, and land on the winner, which is then played
 * first with the rest of the library shuffled in behind it.
 *
 * Only song rows are ever scrolled. The wheels never goes back to the top of the list, and when it
 * runs out at the bottom it wraps to the first song, so nothing above the songs moves.
 */
class ShuffleRouletteState {
  var tick: Int by mutableIntStateOf(0)
    private set

  var running: Boolean by mutableStateOf(false)
    private set

  /** The row the wheel is aiming at; the list attaches a position probe to that one row. */
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

  /**
   * How far the brackets have slid from the middle to sit on a winner the list could not scroll to
   * the centre of the screen — one too close to the top or the bottom. Zero otherwise.
   */
  var needleShiftPx: Float by mutableFloatStateOf(0f)
    private set

  /** Called by the shuffle FAB. Ignored while a spin is already running. */
  fun begin() {
    if (!running) tick++
  }

  /**
   * Spins [listState] over [rowCount] songs and lands on the song at [winnerRow].
   *
   * The spin is one eased scroll from wherever the list is now: it starts at [SPIN_SPEED] and
   * decelerates over enough rows to read as a wheel. When the list runs out at the bottom it wraps
   * to its first song — the only place it ever returns to — and the final approach brings the
   * winner dead centre on the needle using that row's measured position.
   */
  internal suspend fun run(
    listState: LazyListState,
    probe: ShuffleNeedleProbe,
    rowCount: Int,
    winnerRow: Int,
    winnerId: String,
  ) {
    if (running || rowCount <= 0) return
    running = true
    targetSongId = winnerId
    needleShiftPx = 0f
    probe.reset()
    val frost = Animatable(0f)

    try {
      // Frost the top and bottom in as the wheel starts.
      frost.animateTo(1f, tween(280)) { blurProgress = value }

      val rowPx = averageRowPx(listState)
      val here = listState.firstVisibleItemIndex.coerceIn(0, rowCount - 1)
      val runUp = (winnerRow - RUN_UP_ROWS).coerceAtLeast(0)
      val forwardRows = (runUp - here + rowCount) % rowCount
      // Extra laps so a short library still turns the wheel several times, while a long one only
      // goes round when it has to.
      val lapRows = SPIN_SPEED * 2.4f / 3f / rowPx
      var laps = ((lapRows - forwardRows) / rowCount).roundToInt().coerceAtLeast(0)
      if (forwardRows < 4 && laps == 0) laps = 1
      val totalPx = (forwardRows + laps * rowCount) * rowPx
      // An ease-out cubic starts at three times its average speed, so this length of spin sets off
      // at SPIN_SPEED. Clamped so the wheel neither blinks past on a short list nor crawls on a long.
      val durationMs = (3f * totalPx / SPIN_SPEED * 1000f).coerceIn(1500f, 3000f)

      val canScroll =
        listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size
      var travelled = 0f
      val start = withFrameNanos { it }
      var previous = start
      while (true) {
        val now = withFrameNanos { it }
        val t = ((now - start) / 1_000_000f / durationMs).coerceIn(0f, 1f)
        val inverse = 1f - t
        val eased = 1f - inverse * inverse * inverse
        val delta = totalPx * eased - travelled
        val seconds = ((now - previous) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        previous = now
        val consumed = listState.scrollBy(delta)
        travelled += delta
        // The list ran out at the bottom: wrap to its first song and carry on with the wheel.
        if (canScroll && delta > 1f && abs(consumed) < delta * 0.5f) listState.scrollToItem(0)
        speed = (abs(delta) / seconds / SPIN_SPEED).coerceIn(0f, 1f)
        if (t >= 1f) break
      }

      // Final approach: decelerate onto the winner using its measured position, so the wheel stops
      // exactly on it whatever the row heights are.
      var approach = SPIN_SPEED * 0.35f
      var stuck = 0
      var frames = 0
      var previousFrame = withFrameNanos { it }
      while (frames++ < 300) {
        val now = withFrameNanos { it }
        val seconds = ((now - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
        previousFrame = now
        val error = visibleError(listState, probe, winnerId)
        if (error != null && abs(error) <= 0.75f) break
        val maxStep = approach * seconds
        val step =
          if (error == null) {
            // The winner is not on screen yet: keep the wheel turning towards it.
            maxStep
          } else {
            (error * 0.2f).coerceIn(-maxStep, maxStep)
          }
        val consumed = listState.scrollBy(step)
        if (error == null && abs(consumed) < step * 0.5f) listState.scrollToItem(0)
        if (abs(step) > 1.5f && abs(consumed) < 0.5f) {
          // Nothing left to scroll: the winner is as close to the needle as the list can get.
          if (++stuck > 2) break
        } else {
          stuck = 0
        }
        approach = maxOf(approach * 0.985f, 1_200f)
        speed = (approach / SPIN_SPEED).coerceIn(0f, 1f)
      }

      speed = 0f
      // A winner the list could not bring to the middle of the screen: the brackets slide onto it.
      val resting = visibleError(listState, probe, winnerId)
      if (resting != null && abs(resting) > 0.75f) {
        val limit = listState.layoutInfo.viewportSize.height / 2f - 60f
        needleShiftPx = resting.coerceIn(-limit, limit)
      }
      winnerSongId = winnerId
      delay(420) // let the winner pulse read before the frost lifts

      frost.animateTo(0f, tween(380)) { blurProgress = value }
    } finally {
      running = false
      speed = 0f
      blurProgress = 0f
      needleShiftPx = 0f
      targetSongId = null
      winnerSongId = null
    }
  }
}

/** The average height of the rows on screen, in pixels, used to size the spin. */
private fun averageRowPx(listState: LazyListState): Float {
  val sizes = listState.layoutInfo.visibleItemsInfo.map { it.size.toFloat() }
  return if (sizes.isEmpty()) 200f else sizes.average().toFloat().coerceAtLeast(1f)
}

/**
 * How far the winning row still has to travel up to sit under the needle, in pixels. Null while the
 * row is not on screen, since a row that has scrolled away has no position worth aiming at.
 */
private fun visibleError(
  listState: LazyListState,
  probe: ShuffleNeedleProbe,
  winnerId: String,
): Float? {
  if (listState.layoutInfo.visibleItemsInfo.none { it.key == winnerId }) return null
  return probe.errorPx()
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
 * The "> song <" brackets the wheel lands between. Each row that flies past knocks them outward and
 * they spring back, the two trading the blows half a beat apart. When the wheel settles they slide
 * onto the winning row if it could not be brought to the middle of the screen.
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
      animationSpec = infiniteRepeatable(tween(durationMillis = 130, easing = LinearEasing)),
      label = "shuffleNeedleJiggle",
    )
  val shift by
    animateFloatAsState(
      targetValue = state.needleShiftPx,
      animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
      label = "shuffleNeedleShift",
    )
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)
        .offset { IntOffset(0, shift.roundToInt()) }
        .onGloballyPositioned { coords ->
          probe.needleCenterY = coords.positionInRoot().y + coords.size.height / 2f
        }
        .graphicsLayer { alpha = state.blurProgress },
  ) {
    NeedleArrow(speed = state.speed, phase = jiggle, pointsRight = true)
    NeedleArrow(speed = state.speed, phase = jiggle + 0.5f, pointsRight = false)
  }
}

@Composable
private fun NeedleArrow(speed: Float, phase: Float, pointsRight: Boolean) {
  // Sine cubed keeps the sign but sharpens the peak, so a hit reads as a knock rather than a buzz.
  val swing = sin(phase * 2f * PI.toFloat())
  val hit = swing * swing * swing
  val outward = if (pointsRight) -1f else 1f
  Icon(
    painter =
      painterResource(
        if (pointsRight) R.drawable.ic_nav_chevron_right else R.drawable.ic_nav_chevron_left
      ),
    contentDescription = null,
    tint = MaterialTheme.colorScheme.primary,
    modifier =
      Modifier.size(30.dp).graphicsLayer {
        rotationZ = hit * 9f * speed
        translationX = outward * hit * 7.dp.toPx() * speed
        translationY = sin(phase * 6f * PI.toFloat()) * 2.dp.toPx() * speed
        val stretch = 1f + 0.13f * abs(hit) * speed
        scaleX = stretch
        scaleY = 2f - stretch
      },
  )
}
