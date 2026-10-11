package com.example.wallko.ui.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wallko.R
import com.example.wallko.core.geo.cellIdOf
import com.example.wallko.ui.components.DripExpression
import com.example.wallko.ui.components.ElixirBadge
import com.example.wallko.ui.components.EmptyState
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Map tab: fog-of-war over a pastel street map, or the meadow map when tiles are unavailable. */
@Composable
fun MapScreen(modifier: Modifier = Modifier, viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var elixirPop by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(viewModel) { viewModel.elixirPops.collect { elixirPop = it } }
    MapContent(
        state = state,
        elixirPop = elixirPop,
        onElixirPopDone = { elixirPop = null },
        onCameraIdle = viewModel::onCameraIdle,
        modifier = modifier,
    )
}

@Composable
private fun MapContent(
    state: MapUiState,
    elixirPop: Int?,
    onElixirPopDone: () -> Unit,
    onCameraIdle: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val online = remember { !inPreview && isProbablyOnline(context) }
    var tilesFailed by rememberSaveable { mutableStateOf(false) }
    var preferMeadow by rememberSaveable { mutableStateOf(false) }
    var recenterRequests by remember { mutableIntStateOf(0) }
    val tilesAvailable = online && !tilesFailed
    val showMeadow = !tilesAvailable || preferMeadow

    Box(modifier.fillMaxSize()) {
        if (showMeadow) {
            MeadowMap(
                state = state,
                recenterRequests = recenterRequests,
                contentDescription = pluralStringResource(
                    R.plurals.map_meadow_content_description,
                    state.exploredCount,
                    state.exploredCount,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val streetDescription = pluralStringResource(
                R.plurals.map_street_content_description,
                state.exploredCount,
                state.exploredCount,
            )
            MapLibreMapView(
                state = state,
                recenterRequests = recenterRequests,
                onCameraIdle = onCameraIdle,
                onFailed = { tilesFailed = true },
                modifier = Modifier.fillMaxSize().semantics { contentDescription = streetDescription },
            )
        }

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ExploredChip(state.exploredCount)
            if (!tilesAvailable && !state.isEmpty) {
                Text(
                    text = stringResource(R.string.map_offline_note),
                    style = MaterialTheme.typography.labelMedium,
                    color = WallkoColors.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(WallkoColors.CreamDeep.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        if (state.isEmpty) {
            EmptyState(
                message = stringResource(R.string.map_empty),
                expression = DripExpression.SLEEPY,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tilesAvailable) {
                SmallFloatingActionButton(
                    onClick = { preferMeadow = !preferMeadow },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Icon(
                        imageVector = if (preferMeadow) Icons.Rounded.Map else Icons.Rounded.Grass,
                        contentDescription = stringResource(
                            if (preferMeadow) R.string.map_show_streets else R.string.map_show_meadow,
                        ),
                    )
                }
            }
            if (state.cameraTarget != null) {
                SmallFloatingActionButton(
                    onClick = { recenterRequests++ },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = stringResource(R.string.map_recentre))
                }
            }
        }

        if (!showMeadow) {
            // MapLibre's own (i) attribution stays bottom-start; this is the always-visible credit line.
            Text(
                text = stringResource(R.string.map_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = WallkoColors.Ink,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .clip(CircleShape)
                    .background(WallkoColors.Cream.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        if (elixirPop != null) {
            ElixirPopUp(
                count = elixirPop,
                onDone = onElixirPopDone,
                modifier = Modifier.align(Alignment.Center).padding(bottom = 160.dp),
            )
        }
    }
}

@Composable
private fun ExploredChip(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 3.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(WallkoColors.Grass),
            )
            Box(Modifier.width(8.dp))
            Text(
                text = pluralStringResource(R.plurals.map_squares_explored, count, count),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** "Elixir earned": the lavender bottle springs up from the map, lingers, then shrinks away. */
@Composable
private fun ElixirPopUp(count: Int, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(0f) }
    val rise = remember { Animatable(40f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(count) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scale.snapTo(0f)
        rise.snapTo(40f)
        coroutineScope {
            launch {
                rise.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
        delay(2_200)
        scale.animateTo(0f, tween(250))
        onDone()
    }
    Column(
        modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationY = rise.value * density
                alpha = scale.value.coerceIn(0f, 1f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.map_elixir_earned),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = WallkoColors.Ink,
            modifier = Modifier
                .clip(CircleShape)
                .background(WallkoColors.Lavender.copy(alpha = 0.9f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Box(Modifier.size(6.dp))
        ElixirBadge(count = count)
    }
}

/** Best-effort: true unless we can positively tell there is no internet. MapLibre's fail listener covers the rest. */
private fun isProbablyOnline(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}.getOrDefault(true) // e.g. SecurityException without ACCESS_NETWORK_STATE

@Preview(showBackground = true)
@Composable
private fun MapContentPreview() {
    val origin = LngLat(lng = -0.1276, lat = 51.5072)
    val path = (0..20).map { LngLat(origin.lng + it * 0.0004, origin.lat + it * 0.00015) }
    val cells = path.map { cellIdOf(it.lat, it.lng) }.toSet()
    WallkoTheme {
        MapContent(
            state = MapUiState(
                fog = FogShapes(mergeCellRuns(cells), cells.size),
                path = path,
                position = path.last(),
                walking = true,
                loaded = true,
            ),
            elixirPop = null,
            onElixirPopDone = {},
            onCameraIdle = { _, _ -> },
        )
    }
}
