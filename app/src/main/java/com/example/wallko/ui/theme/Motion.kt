package com.example.wallko.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/** Animation specs from docs/design-system.md ("Animations"). */
object WallkoMotion {
    const val SwayDegrees = 2f
    const val SwayPeriodMillis = 3_000

    /** Rocks between -SwayDegrees and +SwayDegrees; one full back-and-forth takes SwayPeriodMillis. */
    fun idleSway(phaseOffsetMillis: Int = 0): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(SwayPeriodMillis / 2, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
        initialStartOffset = StartOffset(phaseOffsetMillis % SwayPeriodMillis),
    )

    const val TapBounceMillis = 300

    /** Squash-and-stretch scale 1.0 -> 1.15 -> 0.95 -> 1.0. */
    val TapBounce: KeyframesSpec<Float> = keyframes {
        durationMillis = TapBounceMillis
        1f at 0
        1.15f at 100
        0.95f at 200
        1f at TapBounceMillis
    }
}

/**
 * Rotation in degrees for the idle sway. Give each plant a different [phaseOffsetMillis]
 * so the garden doesn't move in lockstep.
 */
@Composable
fun rememberIdleSway(phaseOffsetMillis: Int = 0): State<Float> =
    rememberInfiniteTransition(label = "idleSway").animateFloat(
        initialValue = -WallkoMotion.SwayDegrees,
        targetValue = WallkoMotion.SwayDegrees,
        animationSpec = WallkoMotion.idleSway(phaseOffsetMillis),
        label = "swayAngle",
    )
