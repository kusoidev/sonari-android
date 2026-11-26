package app.music.backend.sonari.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.music.backend.sonari.ui.utils.resize
import coil3.compose.AsyncImage

/**
 * One card standing for a whole "For you" playlist: the app builds it on its own, so there is
 * nothing to pick here, only to open it, keep it, or hold it to ask for more of the same.
 */
@Composable
fun ForYouCard(
  title: String = "For you",
  coverUrl: String?,
  songCount: Int,
  kept: Boolean,
  saving: Boolean,
  onOpen: () -> Unit,
  onSave: () -> Unit,
  onLongClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val haptic = LocalHapticFeedback.current

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        // The cards sit one under the other, so the space between them belongs to the card.
        .padding(horizontal = 20.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(22.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .combinedClickable(
          onClick = onOpen,
          onLongClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongClick()
          },
        )
        .padding(horizontal = 12.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AsyncImage(
      model = coverUrl?.resize(544, 544),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(84.dp).clip(RoundedCornerShape(16.dp))
    )

    Spacer(modifier = Modifier.width(16.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(5.dp))
      Text(
        text =
          if (songCount == 1) "1 song, built from what you play"
          else "$songCount songs, built from what you play",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    Spacer(modifier = Modifier.width(14.dp))

    if (kept) {
      Text(
        text = "Saved",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp)
      )
    } else {
      OutlinedButton(
        onClick = onSave,
        enabled = !saving,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp),
      ) {
        Text(if (saving) "Saving" else "Save")
      }
    }
  }
}

/**
 * Stands in for the row while the first build of the session runs, so "For you" is on screen from
 * the moment it is opened instead of appearing once the build has finished.
 */
@Composable
fun ForYouBuildingCard(modifier: Modifier = Modifier) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(22.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal = 12.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(
        modifier = Modifier.size(28.dp),
        color = MaterialTheme.colorScheme.primary,
        strokeWidth = 3.dp,
      )
    }

    Spacer(modifier = Modifier.width(16.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "For you",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(5.dp))
      Text(
        text = "Building from what you play",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
