package app.music.backend.sonari.ui.component

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid

/**
 * The shared source the app's chrome samples through the liquid shaders. It is provided once in
 * [app.music.backend.sonari.MainActivity], where the screen content is tagged as its `liquefiable`
 * source, and read by the surfaces that draw over that content.
 */
val LocalLiquidState = staticCompositionLocalOf<LiquidState?> { null }

/**
 * Renders this composable as liquid glass with [io.github.fletchmckee.liquid], sampling the screen
 * content tagged with the same state.
 *
 * The library's shaders are the real thing: refraction, curve and dispersion bend the pixels behind
 * the surface, frost blurs them, and the rim lights up. Everything it cannot do on a given Android
 * version it degrades on its own (refraction and dispersion are ignored below 13, frost below 12).
 *
 * On a low-RAM phone, or in power saving, only frost, tint and saturation are asked for: the lens
 * work is the expensive part, and a blurred, tinted panel still reads as glass.
 *
 * When the effect is switched off, the platform is too old, or no source has been provided, this
 * falls back to the app's own backdrop pipeline so the surface never loses its background.
 */
@Composable
fun Modifier.sonariLiquid(
  config: GlassEffectConfig,
  component: GlassComponent,
  shape: CornerBasedShape = RoundedCornerShape(28.dp),
  applyEdgeEffects: Boolean = true,
  blurRadiusDp: Float = config.blurRadius,
): Modifier {
  val state = LocalLiquidState.current
  if (state == null || !isGlassSupported() || !config.isEnabledFor(component)) {
    return liquidGlass(
      config = config,
      shape = shape,
      applyEdgeEffects = applyEdgeEffects,
      blurRadiusDp = blurRadiusDp,
    )
  }
  val context = LocalContext.current
  val lowEnd = remember(context) { isGlassLowEnd(context) }
  val lens = applyEdgeEffects && !lowEnd
  val tintColor =
    if (config.surfaceTintColor.isSpecified) {
      config.surfaceTintColor
    } else if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
      Color(0xFFFAFAFA)
    } else {
      Color(0xFF121212)
    }
  return liquid(state) {
    frost = blurRadiusDp.dp
    this.shape = shape
    refraction = if (lens) (config.lensAmount * 0.55f).coerceIn(0f, 0.5f) else 0f
    curve = if (lens) (config.lensHeight * 0.8f).coerceIn(0f, 0.5f) else 0f
    edge = if (lens) 0.12f else 0f
    dispersion = if (lens && config.chromaticAberration) 0.18f else 0f
    saturation = glassSaturation(config.vibrancy)
    tint = tintColor.copy(alpha = config.surfaceOpacity.coerceIn(0f, 1f))
  }
}
