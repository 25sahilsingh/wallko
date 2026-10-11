package com.example.wallko.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.wallko.R
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoMotion
import com.example.wallko.ui.theme.rememberIdleSway
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** UI-only growth stage. Screens map the DB `PlantState` (plus wilt/withered rules) onto this. */
enum class PlantStage { SEEDLING, GROWING, GROWN, WILTING, WITHERED }

/** The tiny face on the pot. SURPRISED is used by the revive sequence (sleeping -> surprised -> happy). */
enum class PotFace { CURIOUS, HAPPY, PROUD, WORRIED, SLEEPING, SURPRISED }

fun PlantStage.defaultFace(): PotFace = when (this) {
    PlantStage.SEEDLING -> PotFace.CURIOUS
    PlantStage.GROWING -> PotFace.HAPPY
    PlantStage.GROWN -> PotFace.PROUD
    PlantStage.WILTING -> PotFace.WORRIED
    PlantStage.WITHERED -> PotFace.SLEEPING
}

private val PotFaceInk = WallkoColors.Ink
private val PotBlush = WallkoColors.Berry.copy(alpha = 0.35f)
private val PotShine = Color.White.copy(alpha = 0.35f)

private val WiltFilter = ColorFilter.colorMatrix(
    saturationTintMatrix(
        saturation = 0.45f,
        scaleR = WallkoColors.Wilt.red * 1.2f,
        scaleG = WallkoColors.Wilt.green * 1.2f,
        scaleB = WallkoColors.Wilt.blue * 1.2f,
    ),
)
private val WitherFilter = ColorFilter.colorMatrix(saturationTintMatrix(0.1f, 0.85f, 0.85f, 0.85f))

/**
 * The hero visual: a terracotta pot with a tiny face, soil, and the plant (a vector sprout for
 * seedlings, otherwise the stylised [sprite]).
 *
 * Drawn in a 100 x 100 unit box: soil surface at y = 64, pot from y = 62 to 97.
 *
 * @param growthFraction only used for [PlantStage.GROWING]: 0 -> sprite at 60 %, 1 -> 90 %.
 * @param swayPhase per-plant offset (0..1 of a sway period) so a garden doesn't sway in lockstep.
 * @param face override the stage's default face (e.g. SURPRISED during revive).
 * @param onClick if non-null the pot is tappable, plays the squash-and-stretch bounce and a light haptic tick.
 *
 * Idle loops (sway, twinkles, sweat drop, "zzz") stand still when animations are off ([rememberReducedMotion]).
 */
@Composable
fun PottedPlant(
    stage: PlantStage,
    sprite: ImageBitmap?,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    growthFraction: Float = 1f,
    swayPhase: Float = 0f,
    face: PotFace = stage.defaultFace(),
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val phaseMillis = ((((swayPhase % 1f) + 1f) % 1f) * WallkoMotion.SwayPeriodMillis).toInt()
    val swayState: State<Float> = if (reducedMotion) remember { mutableFloatStateOf(0f) } else rememberIdleSway(phaseMillis)
    val sway by swayState

    // Bloom in (scale 0 -> 1 with overshoot) when the sprite first appears.
    val showSprite = sprite != null && stage != PlantStage.SEEDLING
    val bloom = remember { Animatable(if (showSprite) 1f else 0f) }
    LaunchedEffect(showSprite) {
        if (showSprite) {
            if (bloom.value < 1f) {
                bloom.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
            }
        } else {
            bloom.snapTo(0f)
        }
    }

    val growthTarget = when (stage) {
        PlantStage.GROWING -> 0.6f + 0.3f * growthFraction.coerceIn(0f, 1f)
        else -> 1f
    }
    val growthScale by animateFloatAsState(growthTarget, tween(600), label = "growthScale")
    val droop by animateFloatAsState(
        targetValue = when (stage) {
            PlantStage.WILTING -> 8f
            PlantStage.WITHERED -> 10f
            else -> 0f
        },
        animationSpec = tween(1_200, easing = FastOutSlowInEasing),
        label = "droop",
    )
    val loop = rememberStageLoop(stage, reducedMotion)

    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val description = contentDescription ?: stage.defaultDescription()

    val clickModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = null, indication = null, role = Role.Button) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { bounce.animateTo(1f, WallkoMotion.TapBounce) }
            onClick()
        }
    } else {
        Modifier
    }

    val spriteFilter = when (stage) {
        PlantStage.WILTING -> WiltFilter
        PlantStage.WITHERED -> WitherFilter
        else -> null
    }
    val faded = stage == PlantStage.WILTING || stage == PlantStage.WITHERED

    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                // Squash-and-stretch: taller and thinner, then shorter and wider.
                val s = bounce.value
                scaleY = s
                scaleX = 2f - s
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .then(clickModifier)
            .semantics { this.contentDescription = description },
    ) {
        val unit = this.size.minDimension / 100f
        val origin = Offset((this.size.width - 100f * unit) / 2f, (this.size.height - 100f * unit) / 2f)
        val soil = origin + Offset(50f * unit, 64f * unit)

        inUnits(origin, unit) { drawDirt() }
        rotate(sway + droop, pivot = soil) {
            if (showSprite && sprite != null) {
                val scale = (growthScale * bloom.value).coerceAtLeast(0f)
                drawSprite(sprite, soil, unit, scale, spriteFilter)
            } else {
                inUnits(origin, unit) { drawSprout(faded) }
            }
        }
        inUnits(origin, unit) {
            drawPotFront()
            drawPotFace(face)
            drawStageExtras(stage, loop.value, swayPhase)
        }
    }
}

