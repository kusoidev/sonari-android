package app.music.backend.sonari.ui.screens.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.music.backend.sonari.db.entities.Song
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter

/** How few rows a spin is allowed to turn over before it winds up first. */
private const val MIN_TRAVEL_ROWS = 14f

/**
 * The shuffle spin, over the real list. [spin] rolls the playlist itself through its songs with one
 * long ease-out and stops on the winning row: the rows that fly past are the playlist's own rows,
 * drawn by the playlist's own list. Nothing is drawn in place of the list.
 *
 * While it runs, [running] is true, and the screens stop the list from being scrolled or tapped, so
 * nothing can be moved until the spin has finished.
 */
class ShuffleRouletteState {
  var tick by mutableIntStateOf(0)
    private set

  var running by mutableStateOf(false)
    private set

  private var claimedTick by mutableIntStateOf(0)

  /** True while the list is rolling. */
  var visible by mutableStateOf(false)
    private set

  /** True once the list has stopped on a song, while its confetti is going off. */
  var landed by mutableStateOf(false)
    private set

  /** 0..1 how strongly the top and bottom of the screen are frosted. */
  var frost by mutableFloatStateOf(0f)
    private set

  /** How fast the list is moving, 0..1: drives how hard the brackets are knocked. */
  var speed by mutableFloatStateOf(0f)
    private set

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
   * Rolls [listState] and hands back which song ends up between the brackets: whatever the payline
   * holds is the winner, so the spin can never stop on a row the brackets are not on.
   *
   * The roll aims at a random song being held at the payline. Once that row is on screen the roll
   * stops trusting units altogether and glides the row itself onto the payline in measured pixels,
   * so a tall header or rows of odd sizes cannot leave the landing short. If the list runs against
   * its end before that row can be centred - the last songs of a playlist cannot reach a payline
   * that sits above them - the roll takes the nearest row above the payline instead, which can be
   * centred exactly, and that row is the winner.
   */
  internal suspend fun spin(
    listState: LazyListState,
    headerItems: Int,
    songCount: Int,
  ): Int {
    if (running || songCount <= 0) return -1
    val rowPx = songRowPxOf(listState, headerItems)
    if (rowPx <= 0f) return -1

    running = true
    visible = true
    landed = false
    speed = 0f
    val frostAnim = Animatable(0f)

    try {
      frostAnim.animateTo(1f, tween(200)) { frost = value }

      // Where the payline sits in song-row units, measured from the content start: a row's middle
      // is what has to land on it, not its edge.
      val layout = listState.layoutInfo
      val paylineRows =
        ((layout.viewportSize.height / 2f) - layout.beforeContentPadding) / rowPx
      val targetRow = headerItems + Random.nextInt(songCount)
      val target = targetRow + 0.5f - paylineRows
      var start = currentRow(listState, rowPx)

      // A row already sitting under the brackets would barely move. Pull the list back first and
      // let it roll in, so every spin turns over real rows rather than twitching by two.
      if (abs(target - start) < MIN_TRAVEL_ROWS) {
        val windUp = if (target >= start) -MIN_TRAVEL_ROWS else MIN_TRAVEL_ROWS
        roll(listState, start + windUp, 220f, -1, headerItems)
        start = currentRow(listState, rowPx)
      }

      val rows = abs(target - start)
      val durationMs = (1500f + rows * 12f).coerceIn(1500f, 2800f)
      roll(listState, target, durationMs, targetRow, headerItems)

      speed = 0f
      val winner = paylineSong(listState, headerItems, songCount)
      landed = true
      delay(900)

      frostAnim.animateTo(0f, tween(300)) { frost = value }
      return winner
    } finally {
      running = false
      visible = false
      landed = false
      frost = 0f
      speed = 0f
    }
  }

  /**
   * Which song the brackets are holding, read out of the layout once the list has stopped.
   *
   * Nothing moves here: the roll has already eased a row into place between the brackets, so this
   * only names what stopped there. Reading it instead of keeping the intended index means a list
   * that clamped at either end still reports the row it really stopped on.
   */
  private fun paylineSong(
    listState: LazyListState,
    headerItems: Int,
    songCount: Int,
  ): Int {
    val row = rowAt(listState.layoutInfo) ?: return 0
    return row.songIndex(headerItems, songCount)
  }

  /** The row the payline cuts through, or the one nearest to it when it lands in a gap. */
  private fun rowAt(layout: LazyListLayoutInfo): LazyListItemInfo? {
    val paylineInContent = layout.viewportSize.height / 2f - layout.beforeContentPadding
    return layout.visibleItemsInfo.firstOrNull {
      it.offset <= paylineInContent && it.offset + it.size > paylineInContent
    } ?: layout.visibleItemsInfo.minByOrNull {
      abs(it.offset + it.size / 2f - paylineInContent)
    }
  }

