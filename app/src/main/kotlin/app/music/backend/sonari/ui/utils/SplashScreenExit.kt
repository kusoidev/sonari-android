package app.music.backend.sonari.ui.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.splashscreen.SplashScreen
import app.music.backend.sonari.ui.launch.LaunchGate
import timber.log.Timber

private const val HandOffFadeMillis = 90L
private const val PlainFadeMillis = 200L

private val FadeEasing = PathInterpolator(0.4f, 0f, 1f, 1f)

fun SplashScreen.handOffToLaunchIntro(introPlays: () -> Boolean) {
    setOnExitAnimationListener { provider ->
        val root = provider.view
        val icon = provider.iconView
        val at = IntArray(2).also(icon::getLocationInWindow)
        Timber.tag("LaunchIntro").d(
            "splash icon %dx%d at %d,%d in a %dx%d window",
            icon.width, icon.height, at[0], at[1], root.width, root.height,
        )

        ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f).apply {
            duration = if (introPlays()) HandOffFadeMillis else PlainFadeMillis
            interpolator = FadeEasing
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    provider.remove()
                    LaunchGate.onSplashGone()
                }
            })
            start()
        }
    }
}
