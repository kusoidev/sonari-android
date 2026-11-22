package app.music.backend.sonari.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where the brackets are on screen and where the winning row is on screen are two different
 * measurements. This test compares them in screen pixels, which is what a person sees, instead of
 * trusting the list's own offsets.
 */
@RunWith(AndroidJUnit4::class)
class ShufflePaylineGeomTest {
  @get:Rule val rule = createComposeRule()

  private val bracketY = mutableFloatStateOf(0f)
  private val listTop = mutableFloatStateOf(0f)
  private val rowTops = mutableMapOf<Int, Float>()
  private val scrollSamples = mutableListOf<Float>()
  private val rowHeights = mutableMapOf<Int, Int>()

  @Test
  fun theWinningRowIsWhereTheBracketsAre() {
    val headerItems = 2
    val songCount = 60
    lateinit var listState: LazyListState
    lateinit var roulette: ShuffleRouletteState
    lateinit var scope: CoroutineScope

    rule.setContent {
      listState = rememberLazyListState()
      roulette = remember { ShuffleRouletteState() }
      scope = rememberCoroutineScope()
      Box(Modifier.fillMaxSize()) {
        LazyColumn(
          state = listState,
          contentPadding = PaddingValues(top = 150.dp, bottom = 240.dp),
          modifier =
            Modifier.fillMaxSize().onGloballyPositioned { listTop.floatValue = it.positionInRoot().y },
        ) {
          items(headerItems) { Box(Modifier.fillMaxWidth().height(280.dp)) }
          items(songCount) { index ->
            Box(
              Modifier.fillMaxWidth()
                .height(84.dp)
                .onGloballyPositioned {
                  rowTops[index] = it.positionInRoot().y
                  rowHeights[index] = it.size.height
                }
            )
          }
        }
        ShuffleNeedleOverlay(
          roulette,
          Modifier.fillMaxSize().onGloballyPositioned {
            bracketY.floatValue = it.positionInRoot().y + it.size.height / 2f
          },
        )
      }
    }
    rule.waitForIdle()

    repeat(6) { spin ->
      // Three spins from the very top, where the wind-up has to climb over the tall header, and
      // three from the middle of the list, where the target is reached without any wind-up.
      rule.runOnIdle {
        scope.launch { listState.scrollToItem(if (spin < 3) 0 else 28) }
      }
      rule.waitForIdle()
      rowTops.clear()
      rowHeights.clear()
      scrollSamples.clear()
      var winner = Int.MIN_VALUE
      rule.runOnIdle {
        scope.launch {
          // What the list does frame by frame, so a jump at the end is visible.
          val sampler = launch {
            while (true) {
              withFrameNanos { }
              val info = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: continue
              scrollSamples.add(
                (listState.firstVisibleItemIndex * info.size +
                    listState.firstVisibleItemScrollOffset).toFloat()
              )
            }
          }
          winner = roulette.spin(listState, headerItems, songCount)
          sampler.cancel()
        }
      }
      rule.waitUntil(timeoutMillis = 30_000) { winner != Int.MIN_VALUE }
      rule.waitForIdle()

      val under = rowTops.entries.firstOrNull { (index, top) ->
        val height = rowHeights[index] ?: 0
        top <= bracketY.floatValue && top + height > bracketY.floatValue
      }
      val viewport = listState.layoutInfo.viewportSize.height
      val firstOffset = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: -1
      val message =
        "spin $spin: bracketY=${bracketY.floatValue} listTop=${listTop.floatValue} " +
          "viewportHalf=${viewport / 2} viewport=$viewport firstOffset=$firstOffset " +
          "paddingTop=${listState.layoutInfo.beforeContentPadding} " +
          "winner=$winner underIndex=${under?.key}"

      org.junit.Assert.assertEquals(message, winner, (under?.key ?: -99))
      org.junit.Assert.assertTrue("$message (winner out of range)", winner in 0 until songCount)
      org.junit.Assert.assertFalse("spin $spin has to be over", roulette.running)

      // The spin has to settle into its row, not roll past and be snapped back: over the last
      // handful of frames the list may only creep.
      val rowPx = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat() ?: 1f
      val tail = scrollSamples.takeLast(30)
      val biggestStep = tail.zipWithNext { a, b -> kotlin.math.abs(b - a) }.maxOrNull() ?: 0f
      org.junit.Assert.assertTrue(
        "spin $spin snapped: a late frame moved $biggestStep px with a row being $rowPx px",
        biggestStep < rowPx * 0.5f,
      )

      // Pixel-perfect: the winning row's centre has to sit on the brackets, not merely somewhere in
      // the same row. Measured in screen pixels, like a person sees it.
      val winnerTop = rowTops[winner]
      val winnerHeight = rowHeights[winner]
      org.junit.Assert.assertNotNull("$message (winner row never measured)", winnerTop)
      val centre = winnerTop!! + (winnerHeight ?: 0) / 2f
      val off = kotlin.math.abs(centre - bracketY.floatValue)
      org.junit.Assert.assertTrue(
        "spin $spin: winning row centre is $off px off the brackets (centre=$centre, " +
          "bracketY=${bracketY.floatValue}) $message",
        off < 3f,
      )
    }
  }
}
