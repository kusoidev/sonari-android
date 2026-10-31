package com.kusoi.sonari.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow

/**
 * Port of Sonari/Models/AppTheme.swift. Saturation-weighted average of a 24x24
 * downscale, then HSB maths into a gradient plus an accent. No bitmap work
 * beyond the sample, so it can run on any dispatcher.
 */
object ThemeSampler {

    fun from(bytes: ByteArray?): AppTheme {
        if (bytes == null) return AppTheme.Dark
        val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return AppTheme.Dark
        val small = Bitmap.createScaledBitmap(full, 24, 24, true)
        val avg = averageColor(small)
        if (small !== full) small.recycle()
        full.recycle()
        if (avg == null) return AppTheme.Dark

        val (hue, saturation, brightness) = rgbToHsb(avg)
        // The accent is the brand violet pulled toward the sleeve hue. Purely
        // sampling it turned pale sleeves into a near-white button, which
        // reads as disabled. Hue follows the art, everything else is clamped.
        val accentHue = lerpHue(0.755f, if (saturation < 0.12f) 0.755f else hue, 0.45f)
        val accent = hsbToRgb(accentHue, 0.70f, 0.88f)
        // The page is a violet room no matter what the sleeve looks like, so
        // the sampled hue only shifts it. A grey sleeve lands on the house
        // violet instead of a dead neutral.
        val tintSaturation = if (saturation < 0.12f) 0.62f else saturation.coerceAtMost(0.9f)
        val tintHue = if (saturation < 0.12f) 0.72f else hue
        val top = hsbToRgb(tintHue, maxOf(tintSaturation * 0.75f, 0.42f), 0.30f)
        val bottom = hsbToRgb(tintHue, maxOf(tintSaturation * 0.4f, 0.20f), 0.08f)

        return AppTheme(
            backgroundTop = top.toColor(),
            backgroundBottom = bottom.toColor(),
            accent = accent.toColor(),
            onAccent = if (relativeLuminance(accent) > 0.55f) SonariText.onAccent else SonariText.primary
        )
    }

    private data class Rgb(val r: Float, val g: Float, val b: Float)

    /** Shortest-path hue blend, both in 0..1. */
    private fun lerpHue(a: Float, b: Float, t: Float): Float {
        var d = b - a
        if (d > 0.5f) d -= 1f
        if (d < -0.5f) d += 1f
        var h = a + d * t
        if (h < 0f) h += 1f
        if (h > 1f) h -= 1f
        return h
    }

    private fun relativeLuminance(rgb: Rgb): Float {
        fun linear(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * linear(rgb.r) + 0.7152f * linear(rgb.g) + 0.0722f * linear(rgb.b)
    }

    private fun averageColor(bitmap: Bitmap): Rgb? {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var wr = 0f
        var wg = 0f
        var wb = 0f
        var total = 0f

        for (p in pixels) {
            val a = (p ushr 24 and 0xFF) / 255f
            if (a <= 0.05f) continue
            val r = (p ushr 16 and 0xFF).toFloat()
            val g = (p ushr 8 and 0xFF).toFloat()
            val b = (p and 0xFF).toFloat()
            val maxC = maxOf(r, g, b)
            if (maxC <= 0f) continue
            val minC = minOf(r, g, b)
            // Vivid pixels dominate; grey pixels barely count.
            val saturation = (maxC - minC) / maxC
            val weight = saturation * saturation + 0.05f
            wr += r * weight
            wg += g * weight
            wb += b * weight
            total += weight
        }
        if (total <= 0f) return null
        return Rgb(wr / total / 255f, wg / total / 255f, wb / total / 255f)
    }

    private fun rgbToHsb(rgb: Rgb): Triple<Float, Float, Float> {
        val maxC = maxOf(rgb.r, rgb.g, rgb.b)
        val minC = minOf(rgb.r, rgb.g, rgb.b)
        val delta = maxC - minC
        var h = 0f
        if (delta > 0f) {
            h = when (maxC) {
                rgb.r -> ((rgb.g - rgb.b) / delta).mod(6f)
                rgb.g -> (rgb.b - rgb.r) / delta + 2f
                else -> (rgb.r - rgb.g) / delta + 4f
            }
            h /= 6f
            if (h < 0f) h += 1f
        }
        val s = if (maxC == 0f) 0f else delta / maxC
        return Triple(h, s, maxC)
    }

    private fun hsbToRgb(h: Float, s: Float, v: Float): Rgb {
        val c = v * s
        val hPrime = h * 6f
        val x = c * (1f - abs(hPrime.mod(2f) - 1f))
        val m = v - c
        val (r, g, b) = when {
            hPrime < 1f -> Triple(c, x, 0f)
            hPrime < 2f -> Triple(x, c, 0f)
            hPrime < 3f -> Triple(0f, c, x)
            hPrime < 4f -> Triple(0f, x, c)
            hPrime < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Rgb(r + m, g + m, b + m)
    }

    private fun Rgb.toColor() = Color(r, g, b)
}
