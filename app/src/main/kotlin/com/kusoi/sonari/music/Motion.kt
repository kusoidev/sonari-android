package com.kusoi.sonari.music

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * One motion language for the whole app. Springs where something lands,
 * eased tweens where something fades. Nothing bounces past 0.62 damping;
 * overshoot reads as a toy.
 */
object Motion {
    /** Tab pill and chips: quick, slightly springy. */
    fun <T> chip() = spring<T>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)

    /** Player header morph. */
    fun <T> header() = spring<T>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)

    /** Lyrics list scroll: long enough to read as a glide, no overshoot. */
    fun <T> lyricScroll() = spring<T>(dampingRatio = 0.92f, stiffness = Spring.StiffnessLow)

    /** Line scale and alpha as a line becomes current. */
    fun <T> lyricScale() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    /** Seek knob grow on drag. */
    fun <T> knob() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessHigh)

    /** Background tint crossfade, matched to the sleeve load. */
    fun <T> theme() = tween<T>(durationMillis = 900, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))

    /** Small fades. */
    fun <T> quickFade() = tween<T>(durationMillis = 220, easing = FastOutSlowInEasing)

    /** Focus ring on text fields. */
    fun <T> focusRing() = tween<T>(durationMillis = 200)

    /** Queue slide in and out of the player. */
    fun <T> queueEnter() = spring<T>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

    /** Artwork card lift when the play screen opens. */
    fun <T> cardEnter() = spring<T>(dampingRatio = 0.88f, stiffness = Spring.StiffnessLow)

    /** Crossfade between tabs. Linear so it reads as a dissolve, not a move. */
    fun <T> tabFade() = tween<T>(durationMillis = 180, easing = LinearEasing)

    /** Floating nav bar ride up on launch. */
    fun <T> navEnter() = spring<T>(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)
}
