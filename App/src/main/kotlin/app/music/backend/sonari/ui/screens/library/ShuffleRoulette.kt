package app.music.backend.sonari.ui.screens.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import app.music.backend.sonari.R
import app.music.backend.sonari.db.entities.Song
import app.music.backend.sonari.ui.component.SongListItem
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter

/** How many rows either side of the middle the wheel still draws. */
private const val WHEEL_REACH = 4

/**
 * Shared between the library screen's shuffle FAB and the songs screen. The FAB only calls [begin];
 * the songs screen answers a fresh [tick] once with a spin, so leaving and re-entering the list
 * never starts a wheel of its own.
 *
 * The wheel is drawn over the list rather than scrolling it: the list never moves, nothing on the
 * screen shakes, and the order it spins through wraps without a seam, which is what made a scrolled
 * wheel look like two separate runs.
 */
class ShuffleRouletteState {
  var tick by mutableIntStateOf(0)
    private set

  var running by mutableStateOf(false)
    private set

  private var claimedTick by mutableIntStateOf(0)

  /** True while the wheel is on screen. */
  var visible by mutableStateOf(false)
    private set

  /** True once the wheel has stopped on a song, while its confetti is going off. */
  var landed by mutableStateOf(false)
    private set

  /** 0..1 how strongly the top and bottom of the screen are frosted. */
  var frost by mutableFloatStateOf(0f)
    private set

  /** How fast the wheel is turning, 0..1: drives the smear and how hard the brackets hit. */
  var speed by mutableFloatStateOf(0f)
    private set

  /** Which song the wheel is on, as a continuous index into [order]: its rotation position. */
  var position by mutableFloatStateOf(0f)
    private set

  /** The order the wheel spins through, which is also the queue that follows the winner. */
  var order by mutableStateOf<List<Song>>(emptyList())
    private set

  /** Height of one row as the list draws it, measured from the first row that lands. */
  var rowHeightPx by mutableFloatStateOf(0f)

  /** Called by the shuffle FAB. Ignored while a spin is already running. */
  fun begin() {
    if (!running) tick++
  }

  /**
   * Claims a tap exactly once. Without this, coming back to the songs list would replay the last
   * spin, because this state outlives the screen.
   */
  fun claim(tick: Int): Boolean {
    if (tick == 0 || tick == claimedTick) return false
    claimedTick = tick
    return true
  }