@Composable
private fun PlantStage.defaultDescription(): String = stringResource(
    when (this) {
        PlantStage.SEEDLING -> R.string.ui_pot_seedling
        PlantStage.GROWING -> R.string.ui_pot_growing
        PlantStage.GROWN -> R.string.ui_pot_grown
        PlantStage.WILTING -> R.string.ui_pot_wilting
        PlantStage.WITHERED -> R.string.ui_pot_withered
    },
)

/** 0..1 looping progress for the stage's little overlay animation (or a constant 0). */
@Composable
private fun rememberStageLoop(stage: PlantStage, reducedMotion: Boolean): State<Float> {
    val period = when (stage) {
        PlantStage.GROWN -> 4_000
        PlantStage.WILTING -> 1_800
        PlantStage.WITHERED -> 3_000
        else -> 0
    }
    // Still frame when animations are off: 0.1 keeps the sweat drop and a "z" visible.
    if (reducedMotion) return remember { mutableFloatStateOf(0.1f) }
    if (period == 0) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "potLoop").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing)),
        label = "potLoopProgress",
    )
}

private inline fun DrawScope.inUnits(origin: Offset, unit: Float, block: DrawScope.() -> Unit) {
    withTransform({
        translate(origin.x, origin.y)
        scale(unit, unit, pivot = Offset.Zero)
    }, block)
}

