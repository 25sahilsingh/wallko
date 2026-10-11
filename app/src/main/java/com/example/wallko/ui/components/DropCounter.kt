package com.example.wallko.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.wallko.R
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoMotion

/**
 * Drop icon + a number that counts up/down to [count]. The drop does the tap bounce whenever
 * the count increases.
 *
 * @param showPlus prefix positive values with "+" (e.g. "+12" drops earned on a walk).
 */
@Composable
fun DropCounter(
    count: Int,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
    iconSize: Dp = 24.dp,
    showPlus: Boolean = false,
) {
    val shown by animateIntAsState(count, tween(700, easing = FastOutSlowInEasing), label = "dropCount")
    val pop = remember { Animatable(1f) }
    var last by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        val grew = count > last
        last = count
        if (grew) pop.animateTo(1f, WallkoMotion.TapBounce)
    }
    val description = pluralStringResource(R.plurals.ui_drops_count, count, count)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.WaterDrop,
            contentDescription = null,
            tint = WallkoColors.SkyDeep,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (showPlus && shown > 0) stringResource(R.string.ui_drops_plus, shown) else "$shown",
            style = textStyle,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@ComponentPreviews
@Composable
private fun DropCounterPreview() {
    ComponentPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DropCounter(count = 37)
            DropCounter(count = 12, showPlus = true, textStyle = MaterialTheme.typography.headlineMedium, iconSize = 32.dp)
            DropCounter(count = 0, textStyle = MaterialTheme.typography.labelLarge, iconSize = 16.dp)
        }
    }
}
