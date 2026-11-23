package app.music.backend.sonari.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades the top of a list out into whatever is behind it.
 *
 * A list is drawn inside a fixed area, so a row that scrolls past that area's top edge is sliced by
 * it: a hard line across the screen with half a row above it. This masks the last stretch of that
 * area with a gradient, so the rows melt into the page instead of ending on a line.
 *
 * Nothing is painted over the list - the content is made see-through, not covered - so it blends
 * with any backdrop, including a blurred cover or the app's own gradient.
 *
 * It is only there while there is something to scroll back to, so a list sitting at its top is left
 * untouched.
 */
@Composable
fun Modifier.softTopEdgeFade(listState: LazyListState?, height: Dp = 96.dp): Modifier {
  val scrolled by
    remember(listState) {
      derivedStateOf {
        listState != null &&
          (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
      }
    }
  val shown by
    animateFloatAsState(
      targetValue = if (scrolled) 1f else 0f,
      animationSpec = tween(durationMillis = 220),
      label = "softTopEdge",
    )
  val fadeHeightPx = with(LocalDensity.current) { height.toPx() }

  return this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
      drawContent()
      if (shown > 0.01f) {
        drawRect(
          brush =
            Brush.verticalGradient(
              0f to Color.Transparent,
              1f to Color.Black,
              endY = fadeHeightPx,
            ),
          blendMode = BlendMode.DstIn,
          alpha = shown,
        )
      }
    }
}
