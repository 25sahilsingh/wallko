package com.example.wallko.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.example.wallko.R
import com.example.wallko.core.db.GrowthSpeed
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoShapes
import kotlinx.coroutines.delay
import kotlin.math.ceil

private val StatIconSize = 22.dp
private val StatLabelWidth = 64.dp

/**
 * One "cute icon row" on the plant card: a label, a row of icons, and an optional "~" marker
 * (tap for an "Estimated value" hint).
 */
@Composable
fun StatRow(
    label: String,
    valueDescription: String,
    modifier: Modifier = Modifier,
    estimated: Boolean = false,
    icons: @Composable RowScope.() -> Unit,
) {
    val description = if (estimated) {
        stringResource(R.string.ui_stat_description_estimated, label, valueDescription)
    } else {
        stringResource(R.string.ui_stat_description, label, valueDescription)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // A minimum (not fixed) width keeps rows aligned but lets large fonts grow the label.
                modifier = Modifier
                    .widthIn(min = StatLabelWidth)
                    .padding(end = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                icons()
            }
        }
        if (estimated) {
            Spacer(Modifier.width(4.dp))
            EstimatedMark()
        }
    }
}

/** Tiny "~" that shows an "Estimated value" bubble when tapped. */
@Composable
fun EstimatedMark(modifier: Modifier = Modifier, hint: String = stringResource(R.string.ui_estimated_hint)) {
    var showHint by remember { mutableStateOf(false) }
    val lift = with(LocalDensity.current) { 40.dp.roundToPx() }
    val showHintLabel = stringResource(R.string.ui_show_hint)
    Box(modifier) {
        Box(
            modifier = Modifier
                // 48 dp touch target (was 32 dp).
                .minimumInteractiveComponentSize()
                .sizeIn(minWidth = 32.dp, minHeight = 32.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = showHintLabel, role = Role.Button) { showHint = !showHint }
                .semantics { contentDescription = hint },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.ui_estimated_mark),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        if (showHint) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -lift),
                onDismissRequest = { showHint = false },
            ) {
                Surface(
                    shape = WallkoShapes.Bubble,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            LaunchedEffect(Unit) {
                delay(2_500)
                showHint = false
            }
        }
    }
}

@Composable
private fun StatIcon(icon: ImageVector, tint: Color, size: Dp = StatIconSize) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
private fun emptyTint(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

/** Thirst as 1-5 drops. */
@Composable
fun ThirstRow(
    level: Int,
    modifier: Modifier = Modifier,
    estimated: Boolean = false,
    label: String = stringResource(R.string.ui_stat_thirst),
) {
    val l = level.coerceIn(1, 5)
    val empty = emptyTint()
    StatRow(label, pluralStringResource(R.plurals.ui_stat_drops_of, 5, l, 5), modifier, estimated) {
        repeat(5) { i -> StatIcon(Icons.Rounded.WaterDrop, if (i < l) WallkoColors.SkyDeep else empty) }
    }
}

/** Sun as 1-3 suns. */
@Composable
fun SunRow(
    level: Int,
    modifier: Modifier = Modifier,
    estimated: Boolean = false,
    label: String = stringResource(R.string.ui_stat_sun),
) {
    val l = level.coerceIn(1, 3)
    val empty = emptyTint()
    StatRow(label, pluralStringResource(R.plurals.ui_stat_suns_of, 3, l, 3), modifier, estimated) {
        repeat(3) { i -> StatIcon(Icons.Rounded.WbSunny, if (i < l) Tones.SunflowerDeep else empty) }
    }
}

/** Growth speed as sprouts: fast = 3, medium = 2, slow = 1, plus the word. */
@Composable
fun GrowthRow(
    speed: GrowthSpeed,
    modifier: Modifier = Modifier,
    estimated: Boolean = false,
    label: String = stringResource(R.string.ui_stat_growth),
) {
    val (count, wordRes) = when (speed) {
        GrowthSpeed.FAST -> 3 to R.string.ui_growth_fast
        GrowthSpeed.MEDIUM -> 2 to R.string.ui_growth_medium
        GrowthSpeed.SLOW -> 1 to R.string.ui_growth_slow
    }
    val word = stringResource(wordRes)
    val empty = emptyTint()
    StatRow(label, word, modifier, estimated) {
        repeat(3) { i -> StatIcon(Icons.Rounded.Spa, if (i < count) WallkoColors.GrassDeep else empty) }
        Spacer(Modifier.width(6.dp))
        Text(word, style = MaterialTheme.typography.labelLarge)
    }
}

/** Number of leaves (1-5) for a classifier confidence score in 0..1. */
fun confidenceLeaves(score: Float): Int =
    if (score.isNaN()) 1 else ceil(score.coerceIn(0f, 1f) * 5f).toInt().coerceIn(1, 5)

@Composable
private fun leavesWord(leaves: Int): String = stringResource(
    when (leaves) {
        5 -> R.string.ui_leaves_5
        4 -> R.string.ui_leaves_4
        3 -> R.string.ui_leaves_3
        2 -> R.string.ui_leaves_2
        else -> R.string.ui_leaves_1
    },
)

/** Scan confidence shown as 1-5 leaves (never a raw percentage). */
@Composable
fun LeafConfidence(
    score: Float,
    modifier: Modifier = Modifier,
    showWord: Boolean = true,
    leafSize: Dp = StatIconSize,
) {
    val leaves = confidenceLeaves(score)
    val empty = emptyTint()
    val word = leavesWord(leaves)
    val description = pluralStringResource(R.plurals.ui_confidence_description, 5, leaves, 5, word.lowercase())
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { i -> StatIcon(Icons.Rounded.Eco, if (i < leaves) WallkoColors.GrassDeep else empty, leafSize) }
        if (showWord) {
            Spacer(Modifier.width(8.dp))
            Text(word, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@ComponentPreviews
@Composable
private fun StatRowPreview() {
    ComponentPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThirstRow(level = 3)
            SunRow(level = 2, estimated = true)
            GrowthRow(speed = GrowthSpeed.FAST)
            GrowthRow(speed = GrowthSpeed.SLOW, estimated = true)
            listOf(0.1f, 0.35f, 0.55f, 0.75f, 0.97f).forEach { LeafConfidence(score = it) }
        }
    }
}
