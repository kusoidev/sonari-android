package app.music.backend.sonari.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * gone: it should not sit in the way of the player. It rises from the bottom edge and sinks back
 * down when it leaves, without fading away.
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

  // The last message the banner was given. The state clears to null the moment the banner is told
  // to leave, but the pill needs its text to keep drawing while it slides down out of sight.
  var shown by remember { mutableStateOf<BannerMessage?>(null) }
  if (message != null) shown = message

  LaunchedEffect(message?.id) {
    if (message != null) {
      delay(ActionBanner.shownForMillis())
      ActionBanner.dismiss()
    }
  }

  AnimatedVisibility(
    visible = message != null,
    enter =
      slideInVertically(
        animationSpec =
          spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow,
          ),
        initialOffsetY = { it },
      ),
    exit =
      slideOutVertically(
        animationSpec = tween(300, easing = FastOutLinearInEasing),
        targetOffsetY = { it },
      ) + fadeOut(animationSpec = tween(160, delayMillis = 140)),
    modifier = modifier,
  ) {
    AnimatedContent(
      targetState = shown,
      transitionSpec = {
        (fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 3 })
          .togetherWith(fadeOut(tween(120)))
      },
      label = "ActionBanner",
    ) { current ->
      if (current != null) {
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
            val icon = current.iconRes
            if (icon != null) {
              Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(Modifier.width(10.dp))
            }
            Text(text = current.text, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    }
  }
}
