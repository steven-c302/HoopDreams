package com.partyos.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The show's motion vocabulary. Every entrance animates only inside graphicsLayer, so a moving
 * element never recomposes or re-measures (spec §9). Springs overshoot on purpose: props land.
 */
object Motion {
    val slam = spring<Float>(dampingRatio = 0.42f, stiffness = 520f)
    val pop = spring<Float>(dampingRatio = 0.5f, stiffness = 700f)
    val deal = spring<Float>(dampingRatio = 0.62f, stiffness = 340f)
    const val STAGGER_MS = 70
}

/** Runs 0→1 on a spring once, after [delayMs]. Values past 1 are the overshoot. */
@Composable
private fun rememberEntrance(delayMs: Int, spec: AnimationSpec<Float>): Animatable<Float, *> {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        p.animateTo(1f, spec)
    }
    return p
}

/** Big and tilted, then lands with a squash. For headlines, names and stamps. */
@Composable
fun Slam(modifier: Modifier = Modifier, delayMs: Int = 0, from: Float = 1.8f, tilt: Float = -8f, content: @Composable () -> Unit) {
    val p = rememberEntrance(delayMs, Motion.slam)
    Box(
        modifier.graphicsLayer {
            val v = p.value
            val s = from + (1f - from) * v
            scaleX = s; scaleY = s
            rotationZ = tilt * (1f - v)
            alpha = (v * 4f).coerceIn(0f, 1f)
        },
    ) { content() }
}

/** Grows from small with a bounce. For chips, avatars and ticks. */
@Composable
fun Pop(modifier: Modifier = Modifier, delayMs: Int = 0, content: @Composable () -> Unit) {
    val p = rememberEntrance(delayMs, Motion.pop)
    Box(
        modifier.graphicsLayer {
            val s = 0.4f + 0.6f * p.value
            scaleX = s; scaleY = s
            alpha = (p.value * 2f).coerceIn(0f, 1f)
        },
    ) { content() }
}

/** Flies up from below and settles its tilt, like a card dealt onto the table. */
@Composable
fun Deal(modifier: Modifier = Modifier, delayMs: Int = 0, tilt: Float = 10f, content: @Composable () -> Unit) {
    val p = rememberEntrance(delayMs, Motion.deal)
    Box(
        modifier.graphicsLayer {
            val v = p.value
            translationY = (1f - v) * size.height * 1.2f
            rotationZ = tilt * (1f - v)
            alpha = (v * 3f).coerceIn(0f, 1f)
        },
    ) { content() }
}

/** Deals children in one after another. */
@Composable
fun Stagger(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    Deal(modifier, delayMs = index * Motion.STAGGER_MS, tilt = if (index % 2 == 0) 6f else -6f, content = content)

/** A slow idle sway for things that are waiting. */
@Composable
fun Wobble(modifier: Modifier = Modifier, degrees: Float = 1.5f, periodMs: Int = 2_400, content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "wobble")
    val a by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    Box(modifier.graphicsLayer { rotationZ = a * degrees }) { content() }
}

/**
 * Counts from [from] to [to]. The shown number moves in at most ~24 steps, so the text
 * recomposes a couple of dozen times rather than every frame. [onStep] fires per step (tick sounds).
 */
@Composable
fun CountUp(
    from: Int, to: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier,
    delayMs: Int = 0, durationMs: Int = 900, format: (Int) -> String = { "%,d".format(it) }, onStep: (Int) -> Unit = {},
) {
    val v = remember(from, to) { Animatable(from.toFloat()) }
    val step = max(1, abs(to - from) / 24)
    val shown by remember(from, to) {
        derivedStateOf { if (v.value == to.toFloat()) to else from + ((v.value - from) / step).roundToInt() * step }
    }
    LaunchedEffect(from, to) {
        if (delayMs > 0) delay(delayMs.toLong())
        v.animateTo(to.toFloat(), tween(durationMs, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(shown) { if (shown != from) onStep(shown) }
    Text(format(shown), style = style, color = color, modifier = modifier)
}
