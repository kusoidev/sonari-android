package app.music.backend.sonari.ui.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.music.backend.sonari.generate.TasteSuggestion
import app.music.backend.sonari.ui.utils.resize
import coil3.compose.AsyncImage

/** One suggested song, with the reason it was suggested under it. */
@Composable
fun TasteSuggestionCard(
  suggestion: TasteSuggestion,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val song = suggestion.song
  val shape = RoundedCornerShape(14.dp)

  Column(
    modifier =
      modifier
        .width(150.dp)
        .clip(shape)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(bottom = 4.dp)
  ) {
    AsyncImage(
      model = song.thumbnail.resize(544, 544),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(150.dp).clip(shape)
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = song.title,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Text(
      text = suggestion.reason,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}
