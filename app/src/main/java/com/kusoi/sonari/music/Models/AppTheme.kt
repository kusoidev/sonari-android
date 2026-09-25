package com.kusoi.sonari.music.Models

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Dynamic theme derived from the current track's artwork. Only color math runs here.
 * Mirrors the Swift AppTheme: three colors tint the whole app.
 */
data class AppTheme(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val accent: Color,
    val onAccent: Color
) {
    companion object {
        val dark = AppTheme(
            backgroundTop = Color(0.08f, 0.08f, 0.09f),
            backgroundBottom = Color(0.03f, 0.03f, 0.04f),
            accent = Color.White,
            onAccent = Color.Black
        )

        fun from(image: Bitmap?): AppTheme {
            val avg = image?.let { averageColor(it) } ?: return dark

            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(
                (avg.red * 255).toInt(),
                (avg.green * 255).toInt(),
                (avg.blue * 255).toInt(),
                hsv
            )
            val hue = hsv[0] / 360f
            val saturation = hsv[1]
            val brightness = hsv[2]

            /** Accent: pump saturation enough to read as a real color, keep it mid-bright. */
            val accentSaturation = max(saturation, 0.45f)
            val accentColor = Color.hsv(
                hue * 360f,
                accentSaturation,
                max(brightness, 0.55f)
            )

            /** Background top: same hue, lower saturation, dark enough for white text. */
            val topSaturation = max(saturation * 0.55f, 0.12f)
            val topColor = Color.hsv(hue * 360f, topSaturation, 0.22f)

            /** Background bottom: near-black with a hint of the hue. */
            val bottomColor = Color.hsv(hue * 360f, topSaturation * 0.4f, 0.06f)

            return AppTheme(
                backgroundTop = topColor,
                backgroundBottom = bottomColor,
                accent = accentColor,
                onAccent = bestForeground(accentColor)
            )
        }

        /** Black or white, whichever contrasts better with the accent. */
        private fun bestForeground(color: Color): Color {
            val luminance = relativeLuminance(color)
            return if (luminance > 0.55f) Color.Black else Color.White
        }

        private fun relativeLuminance(color: Color): Float {
            fun linear(c: Float): Float =
                if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
            return 0.2126f * linear(color.red) + 0.7152f * linear(color.green) + 0.0722f * linear(color.blue)
        }

        /**
         * Saturation-weighted average. Vivid pixels dominate, gray pixels barely count,
         * so the theme follows the artwork's real color instead of a muddy brown.
         */
        private fun averageColor(bitmap: Bitmap): Color? {
            val width = min(24, bitmap.width)
            val height = min(24, bitmap.height)
            if (width <= 0 || height <= 0) return null

            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, false)

            var weightedR = 0f
            var weightedG = 0f
            var weightedB = 0f
            var totalWeight = 0f

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = scaled.getPixel(x, y)
                    val a = android.graphics.Color.alpha(pixel) / 255f
                    if (a <= 0.05f) continue

                    val r = android.graphics.Color.red(pixel).toFloat()
                    val g = android.graphics.Color.green(pixel).toFloat()
                    val b = android.graphics.Color.blue(pixel).toFloat()

                    val maxC = max(r, max(g, b))
                    val minC = min(r, min(g, b))
                    if (maxC <= 0f) continue

                    val saturation = (maxC - minC) / maxC
                    val weight = saturation * saturation + 0.05f

                    weightedR += r * weight
                    weightedG += g * weight
                    weightedB += b * weight
                    totalWeight += weight
                }
            }

            if (totalWeight <= 0f) return null
            return Color(
                red = weightedR / totalWeight / 255f,
                green = weightedG / totalWeight / 255f,
                blue = weightedB / totalWeight / 255f,
                alpha = 1f
            )
        }
    }
}