  /**
   * Spins through [order] and stops on the song at [winnerIndex], then leaves the winner on screen
   * for a moment while the confetti goes off.
   *
   * One continuous ease-out from the first frame to the last: the wheel starts fast enough to smear,
   * slows the whole way, and stops exactly on the winner because the rotation position ends on its
   * index. Nothing is measured, so it cannot miss.
   */
  internal suspend fun spin(order: List<Song>, winnerIndex: Int) {
    if (running || order.isEmpty()) return
    running = true
    visible = true
    landed = false
    this.order = order
    position = 0f
    speed = 0f
    val songs = order.size
    val laps = if (songs > 20) 3 else 2
    val target = (winnerIndex + laps * songs).toFloat()
    val durationMs = (2200f + songs * 4f).coerceIn(2400f, 3200f)
    val frostAnim = Animatable(0f)

    try {
      frostAnim.animateTo(1f, tween(240)) { frost = value }

      val start = withFrameNanos { it }
      var previousFrame = start
      var previousPosition = 0f
      while (true) {
        val now = withFrameNanos { it }
        val t = ((now - start) / 1_000_000f / durationMs).coerceIn(0f, 1f)
        val inverse = 1f - t
        position = target * (1f - inverse * inverse * inverse)
        val seconds = ((now - previousFrame) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        previousFrame = now
        speed = ((position - previousPosition) / seconds / 40f).coerceIn(0f, 1f)
        previousPosition = position
        if (t >= 1f) break
      }

      position = target
      speed = 0f
      landed = true
      delay(900)

      frostAnim.animateTo(0f, tween(320)) { frost = value }
    } finally {
      running = false
      visible = false
      landed = false
      frost = 0f
      speed = 0f
      position = 0f
      this.order = emptyList()
    }
  }
}

/**
 * The wheel itself: a column of song titles turning past the middle of the screen, the one between
 * the brackets full size and bright while the rows either side of it shrink and fade away.
 *
 * The rows are keyed by their slot number and read the rotation position only in the layout and
 * draw phases, so a fast spin moves them without recomposing every row on every frame.
 */
@Composable
fun BoxScope.ShuffleWheel(state: ShuffleRouletteState, modifier: Modifier = Modifier) {
  if (!state.visible) return
  val songs = state.order
  if (songs.isEmpty()) return

  val count = songs.size
  val fallbackRowPx = with(LocalDensity.current) { 72.dp.toPx() }
  // Quantised so the blur is rebuilt a handful of times over the spin instead of every frame.
  val smear = 14.dp * (((state.speed * 4f).roundToInt() / 4f))
  val restingRowPx = if (state.rowHeightPx > 0f) state.rowHeightPx else fallbackRowPx

  Box(
    modifier =
      modifier
        .graphicsLayer { alpha = state.frost }
        .then(if (smear > 0.4.dp) Modifier.blur(smear) else Modifier),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth(0.96f)
          .height(with(LocalDensity.current) { restingRowPx.toDp() })
          .clip(RoundedCornerShape(18.dp))
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    )

    val rowPx = if (state.rowHeightPx > 0f) state.rowHeightPx else fallbackRowPx
    val first = floor(state.position).toInt() - WHEEL_REACH
    val last = ceil(state.position).toInt() + WHEEL_REACH
    for (row in first..last) {
      key(row) {
        val song = songs[((row % count) + count) % count]
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .offset { IntOffset(0, ((row - state.position) * rowPx).roundToInt()) }
              .graphicsLayer {
                val distance = abs(row - state.position)
                val falloff = (distance / 2.4f).coerceIn(0f, 1f)
                val scale = 1f - 0.22f * falloff
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.7f * falloff
              },
        ) {
          // The row the list would draw, so the wheel shows the cover, the liked and library
          // badges, the artist, the length and the size exactly as the list does.
          SongListItem(
            song = song,
            isSwipeable = false,
            showDownloadIcon = false,
            showSize = true,
            shape = RoundedCornerShape(12.dp),
            trailingContent = {
              Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
            },
            modifier =
              Modifier.fillMaxWidth().onGloballyPositioned { coords ->
                val height = coords.size.height.toFloat()
                if (height > 0f && height != state.rowHeightPx) state.rowHeightPx = height
              },
          )
        }
      }
    }
  }
}

/**
 * Frosted edges for the wheel, anchored to the very top and bottom of the screen instead of the top
 * of the list, so there is no hard edge where the header ends. Only the songs sit behind it: the
 * chips and the sort row are drawn over it and stay crisp.
 */
@Composable
fun BoxScope.ShuffleFrostOverlay(state: ShuffleRouletteState, modifier: Modifier = Modifier) {
  if (state.frost <= 0.01f) return
  val surface = MaterialTheme.colorScheme.surface
  Box(modifier = modifier.graphicsLayer { alpha = state.frost }) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(280.dp)
          .align(Alignment.TopCenter)
          .background(
            Brush.verticalGradient(
              0f to surface.copy(alpha = 0.85f),
              0.35f to surface.copy(alpha = 0.55f),
              0.7f to surface.copy(alpha = 0.22f),
              1f to Color.Transparent,
            )
          ),
    )
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(300.dp)
          .align(Alignment.BottomCenter)
          .background(
            Brush.verticalGradient(
              0f to Color.Transparent,
              0.3f to surface.copy(alpha = 0.22f),
              0.65f to surface.copy(alpha = 0.55f),
              1f to surface.copy(alpha = 0.88f),
            )
          ),
    )
  }
}

/**
 * The "> song <" brackets around the middle of the wheel. Each row that turns past knocks them
 * outward and they spring back, the two trading the blows half a beat apart, harder the faster the
 * wheel is going.
 */
