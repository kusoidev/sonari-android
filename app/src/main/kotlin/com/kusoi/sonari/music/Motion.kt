package com.kusoi.sonari.music

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Every timing curve the iOS app uses, named. SwiftUI spring(response:
 * dampingFraction:) maps to Compose stiffness/damping, so these are tuned to
 * land on the same settle time rather than copied as raw numbers.
 *
 * Generic over T so the same spec serves Float, Dp and Color animations.
 */
object Motion {
    /** Tab bar selection and chips: spring(response: 0.3, damping: 0.75). */
    fun <T> chip() = spring<T>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)

    /** Player header morph: spring(response: 0.38, damping: 0.82). */
    fun <T> header() = spring<T>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    /** Karaoke line scroll: spring(response: 0.55, damping: 0.82). */
    fun <T> lyricScroll() = spring<T>(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)

    /** Line scale as it becomes current: spring(response: 0.45, damping: 0.78). */
    fun <T> lyricScale() = spring<T>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)

    /** Seek knob grow on drag: spring(response: 0.25). */
    fun <T> knob() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessHigh)

    /** Background tint crossfade: easeInOut 0.6s. */
    fun <T> theme() = tween<T>(durationMillis = 600, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f))

    /** Small fades like the search Cancel button: easeOut 0.2s. */
    fun <T> quickFade() = tween<T>(durationMillis = 200)

    /** Focus ring on text fields: easeOut 0.2s. */
    fun <T> focusRing() = tween<T>(durationMillis = 200)
}
