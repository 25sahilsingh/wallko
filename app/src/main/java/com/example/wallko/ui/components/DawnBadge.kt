package com.example.wallko.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.wallko.R
import com.example.wallko.ui.theme.WallkoColors

/** "×2 early bird" pill on a Lavender -> Peach dawn gradient with a slowly rising, turning sun. */
@Composable
fun DawnBadge(
    modifier: Modifier = Modifier,
    multiplier: Int = 2,
) {
    val description = stringResource(R.string.ui_dawn_badge_description, multiplier)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(WallkoColors.Lavender, WallkoColors.Peach)))
            .clearAndSetSemantics { contentDescription = description }
            .padding(start = 8.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RisingSun(size = 28.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.ui_dawn_badge_label, multiplier),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            // The pill is always Lavender -> Peach, so Ink stays readable in dark theme too.
            color = WallkoColors.Ink,
        )
    }
}

/** A small sun peeking over a horizon line; bobs up and down and turns its rays. Decorative. */
@Composable
fun RisingSun(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val reducedMotion = rememberReducedMotion()
    val rise = rememberLoopOrStill(
        reducedMotion = reducedMotion,
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600), RepeatMode.Reverse),
        label = "sunRise",
    )
    val spin = rememberLoopOrStill(
        reducedMotion = reducedMotion,
        initialValue = 0f,
        targetValue = 45f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing)),
        label = "sunSpin",
    )
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val w = this.size.width
        val h = this.size.height
        val radius = w * 0.2f
        val center = Offset(w / 2f, h * 0.62f - rise.value * h * 0.1f)
        drawSunGlyph(center, radius, WallkoColors.Sunflower, rays = 8, rayRotationDegrees = spin.value)
        // Horizon.
        drawLine(
            color = WallkoColors.Terracotta,
            start = Offset(w * 0.08f, h * 0.86f),
            end = Offset(w * 0.92f, h * 0.86f),
            strokeWidth = h * 0.09f,
            cap = StrokeCap.Round,
        )
    }
}

@ComponentPreviews
@Composable
private fun DawnBadgePreview() {
    ComponentPreviewSurface {
        DawnBadge()
    }
}
