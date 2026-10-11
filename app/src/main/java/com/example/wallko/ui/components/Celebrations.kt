package com.example.wallko.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.wallko.ui.theme.WallkoColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Lightweight Compose-only celebrations (Canvas + one Animatable each, <= 40 particles).
 *
 * Each overlay plays once whenever [trigger] changes to a new non-null value (including the first
 * composition if it starts non-null). Pass null for "nothing to celebrate yet"; a counter or the
 * id of the thing being celebrated makes a good trigger. Overlays are decorative and draw nothing
 * while idle; they do not intercept touches and expose no semantics. Confetti is skipped entirely when
 * the user has turned animations off ([rememberReducedMotion]).
 */

private const val MaxParticles = 40

private val ConfettiColors = listOf(
    WallkoColors.Sunflower,
    WallkoColors.Berry,
    WallkoColors.Sky,
    WallkoColors.Grass,
    WallkoColors.Lavender,
    WallkoColors.Peach,
)

/** 0 -> 1 over [durationMillis] each time [trigger] changes to non-null; rests at 1 (idle). */
@Composable
private fun rememberBurstProgress(trigger: Any?, durationMillis: Int): State<Float> {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger != null) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
        }
    }
    return progress.asState()
}

private fun fadeOut(p: Float, from: Float = 0.7f): Float = if (p < from) 1f else ((1f - p) / (1f - from)).coerceIn(0f, 1f)

// ---------------------------------------------------------------------------------------------
// Confetti burst

private class ConfettiPiece(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val startRotation: Float,
    val wobble: Float,
    val color: Color,
    val width: Float,
    val height: Float,
    val round: Boolean,
)

private fun confettiPieces(random: Random, count: Int, colors: List<Color>): List<ConfettiPiece> =
    List(count.coerceIn(0, MaxParticles)) {
        ConfettiPiece(
            // Mostly upward cone so the burst arcs up then falls.
            angle = (-PI / 2 + (random.nextFloat() - 0.5f) * 2.6f).toFloat(),
            speed = 0.6f + random.nextFloat() * 0.8f,
            spin = (random.nextFloat() - 0.5f) * 900f,
            startRotation = random.nextFloat() * 360f,
            wobble = random.nextFloat() * 6f,
            color = colors[random.nextInt(colors.size)],
            width = 6f + random.nextFloat() * 5f,
            height = 4f + random.nextFloat() * 4f,
            round = random.nextInt(3) == 0,
        )
    }

/**
 * Confetti burst from [origin] (fractions of the overlay's width/height).
 * Put it in a Box over the content with `Modifier.matchParentSize()` / `fillMaxSize()`.
 */
@Composable
fun ConfettiBurst(
    trigger: Any?,
    modifier: Modifier = Modifier,
    origin: Offset = Offset(0.5f, 0.45f),
    particleCount: Int = 36,
    colors: List<Color> = ConfettiColors,
    durationMillis: Int = 1_600,
) {
    if (rememberReducedMotion()) return
    val progress = rememberBurstProgress(trigger, durationMillis)
    val pieces = remember(trigger, particleCount, colors) {
        confettiPieces(Random(trigger.hashCode()), particleCount, colors.ifEmpty { ConfettiColors })
    }
    ConfettiCanvas(pieces, origin, durationMillis, { progress.value }, modifier)
}

