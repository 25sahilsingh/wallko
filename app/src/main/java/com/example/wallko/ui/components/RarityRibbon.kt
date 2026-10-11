package com.example.wallko.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wallko.R
import com.example.wallko.core.db.Rarity
import com.example.wallko.ui.theme.WallkoColors
import kotlin.math.PI
import kotlin.math.sin

/** Palette colour for a rarity (also handy for filter chips and card borders). */
fun Rarity.paletteColor(): Color = when (this) {
    Rarity.COMMON -> WallkoColors.Grass
    Rarity.UNCOMMON -> WallkoColors.Sky
    Rarity.RARE -> WallkoColors.Berry
    Rarity.EPIC -> WallkoColors.Lavender
    Rarity.LEGENDARY -> WallkoColors.Sunflower
}

/** "Common", "Uncommon", ... (English, for logs and non-UI code; UI should use [label]). */
fun Rarity.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

/** Localised rarity name from string resources. */
@Composable
fun Rarity.label(): String = stringResource(
    when (this) {
        Rarity.COMMON -> R.string.ui_rarity_common
        Rarity.UNCOMMON -> R.string.ui_rarity_uncommon
        Rarity.RARE -> R.string.ui_rarity_rare
        Rarity.EPIC -> R.string.ui_rarity_epic
        Rarity.LEGENDARY -> R.string.ui_rarity_legendary
    },
)

private fun Rarity.icon(): ImageVector = when (this) {
    Rarity.COMMON -> Icons.Rounded.Eco
    Rarity.UNCOMMON -> Icons.Rounded.WaterDrop
    Rarity.RARE -> Icons.Rounded.Favorite
    Rarity.EPIC -> Icons.Rounded.AutoAwesome
    Rarity.LEGENDARY -> Icons.Rounded.Star
}

/**
 * Rounded rarity ribbon: a toy-style pill in the rarity colour with a darker 2 dp bottom edge.
 * Legendary gets twinkling sparkles around it.
 */
@Composable
fun RarityRibbon(
    rarity: Rarity,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val color = rarity.paletteColor()
    val edge = lerp(color, Color.Black, 0.2f)
    val name = rarity.label()
    val description = stringResource(R.string.ui_rarity_description, name)
    // Text and icon are Ink on the (always light) rarity colour, so they read fine in dark theme too.
    Box(modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(edge)
                .padding(bottom = 2.dp)
                .clip(CircleShape)
                .background(color)
                .padding(
                    horizontal = if (compact) 10.dp else 14.dp,
                    vertical = if (compact) 3.dp else 6.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = rarity.icon(),
                contentDescription = null,
                tint = WallkoColors.Ink,
                modifier = Modifier.size(if (compact) 14.dp else 18.dp),
            )
            Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
            Text(
                text = name.uppercase(),
                color = WallkoColors.Ink,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        if (rarity == Rarity.LEGENDARY) {
            LegendarySparkles(Modifier.matchParentSize())
        }
    }
}

private val SparkleSpots = listOf(
    Triple(Offset(0.04f, 0.05f), 5f, 0f),
    Triple(Offset(0.95f, 0.15f), 4f, 0.35f),
    Triple(Offset(0.78f, 1.0f), 3.5f, 0.65f),
)

@Composable
private fun LegendarySparkles(modifier: Modifier) {
    // With animations off the sparkles hold still (one stays lit at t = 0).
    val t = rememberLoopOrStill(
        reducedMotion = rememberReducedMotion(),
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing)),
        label = "legendaryTwinkle",
    )
    Canvas(modifier) {
        SparkleSpots.forEachIndexed { i, (spot, radiusDp, phase) ->
            val s = sin(((t.value + phase) % 1f) * 2f * PI.toFloat()).coerceAtLeast(0f)
            drawSparkleStar(
                center = Offset(spot.x * size.width, spot.y * size.height),
                radius = radiusDp.dp.toPx() * s,
                color = if (i % 2 == 0) Color.White else Tones.SunflowerDeep,
            )
        }
    }
}

@ComponentPreviews
@Composable
private fun RarityRibbonPreview() {
    ComponentPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Rarity.entries.forEach { RarityRibbon(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Rarity.entries.forEach { RarityRibbon(it, compact = true) }
            }
        }
    }
}
