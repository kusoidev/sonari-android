package app.music.backend.sonari.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/** Where a cover is drawn: its rectangle in root pixels, and the corner radius it is clipped to. */
data class ArtworkSlot(val rect: Rect, val cornerRadius: Dp)

/**
 * The cover's hand-off from the player into the queue's header.
 *
 * The player's artwork and the 48dp slot in the queue header are the same cover, so the queue no
 * longer fades its own copy in while the player throws its copy away into the corner: one cover
 * travels from the artwork's place on the player to the slot, and the header's own copy only takes
 * over once it has landed, exactly on top of it.
 *
 * Both rectangles are measured, in root pixels, so nothing has to know the layout of either screen:
 * [player] is the artwork as the player draws it, [queue] is the slot as the queue draws it - and the
 * slot moves with the sheet, which is what lets the cover ride along and stay on it.
 */
@Stable
class ArtworkFlight {
  var player by mutableStateOf<ArtworkSlot?>(null)
  var queue by mutableStateOf<ArtworkSlot?>(null)
}

/** Reports where the player's artwork is drawn, for [ArtworkFlight] to carry the cover from. */
fun Modifier.reportArtworkSlot(flight: ArtworkFlight, cornerRadius: Dp): Modifier =
  onGloballyPositioned { coordinates ->
    flight.player =
      ArtworkSlot(
        rect = Rect(coordinates.positionInRoot(), coordinates.size.toSize()),
        cornerRadius = cornerRadius,
      )
  }

/** A smooth S from 0 at [edge0] to 1 at [edge1], flat at both ends. */
internal fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
  if (edge1 <= edge0) return if (value < edge1) 0f else 1f
  val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

/** Where in the shot the cover starts to move, and where it is done moving. */
private const val TRAVEL_START = 0.30f
private const val TRAVEL_END = 0.80f
private const val SHRINK_START = 0.24f
private const val SHRINK_END = 0.74f

/**
 * How far into the drag the travelling cover has taken the cover over from the copy the player
 * draws. It fades in *on top of* that copy, which never dims: two copies at half opacity are not one
 * opaque cover - the screen behind them shows through - and that dip in solidity is the blink you
 * see at each end of the move. So one copy is always solid, and the other only ever fades over it.
 */
internal const val COVER_HANDED_OVER = 0.06f

/** From here the queue header's own copy is solid under the travelling one. */
internal const val SLOT_TAKES_COVER = TRAVEL_END

/** How far into the drag the queue's slot has become the solid copy of the cover. */
internal fun artworkHandoff(progress: Float): Float = smoothStep(0.85f, 1f, progress)

/** Has the travelling copy taken the cover over from the player's own? */
internal fun artworkCarried(progress: Float): Boolean = progress >= COVER_HANDED_OVER

/** Is the queue header's copy of the cover solid yet? */
internal fun slotHoldsCover(progress: Float): Boolean = progress >= SLOT_TAKES_COVER

/**
 * The travelling copy's opacity: it fades in over the player's copy, stays solid while it flies, and
 * fades out over the queue's copy in the header.
 */
internal fun artworkFlightAlpha(progress: Float): Float =
  if (progress < COVER_HANDED_OVER) smoothStep(0f, COVER_HANDED_OVER, progress)
  else 1f - artworkHandoff(progress)

/**
 * The one cover that travels. Drawn on top of the sheet, so it is never clipped by the queue rising
 * under it, and driven entirely by [progress] - the queue sheet's own drag progress. It holds on the
 * player while the sheet climbs towards it, then flies into the header slot and shrinks into it, so
 * pushing the sheet back down simply brings it home again, and tapping while it is still moving picks
 * it up from wherever it is.
 */
@Composable
fun ArtworkFlightOverlay(
  flight: ArtworkFlight,
  progress: () -> Float,
  url: String?,
  crop: Boolean,
  modifier: Modifier = Modifier,
) {
  val from = flight.player ?: return
  val to = flight.queue ?: from
  if (url.isNullOrEmpty()) return

  // Read here, in this composable's own scope: the drag recomposes this one small overlay and
  // nothing else in the player.
  val slide = progress().coerceIn(0f, 1f)
  val alpha = artworkFlightAlpha(slide)

  // Even at zero alpha the cover stays composed: the image is loaded and ready while the queue is
  // still shut, so the first frame it is needed in is a frame it can already draw. Unmounting it
  // would mean loading it the moment the drag starts, and the empty frame that shows up as a blink.
  val travel = smoothStep(TRAVEL_START, TRAVEL_END, slide)
  val shrink = smoothStep(SHRINK_START, SHRINK_END, slide)
  val centerX = from.rect.center.x + (to.rect.center.x - from.rect.center.x) * travel
  val centerY = from.rect.center.y + (to.rect.center.y - from.rect.center.y) * travel
  val width = from.rect.width + (to.rect.width - from.rect.width) * shrink
  val height = from.rect.height + (to.rect.height - from.rect.height) * shrink
  val cornerRadius = from.cornerRadius + (to.cornerRadius - from.cornerRadius) * shrink

  val density = LocalDensity.current
  var origin by remember { mutableStateOf(Offset.Zero) }

  Box(modifier = modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
    Box(
      modifier =
        Modifier.offset {
            IntOffset(
              (centerX - origin.x - width / 2f).roundToInt(),
              (centerY - origin.y - height / 2f).roundToInt(),
            )
          }
          .size(width = with(density) { width.toDp() }, height = with(density) { height.toDp() })
          .graphicsLayer { this.alpha = alpha }
          .clip(RoundedCornerShape(cornerRadius))
          // The same backing the player draws behind its artwork. A thumbnail that is not square
          // leaves bars either side of it in that colour, and a travelling copy without them would
          // make those bars disappear while the picture is in the air.
          .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
      AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}
