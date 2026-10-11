package com.example.wallko.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.wallko.R
import com.example.wallko.ui.components.DripExpression
import com.example.wallko.ui.components.DripMascot
import com.example.wallko.ui.components.ElixirBottle
import com.example.wallko.ui.components.PlantStage
import com.example.wallko.ui.components.PottedPlant
import com.example.wallko.ui.components.ToyButton
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoMotion
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val STORY_PAGE_COUNT = 3

/**
 * Big Drip illustration for story page [page]. [pageOffset] is how far the page is from the centre
 * (0 = settled, ±1 = one page away); Drip moves slower than the page for a parallax feel.
 */
@Composable
internal fun StoryArt(page: Int, pageOffset: Float, modifier: Modifier = Modifier) {
    val description = stringResource(
        when (page) {
            0 -> R.string.onboarding_story1_art
            1 -> R.string.onboarding_story2_art
            else -> R.string.onboarding_story3_art
        },
    )
    val bob by rememberDripBob()
    Box(
        modifier = modifier
            .size(260.dp)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Props drift the opposite way to Drip so the scene has depth.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = -pageOffset * size.width * 0.25f },
        ) {
            when (page) {
                0 -> {
                    Bubble(Icons.Rounded.CameraAlt, WallkoColors.Sky, Modifier.align(Alignment.TopStart).padding(8.dp))
                    PottedPlant(
                        stage = PlantStage.GROWN,
                        sprite = null,
                        size = 92.dp,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
                1 -> {
                    Bubble(Icons.Rounded.WaterDrop, WallkoColors.Sky, Modifier.align(Alignment.TopEnd).padding(8.dp))
                    PottedPlant(
                        stage = PlantStage.WILTING,
                        sprite = null,
                        size = 88.dp,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
                else -> {
                    Bubble(Icons.Rounded.Place, WallkoColors.Berry, Modifier.align(Alignment.TopStart).padding(8.dp))
                    ElixirBottle(size = 72.dp, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
                }
            }
        }
        DripMascot(
            expression = when (page) {
                0 -> DripExpression.CURIOUS
                1 -> DripExpression.HAPPY
                else -> DripExpression.CHEERING
            },
            size = 170.dp,
            modifier = Modifier.graphicsLayer {
                val distance = abs(pageOffset).coerceAtMost(1f)
                translationX = pageOffset * size.width * 0.6f
                translationY = bob * density
                alpha = 1f - distance * 0.7f
                val s = 1f - distance * 0.25f
                scaleX = s
                scaleY = s
            },
        )
    }
}

/** Drip next to a little icon badge explaining what a permission is for. */
@Composable
internal fun PermissionArt(step: PermissionStep, modifier: Modifier = Modifier) {
    val (icon, tint, expression, descriptionRes) = when (step) {
        PermissionStep.CAMERA -> ArtSpec(Icons.Rounded.CameraAlt, WallkoColors.Sky, DripExpression.CURIOUS, R.string.onboarding_camera_art)
        PermissionStep.ACTIVITY_RECOGNITION -> ArtSpec(
            Icons.AutoMirrored.Rounded.DirectionsWalk,
            WallkoColors.Grass,
            DripExpression.HAPPY,
            R.string.onboarding_activity_art,
        )
        PermissionStep.LOCATION -> ArtSpec(Icons.Rounded.Place, WallkoColors.Berry, DripExpression.CURIOUS, R.string.onboarding_location_art)
        PermissionStep.NOTIFICATIONS -> ArtSpec(
            Icons.Rounded.Notifications,
            WallkoColors.Sunflower,
            DripExpression.HAPPY,
            R.string.onboarding_notifications_art,
        )
        PermissionStep.HEALTH_CONNECT, PermissionStep.HEALTH_CONNECT_INSTALL -> ArtSpec(
            Icons.Rounded.Favorite,
            WallkoColors.Peach,
            DripExpression.CHEERING,
            R.string.onboarding_health_art,
        )
    }
    val description = stringResource(descriptionRes)
    val bob by rememberDripBob()
    Box(
        modifier
            .size(width = 180.dp, height = 150.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        DripMascot(
            expression = expression,
            size = 130.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .graphicsLayer { translationY = bob * density },
        )
        Bubble(icon, tint, Modifier.align(Alignment.TopEnd))
    }
}

private data class ArtSpec(val icon: ImageVector, val tint: Color, val expression: DripExpression, val description: Int)

@Composable
private fun Bubble(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = WallkoColors.Ink, modifier = Modifier.size(size * 0.55f))
    }
}

/** Gentle up/down float in dp, -5..5 over ~1.6 s. */
@Composable
private fun rememberDripBob() = rememberInfiniteTransition(label = "dripBob").animateFloat(
    initialValue = -5f,
    targetValue = 5f,
    animationSpec = infiniteRepeatable(tween(1_600), RepeatMode.Reverse),
    label = "dripBobOffset",
)

/** Page dots; the current one stretches into a pill. */
@Composable
internal fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_page_indicator, current + 1, count)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 28.dp else 10.dp, label = "dotWidth")
            Box(
                Modifier
                    .height(10.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (selected) WallkoColors.Grass else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

/**
 * [ToyButton] that springs in whenever [bounceKey] changes and does the design-system tap bounce
 * (1.0 → 1.15 → 0.95 → 1.0) when pressed.
 */
@Composable
internal fun BouncyToyButton(
    text: String,
    onClick: () -> Unit,
    bounceKey: Any?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = WallkoColors.Grass,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(bounceKey) {
        scale.snapTo(0.8f)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    ToyButton(
        text = text,
        onClick = {
            scope.launch {
                scale.snapTo(1f)
                scale.animateTo(1f, WallkoMotion.TapBounce)
            }
            onClick()
        },
        enabled = enabled,
        containerColor = containerColor,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}
