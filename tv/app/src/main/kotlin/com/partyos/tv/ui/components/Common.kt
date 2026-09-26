package com.partyos.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.partyos.tv.ui.theme.Party
import partyos.engine.Avatar

fun Avatar.colorValue(): Color = runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrDefault(Party.Muted)

@Composable
fun AvatarDot(avatar: Avatar, size: Dp = 44.dp, dim: Boolean = false) {
    Box(
        Modifier.size(size).graphicsLayer { alpha = if (dim) 0.35f else 1f }.background(avatar.colorValue(), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(avatar.emoji, fontSize = (size.value * 0.5f).sp, textAlign = TextAlign.Center) }
}

/** Frame clock shared by countdown widgets; read only inside draw lambdas so ticking never recomposes. */
@Composable
fun rememberFrameClock(): () -> Long {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) withInfiniteAnimationFrameMillis { now.longValue = System.currentTimeMillis() } }
    return { now.longValue }
}

/** Milliseconds since this first composed, from the frame clock (deterministic under test clocks). */
@Composable
fun rememberElapsedMs(): () -> Long {
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { elapsed.longValue = it - start }
    }
    return { elapsed.longValue }
}

/**
 * The game-show clock: an ink dial with a thick gold ring that drains. In the last five seconds it
 * turns red and throbs (a graphicsLayer scale read from the frame clock, so it never recomposes).
 */
@Composable
fun CountdownRing(deadlineAt: Long?, totalMs: Long, frozenMs: Long?, size: Dp = 88.dp, color: Color = Party.Gold) {
    val now = rememberFrameClock()
    fun left() = frozenMs ?: deadlineAt?.let { (it - now()).coerceAtLeast(0) } ?: 0L
    Box(
        Modifier.size(size)
            .graphicsLayer {
                val l = left()
                val s = if (l in 1 until 5_000) 1f + 0.07f * (1f - (l % 1000) / 1000f) else 1f
                scaleX = s; scaleY = s
            }
            .drawBehind {
                val l = left()
                val frac = if (totalMs <= 0) 0f else (l.toFloat() / totalMs).coerceIn(0f, 1f)
                val stroke = this.size.minDimension * 0.13f
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawCircle(Party.Ink, this.size.minDimension / 2)
                drawArc(Party.BulbOff, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(if (l < 5_000) Party.Red else color, -90f, 360f * frac, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            },
        contentAlignment = Alignment.Center,
    ) { CountdownText(deadlineAt, frozenMs, now) }
}

@Composable
private fun CountdownText(deadlineAt: Long?, frozenMs: Long?, now: () -> Long) {
    // Recomposes once per second at most: the displayed number is derived, not the frame time.
    val seconds by androidx.compose.runtime.remember(deadlineAt, frozenMs) {
        androidx.compose.runtime.derivedStateOf { ((frozenMs ?: deadlineAt?.let { it - now() } ?: 0L).coerceAtLeast(0) + 999) / 1000 }
    }
    Text("$seconds", fontSize = 30.sp, fontFamily = Party.Display, color = Party.Text)
}