  /** Which song a row stands for, kept inside the songs when the payline meets a header. */
  private fun LazyListItemInfo.songIndex(headerItems: Int, songCount: Int): Int =
    (index - headerItems).coerceIn(0, songCount - 1)

  /**
   * One continuous ease-out of the real list towards [target], a position in song-row units (3.5 is
   * halfway between the third and fourth song). While [targetRow] is on screen the eased fraction is
   * applied to the distance measured from the row itself to the payline, re-read every frame, so the
   * last pixels always belong to that row: a roll that only trusted units would land wherever the
   * units guessed, this lands on what is actually there.
   */
  private suspend fun roll(
    listState: LazyListState,
    target: Float,
    durationMs: Float,
    targetRow: Int,
    headerItems: Int,
  ) {
    val startFrame = withFrameNanos { it }
    var previousFrame = startFrame
    var previousT = 0f
    var aimRow = targetRow
    val startRow = currentRow(listState, songRowPxOf(listState, headerItems))
    while (true) {
      val now = withFrameNanos { it }
      val t = ((now - startFrame) / 1_000_000f / durationMs).coerceIn(0f, 1f)
      // Quintic ease-out: the list keeps most of its speed for the first half and then lets go in a
      // long, quiet tail, which is how a wheel actually comes to rest rather than a cubic cut.
      val inverse = 1f - t
      val eased = 1f - inverse * inverse * inverse * inverse * inverse
      val rowPx = songRowPxOf(listState, headerItems)
      if (rowPx <= 0f) break
      val targetInfo =
        if (aimRow >= 0) listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == aimRow }
        else null

      val deltaPx =
        if (targetInfo != null) {
          // The row the roll is reaching for is on screen: consume the eased fraction of what is
          // actually left between it and the payline. The cap keeps one hitchy frame from turning
          // its share of the distance into a jump.
          val paylinePx =
            listState.layoutInfo.viewportSize.height / 2f - listState.layoutInfo.beforeContentPadding
          val remainingPx = targetInfo.offset + targetInfo.size / 2f - paylinePx
          val previousEased =
            1f - (1f - previousT) * (1f - previousT) * (1f - previousT) * (1f - previousT) *
              (1f - previousT)
          val fraction =
            if (eased >= 1f) 1f else (eased - previousEased) / (1f - previousEased)
          remainingPx * fraction.coerceIn(0f, 0.35f)
        } else {
          // The aim row has not scrolled into view yet: approximate in row units until it is,
          // and let the measured tail take over the moment it is.
          val wanted = startRow + (target - startRow) * eased
          (wanted - currentRow(listState, rowPx)) * rowPx
        }

      val consumed =
        if (abs(deltaPx) >= 0.5f) listState.scrollBy(deltaPx) else deltaPx
      if (aimRow >= 0 && deltaPx > 0.5f && consumed < deltaPx - 0.5f) {
        // The list has run against its end clamp with the aim row still short of the payline: that
        // row can never be centred on it, because the list has nothing left to give. The row above
        // the payline still can - it is glided in with a backwards roll - so what stops between the
        // brackets is exactly centred rather than hanging half a row low.
        val paylineRow = rowAt(listState.layoutInfo)
        val next = (paylineRow?.index ?: aimRow) - 1
        if (next >= headerItems) aimRow = next
      }
      val seconds = ((now - previousFrame) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
      speed = (abs(deltaPx) / seconds / 8000f).coerceIn(0f, 1f)
      previousT = t
      previousFrame = now
      if (t >= 1f) break
    }
    overshootThenSettle(listState, aimRow)
    speed = 0f
  }

