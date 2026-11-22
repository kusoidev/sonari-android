package app.music.backend.sonari.sonari.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.music.backend.sonari.R
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * The update prompt, laid out like the reference: one card in the middle of the screen, the download
 * mark with a red dot saying something is waiting, a line about what it is for, where the user
 * stands next to where they could be, and one button to go and get it.
 *
 * "Not now" (or the close mark) puts that release off, so the prompt comes back only when a newer
 * one ships.
 */
@Composable
fun UpdateAvailableDialog(
  currentVersion: String,
  latestVersion: String,
  onUpdate: () -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
      shape = AbsoluteSmoothCornerShape(32.dp, 60),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
    ) {
      Box {
        Column(
          modifier =
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 18.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          UpdateMark()

          Spacer(Modifier.height(22.dp))

          Text(
            text = stringResource(R.string.update_dialog_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
          )

          Spacer(Modifier.height(10.dp))

          Text(
            text = stringResource(R.string.update_dialog_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          Spacer(Modifier.height(14.dp))

          Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
          ) {
            Text(
              text = stringResource(R.string.update_versions, currentVersion, latestVersion),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
          }

          Spacer(Modifier.height(22.dp))

          Button(
            onClick = onUpdate,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp),
          ) {
            Text(
              text = stringResource(R.string.update_dialog_update),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
          }

          Spacer(Modifier.height(2.dp))

          TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(
              text = stringResource(R.string.update_dialog_later),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp),
        ) {
          Icon(
            painter = painterResource(R.drawable.close),
            contentDescription = stringResource(R.string.update_dialog_later),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

/**
 * The download mark from the reference: a soft disc, a big arrow, and a red dot on its corner
 * saying the release is waiting.
 */
@Composable
private fun UpdateMark() {
  Box(modifier = Modifier.size(98.dp), contentAlignment = Alignment.Center) {
    Box(
      modifier =
        Modifier.size(86.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape)
    )
    Icon(
      painter = painterResource(R.drawable.update),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(52.dp),
    )
    Box(
      modifier =
        Modifier.align(Alignment.BottomEnd)
          .size(28.dp)
          .background(MaterialTheme.colorScheme.error, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "!",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onError,
      )
    }
  }
}
