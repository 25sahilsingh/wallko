package com.example.wallko.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.wallko.R
import com.example.wallko.ui.theme.WallkoColors
import kotlin.math.PI
import kotlin.math.sin

enum class TankGaugeVariant {
    /** Small pill for the garden's top-right corner. */
    Compact,

    /** Big tank for the walk screen. */
    Large,
}

/**
 * Water tank gauge: Sky liquid with a gentle wave, level = drops / capacity, label "37 / 60".
 * Level and number animate when [drops] changes.
 */
@Composable
fun TankGauge(
    drops: Int,
    capacity: Int,
    modifier: Modifier = Modifier,
    variant: TankGaugeVariant = TankGaugeVariant.Compact,
    onClick: (() -> Unit)? = null,
) {
    val fraction = if (capacity > 0) (drops.toFloat() / capacity).coerceIn(0f, 1f) else 0f
    val level = animateFloatAsState(fraction, tween(900, easing = FastOutSlowInEasing), label = "tankLevel")
    val shownDrops by animateIntAsState(drops, tween(900, easing = FastOutSlowInEasing), label = "tankDrops")
    val phase = rememberLoopOrStill(
        reducedMotion = rememberReducedMotion(),
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2_200, easing = LinearEasing)),
        label = "tankWave",
    )
    val description = pluralStringResource(R.plurals.ui_tank_description, capacity, drops, capacity)
    val label = stringResource(R.string.ui_tank_label, shownDrops, capacity)
    val openLabel = stringResource(R.string.ui_tank_open)
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    when (variant) {
        TankGaugeVariant.Compact -> Row(
            modifier = modifier
                // The pill is 40 dp tall; give it a 48 dp touch target when it is tappable.
                .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
                .clip(CircleShape)
                .then(clickModifier)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(WallkoColors.Sky.copy(alpha = 0.18f))
                    .border(2.dp, WallkoColors.SkyDeep, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Liquid(level, phase, amplitude = 1.5.dp, modifier = Modifier.fillMaxSize())
                Icon(
                    imageVector = Icons.Rounded.WaterDrop,
                    contentDescription = null,
                    tint = WallkoColors.Ink.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }

        TankGaugeVariant.Large -> Column(
            modifier = modifier
                .then(clickModifier)
                .semantics(mergeDescendants = true) { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val shape = RoundedCornerShape(32.dp)
            Box(
                modifier = Modifier
                    .size(width = 128.dp, height = 168.dp)
                    .clip(shape)
                    .background(WallkoColors.Sky.copy(alpha = 0.15f))
                    .border(3.dp, WallkoColors.SkyDeep, shape),
            ) {
                Liquid(level, phase, amplitude = 4.dp, modifier = Modifier.fillMaxSize())
                TankGlass(Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.clearAndSetSemantics {},
            ) {
                Text(label, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = pluralStringResource(R.plurals.ui_tank_unit, capacity),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

/** Two layered sine waves filling the box from the bottom up to [level]. */
@Composable
private fun Liquid(level: State<Float>, phase: State<Float>, amplitude: Dp, modifier: Modifier) {
    Canvas(modifier) {
        val l = level.value
        if (l <= 0.001f) return@Canvas
        // Calm the surface when full so the wave doesn't look clipped.
        val amp = amplitude.toPx() * if (l > 0.97f) 0.3f else 1f
        val surfaceY = size.height * (1f - l)
        val p = phase.value
        drawWave(surfaceY, amp, p + PI.toFloat() * 0.7f, WallkoColors.SkyDeep)
        drawWave(surfaceY + amp * 0.6f, amp, p, WallkoColors.Sky)
    }
}

private fun DrawScope.drawWave(surfaceY: Float, amp: Float, phase: Float, color: Color) {
    val steps = 24
    val path = Path().apply {
        moveTo(0f, size.height)
        for (i in 0..steps) {
            val x = size.width * i / steps
            lineTo(x, surfaceY + amp * sin(phase + i.toFloat() / steps * 2f * PI.toFloat()))
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}

/** Glass shine and level ticks over the big tank. */
@Composable
private fun TankGlass(modifier: Modifier) {
    Canvas(modifier) {
        val tick = WallkoColors.SkyDeep.copy(alpha = 0.6f)
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(tick, Offset(size.width - 26.dp.toPx(), y), Offset(size.width - 14.dp.toPx(), y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        drawRoundRect(
            color = Color.White.copy(alpha = 0.35f),
            topLeft = Offset(14.dp.toPx(), 18.dp.toPx()),
            size = Size(8.dp.toPx(), size.height * 0.45f),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
    }
}

@ComponentPreviews
@Composable
private fun TankGaugeCompactPreview() {
    ComponentPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TankGauge(drops = 0, capacity = 60)
            TankGauge(drops = 30, capacity = 60)
            TankGauge(drops = 60, capacity = 60)
        }
    }
}

@ComponentPreviews
@Composable
private fun TankGaugeLargePreview() {
    ComponentPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TankGauge(drops = 0, capacity = 60, variant = TankGaugeVariant.Large)
            TankGauge(drops = 30, capacity = 60, variant = TankGaugeVariant.Large)
            TankGauge(drops = 60, capacity = 60, variant = TankGaugeVariant.Large)
        }
    }
}
