package com.example.wallko.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val STREET_ZOOM = 15.5
private const val STYLE_TIMEOUT_MILLIS = 15_000L

/**
 * MapLibre map with the pastel OpenFreeMap style, fog, explored tint, walk path and Drip.
 *
 * @param recenterRequests bump to fly back to Drip (and resume following during a walk).
 * @param onFailed the style could not be loaded (offline, server down, or timed out) → caller shows the meadow.
 */
@Composable
internal fun MapLibreMapView(
    state: MapUiState,
    recenterRequests: Int,
    onCameraIdle: (lat: Double, lng: Double) -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnFailed by rememberUpdatedState(onFailed)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext) // idempotent; must precede MapView creation
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    /** Follow Drip during a walk until the user drags the map. */
    var following by remember { mutableStateOf(true) }
    var cameraPlaced by remember { mutableStateOf(false) }

    // Lifecycle forwarding. Adding the observer replays ON_START/ON_RESUME; on dispose we unwind in order.
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        val memory = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = mapView.onLowMemory()

            @Suppress("DEPRECATION")
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView.onLowMemory()
            }
        }
        val failListener = MapView.OnDidFailLoadingMapListener { currentOnFailed() }
        lifecycle.addObserver(observer)
        context.registerComponentCallbacks(memory)
        mapView.addOnDidFailLoadingMapListener(failListener)
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(memory)
            mapView.removeOnDidFailLoadingMapListener(failListener)
            style = null
            map = null
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Map + style setup, once.
    DisposableEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isAttributionEnabled = true
                isLogoEnabled = true
                isCompassEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
            }
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) following = false
            }
            m.addOnCameraIdleListener {
                m.cameraPosition.target?.let { currentOnCameraIdle(it.latitude, it.longitude) }
            }
            m.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { loaded ->
                runCatching {
                    applyPastelStyle(loaded)
                    val bitmap = renderDripMarkerBitmap(density, context.resources.displayMetrics.densityDpi)
                    addWallkoLayers(loaded, bitmap)
                    style = loaded
                }.onFailure { currentOnFailed() }
            }
            map = m
        }
        onDispose { }
    }

    // No style in time → treat as offline.
    LaunchedEffect(mapView) {
        delay(STYLE_TIMEOUT_MILLIS)
        if (style == null) currentOnFailed()
    }

    // ---- Data → sources ----
    LaunchedEffect(style, state.fog) {
        val s = style ?: return@LaunchedEffect
        val (fog, explored) = withContext(Dispatchers.Default) { fogCollection(state.fog) to exploredCollection(state.fog) }
        s.setGeoJsonSafely(EXPLORED_SOURCE, explored)
        s.setGeoJsonSafely(FOG_SOURCE, fog)
    }
    LaunchedEffect(style, state.path) {
        style?.setGeoJsonSafely(PATH_SOURCE, pathCollection(state.path))
    }
    LaunchedEffect(style, state.position) {
        style?.setGeoJsonSafely(DRIP_SOURCE, dripCollection(state.position))
    }

    // ---- Camera ----
    val target = state.cameraTarget
    LaunchedEffect(map, target != null) {
        val m = map ?: return@LaunchedEffect
        if (!cameraPlaced && target != null) {
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lng), STREET_ZOOM))
            cameraPlaced = true
        }
    }
    LaunchedEffect(map, recenterRequests) {
        val m = map ?: return@LaunchedEffect
        if (recenterRequests == 0) return@LaunchedEffect
        following = true
        val t = state.cameraTarget ?: return@LaunchedEffect
        val zoom = maxOf(m.cameraPosition.zoom, STREET_ZOOM)
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(t.lat, t.lng), zoom), 600)
    }
    LaunchedEffect(map, state.position, state.walking) {
        val m = map ?: return@LaunchedEffect
        val p = state.position ?: return@LaunchedEffect
        if (state.walking && following && cameraPlaced) {
            m.animateCamera(CameraUpdateFactory.newLatLng(LatLng(p.lat, p.lng)), 500)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