  /**
   * A wheel does not stop dead on the line: it rolls a hair past it and eases back. This carries the
   * winning row a few pixels beyond the payline and lets [settleOnPayline] bring it back, so the stop
   * reads as the list settling instead of as a cut. The extra pixels are taken back at once when the
   * list is against one of its ends and refuses them, because then there is nothing to roll into.
   */
  private suspend fun overshootThenSettle(listState: LazyListState, targetRow: Int) {
    if (targetRow >= 0) {
      val info = listState.layoutInfo
      val targetInfo = info.visibleItemsInfo.firstOrNull { it.index == targetRow }
      if (targetInfo != null) {
        val paylinePx = info.viewportSize.height / 2f - info.beforeContentPadding
        val remainingPx = targetInfo.offset + targetInfo.size / 2f - paylinePx
        // Only roll past the line when the row is already there; a long distance left over by a
        // hitchy frame is not something to overshoot, it is something to settle.
        if (abs(remainingPx) > targetInfo.size) {
          settleOnPayline(listState, targetRow)
          return
        }
        val overshoot =
          (targetInfo.size * 0.07f).coerceIn(3f, 18f) * if (remainingPx >= 0f) 1f else -1f
        val wanted = remainingPx + overshoot
        if (abs(wanted) >= 0.5f) {
          val consumed = listState.scrollBy(wanted)
          if (abs(consumed) < abs(wanted) - 0.5f) {
            // Clamped at an end: undo the part of the overshoot that did land, and settle exactly.
            listState.scrollBy(-overshoot)
          }
        }
      }
    }
    settleOnPayline(listState, targetRow)
  }

  /**
   * The last few pixels of a landing, measured. A dropped or hitchy frame during the roll can leave
   * a little distance; this finishes the glide over a couple of quiet frames instead of jumping, and
   * never fights a list that has clamped at one of its ends.
   */
  private suspend fun settleOnPayline(listState: LazyListState, targetRow: Int) {
    if (targetRow < 0) return
    repeat(8) {
      val info = listState.layoutInfo
      val targetInfo = info.visibleItemsInfo.firstOrNull { it.index == targetRow } ?: return
      val paylinePx = info.viewportSize.height / 2f - info.beforeContentPadding
      val remainingPx = targetInfo.offset + targetInfo.size / 2f - paylinePx
      if (abs(remainingPx) < 0.5f) return
      val step = if (abs(remainingPx) > 1f) remainingPx * 0.5f else remainingPx
      val consumed = listState.scrollBy(step)
      if (abs(consumed) < abs(step) * 0.5f) return
      withFrameNanos { }
    }
  }

  /** Where the list is, in song-row units: 3.5 means halfway through the fourth row. */
  private fun currentRow(listState: LazyListState, rowPx: Float): Float {
    if (rowPx <= 0f) return listState.firstVisibleItemIndex.toFloat()
    return listState.firstVisibleItemIndex + listState.firstVisibleItemScrollOffset / rowPx
  }

  /**
   * Height of a song row as the list draws it, measured from what is on screen. A header at the top
   * of the viewport is skipped, so its greater height is never mistaken for a song row's.
   */
  private fun songRowPxOf(listState: LazyListState, headerItems: Int): Float =
    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index >= headerItems }?.size?.toFloat()
      ?: listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat()
      ?: 0f
}

/**
 * Frosted edges for the spin, anchored to the very top and bottom of the screen instead of the top
 * of the list, so the middle of the playlist stays clear while the edges fade away.
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
 * The payline the spin comes to rest on: a hairline across the middle of the list with a soft glow
 * behind it. The line brightens while the list is running - faster rows, brighter line - and flashes
 * once when a song settles on it, so the landing is felt on the screen as well as in the hand.
 *
 * No arrows: the list itself says where it is going, and the winning row is the row the line cuts.
 */
@Composable
fun BoxScope.ShuffleNeedleOverlay(
  state: ShuffleRouletteState,
  modifier: Modifier = Modifier,
) {
  if (!state.visible) return
  val shown by
    animateFloatAsState(
      targetValue = if (state.visible) 1f else 0f,
      animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
      label = "shufflePaylineAlpha",
    )
  val haptics = LocalHapticFeedback.current
  // One pulse per landing, and one tap in the hand to go with it.
  val landPulse = remember { Animatable(0f) }
  LaunchedEffect(state.landed) {
    if (!state.landed) return@LaunchedEffect
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    landPulse.snapTo(1f)
    landPulse.animateTo(0f, tween(durationMillis = 520, easing = LinearEasing))
  }
  val lineColor = MaterialTheme.colorScheme.primary

  Canvas(modifier = modifier.fillMaxSize().graphicsLayer { alpha = shown * state.frost }) {
    val y = size.height / 2f
    val speed = state.speed.coerceIn(0f, 1f)
    val pulse = landPulse.value.coerceIn(0f, 1f)
    // The glow band widens as the list runs, and flares when it stops.
    val bandHalf = (34f + 26f * speed + 30f * pulse).dp.toPx()
    drawRect(
      brush =
        Brush.verticalGradient(
          colors =
            listOf(
              Color.Transparent,
              lineColor.copy(alpha = 0.06f + 0.07f * speed + 0.10f * pulse),
              Color.Transparent,
            ),
          startY = y - bandHalf,
          endY = y + bandHalf,
        ),
      topLeft = Offset(0f, y - bandHalf),
      size = Size(size.width, bandHalf * 2f),
    )
    // The line itself: brightest in the middle, gone before it reaches the edges, so it reads as a
    // place to land rather than as a divider across the screen.
    val core = 0.35f + 0.30f * speed + 0.45f * pulse
    drawRect(
      brush =
        Brush.horizontalGradient(
          0f to Color.Transparent,
          0.16f to lineColor.copy(alpha = core * 0.45f),
          0.5f to lineColor.copy(alpha = core),
          0.84f to lineColor.copy(alpha = core * 0.45f),
          1f to Color.Transparent,
        ),
      topLeft = Offset(0f, y - 0.75.dp.toPx()),
      size = Size(size.width, 1.5.dp.toPx()),
    )
    // Two short ticks at the ends of the line, so the eye can find the payline at a glance without
    // an arrow telling it which way the list is going.
    val tickWidth = 10.dp.toPx()
    val tickInset = size.width * 0.16f
    val tickAlpha = 0.30f + 0.25f * speed + 0.35f * pulse
    listOf(tickInset, size.width - tickInset).forEach { x ->
      drawRect(
        brush =
          Brush.horizontalGradient(
            colors =
              listOf(
                lineColor.copy(alpha = tickAlpha * 0.2f),
                lineColor.copy(alpha = tickAlpha),
              ),
          ),
        topLeft = Offset(x - tickWidth / 2f, y - 0.75.dp.toPx()),
        size = Size(tickWidth, 1.5.dp.toPx()),
      )
    }
  }
}

