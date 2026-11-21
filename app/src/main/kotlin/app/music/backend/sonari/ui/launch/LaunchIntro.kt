package app.music.backend.sonari.ui.launch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.music.backend.sonari.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// --- Gate ---
object LaunchGate {
    private val introDone = MutableStateFlow(false)
    private val splashGone = MutableStateFlow(false)

    @Volatile internal var holdHome = false

    fun begin() { holdHome = true; splashGone.value = false; introDone.value = false }
    fun skip() { holdHome = false; introDone.value = true }
    fun onSplashGone() { splashGone.value = true }
    internal fun finish() { introDone.value = true }

    internal suspend fun awaitSplashGone(timeoutMillis: Long) {
        withTimeoutOrNull(timeoutMillis) { splashGone.first { it } }
    }
    suspend fun awaitIntro(timeoutMillis: Long = 8_000L) {
        withTimeoutOrNull(timeoutMillis) { introDone.first { it } }
    }
}

// --- Timing & easing (different from Mochy for distinct feel) ---
private const val FlightMillis = 720
private const val PreludeMillis = 950
private const val RiseDelayMillis = 60
private const val RiseMillis = 460
private const val RiseStaggerMillis = 45
private const val RiseOrders = 4
private const val RiseTotalMillis = RiseMillis + (RiseOrders - 1) * RiseStaggerMillis
private val MoveEasing = CubicBezierEasing(0.38f, 0f, 0.18f, 1f)
private val ScaleEasing = CubicBezierEasing(0.3f, 0.05f, 0.2f, 1f)
private val RiseEasing = CubicBezierEasing(0.22f, 0.08f, 0.1f, 1f)
private val RiseDistance = 56.dp
private const val EssentialsTimeoutMillis = 2_200L
private const val SplashTimeoutMillis = 1_200L
private const val BlurPeak = 6f

// Geometry
private val SplashIconSize = 288.dp
private const val SplashViewport = 235f
private val Pivot = Offset(SplashViewport / 2f, SplashViewport / 2f)
private const val IconArtSize = 235f // treat icon as filling the viewport

@Stable
class LaunchIntroState internal constructor(val plays: Boolean) {
    internal val prelude = Animatable(if (plays) 0f else 1f)
    internal val flight = Animatable(if (plays) 0f else 1f)
    internal val rise = Animatable(if (plays) 0f else 1f)

    internal var markBounds by mutableStateOf<Rect?>(null)
    internal var homeComposed by mutableStateOf(false)
    internal var landed by mutableStateOf(!plays)
    var done by mutableStateOf(!plays)
        internal set

    internal fun riseAt(order: Int): Float {
        val millis = rise.value * RiseTotalMillis - order * RiseStaggerMillis
        return RiseEasing.transform((millis / RiseMillis).coerceIn(0f, 1f))
    }
}

val LocalLaunchIntro = staticCompositionLocalOf<LaunchIntroState?> { null }

@Composable
fun rememberLaunchIntroState(plays: Boolean): LaunchIntroState = remember { LaunchIntroState(plays) }

@Composable
fun Modifier.launchMarkTarget(): Modifier {
    val state = LocalLaunchIntro.current ?: return this
    if (state.done) return this
    return this
        .onGloballyPositioned { state.markBounds = it.boundsInRoot() }
        .graphicsLayer { alpha = if (state.landed) 1f else 0f }
}

