package com.example.wallko.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.wallko.R
import com.example.wallko.ui.theme.WallkoColors
import kotlin.math.roundToInt

/** The lavender elixir bottle, drawn in a 100 x 100 unit box. Decorative (no semantics). */
@Composable
fun ElixirBottle(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val unit = this.size.minDimension / 100f
        withTransform({
            translate((this.size.width - 100f * unit) / 2f, (this.size.height - 100f * unit) / 2f)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            val outline = Stroke(width = 5f, cap = StrokeCap.Round)
            val glass = Color.White.copy(alpha = 0.7f)
            val bodyRect = Rect(center = Offset(50f, 64f), radius = 30f)
            val body = Path().apply {
                addOval(bodyRect)
                addRoundRect(
                    RoundRect(
                        rect = Rect(Offset(40f, 20f), Size(20f, 22f)),
                        cornerRadius = CornerRadius(4f),
                    ),
                )
            }
            drawPath(body, glass)
            // Liquid: lower part of the round body.
            clipPath(Path().apply { addOval(bodyRect) }) {
                drawRect(WallkoColors.Lavender, topLeft = Offset(20f, 56f), size = Size(60f, 40f))
                drawOval(Color.White.copy(alpha = 0.35f), topLeft = Offset(26f, 54f), size = Size(48f, 6f))
            }
            drawOval(Tones.LavenderDeep, topLeft = bodyRect.topLeft, size = bodyRect.size, style = outline)
            drawRoundRect(Tones.LavenderDeep, Offset(40f, 20f), Size(20f, 22f), CornerRadius(4f), style = outline)
            // Cork.
            drawRoundRect(WallkoColors.Soil, Offset(37f, 6f), Size(26f, 15f), CornerRadius(6f))
            // Shine and a little sparkle inside.
            drawRoundRect(Color.White.copy(alpha = 0.8f), Offset(30f, 50f), Size(6f, 16f), CornerRadius(3f))
            drawSparkleStar(Offset(58f, 74f), 8f, Color.White)
        }
    }
}

/**
 * Elixir count pill: bottle + "×3", with an optional progress ring (0..1) toward the next elixir.
 */
@Composable
fun ElixirBadge(
    count: Int,
    modifier: Modifier = Modifier,
    progressToNext: Float? = null,
    onClick: (() -> Unit)? = null,
) {
    val countText = pluralStringResource(R.plurals.ui_elixir_count, count, count)
    val description = if (progressToNext != null) {
        stringResource(R.string.ui_elixir_progress, countText, (progressToNext.coerceIn(0f, 1f) * 100).roundToInt())
    } else {
        countText
    }
    val openLabel = stringResource(R.string.ui_elixir_open)
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            // The pill is 44 dp tall; give it a 48 dp touch target when it is tappable.
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .clip(CircleShape)
            .then(clickModifier)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clearAndSetSemantics { contentDescription = description }
            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (progressToNext != null) {
                ProgressRing(progressToNext, Modifier.fillMaxSize())
            }
            ElixirBottle(size = 26.dp)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.ui_elixir_short, count),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun ProgressRing(progress: Float, modifier: Modifier) {
    val animated = animateFloatAsState(progress.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "elixirRing")
    Canvas(modifier) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = WallkoColors.Lavender.copy(alpha = 0.35f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = Tones.LavenderDeep,
            startAngle = -90f,
            sweepAngle = 360f * animated.value,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@ComponentPreviews
@Composable
private fun ElixirBadgePreview() {
    ComponentPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElixirBottle(size = 64.dp)
            ElixirBadge(count = 0)
            ElixirBadge(count = 2, progressToNext = 0.4f)
            ElixirBadge(count = 5, progressToNext = 1f)
        }
    }
}
