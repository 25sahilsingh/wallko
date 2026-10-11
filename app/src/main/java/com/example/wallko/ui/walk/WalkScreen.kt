package com.example.wallko.ui.walk

import android.content.ActivityNotFoundException
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wallko.R
import com.example.wallko.core.config.GameRules
import com.example.wallko.ui.components.ConfettiBurst
import com.example.wallko.ui.components.DawnBadge
import com.example.wallko.ui.components.DripExpression
import com.example.wallko.ui.components.DripMascot
import com.example.wallko.ui.components.DropCounter
import com.example.wallko.ui.components.ElixirBadge
import com.example.wallko.ui.components.TankGauge
import com.example.wallko.ui.components.TankGaugeVariant
import com.example.wallko.ui.components.ToyButton
import com.example.wallko.ui.settings.UnitSystem
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoShapes
import com.example.wallko.ui.theme.WallkoTheme
import com.example.wallko.walk.HealthConnectStatus
import com.example.wallko.walk.LiveWalkMath
import com.example.wallko.walk.StepSource
import com.example.wallko.walk.StepSyncState
import com.example.wallko.walk.WalkFormat
import com.example.wallko.walk.WalkLiveState
import com.example.wallko.walk.WalkPhase
import com.example.wallko.walk.WalkResult
import com.example.wallko.walk.model.RewardKind
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** TalkBack hears the live drops total at most this often, so a walk doesn't turn into a stream of numbers. */
private const val DROPS_ANNOUNCE_INTERVAL_MS = 30_000L

@Composable
fun WalkScreen(modifier: Modifier = Modifier, viewModel: WalkViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }

    var locationAsk by rememberSaveable { mutableStateOf(LocationAsk.NONE) }
    var askedOptional by rememberSaveable { mutableStateOf(false) }

    val walkPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.refreshSteps() // activity recognition may have just been granted
        if (WalkPermissions.hasFine(context)) {
            locationAsk = LocationAsk.NONE
            viewModel.onStartWalk()
        } else {
            val coarse = result[WalkPermissions.COARSE] == true || WalkPermissions.granted(context, WalkPermissions.COARSE)
            val canAskAgain = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, WalkPermissions.FINE) } ?: true
            locationAsk = when {
                coarse -> LocationAsk.NEED_PRECISE
                canAskAgain -> LocationAsk.DENIED
                else -> LocationAsk.DENIED_FOREVER
            }
        }
    }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshSteps()
    }
    val healthLauncher = rememberLauncherForActivityResult(viewModel.healthPermissionContract()) {
        viewModel.refreshSteps()
    }

    val haptics = LocalHapticFeedback.current
    val onStart: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
        when {
            !WalkPermissions.hasFine(context) -> locationAsk = LocationAsk.RATIONALE
            !askedOptional && WalkPermissions.missingOptional(context).isNotEmpty() -> {
                askedOptional = true
                walkPermissionLauncher.launch(WalkPermissions.walkPermissions())
            }
            else -> viewModel.onStartWalk()
        }
    }
    val openSettings: () -> Unit = {
        runCatching { context.startActivity(WalkPermissions.appSettingsIntent(context)) }
    }

    // Elixir earned: one celebratory buzz per finished walk.
    val elixirWalkId = state.result?.takeIf { (it.summary?.elixirEarned ?: 0) > 0 }?.walkId
    LaunchedEffect(elixirWalkId) {
        if (elixirWalkId != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    val live = state.live
    val dawnSky = live?.isDawn ?: state.dawnNow
    // The dawn gradient is light in both themes, so text drawn straight on it must stay Ink (dark theme contrast).
    val skyContent = if (dawnSky) WallkoColors.Ink else MaterialTheme.colorScheme.onBackground
    Box(
        modifier
            .fillMaxSize()
            .background(walkSky(dawnSky, isSystemInDarkTheme())),
    ) {
        CompositionLocalProvider(LocalContentColor provides skyContent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TopRow(dawn = dawnSky, elixir = state.elixir, elixirFraction = live?.elixirFraction
                    ?: LiveWalkMath.elixirFraction(state.elixirProgressM))

                when {
                    live != null -> LiveWalk(live, state.tankDrops, state.tankCapacity, state.units, onStop = {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
                        viewModel.onStopWalk()
                    })
                    locationAsk != LocationAsk.NONE -> PermissionCard(
                        ask = locationAsk,
                        onAllow = { askedOptional = true; walkPermissionLauncher.launch(WalkPermissions.walkPermissions()) },
                        onSettings = openSettings,
                        onDismiss = { locationAsk = LocationAsk.NONE },
                    )
                    else -> Idle(state.dawnNow, state.tankDrops, state.tankCapacity, onStart)
                }

                state.result?.let { SummaryCard(it, state.units, onDismiss = viewModel::onDismissSummary) }

                if (live == null) {
                    PassiveStepsCard(
                        steps = state.steps,
                        onConnectHealth = { healthLauncher.launch(viewModel.healthPermissions) },
                        onInstallHealth = {
                            try {
                                context.startActivity(viewModel.installHealthConnectIntent())
                            } catch (_: ActivityNotFoundException) {
                                // No Play Store: nothing to do; the sensor fallback still works.
                            }
                        },
                        onAllowActivity = { activityLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION) },
                    )
                }
            }
        }

        // Elixir celebration over everything.
        state.result?.takeIf { (it.summary?.elixirEarned ?: 0) > 0 }?.let {
            ConfettiBurst(trigger = it.walkId, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TopRow(dawn: Boolean, elixir: Int, elixirFraction: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.walk_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.weight(1f))
        if (dawn) {
            DawnBadge()
            Spacer(Modifier.width(8.dp))
        }
        ElixirBadge(count = elixir, progressToNext = elixirFraction)
    }
}

// ---- Idle ----

@Composable
private fun Idle(dawnNow: Boolean, tankDrops: Int, tankCapacity: Int, onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Bubble(stringResource(if (dawnNow) R.string.walk_idle_bubble_dawn else R.string.walk_idle_bubble))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DripMascot(if (dawnNow) DripExpression.CHEERING else DripExpression.HAPPY, size = 120.dp)
            TankGauge(drops = tankDrops, capacity = tankCapacity, variant = TankGaugeVariant.Large)
        }
        ToyButton(
            text = stringResource(R.string.walk_start),
            onClick = onStart,
            icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
            modifier = Modifier.fillMaxWidth(0.8f).height(64.dp),
        )
    }
}

