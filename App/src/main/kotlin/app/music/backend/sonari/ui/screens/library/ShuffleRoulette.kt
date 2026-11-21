package app.music.backend.sonari.ui.screens.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.music.backend.sonari.db.entities.Song
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.delay

/** How many stops the wheel makes before it settles on the winner. */
private const val SPIN_STEPS = 16

/**
 * Shared between the library screen's shuffle FAB and the songs list. The FAB only calls
 * [begin]; whichever songs screen is mounted watches [tick] and runs the roulette over its
 * own songs: the wheel races across the rows with a motion-blur trail, slows down, frosts
 * the top and bottom while it spins, pulses the winner and hands it to the player.
 */
class ShuffleRouletteState {
  var tick: Int by mutableIntStateOf(0)
    private set

  var running: Boolean by mutableStateOf(false)
    private set

  /** The row the wheel is on right now. */
  var highlightSongId: String? by mutableStateOf(null)
    private set

  /** The row the wheel settled on; drives the winner pulse. */
  var winnerSongId: String? by mutableStateOf(null)
    private set

  /** 0..1 — how strongly the top and bottom of the screen are frosted. */
  var blurProgress: Float by mutableFloatStateOf(0f)
    private set

  /** 1 while the wheel is flying, 0 once it slows — drives the motion blur. */
  var speedBlur: Float by mutableFloatStateOf(0f)
    private set

  /** Rows the wheel recently passed; each entry keys a fading trail overlay. */
  val trailTimes = mutableStateMapOf<String, Long>()

  /** Called by the shuffle FAB. Ignored while a spin is already running. */
  fun begin() {
    if (!running) tick++
  }

  internal suspend fun run(songs: List<Song>, onPlay: (Song) -> Unit) {
    if (songs.isEmpty() || running) return
    running = true
    try {
      val winner = songs.random()
      val frost = Animatable(0f)

      // Frost the top and bottom in as the wheel starts.
      frost.animateTo(1f, tween(320)) { blurProgress = value }

      var delayMs = 45f
      var index = Random.nextInt(songs.size)
      for (step in 1..SPIN_STEPS) {
        val isFinal = step == SPIN_STEPS
        val song = if (isFinal) winner else songs[index]
        highlightSongId = song.id
        trailTimes[song.id] = System.nanoTime()
        // Fast part of the wheel: highlights smear; as it decelerates they sharpen.
        speedBlur = if (!isFinal && delayMs < 110f) 1f else 0f
        delay(delayMs.toLong())
        if (isFinal) break
        delayMs = min(delayMs * 1.18f, 260f)
        index = (index + Random.nextInt(1, 4)).mod(songs.size)
      }

      highlightSongId = winner.id
      speedBlur = 0f
      delay(420) // let the winner pulse read before the frost lifts

      frost.animateTo(0f, tween(380)) { blurProgress = value }
      highlightSongId = null
      winnerSongId = null
      trailTimes.clear()

      onPlay(winner)
    } finally {
      running = false
      blurProgress = 0f
      speedBlur = 0f
      highlightSongId = null
      winnerSongId = null
      trailTimes.clear()
    }
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
 * The smear each wheel stop leaves behind: a highlight that flashes on and fades out, blurred
 * while the wheel is fast ([ShuffleRouletteState.speedBlur]) and sharp as it slows.
 */
@Composable
fun ShuffleTrailOverlay(state: ShuffleRouletteState, songId: String, modifier: Modifier = Modifier) {
  val visit = state.trailTimes[songId]
  val alpha = remember { Animatable(0f) }
  LaunchedEffect(visit) {
    if (visit == null) {
      alpha.snapTo(0f)
    } else {
      alpha.snapTo(0.55f)
      alpha.animateTo(0f, tween(300))
    }
  }
  if (alpha.value > 0.02f) {
    val radius = 6.dp * state.speedBlur
    Box(
      modifier =
        modifier
          .background(
            MaterialTheme.colorScheme.primary.copy(alpha = alpha.value),
            RoundedCornerShape(14.dp),
          )
          .then(if (radius > 0.5.dp) Modifier.blur(radius) else Modifier),
    )
  }
}