/** A burst of confetti out of the middle of the list once it has stopped on a song. */
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

/** How many items a playlist puts before its songs: the header and the sort row. */
private const val PLAYLIST_HEADER_ITEMS = 2

/**
 * The playlist side of the spin. A playlist's Shuffle button calls the returned action, which rolls
 * the playlist's own list to a random song and only then hands the winner-first order to [onQueue],
 * so what starts playing is what the list actually stopped on.
 *
 * The state is what the screen draws with [PlaylistShuffleOverlay], and [ShuffleRouletteState.running]
 * is what keeps the list from being scrolled while it turns.
 */
@Composable
fun rememberPlaylistShuffle(
  songs: List<Song>,
  listState: LazyListState,
  headerItems: Int = PLAYLIST_HEADER_ITEMS,
  onQueue: (List<Song>) -> Unit,
): Pair<ShuffleRouletteState, () -> Unit> {
  val roulette = remember { ShuffleRouletteState() }
  val scope = rememberCoroutineScope()
  val currentSongs by rememberUpdatedState(songs)
  val currentOnQueue by rememberUpdatedState(onQueue)
  val currentHeaderItems by rememberUpdatedState(headerItems)
  val shuffle: () -> Unit = {
    val songsNow = currentSongs
    if (songsNow.isNotEmpty()) {
      scope.launch {
        val order = songsNow.shuffled()
        val winnerRow = roulette.spin(listState, currentHeaderItems, songsNow.size)
        if (winnerRow in songsNow.indices) {
          // What the brackets came to rest on plays first, then everything else in shuffled order.
          val at = order.indexOfFirst { it.id == songsNow[winnerRow].id }.coerceAtLeast(0)
          currentOnQueue(order.drop(at) + order.take(at))
        }
      }
    }
  }
  return roulette to shuffle
}

/**
 * While the list turns, nothing under it may be touched: no scrolling, no taps, no swipes, no
 * reordering. Drawn over the screen, it swallows every pointer event until the spin has landed, and
 * the screens additionally stop their list from scrolling while [ShuffleRouletteState.running].
 */
@Composable
fun BoxScope.ShuffleInputBlocker(state: ShuffleRouletteState) {
  if (!state.visible) return
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color.Transparent)
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              event.changes.forEach { it.consume() }
            }
          }
        }
  )
}

/**
 * Everything the spin draws over a playlist: the frosted edges, the payline in the middle, the
 * confetti when it lands, and a blocker so the screen cannot be touched until it stops. The playlist
 * itself is what turns, so there is nothing to draw over it.
 *
 * Draw it last inside the playlist screen's root Box.
 */
@Composable
fun BoxScope.PlaylistShuffleOverlay(state: ShuffleRouletteState) {
  if (state.frost <= 0.01f && !state.visible) return
  ShuffleFrostOverlay(state, Modifier.fillMaxSize())
  if (!state.visible) return
  ShuffleNeedleOverlay(state, Modifier.fillMaxSize())
  ShuffleConfetti(state, Modifier.fillMaxSize())
  ShuffleInputBlocker(state)
}
