package app.music.backend.sonari.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import app.music.backend.sonari.ui.utils.fadingEdge

@Composable
fun OnlineBlur(
  thumbnailUrl: String?,
  modifier: Modifier = Modifier,
) {
  val page = MaterialTheme.colorScheme.background
  Box(modifier = modifier) {
    if (thumbnailUrl != null) {
      AsyncImage(
        model = thumbnailUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().blur(50.dp)
      )
    }

    // The blur melts into the page as it goes down instead of stopping on a line: whatever height
    // the caller gives it, by the bottom it is the page again.
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              0f to Color.Transparent,
              0.35f to page.copy(alpha = 0.12f),
              0.72f to page.copy(alpha = 0.72f),
              1f to page,
            )
          )
    )
  }
}
