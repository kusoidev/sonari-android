package app.music.backend.sonari.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The soft edge a list gets at the top of the screen.
 *
 * A list is drawn inside a fixed area, so a row that scrolls past that edge is sliced by it: a hard
 * line across the screen with half a song above it. This lays a frosted fade over the last stretch
 * of that area, the same gradient the shuffle spin uses, so whatever scrolls up melts into the
 * background instead of being cut off.
 *
 * It is only there while there is something to scroll back to, so a list sitting at its top is left
 * untouched.
 */
@Composable
fun SoftTopEdge(
  listState: LazyListState?,
  modifier: Modifier = Modifier,
  height: Dp = 104.dp,
  color: Color = MaterialTheme.colorScheme.surface,
) {
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
  if (shown <= 0.01f) return

  Box(
    modifier
      .fillMaxWidth()
      .height(height)
      .graphicsLayer { alpha = shown }
      .background(
        Brush.verticalGradient(
          0f to color.copy(alpha = 0.92f),
          0.4f to color.copy(alpha = 0.6f),
          1f to Color.Transparent,
        )
      )
  )
}