@Composable
fun BoxScope.ShuffleNeedleOverlay(
  state: ShuffleRouletteState,
  modifier: Modifier = Modifier,
) {
  if (!state.visible) return
  val jiggle by
    rememberInfiniteTransition(label = "shuffleNeedle").animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(durationMillis = 260, easing = LinearEasing)),
      label = "shuffleNeedleJiggle",
    )
  val shown by
    animateFloatAsState(
      targetValue = if (state.visible) 1f else 0f,
      animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
      label = "shuffleNeedleAlpha",
    )
  Box(
    modifier = modifier.fillMaxSize().graphicsLayer { alpha = shown * state.frost },
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
    ) {
      NeedleArrow(speed = state.speed, phase = jiggle, pointsRight = true)
      NeedleArrow(speed = state.speed, phase = jiggle + 0.5f, pointsRight = false)
    }
  }
}

@Composable
private fun NeedleArrow(speed: Float, phase: Float, pointsRight: Boolean) {
  // A filled, rounded play wedge like a real struck indicator, themed with the app's accent, and
  // both sides driven by the same wrapped beat so they knock together instead of one going dead.
  val beat = phase - kotlin.math.floor(phase)
  val ring = exp(-3.2f * beat) * cos(5f * PI.toFloat() * beat)
  val hit = (ring * speed).coerceIn(-1f, 1f)
  val outward = if (pointsRight) -1f else 1f
  val tint = MaterialTheme.colorScheme.primary
  val mirror = if (pointsRight) 1f else -1f

  Canvas(
    modifier =
      Modifier.size(26.dp).graphicsLayer {
        rotationZ = hit * 4f
        translationX = outward * hit * 6.dp.toPx()
        translationY = hit * 1.5f.dp.toPx()
        scaleX = mirror * (1f + 0.09f * abs(hit))
        scaleY = 1f - 0.05f * abs(hit)
      },
  ) {
    val corner = size.minDimension * 0.22f
    val left = size.width * 0.16f
    val top = size.height * 0.06f
    val bottom = size.height * 0.94f
    val tip = Offset(size.width * 0.95f, size.height * 0.5f)
    val path = Path()
    // Two rounded corners on the flat side, one on the tip: the wedge in the reference image.
    val a = Offset(left, top)
    val b = Offset(left, bottom)
    val abEdge = (b - a).normalized()
    val baEdge = (a - b).normalized()
    val aTip = (tip - a).normalized()
    val bTip = (tip - b).normalized()

    path.moveTo((a + abEdge * corner).x, (a + abEdge * corner).y)
    path.lineTo((b + baEdge * corner).x, (b + baEdge * corner).y)
    path.quadraticBezierTo(b.x, b.y, (b + bTip * corner).x, (b + bTip * corner).y)
    path.lineTo((tip + bTip * corner).x, (tip + bTip * corner).y)
    path.quadraticBezierTo(tip.x, tip.y, (tip + aTip * corner).x, (tip + aTip * corner).y)
    path.lineTo((a + aTip * corner).x, (a + aTip * corner).y)
    path.quadraticBezierTo(a.x, a.y, (a + abEdge * corner).x, (a + abEdge * corner).y)
    path.close()

    drawPath(path = path, color = tint)
  }
}

private fun Offset.normalized(): Offset {
  val length = kotlin.math.sqrt(x * x + y * y)
  return if (length <= 0.0001f) Offset.Zero else Offset(x / length, y / length)
}

/** A burst of confetti out of the middle of the wheel once it has stopped on a song. */
@Composable
fun BoxScope.ShuffleConfetti(state: ShuffleRouletteState, modifier: Modifier = Modifier) {
  if (!state.landed) return
  val parties =
    listOf(
      Party(
        speed = 16f,
        maxSpeed = 52f,
        damping = 0.9f,
        spread = 360,
        colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def, 0x4cd7f0),
        emitter = Emitter(duration = 260, TimeUnit.MILLISECONDS).max(180),
        position = Position.Relative(0.5, 0.5),
      )
    )
  KonfettiView(modifier = modifier.fillMaxSize(), parties = parties)
}
