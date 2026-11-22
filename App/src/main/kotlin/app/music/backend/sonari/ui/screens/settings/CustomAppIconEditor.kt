package app.music.backend.sonari.ui.screens.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.music.backend.sonari.utils.CustomAppIcon
import kotlin.math.max

/**
 * Square crop for the custom icon: drag the photo around, zoom it in, and what the circle shows is
 * what the icon gets.
 */
@Composable
fun CustomAppIconEditorDialog(
  source: Bitmap,
  onDismiss: () -> Unit,
  onConfirm: (Bitmap) -> Unit
) {
  val previewSize = 240.dp
  val previewPx = with(LocalDensity.current) { previewSize.toPx() }
  val image = remember(source) { source.asImageBitmap() }

  var zoom by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  fun clamp(candidate: Offset, zoomValue: Float): Offset {
    val scale = max(previewPx / source.width, previewPx / source.height) * zoomValue
    val limitX = max(0f, (source.width * scale - previewPx) / 2f)
    val limitY = max(0f, (source.height * scale - previewPx) / 2f)
    return Offset(
      candidate.x.coerceIn(-limitX, limitX),
      candidate.y.coerceIn(-limitY, limitY)
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Your icon") },
    text = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
          modifier =
            Modifier.size(previewSize)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceContainerHighest)
              .pointerInput(zoom) {
                detectDragGestures { change, drag ->
                  change.consume()
                  offset = clamp(offset + drag, zoom)
                }
              },
          contentAlignment = Alignment.Center
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = max(size.width / source.width, size.height / source.height) * zoom
            withTransform({
              translate(size.width / 2f + offset.x, size.height / 2f + offset.y)
              scale(scale, scale, pivot = Offset.Zero)
              translate(-source.width / 2f, -source.height / 2f)
            }) {
              drawImage(image)
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "Drag to move, slide to zoom",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
          value = zoom,
          onValueChange = {
            zoom = it
            offset = clamp(offset, it)
          },
          valueRange = 1f..3f
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onConfirm(
            CustomAppIcon.render(
              source = source,
              zoom = zoom,
              offsetX = offset.x,
              offsetY = offset.y,
              previewSizePx = previewPx
            )
          )
        }
      ) {
        Text("Use it")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}