// ---- Live ----

@Composable
private fun LiveWalk(live: WalkLiveState, tankDrops: Int, tankCapacity: Int, units: UnitSystem, onStop: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            WalkFormat.elapsed(live.elapsed),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        when (live.phase) {
            WalkPhase.STARTING -> StatusLine(stringResource(R.string.walk_starting), busy = true)
            WalkPhase.FINISHING -> StatusLine(stringResource(R.string.walk_finishing), busy = true)
            WalkPhase.TRACKING -> when {
                !live.gpsReady -> StatusLine(stringResource(R.string.walk_gps_searching), busy = true)
                !live.counting -> StatusLine(stringResource(R.string.walk_warming_up))
            }
        }

        // Tank rises with the drops this walk has earned so far.
        Box(contentAlignment = Alignment.Center) {
            TankGauge(
                drops = (tankDrops + live.dropsSoFar).coerceAtMost(tankCapacity),
                capacity = tankCapacity,
                variant = TankGaugeVariant.Large,
            )
            FloatingDrops(live.dropsSoFar, Modifier.matchParentSize())
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(Icons.Rounded.Straighten, stringResource(R.string.walk_distance_label), WalkFormat.distance(live.distanceM, units), Modifier.weight(1f))
            Stat(Icons.Rounded.Explore, stringResource(R.string.walk_new_areas_label), live.newCells.toString(), Modifier.weight(1f))
            LiveDrops(live.dropsSoFar, Modifier.weight(1f))
        }

        // Drip walks beside the step counter, footprints trailing behind.
        Surface(shape = WallkoShapes.Card, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), contentColor = MaterialTheme.colorScheme.onSurface) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Footsteps()
                DripMascot(if (live.isDawn) DripExpression.CHEERING else DripExpression.HAPPY, size = 64.dp)
                Spacer(Modifier.width(12.dp))
                val stepsLabel = stringResource(if (live.steps != null) R.string.walk_steps_label else R.string.walk_steps_off)
                val stepsDescription = live.steps?.let { stringResource(R.string.walk_stat_description, stepsLabel, WalkFormat.count(it)) }
                    ?: stringResource(R.string.walk_steps_unknown_description)
                Column(Modifier.clearAndSetSemantics { contentDescription = stepsDescription }) {
                    Text(
                        live.steps?.let { WalkFormat.count(it) } ?: stringResource(R.string.walk_steps_unknown),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(stepsLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        val per = GameRules.Elixir.METERS_IN_NEW_CELLS_PER_ELIXIR
        Text(
            stringResource(R.string.walk_elixir_progress, (live.elixirFraction * per).roundToInt(), per.toInt()),
            style = MaterialTheme.typography.bodyMedium,
            // Drawn on the sky, not a surface: soften the sky's content colour instead of onSurfaceVariant.
            color = LocalContentColor.current.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )

        ToyButton(
            text = stringResource(R.string.walk_stop),
            onClick = onStop,
            icon = Icons.Rounded.Stop,
            enabled = live.phase != WalkPhase.FINISHING,
            containerColor = WallkoColors.Berry,
            modifier = Modifier.fillMaxWidth(0.7f),
        )
    }
}

/**
 * Drops earned so far. Visually the animated [DropCounter]; for TalkBack a polite live region whose text only
 * updates every [DROPS_ANNOUNCE_INTERVAL_MS], so it is announced now and then rather than on every drop.
 */
@Composable
private fun LiveDrops(drops: Int, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(drops)
    var announced by remember { mutableIntStateOf(drops) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(DROPS_ANNOUNCE_INTERVAL_MS)
            announced = latest
        }
    }
    val description = pluralStringResource(R.plurals.walk_drops_live_description, announced, announced)
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DropCounter(count = drops, showPlus = true)
        Text(stringResource(R.string.walk_drops_label), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.walk_stat_description, label, value)
    Column(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = WallkoColors.SkyDeep)
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatusLine(text: String, busy: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

// ---- Summary ----

@Composable
private fun SummaryCard(result: WalkResult, units: UnitSystem, onDismiss: () -> Unit) {
    val summary = result.summary
    Surface(shape = WallkoShapes.Card, color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val headline = when {
                summary == null -> stringResource(R.string.walk_summary_error)
                !summary.valid -> stringResource(R.string.walk_summary_too_short)
                else -> stringResource(R.string.walk_summary_title)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DripMascot(
                    when {
                        summary == null -> DripExpression.WORRIED
                        !summary.valid -> DripExpression.CURIOUS
                        else -> DripExpression.CHEERING
                    },
                    size = 56.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }
            if (summary == null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.walk_summary_done)) }
                return@Column
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(Icons.Rounded.Timer, stringResource(R.string.walk_elapsed_label), WalkFormat.elapsed(summary.duration), Modifier.weight(1f))
                Stat(Icons.Rounded.Straighten, stringResource(R.string.walk_distance_label), WalkFormat.distance(summary.distanceM, units), Modifier.weight(1f))
                Stat(Icons.AutoMirrored.Rounded.DirectionsWalk, stringResource(R.string.walk_steps_label), WalkFormat.count(summary.steps), Modifier.weight(1f))
                Stat(Icons.Rounded.Explore, stringResource(R.string.walk_new_areas_label), summary.newCells.size.toString(), Modifier.weight(1f))
            }

            if (summary.valid) {
                summary.breakdown.forEach { line ->
                    val label = when (line.kind) {
                        RewardKind.WALK_DISTANCE -> R.string.walk_summary_line_distance
                        RewardKind.NEW_CELL -> R.string.walk_summary_line_cells
                        RewardKind.DAWN_BONUS -> R.string.walk_summary_line_dawn
                    }
                    SummaryLine(stringResource(label)) {
                        DropCounter(count = line.drops, showPlus = true, textStyle = MaterialTheme.typography.titleMedium, iconSize = 18.dp)
                    }
                }
                SummaryLine(stringResource(R.string.walk_summary_total), bold = true) {
                    DropCounter(count = summary.totalDrops, showPlus = true)
                }
                if (result.dropsOverflow > 0) {
                    Text(
                        stringResource(R.string.walk_summary_overflow, result.dropsOverflow),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (summary.elixirEarned > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ElixirBadge(count = summary.elixirEarned)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.walk_summary_elixir, summary.elixirEarned), style = MaterialTheme.typography.titleMedium)
                    }
                }
                val per = GameRules.Elixir.METERS_IN_NEW_CELLS_PER_ELIXIR
                Text(
                    stringResource(R.string.walk_elixir_progress, summary.elixirProgressM.roundToInt(), per.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ToyButton(
                text = stringResource(R.string.walk_summary_done),
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, bold: Boolean = false, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        value()
    }
}

// ---- Passive steps ----

@Composable
private fun PassiveStepsCard(
    steps: StepSyncState,
    onConnectHealth: () -> Unit,
    onInstallHealth: () -> Unit,
    onAllowActivity: () -> Unit,
) {
    Surface(shape = WallkoShapes.Card, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.walk_steps_today_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            val count = steps.stepsToday
            if (count != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.walk_steps_today_value, WalkFormat.count(count)),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                    )
                    DropCounter(count = steps.dropsToday, showPlus = true, textStyle = MaterialTheme.typography.titleMedium, iconSize = 18.dp)
                }
                if (steps.source != StepSource.NONE) {
                    Text(
                        stringResource(if (steps.source == StepSource.HEALTH_CONNECT) R.string.walk_steps_from_hc else R.string.walk_steps_from_sensor),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (steps.loaded) {
                Text(stringResource(R.string.walk_steps_none), style = MaterialTheme.typography.bodyMedium)
            }

            when {
                steps.healthConnect == HealthConnectStatus.AVAILABLE && !steps.healthPermissionGranted ->
                    TextButton(onClick = onConnectHealth) { Text(stringResource(R.string.walk_connect_hc)) }
                steps.healthConnect == HealthConnectStatus.UPDATE_REQUIRED ->
                    TextButton(onClick = onInstallHealth) { Text(stringResource(R.string.walk_install_hc)) }
            }
            if (steps.loaded && steps.source == StepSource.NONE) {
                TextButton(onClick = onAllowActivity) { Text(stringResource(R.string.walk_allow_activity)) }
            }
        }
    }
}

// ---- Permissions ----

@Composable
private fun PermissionCard(ask: LocationAsk, onAllow: () -> Unit, onSettings: () -> Unit, onDismiss: () -> Unit) {
    val (message, expression) = when (ask) {
        LocationAsk.RATIONALE, LocationAsk.NONE -> stringResource(R.string.walk_perm_rationale) to DripExpression.CURIOUS
        LocationAsk.DENIED -> stringResource(R.string.walk_perm_denied) to DripExpression.WORRIED
        LocationAsk.DENIED_FOREVER -> stringResource(R.string.walk_perm_denied_forever) to DripExpression.WORRIED
        LocationAsk.NEED_PRECISE -> stringResource(R.string.walk_perm_need_precise) to DripExpression.CURIOUS
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Bubble(message)
        DripMascot(expression, size = 120.dp)
        when (ask) {
            LocationAsk.RATIONALE, LocationAsk.NONE ->
                ToyButton(stringResource(R.string.walk_perm_rationale_ok), onClick = onAllow)
            LocationAsk.DENIED ->
                ToyButton(stringResource(R.string.walk_perm_try_again), onClick = onAllow)
            LocationAsk.DENIED_FOREVER ->
                ToyButton(stringResource(R.string.walk_perm_open_settings), onClick = onSettings, icon = Icons.Rounded.Settings)
            // Stacked, not side by side, so both labels fit at large font sizes.
            LocationAsk.NEED_PRECISE -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToyButton(stringResource(R.string.walk_perm_try_again), onClick = onAllow)
                ToyButton(stringResource(R.string.walk_perm_open_settings), onClick = onSettings, containerColor = WallkoColors.Sky)
            }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.walk_perm_not_now)) }
    }
}

@Composable
private fun Bubble(text: String) {
    Surface(shape = WallkoShapes.Bubble, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LiveWalkPreview() {
    WallkoTheme {
        Box(Modifier.background(walkSky(dawn = true, dark = false)).padding(16.dp)) {
            LiveWalk(
                WalkLiveState(
                    walkId = 1, phase = WalkPhase.TRACKING, startedAtMillis = 0,
                    elapsed = java.time.Duration.ofSeconds(754), distanceM = 1_240.0, steps = 1_530,
                    dropsSoFar = 36, newCells = 4, isDawn = true, counting = true, elixirFraction = 0.4f, gpsReady = true,
                ),
                tankDrops = 20, tankCapacity = 60, units = UnitSystem.METRIC, onStop = {},
            )
        }
    }
}