@Composable
private fun ConfettiCanvas(
    pieces: List<ConfettiPiece>,
    origin: Offset,
    durationMillis: Int,
    progress: () -> Float,
    modifier: Modifier,
) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val p = progress()
        if (p <= 0f || p >= 1f) return@Canvas
        val m = size.minDimension
        val t = p * durationMillis / 1_000f
        val gravity = 1.4f
        val alpha = fadeOut(p)
        val ox = origin.x * size.width
        val oy = origin.y * size.height
        val unit = 1.dp.toPx()
        pieces.forEach { piece ->
            val x = ox + cos(piece.angle) * piece.speed * t * m + sin(t * 6f + piece.wobble) * 0.02f * m
            val y = oy + sin(piece.angle) * piece.speed * t * m + 0.5f * gravity * t * t * m
            translate(x, y) {
                rotate(piece.startRotation + piece.spin * t, pivot = Offset.Zero) {
                    if (piece.round) {
                        drawCircle(piece.color, radius = piece.height * 0.7f * unit, center = Offset.Zero, alpha = alpha)
                    } else {
                        val w = piece.width * unit
                        val h = piece.height * unit
                        drawRoundRect(
                            color = piece.color,
                            topLeft = Offset(-w / 2f, -h / 2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(h * 0.3f),
                            alpha = alpha,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Lavender sparkle swirl (revive / elixir)

private class SwirlSparkle(
    val startRadius: Float,
    val startAngle: Float,
    val delay: Float,
    val size: Float,
    val color: Color,
)

private fun swirlSparkles(random: Random, count: Int): List<SwirlSparkle> {
    val colors = listOf(WallkoColors.Lavender, Color.White, Tones.LavenderDeep, WallkoColors.Lavender)
    return List(count.coerceIn(0, MaxParticles)) {
        SwirlSparkle(
            startRadius = 0.32f + random.nextFloat() * 0.2f,
            startAngle = random.nextFloat() * 2f * PI.toFloat(),
            delay = random.nextFloat() * 0.3f,
            size = 4f + random.nextFloat() * 6f,
            color = colors[random.nextInt(colors.size)],
        )
    }
}

/** Lavender sparkles spiralling into [center] (fractions of the overlay size), ending in a soft glow. */
@Composable
fun SparkleSwirl(
    trigger: Any?,
    modifier: Modifier = Modifier,
    center: Offset = Offset(0.5f, 0.5f),
    particleCount: Int = 24,
    durationMillis: Int = 1_400,
) {
    val progress = rememberBurstProgress(trigger, durationMillis)
    val sparkles = remember(trigger, particleCount) { swirlSparkles(Random(trigger.hashCode()), particleCount) }
    SwirlCanvas(sparkles, center, { progress.value }, modifier)
}

@Composable
private fun SwirlCanvas(sparkles: List<SwirlSparkle>, center: Offset, progress: () -> Float, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val p = progress()
        if (p <= 0f || p >= 1f) return@Canvas
        val m = size.minDimension
        val c = Offset(center.x * size.width, center.y * size.height)
        val unit = 1.dp.toPx()
        sparkles.forEach { s ->
            val q = ((p - s.delay) / 0.7f).coerceIn(0f, 1f)
            if (q <= 0f || q >= 1f) return@forEach
            val eased = q * q * (3f - 2f * q)
            val r = s.startRadius * m * (1f - eased)
            val a = s.startAngle + eased * 3f * PI.toFloat()
            val twinkle = 0.6f + 0.4f * sin(q * PI.toFloat() * 6f)
            drawSparkleStar(
                center = c + Offset(cos(a) * r, sin(a) * r),
                radius = s.size * unit * twinkle,
                color = s.color,
                alpha = sin(q * PI.toFloat()).coerceIn(0f, 1f),
            )
        }
        // Soft glow as the sparkles arrive.
        if (p > 0.6f) {
            val g = (p - 0.6f) / 0.4f
            drawCircle(
                color = WallkoColors.Lavender,
                radius = m * (0.05f + 0.15f * g),
                center = c,
                alpha = 0.5f * sin(g * PI.toFloat()),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Falling water drops (the daily "drinking" moment)

private class FallingDrop(val x: Float, val delay: Float, val size: Float)

private fun fallingDrops(random: Random, count: Int, lanes: List<Float>): List<FallingDrop> =
    List(count.coerceIn(0, MaxParticles)) { i ->
        val x = if (lanes.isNotEmpty()) {
            lanes[i % lanes.size] + (random.nextFloat() - 0.5f) * 0.04f
        } else {
            0.08f + random.nextFloat() * 0.84f
        }
        FallingDrop(x = x, delay = random.nextFloat() * 0.5f, size = 10f + random.nextFloat() * 6f)
    }

/**
 * Blue drops falling from the top edge to [landFraction] of the height, each ending in a small
 * splash ring.
 *
 * @param lanes optional x positions (fractions of width), e.g. the centres of the pots; drops are
 * spread over these lanes round-robin. Empty = random positions.
 */
@Composable
fun FallingDrops(
    trigger: Any?,
    modifier: Modifier = Modifier,
    lanes: List<Float> = emptyList(),
    landFraction: Float = 0.8f,
    dropCount: Int = 16,
    durationMillis: Int = 1_800,
) {
    val progress = rememberBurstProgress(trigger, durationMillis)
    val drops = remember(trigger, dropCount, lanes) { fallingDrops(Random(trigger.hashCode()), dropCount, lanes) }
    FallingDropsCanvas(drops, landFraction, { progress.value }, modifier)
}

@Composable
private fun FallingDropsCanvas(drops: List<FallingDrop>, landFraction: Float, progress: () -> Float, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val p = progress()
        if (p <= 0f || p >= 1f) return@Canvas
        val unit = 1.dp.toPx()
        val landY = size.height * landFraction.coerceIn(0f, 1f)
        drops.forEach { d ->
            val fall = ((p - d.delay) / 0.4f).coerceIn(0f, 1f)
            if (fall <= 0f) return@forEach
            val x = d.x * size.width
            val h = d.size * unit
            if (fall < 1f) {
                // Ease-in: drops accelerate as they fall.
                val y = -h + (landY + h) * fall * fall
                drawWaterDrop(Offset(x, y - h), h)
            } else {
                val splash = ((p - d.delay - 0.4f) / 0.12f).coerceIn(0f, 1f)
                if (splash < 1f) {
                    drawOval(
                        color = WallkoColors.SkyDeep,
                        topLeft = Offset(x - h * (0.4f + splash), landY - h * 0.2f * (1f + splash)),
                        size = Size(h * 2f * (0.4f + splash), h * 0.4f * (1f + splash)),
                        alpha = 1f - splash,
                        style = Stroke(width = 2f * unit),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews (frozen mid-animation, since LaunchedEffect doesn't run in static previews)

@ComponentPreviews
@Composable
private fun CelebrationsPreview() {
    val confetti = remember { confettiPieces(Random(1), 36, ConfettiColors) }
    val swirl = remember { swirlSparkles(Random(2), 24) }
    val drops = remember { fallingDrops(Random(3), 16, listOf(0.25f, 0.5f, 0.75f)) }
    ComponentPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfettiCanvas(confetti, Offset(0.5f, 0.5f), 1_600, { 0.3f }, Modifier.size(140.dp))
            SwirlCanvas(swirl, Offset(0.5f, 0.5f), { 0.45f }, Modifier.size(140.dp))
            FallingDropsCanvas(drops, 0.8f, { 0.4f }, Modifier.size(140.dp))
        }
    }
}
