package app.music.backend.sonari.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.music.backend.sonari.ui.utils.resize
import coil3.compose.AsyncImage

/**
 * One card standing for the whole "For you" playlist: the app builds it on its own, so there is
 * nothing to pick here, only to open it or keep it.
 */
@Composable
fun ForYouCard(
  coverUrl: String?,
  songCount: Int,
  kept: Boolean,
  saving: Boolean,
  onOpen: () -> Unit,
  onSave: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .clip(RoundedCornerShape(22.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .clickable(onClick = onOpen)
        .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AsyncImage(
      model = coverUrl?.resize(544, 544),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(92.dp).clip(RoundedCornerShape(16.dp))
    )

    Spacer(modifier = Modifier.width(14.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "For you",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = if (songCount == 1) "1 song, built from what you play"
        else "$songCount songs, built from what you play",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }

    Spacer(modifier = Modifier.width(8.dp))

    if (kept) {
      Text(
        text = "Saved",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary
      )
    } else {
      OutlinedButton(onClick = onSave, enabled = !saving) {
        Text(if (saving) "Saving" else "Save")
      }
    }
  }
}
