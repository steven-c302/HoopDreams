package com.partyos.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

/**
 * Slowly drifting colour blobs behind every screen. Only the layer's transform animates,
 * so the GPU moves pre-drawn gradients instead of recomposing or re-rasterising each frame.
 */
@Composable
fun Backdrop(a: Color = Party.Plum, b: Color = Party.Pink.copy(alpha = 0.35f), content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "backdrop")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    Box(Modifier.fillMaxSize().background(Party.Ink)) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { translationX = (drift - 0.5f) * 160f; translationY = (0.5f - drift) * 90f; scaleX = 1.3f; scaleY = 1.3f }
                .drawBehind {
                    drawRect(Brush.radialGradient(listOf(a, Color.Transparent), Offset(size.width * 0.2f, size.height * 0.1f), size.minDimension * 0.9f))
                    drawRect(Brush.radialGradient(listOf(b, Color.Transparent), Offset(size.width * 0.85f, size.height * 0.9f), size.minDimension * 0.8f))
                },
        )
        content()
    }
}

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
    LaunchedEffect(Unit) { while (true) withFrameMillis { now.longValue = System.currentTimeMillis() } }
    return { now.longValue }
}

/** Countdown ring drawn from an absolute deadline; paused stages pass a frozen remaining time instead. */
@Composable
fun CountdownRing(deadlineAt: Long?, totalMs: Long, frozenMs: Long?, size: Dp = 88.dp, color: Color = Party.Gold) {
    val now = rememberFrameClock()
    Box(
        Modifier.size(size).drawBehind {
            val left = frozenMs ?: deadlineAt?.let { (it - now()).coerceAtLeast(0) } ?: 0L
            val frac = if (totalMs <= 0) 0f else (left.toFloat() / totalMs).coerceIn(0f, 1f)
            val stroke = this.size.minDimension * 0.1f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Party.Line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(if (left < 5_000) Party.Red else color, -90f, 360f * frac, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
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
