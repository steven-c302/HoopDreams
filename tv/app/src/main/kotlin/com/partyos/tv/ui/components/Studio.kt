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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.partyos.tv.ui.theme.Party
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * The Studio: set pieces shared by every screen. All art is drawn in code with brushes and paths
 * built once per size (drawWithCache). Motion is either a graphicsLayer transform or a draw-phase
 * read of the frame clock, never a recomposition (spec §9).
 */

/**
 * The stage: velvet curtain wall, slow sunburst, two swaying spotlights and a floor glow.
 * [floor] tints the glow and [rays] the sunburst, so each game can bring its own light.
 */
@Composable
fun StudioBackdrop(floor: Color = Party.Pink, rays: Color = Party.Gold, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "studio")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "spin")
    val sway by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(7_000), RepeatMode.Reverse), label = "sway")
    Box(Modifier.fillMaxSize().background(Party.Ink)) {
        // Curtain wall: mirrored velvet folds fading into the dark floor.
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val folds = Brush.horizontalGradient(listOf(Party.Velvet, Party.VelvetHi.copy(alpha = 0.75f), Party.Velvet), 0f, size.width / 14f, TileMode.Mirror)
                val fade = Brush.verticalGradient(listOf(Color.Transparent, Party.Ink), size.height * 0.15f, size.height * 0.72f)
                onDrawBehind {
                    drawRect(folds, alpha = 0.55f)
                    drawRect(fade)
                }
            },
        )
        // Sunburst behind centre stage, turning slowly.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { rotationZ = spin; scaleX = 2.2f; scaleY = 2.2f; alpha = 0.07f }
                .drawWithCache {
                    val c = Offset(size.width / 2, size.height / 2)
                    val r = size.maxDimension
                    val n = 24
                    val path = Path().apply {
                        for (i in 0 until n) {
                            val a0 = (i * 2.0 * PI / n).toFloat()
                            val a1 = a0 + (PI / n).toFloat()
                            moveTo(c.x, c.y)
                            lineTo(c.x + r * cos(a0), c.y + r * sin(a0))
                            lineTo(c.x + r * cos(a1), c.y + r * sin(a1))
                            close()
                        }
                    }
                    onDrawBehind { drawPath(path, rays) }
                },
        )
        Spotlight(Modifier.graphicsLayer { rotationZ = 8f + sway * 7f; transformOrigin = TransformOrigin(0.12f, 0f) }, fromLeft = true)
        Spotlight(Modifier.graphicsLayer { rotationZ = -8f + sway * 6f; transformOrigin = TransformOrigin(0.88f, 0f) }, fromLeft = false)
        // Floor glow and vignette.
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val glow = Brush.radialGradient(listOf(floor.copy(alpha = 0.32f), Color.Transparent), Offset(size.width / 2, size.height * 1.05f), size.width * 0.55f)
                val vignette = Brush.radialGradient(listOf(Color.Transparent, Party.Ink.copy(alpha = 0.75f)), Offset(size.width / 2, size.height / 2), size.maxDimension * 0.75f)
                onDrawBehind {
                    drawRect(glow)
                    drawRect(vignette)
                }
            },
        )
        content()
    }
}

@Composable
private fun Spotlight(modifier: Modifier, fromLeft: Boolean) {
    Box(
        modifier.fillMaxSize().drawWithCache {
            val x = if (fromLeft) size.width * 0.12f else size.width * 0.88f
            val beam = Path().apply {
                moveTo(x - size.width * 0.015f, 0f)
                lineTo(x + size.width * 0.015f, 0f)
                lineTo(x + size.width * 0.2f, size.height * 1.1f)
                lineTo(x - size.width * 0.2f, size.height * 1.1f)
                close()
            }
            val light = Brush.verticalGradient(listOf(Party.Cream.copy(alpha = 0.22f), Party.Cream.copy(alpha = 0.03f), Color.Transparent), 0f, size.height)
            onDrawBehind { drawPath(beam, light) }
        },
    )
}

