package com.example.wallko.ui.walk

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.wallko.R
import com.example.wallko.ui.theme.WallkoColors
import kotlinx.coroutines.launch

/** Sky behind the walk screen: Lavender → Peach at dawn, soft Sky → Cream otherwise. */
fun walkSky(dawn: Boolean, dark: Boolean): Brush = when {
    dawn -> Brush.verticalGradient(listOf(WallkoColors.Lavender, WallkoColors.Peach, WallkoColors.PeachSoft))
    dark -> Brush.verticalGradient(listOf(WallkoColors.NightSurface, WallkoColors.NightBackground))
    else -> Brush.verticalGradient(listOf(WallkoColors.Sky.copy(alpha = 0.45f), WallkoColors.Cream))
}

/** True when the system animator duration scale is 0 ("Remove animations"). */
@Composable
internal fun rememberWalkReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** A little trail of footprints that appear one after another, left to right, then fade. Decorative. */
@Composable
fun Footsteps(modifier: Modifier = Modifier, width: Dp = 72.dp, height: Dp = 28.dp, color: Color = WallkoColors.Soil) {
    val reducedMotion = rememberWalkReducedMotion()
    // Reduced motion: a still trail, all four prints at the same soft alpha.
    val phase = if (reducedMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "footsteps").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2_000, easing = LinearEasing)),
            label = "footstepsPhase",
        )
    }
    Canvas(modifier.size(width, height).clearAndSetSemantics {}) {
        val t = phase?.value ?: 0f
        val count = 4
        val stepW = size.width / count
        for (i in 0 until count) {
            // Each print fades in at its turn and fades out over the rest of the cycle.
            val appear = i / count.toFloat()
            val age = ((t - appear + 1f) % 1f)
            val alpha = if (phase == null) 0.6f else (1f - age).coerceIn(0f, 1f) * 0.85f
            val up = i % 2 == 0
            val cx = stepW * (i + 0.5f)
            val cy = if (up) size.height * 0.32f else size.height * 0.68f
            drawFootprint(Offset(cx, cy), size.height * 0.36f, color.copy(alpha = alpha))
        }
    }
}

private fun DrawScope.drawFootprint(center: Offset, h: Float, color: Color) {
    rotate(90f, pivot = center) {
        val w = h * 0.55f
        drawOval(color, topLeft = Offset(center.x - w / 2, center.y - h * 0.25f), size = Size(w, h * 0.75f))
        val toe = h * 0.13f
        for (k in 0..2) {
            val x = center.x - w * 0.35f + k * w * 0.35f
            drawCircle(color, radius = toe, center = Offset(x, center.y - h * 0.42f))
        }
    }
}

private class FloatBurst(val id: Long, val amount: Int)

/**
 * Whenever [drops] grows, a "+n" drop floats up from the bottom of this box and fades out.
 * Place it over the tank with `Modifier.matchParentSize()`.
 */
@Composable
fun FloatingDrops(drops: Int, modifier: Modifier = Modifier) {
    val reducedMotion = rememberWalkReducedMotion()
    val bursts = remember { mutableStateListOf<FloatBurst>() }
    var last by remember { mutableIntStateOf(drops) }
    var nextId by remember { mutableLongStateOf(0L) }
    LaunchedEffect(drops) {
        if (drops > last && !reducedMotion) bursts += FloatBurst(nextId++, drops - last)
        last = drops
    }
    Box(modifier.clearAndSetSemantics {}, contentAlignment = Alignment.BottomCenter) {
        bursts.forEach { burst ->
            key(burst.id) {
                FloatingDrop(burst.amount, xShift = ((burst.id % 3) - 1) * 28f) { bursts.remove(burst) }
            }
        }
    }
}

@Composable
private fun FloatingDrop(amount: Int, xShift: Float, onDone: () -> Unit) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { rise.animateTo(1f, tween(1_400, easing = FastOutSlowInEasing)) }.join()
        onDone()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer {
            translationY = -rise.value * 160.dp.toPx()
            translationX = xShift
            alpha = (1f - rise.value).coerceIn(0f, 1f)
            val s = 0.8f + 0.4f * (1f - rise.value)
            scaleX = s
            scaleY = s
        },
    ) {
        Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = WallkoColors.SkyDeep, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(2.dp))
        Text(stringResource(R.string.walk_float_drops, amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = WallkoColors.Ink)
    }
}