@Composable
fun Modifier.launchRise(order: Int, distance: Dp = RiseDistance): Modifier {
    val state = LocalLaunchIntro.current ?: return this
    if (state.done) return this
    return graphicsLayer {
        val e = state.riseAt(order)
        alpha = e
        translationY = (1f - e) * distance.toPx()
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

@Composable
fun ReportLaunchHomeComposed() {
    val state = LocalLaunchIntro.current ?: return
    if (state.done || state.homeComposed) return
    LaunchedEffect(state) { state.homeComposed = true }
}

@Composable
fun rememberLaunchHold(): Boolean {
    var held by remember { mutableStateOf(LaunchGate.holdHome) }
    if (held) {
        LaunchedEffect(Unit) {
            LaunchGate.awaitIntro(timeoutMillis = 15_000L)
            LaunchGate.holdHome = false
            held = false
        }
    }
    return held
}

private data class MarkPlacement(val origin: Offset, val unit: Float)

@Composable
fun LaunchIntroOverlay(state: LaunchIntroState, essentialsReady: Boolean) {
    if (state.done) return
    val ready by rememberUpdatedState(essentialsReady)
    val context = LocalContext.current
    val iconBitmap = remember { loadIcon(context) }

    LaunchedEffect(state) {
        val shownAt = SystemClock.uptimeMillis()
        val inTime = withTimeoutOrNull(EssentialsTimeoutMillis) {
            snapshotFlow { ready && state.homeComposed && state.markBounds != null }.first { it }
        } != null
        LaunchGate.awaitSplashGone(SplashTimeoutMillis)
        Timber.tag("LaunchIntro").d(
            "take-off after %dms essentials=%s ready=%b home=%b bounds=%b",
            SystemClock.uptimeMillis() - shownAt, if (inTime) "ready" else "timeout",
            ready, state.homeComposed, state.markBounds != null
        )
        withFrameNanos { }
        withFrameNanos { }
        // Prelude: aggressive shake at center, then gentle pulse
        if (state.prelude.value < 1f) {
            // Shake only on first launch when icon is centered
            val isFirstLaunch = !state.homeComposed
            val shakeProgress = state.prelude.value
            val shakeIntensity = if (isFirstLaunch && shakeProgress < 0.5f) {
                // Aggressive shake: back-and-forth motion
                val shake = sin(shakeProgress * PI.toFloat() * 6f) * 0.15f * (1f - shakeProgress * 2f)
                state.prelude.animateTo(1f, tween(PreludeMillis, easing = LinearEasing))
                shake
            } else {
                state.prelude.animateTo(1f, tween(PreludeMillis, easing = LinearEasing))
                0f
            }
        }
        val flightAt = SystemClock.uptimeMillis()
        coroutineScope {
            launch { state.rise.animateTo(1f, tween(RiseTotalMillis, RiseDelayMillis, LinearEasing)) }
            state.flight.animateTo(1f, tween(FlightMillis, easing = LinearEasing))
            state.landed = true
        }
        Timber.tag("LaunchIntro").d("landed %dms after take-off", SystemClock.uptimeMillis() - flightAt)
        LaunchGate.finish()
        state.done = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
                .drawBehind {
                    val backdrop = 1f - smoothstep(0.15f, 0.75f, state.flight.value)
                    if (backdrop > 0f) drawRect(Color.Black.copy(alpha = backdrop))
                }
        )

        // Flying icon (ripple rings removed for cleaner look)
        val density = androidx.compose.ui.platform.LocalDensity.current
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (state.landed || iconBitmap == null) return@drawBehind
                    val placement = placementAt(state, IntSize(size.width.toInt(), size.height.toInt()), density)
                    val p = state.prelude.value
                    val t = state.flight.value

                    var breathe = 1f
                    var alpha = 1f
                    if (t < 0.01f) {
                        val entrance = smoothstep(0f, 0.4f, p)
                        breathe = 0.9f + 0.1f * entrance + sin(p * PI.toFloat() * 2f) * 0.02f
                        alpha = entrance
                    } else {
                        alpha = 1f - smoothstep(0.8f, 1f, t) * 0.2f
                    }

                    val iconPx = SplashIconSize.value * density.density * placement.unit
                    val cx = placement.origin.x + iconPx / 2f
                    val cy = placement.origin.y + iconPx / 2f
                    val drawSize = iconPx * breathe
                    val left = cx - drawSize / 2f
                    val top = cy - drawSize / 2f

                    drawIntoCanvas { canvas ->
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
                        }
                        canvas.nativeCanvas.drawBitmap(iconBitmap, null, AndroidRect(left.toInt(), top.toInt(), (left + drawSize).toInt(), (top + drawSize).toInt()), paint)
                    }
                }
        )
    }
}

private fun placementAt(state: LaunchIntroState, size: IntSize, density: Density): MarkPlacement {
    val t = state.flight.value
    val splashPx = with(density) { SplashIconSize.toPx() }
    val startUnit = 1f
    val startOrigin = Offset(
        size.width / 2f - splashPx / 2f,
        size.height / 2f - splashPx / 2f
    )

    val target = state.markBounds ?: run {
        // No target yet - stay at center, shrink slightly at end
        val u = if (t > 0f) 1f - 0.3f * MoveEasing.transform(t) else 1f
        val s = splashPx * u
        return MarkPlacement(Offset(size.width / 2f - s / 2f, size.height / 2f - s / 2f), u)
    }

    val targetSize = min(target.width, target.height).coerceAtLeast(1f)
    val endPx = targetSize * 1.15f
    val endUnit = (endPx / splashPx).coerceIn(0.05f, 2f)

    if (t <= 0f) return MarkPlacement(startOrigin, startUnit)

    val moveT = MoveEasing.transform(t)
    val scaleT = ScaleEasing.transform(t)
    val unit = startUnit + (endUnit - startUnit) * scaleT
    val curPx = splashPx * unit

    val startCx = startOrigin.x + splashPx / 2f
    val startCy = startOrigin.y + splashPx / 2f
    val endCx = target.left + target.width / 2f
    val endCy = target.top + target.height / 2f

    val cx = startCx + (endCx - startCx) * moveT
    val cy = startCy + (endCy - startCy) * moveT
    // Subtle arc
    val arc = -sin(t * PI.toFloat()) * 30f * density.density

    return MarkPlacement(Offset(cx - curPx / 2f, cy - curPx / 2f + arc), unit)
}

private fun loadIcon(context: Context): Bitmap? {
    return try {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return null
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bitmap
    } catch (e: Exception) {
        Timber.tag("LaunchIntro").e(e, "Failed to load icon")
        null
    }
}

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
