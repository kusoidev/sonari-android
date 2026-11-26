package app.music.backend.sonari.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BannerMessage(
  val id: Long,
  val text: String,
  val iconRes: Int? = null,
)

/**
 * One short line about what just happened, such as a queue being shuffled. Shown for a moment and
 * gone: it should not sit in the way of the player.
 */
object ActionBanner {
  private const val ShowForMillis = 2000L

  private val _message = MutableStateFlow<BannerMessage?>(null)
  val message: StateFlow<BannerMessage?> = _message.asStateFlow()

  private var counter = 0L

  fun show(text: String, iconRes: Int? = null) {
    counter += 1
    _message.value = BannerMessage(id = counter, text = text, iconRes = iconRes)
  }

  fun dismiss() {
    _message.value = null
  }

  fun shownForMillis(): Long = ShowForMillis
}

@Composable
fun ActionBannerHost(modifier: Modifier = Modifier) {
  val message by ActionBanner.message.collectAsState()

  LaunchedEffect(message?.id) {
    if (message != null) {
      delay(ActionBanner.shownForMillis())
      ActionBanner.dismiss()
    }
  }

  AnimatedVisibility(
    visible = message != null,
    enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
    exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 4 },
    modifier = modifier,
  ) {
    val shown = message
    if (shown != null) {
      Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val icon = shown.iconRes
          if (icon != null) {
            Icon(
              painter = painterResource(icon),
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
          }
          Text(text = shown.text, style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
  }
}
