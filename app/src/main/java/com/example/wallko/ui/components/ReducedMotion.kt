package com.example.wallko.ui.components

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

private fun readReducedMotion(resolver: ContentResolver): Boolean =
    runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        .getOrDefault(false)

/**
 * True when the user has turned animations off (Developer options / Accessibility "Remove animations",
 * i.e. `Settings.Global.ANIMATOR_DURATION_SCALE == 0`). Idle loops (sway, twinkles, waves, clouds) and
 * confetti should stand still when this is true. Updates live if the setting changes.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val resolver = LocalContext.current.contentResolver
    var reduced by remember(resolver) { mutableStateOf(readReducedMotion(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readReducedMotion(resolver)
            }
        }
        val registered = runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }.isSuccess
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/**
 * An infinite float loop that stands still at [initialValue] when [reducedMotion] is true
 * (no frame callbacks at all, rather than a transition stuck at scale 0).
 */
@Composable
internal fun rememberLoopOrStill(
    reducedMotion: Boolean,
    initialValue: Float,
    targetValue: Float,
    animationSpec: InfiniteRepeatableSpec<Float>,
    label: String,
): State<Float> =
    if (reducedMotion) {
        remember { mutableFloatStateOf(initialValue) }
    } else {
        rememberInfiniteTransition(label = label).animateFloat(initialValue, targetValue, animationSpec, label = "${label}Value")
    }