private fun DrawScope.drawSprite(sprite: ImageBitmap, soil: Offset, unit: Float, scale: Float, filter: ColorFilter?) {
    if (sprite.width <= 0 || sprite.height <= 0) return
    val fit = min(84f * unit / sprite.width, 62f * unit / sprite.height)
    val w = sprite.width * fit * scale
    val h = sprite.height * fit * scale
    if (w < 1f || h < 1f) return
    drawImage(
        image = sprite,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(sprite.width, sprite.height),
        // Sink the base slightly into the soil so it never floats above the rim.
        dstOffset = IntOffset((soil.x - w / 2f).roundToInt(), (soil.y + 2f * unit - h).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        colorFilter = filter,
        filterQuality = FilterQuality.Medium,
    )
}

private fun DrawScope.drawDirt() {
    drawOval(Tones.Dirt, topLeft = Offset(23f, 58f), size = Size(54f, 10f))
}

private fun DrawScope.drawSprout(faded: Boolean) {
    val stem = if (faded) WallkoColors.Wilt else WallkoColors.GrassDeep
    val leaf = if (faded) WallkoColors.Wilt else WallkoColors.Grass
    val stemPath = Path().apply {
        moveTo(50f, 66f)
        quadraticTo(47f, 57f, 50f, 47f)
    }
    drawPath(stemPath, stem, style = Stroke(width = 3f, cap = StrokeCap.Round))
    val left = Path().apply {
        moveTo(49.5f, 53f)
        cubicTo(45f, 44f, 37f, 44f, 33f, 48f)
        cubicTo(37f, 55f, 45f, 57f, 49.5f, 53f)
        close()
    }
    val right = Path().apply {
        moveTo(50.5f, 49f)
        cubicTo(55f, 39f, 64f, 38f, 68f, 41f)
        cubicTo(65f, 49f, 56f, 52f, 50.5f, 49f)
        close()
    }
    drawPath(left, leaf)
    drawPath(right, leaf)
    drawPath(left, stem, style = Stroke(width = 1.5f, join = StrokeJoin.Round))
    drawPath(right, stem, style = Stroke(width = 1.5f, join = StrokeJoin.Round))
}

private fun DrawScope.drawPotFront() {
    val outline = Stroke(width = 2f, join = StrokeJoin.Round)
    val body = Path().apply {
        moveTo(25f, 70f)
        lineTo(75f, 70f)
        lineTo(70f, 93f)
        quadraticTo(69.2f, 97f, 65f, 97f)
        lineTo(35f, 97f)
        quadraticTo(30.8f, 97f, 30f, 93f)
        close()
    }
    drawPath(body, WallkoColors.Terracotta)
    drawPath(body, WallkoColors.Soil, style = outline)
    // Rim overlaps the body top and hides the plant's base.
    drawRoundRect(Tones.PotRim, Offset(20f, 62f), Size(60f, 11f), CornerRadius(5.5f))
    drawRoundRect(WallkoColors.Soil, Offset(20f, 62f), Size(60f, 11f), CornerRadius(5.5f), style = outline)
    drawRoundRect(PotShine, Offset(24f, 64.5f), Size(16f, 2.5f), CornerRadius(1.25f))
    drawRoundRect(PotShine, Offset(30.5f, 76f), Size(3.5f, 12f), CornerRadius(1.75f))
}

private fun DrawScope.drawPotFace(face: PotFace) {
    val line = Stroke(width = 1.8f, cap = StrokeCap.Round)
    val leftEye = Offset(43f, 81.5f)
    val rightEye = Offset(57f, 81.5f)

    if (face != PotFace.SLEEPING) {
        drawCircle(PotBlush, radius = 3f, center = Offset(37.5f, 87f))
        drawCircle(PotBlush, radius = 3f, center = Offset(62.5f, 87f))
    }

    when (face) {
        PotFace.CURIOUS -> {
            potEye(leftEye, 2.1f)
            potEye(rightEye, 2.9f)
            drawCircle(PotFaceInk, radius = 1.7f, center = Offset(50f, 88.5f), style = Stroke(width = 1.6f))
        }

        PotFace.HAPPY -> {
            potEye(leftEye, 2.4f)
            potEye(rightEye, 2.4f)
            drawArc(PotFaceInk, 20f, 140f, false, Offset(45.5f, 83.5f), Size(9f, 6f), style = line)
        }

        PotFace.PROUD -> {
            // "^ ^" eyes and a big open smile.
            drawArc(PotFaceInk, 200f, 140f, false, Offset(39.5f, 80f), Size(7f, 5f), style = line)
            drawArc(PotFaceInk, 200f, 140f, false, Offset(53.5f, 80f), Size(7f, 5f), style = line)
            drawArc(PotFaceInk, 0f, 180f, true, Offset(45f, 84f), Size(10f, 8f))
            drawArc(WallkoColors.Berry, 0f, 180f, true, Offset(47.5f, 88f), Size(5f, 3.5f))
        }

        PotFace.WORRIED -> {
            potEye(leftEye, 2.3f)
            potEye(rightEye, 2.3f)
            drawLine(PotFaceInk, Offset(39.5f, 77f), Offset(45f, 75.5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            drawLine(PotFaceInk, Offset(55f, 75.5f), Offset(60.5f, 77f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            drawArc(PotFaceInk, 200f, 140f, false, Offset(46f, 87f), Size(8f, 5f), style = line)
        }

        PotFace.SLEEPING -> {
            drawArc(PotFaceInk, 20f, 140f, false, Offset(40f, 80f), Size(6f, 4f), style = line)
            drawArc(PotFaceInk, 20f, 140f, false, Offset(54f, 80f), Size(6f, 4f), style = line)
            drawOval(PotFaceInk, Offset(48.5f, 87f), Size(3f, 2.2f))
        }

        PotFace.SURPRISED -> {
            potEye(leftEye, 2.8f)
            potEye(rightEye, 2.8f)
            drawOval(PotFaceInk, Offset(47.5f, 85.5f), Size(5f, 6f))
        }
    }
}

private fun DrawScope.potEye(center: Offset, radius: Float) {
    drawCircle(PotFaceInk, radius = radius, center = center)
    drawCircle(Color.White, radius = radius * 0.35f, center = center + Offset(radius * 0.35f, -radius * 0.35f))
}

private fun DrawScope.drawStageExtras(stage: PlantStage, progress: Float, swayPhase: Float) {
    when (stage) {
        PlantStage.GROWN -> {
            // Occasional twinkles: visible only for a short slice of each loop.
            fun twinkle(offset: Float): Float {
                val p = ((progress + offset) % 1f)
                return if (p < 0.2f) sin(p / 0.2f * PI.toFloat()) else 0f
            }
            val shift = (swayPhase % 1f) * 10f
            drawSparkleStar(Offset(80f - shift, 16f), 6f * twinkle(0f), WallkoColors.Sunflower)
            drawSparkleStar(Offset(20f + shift, 30f), 4.5f * twinkle(0.45f), WallkoColors.Sunflower)
            drawSparkleStar(Offset(70f, 40f), 3f * twinkle(0.7f), Color.White)
        }

        PlantStage.WILTING -> {
            // A single sweat drop sliding down the pot's side, on a loop.
            val y = 66f + 14f * progress
            val alpha = if (progress < 0.6f) 1f else (1f - progress) / 0.4f
            drawWaterDrop(Offset(76f, y), height = 8f, alpha = alpha)
        }

        PlantStage.WITHERED -> {
            for (i in 0 until 3) {
                val q = (progress + i / 3f) % 1f
                val alpha = sin(q * PI.toFloat())
                val s = 4f + 3f * q
                drawSleepyZ(Offset(70f + 10f * q, 58f - 32f * q), s, WallkoColors.Lavender, strokeWidth = 1.8f, alpha = alpha)
            }
        }

        PlantStage.SEEDLING, PlantStage.GROWING -> Unit
    }
}

/** A stand-in sprite for previews: a green bush with berry flowers. */
private fun previewSprite(): ImageBitmap {
    val bmp = Bitmap.createBitmap(120, 150, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFF5DB53A.toInt()
    c.drawRect(56f, 70f, 64f, 150f, p)
    p.color = 0xFF7ED957.toInt()
    c.drawCircle(60f, 62f, 44f, p)
    c.drawCircle(30f, 95f, 22f, p)
    c.drawCircle(90f, 95f, 22f, p)
    p.color = 0xFFFF6F91.toInt()
    c.drawCircle(44f, 48f, 11f, p)
    c.drawCircle(78f, 66f, 10f, p)
    c.drawCircle(58f, 30f, 8f, p)
    return bmp.asImageBitmap()
}

@ComponentPreviews
@Composable
private fun PottedPlantPreview() {
    val sprite = remember { previewSprite() }
    ComponentPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlantStage.entries.forEachIndexed { i, stage ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PottedPlant(
                        stage = stage,
                        sprite = sprite,
                        growthFraction = 0.5f,
                        swayPhase = i / 5f,
                        size = 88.dp,
                        onClick = {},
                    )
                    Text(stage.name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@ComponentPreviews
@Composable
private fun PotFacesPreview() {
    ComponentPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PotFace.entries.forEach { face ->
                PottedPlant(stage = PlantStage.SEEDLING, sprite = null, face = face, size = 64.dp)
            }
        }
    }
}