/**
 * A plate ringed with chasing marquee bulbs. The chase reads the frame clock in the draw phase,
 * so only this node redraws.
 */
@Composable
fun MarqueeFrame(
    modifier: Modifier = Modifier,
    plate: Color = Party.Velvet,
    rim: Color = Party.Gold,
    spacing: Dp = 22.dp,
    bulb: Dp = 4.5.dp,
    radius: Dp = 22.dp,
    contentPadding: Dp = 26.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val now = rememberFrameClock()
    Box(
        modifier.drawWithCache {
            val inset = bulb.toPx() * 2.4f
            val pts = perimeter(size, inset, spacing.toPx())
            val r = bulb.toPx()
            val corner = CornerRadius(radius.toPx())
            onDrawBehind {
                drawRoundRect(plate, cornerRadius = corner)
                drawRoundRect(rim, cornerRadius = corner, style = Stroke(r * 0.9f))
                val step = ((now() / 170L) % 3L).toInt()
                pts.forEachIndexed { i, p ->
                    if (i % 3 == step) {
                        drawCircle(rim.copy(alpha = 0.28f), r * 2.3f, p)
                        drawCircle(Party.Cream, r, p)
                    } else {
                        drawCircle(Party.BulbOff, r * 0.85f, p)
                    }
                }
            }
        }.padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private fun perimeter(size: Size, inset: Float, spacing: Float): List<Offset> {
    val tl = Offset(inset, inset)
    val tr = Offset(size.width - inset, inset)
    val br = Offset(size.width - inset, size.height - inset)
    val bl = Offset(inset, size.height - inset)
    val out = ArrayList<Offset>()
    for ((a, b) in listOf(tl to tr, tr to br, br to bl, bl to tl)) {
        val n = max(1, ((b - a).getDistance() / spacing).roundToInt())
        for (i in 0 until n) out += a + (b - a) * (i / n.toFloat())
    }
    return out
}

/**
 * A chunky stage prop: flat fill, thick outline and a hard offset shadow (two rects, no blur).
 * [tilt] sets it slightly askew so cards feel like objects rather than panels.
 */
fun Modifier.prop(
    fill: Color = Party.Cream,
    edge: Color = Party.Ink,
    shadow: Color = Party.Gold,
    radius: Dp = 18.dp,
    offset: Dp = 7.dp,
    stroke: Dp = 3.dp,
    tilt: Float = 0f,
): Modifier = this
    .then(if (tilt != 0f) Modifier.graphicsLayer { rotationZ = tilt } else Modifier)
    .drawBehind {
        val cr = CornerRadius(radius.toPx())
        val o = offset.toPx()
        if (o > 0f) drawRoundRect(shadow, Offset(o, o), size, cr)
        drawRoundRect(fill, cornerRadius = cr)
        if (stroke.value > 0f) {
            val s = stroke.toPx()
            drawRoundRect(edge, Offset(s / 2, s / 2), Size(size.width - s, size.height - s), cr, style = Stroke(s))
        }
    }

/** The ON AIR light: glows and breathes while the clock runs, dark otherwise. */
@Composable
fun OnAirSign(lit: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "onair")
    val breathe by t.animateFloat(0.72f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "breathe")
    NeonSign("ON AIR", lit, Party.Red, modifier.graphicsLayer { alpha = if (lit) breathe else 1f })
}

/** A lit sign: neon fill with a soft halo when on, dark glass with dim letters when off. */
@Composable
fun NeonSign(
    text: String, lit: Boolean, color: Color, modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(fontFamily = Party.Display, fontSize = 18.sp, letterSpacing = 2.sp),
) {
    Box(
        modifier
            .drawBehind {
                val cr = CornerRadius(10.dp.toPx())
                if (lit) {
                    val halo = 8.dp.toPx()
                    drawRoundRect(color.copy(alpha = 0.25f), Offset(-halo, -halo), Size(size.width + halo * 2, size.height + halo * 2), CornerRadius(cr.x + halo))
                    drawRoundRect(color, cornerRadius = cr)
                } else {
                    drawRoundRect(Party.Night, cornerRadius = cr)
                    drawRoundRect(color.copy(alpha = 0.35f), cornerRadius = cr, style = Stroke(2.dp.toPx()))
                }
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) { Text(text, style = style, color = if (lit) Party.Ink else color.copy(alpha = 0.4f)) }
}

/** A rubber stamp slapped onto a card: FAKE!, THE TRUTH, WINNER. */
@Composable
fun Stamp(text: String, color: Color, modifier: Modifier = Modifier, tilt: Float = -10f, size: Int = 26, delayMs: Int = 0) {
    Slam(modifier, delayMs = delayMs, from = 2.4f, tilt = tilt * 2) {
        Box(
            Modifier
                .graphicsLayer { rotationZ = tilt }
                .drawBehind {
                    val s = 3.dp.toPx()
                    drawRoundRect(color, Offset(s / 2, s / 2), Size(this.size.width - s, this.size.height - s), CornerRadius(8.dp.toPx()), style = Stroke(s))
                }
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) { Text(text, style = TextStyle(fontFamily = Party.Display, fontSize = size.sp, letterSpacing = 1.sp), color = color) }
    }
}

private val confettiColors = listOf(Party.Gold, Party.Pink, Party.Mint, Party.Sky, Party.Cream, Party.Orange)

private class Bit(val x0: Float, val vx: Float, val vy: Float, val spin: Float, val w: Float, val h: Float, val color: Color, val delay: Float)

/**
 * Two confetti cannons fired from the bottom corners when this enters composition. Particles are a
 * pure function of elapsed time, drawn on one canvas; nothing is stored per frame.
 */
@Composable
fun Confetti(modifier: Modifier = Modifier, count: Int = 120, seed: Int = 7, durationMs: Long = 4_200) {
    val elapsed = rememberElapsedMs()
    val bits = remember(seed, count) {
        val rnd = Random(seed)
        List(count) { i ->
            val left = i % 2 == 0
            Bit(
                x0 = if (left) 0.02f else 0.98f,
                vx = (if (left) 1f else -1f) * (0.18f + rnd.nextFloat() * 0.32f),
                vy = -(1.05f + rnd.nextFloat() * 0.55f),
                spin = (rnd.nextFloat() - 0.5f) * 1_400f,
                w = 7f + rnd.nextFloat() * 7f,
                h = 4f + rnd.nextFloat() * 4f,
                color = confettiColors[i % confettiColors.size],
                delay = rnd.nextFloat() * 0.35f,
            )
        }
    }
    Box(
        modifier.fillMaxSize().drawBehind {
            val t = elapsed() / 1000f
            if (t * 1000 > durationMs) return@drawBehind
            val g = 1.3f // screen heights per second²
            val d = density
            bits.forEach { b ->
                val tt = t - b.delay
                if (tt <= 0f) return@forEach
                val drag = 1f / (1f + tt * 0.9f)
                val x = (b.x0 + b.vx * tt * drag) * size.width
                val y = (1.02f + b.vy * tt + 0.5f * g * tt * tt) * size.height
                if (y > size.height * 1.05f && tt > 0.5f) return@forEach
                val alpha = (1f - (tt - 2.6f).coerceAtLeast(0f)).coerceIn(0f, 1f)
                rotate(b.spin * tt, Offset(x, y)) {
                    drawRect(b.color.copy(alpha = alpha), Offset(x - b.w * d / 2, y - b.h * d / 2), Size(b.w * d, b.h * d))
                }
            }
        },
    )
}

/** A small caps label on a pill, for badges like ROUND 2 OF 5. */
@Composable
fun Badge(text: String, fill: Color, modifier: Modifier = Modifier, ink: Color = Party.Ink) {
    Text(
        text, color = ink,
        style = TextStyle(fontFamily = Party.Display, fontSize = 16.sp, letterSpacing = 1.sp),
        modifier = modifier.background(fill, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
