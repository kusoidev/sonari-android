package app.music.backend.sonari.ui.component

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.flow.first

/** How much slower each row below the sung one is, per row of distance. */
private const val LagPerRowMs = 55f

/** Past this many rows the lag stops growing, so far rows still settle. */
private const val MaxRows = 9

/**
 * The follow-through on an auto scroll: rows below the sung line trail the scroll, the further down
 * the later, then each catches up on its own. A line change therefore arrives as a wave rather than
 * as every row jumping at once.
 *
 * The numbers are not snapshot state - one tick per frame tells the layers reading them to redraw.
 */
@Stable
class LyricsRipple(rowCount: Int) {
  private val lags = FloatArray(rowCount)
  private val velocities = FloatArray(rowCount)

  private val version = mutableIntStateOf(0)
  private val moving = mutableStateOf(false)

  var anchorRow = -1

  /** How far this row trails its place, in px. Read it in a graphics layer only. */
  fun lagOf(row: Int): Float {
    version.intValue
    return if (row in lags.indices) lags[row] else 0f
  }

  /** The list just scrolled by [px] and this row holds back by that much. */
  fun holdBack(row: Int, px: Float) {
    if (row !in lags.indices) return
    lags[row] += px
    version.intValue++
    moving.value = true
  }

  /** Runs while the page is open, asleep whenever every row is in place. */
  suspend fun run() {
    while (true) {
      snapshotFlow { moving.value }.first { it }
      var lastNanos = 0L
      var still: Boolean
      do {
        still =
          withFrameNanos { now ->
            val dt =
              if (lastNanos == 0L) 0.016f
              else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.064f)
            lastNanos = now
            step(dt).also { version.intValue++ }
          }
      } while (still || anyLag())
      moving.value = false
    }
  }

  /**
   * Each row chases its place on a critically damped spring, solved exactly, so any frame time
   * stays stable. Nearer rows are given a stiffer spring than far ones, which is what spreads the
   * catch-up out into a wave. Distance is measured either way, so the lines already sung trail off
   * the top on the same wave as the ones still to come.
   */
  private fun step(dt: Float): Boolean {
    var any = false
    for (row in lags.indices) {
      val lag = lags[row]
      val v = velocities[row]
      if (lag == 0f && v == 0f) continue
      val k = abs(row - anchorRow).coerceIn(1, MaxRows)
      val omega = 1000f / (LagPerRowMs * k)
      val e = exp(-omega * dt)
      val tmp = (v + omega * lag) * dt
      val nextLag = (lag + tmp) * e
      val nextV = (v - omega * tmp) * e
      if (abs(nextLag) < 0.3f && abs(nextV) < 4f) {
        lags[row] = 0f
        velocities[row] = 0f
      } else {
        lags[row] = nextLag
        velocities[row] = nextV
        any = true
      }
    }
    return any
  }

  private fun anyLag(): Boolean = lags.any { it != 0f }
}
