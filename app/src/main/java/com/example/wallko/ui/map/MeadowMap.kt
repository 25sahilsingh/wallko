package com.example.wallko.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.wallko.ui.theme.WallkoColors
import kotlin.math.max
import kotlin.math.min

/** At zoom 1 a 100 m cell is ~44 dp. */
private const val BASE_DP_PER_METER = 0.44f
private const val MIN_ZOOM = 0.05f
private const val MAX_ZOOM = 6f

/**
 * Offline fallback: a cream meadow (= fog) with explored cells as soft green rounded patches, the walk path as
 * dots and Drip at the current position. Pinch to zoom, drag to pan.
 *
 * @param recenterRequests bump to reset pan/zoom onto Drip.
 */
@Composable
internal fun MeadowMap(
    state: MapUiState,
    recenterRequests: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val origin = state.cameraTarget
    val projection = remember(origin == null) {
        origin?.let { EquirectProjection(it.lat, it.lng) }
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        // Zoom about the screen centre: scale the existing pan with the zoom.
        pan = pan * (newZoom / zoom) + panChange
        zoom = newZoom
    }
    LaunchedEffect(recenterRequests) {
        if (recenterRequests > 0) {
            zoom = 1f
            pan = Offset.Zero
        }
    }

    // Fog/meadow ground follows the theme: Cream in light, warm night background in dark.
    val ground = MaterialTheme.colorScheme.background
    // Path dots: Soil in light theme, Peach in dark (onSurfaceVariant), so they stay visible on the night ground.
    val pathColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier
            .transformable(transform)
            .semantics { this.contentDescription = contentDescription },
    ) {
        drawRect(ground)
        drawTufts(pan)
        val proj = projection ?: return@Canvas

        val pxPerMeter = BASE_DP_PER_METER * density * zoom
        // Keep Drip in view: the screen centre is the current position (or the origin if unknown).
        val focus = state.position ?: origin ?: return@Canvas
        val cx = size.width / 2f + pan.x - (proj.xMeters(focus.lng) * pxPerMeter).toFloat()
        val cy = size.height / 2f + pan.y + (proj.yMeters(focus.lat) * pxPerMeter).toFloat()
        fun x(lng: Double) = cx + (proj.xMeters(lng) * pxPerMeter).toFloat()
        fun y(lat: Double) = cy - (proj.yMeters(lat) * pxPerMeter).toFloat()

        // Explored patches.
        val patch = WallkoColors.Grass.copy(alpha = 0.45f)
        val gap = 1.dp.toPx()
        for (b in state.fog.explored) {
            val left = x(b.west)
            val right = x(b.east)
            val top = y(b.north)
            val bottom = y(b.south)
            if (right < 0f || left > size.width || bottom < 0f || top > size.height) continue
            val w = max(right - left - gap, 1f)
            val h = max(bottom - top - gap, 1f)
            val r = min(w, h) * 0.3f
            drawRoundRect(patch, Offset(left + gap / 2f, top + gap / 2f), Size(w, h), CornerRadius(r))
        }

        // Walk path: dotted footsteps.
        if (state.path.size >= 2) {
            val path = Path().apply {
                moveTo(x(state.path[0].lng), y(state.path[0].lat))
                for (i in 1 until state.path.size) lineTo(x(state.path[i].lng), y(state.path[i].lat))
            }
            val dot = 5.dp.toPx()
            drawPath(
                path,
                pathColor,
                style = Stroke(
                    width = dot,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, dot * 2.2f)),
                ),
            )
        }

        // Drip, anchored bottom-centre on the position.
        state.position?.let { p ->
            val s = DripMarkerSize.toPx()
            translate(x(p.lng) - s / 2f, y(p.lat) - s) {
                drawDripIn(s)
            }
        }
    }
}

private fun DrawScope.drawDripIn(sizePx: Float) {
    // drawDripMarker fills the DrawScope's size, so clip a sub-scope of the marker's size.
    inset(0f, 0f, size.width - sizePx, size.height - sizePx) {
        drawDripMarker()
    }
}

/** Little grass tufts on a loose grid that moves with the pan, so dragging feels like moving over a meadow. */
private fun DrawScope.drawTufts(pan: Offset) {
    val spacing = 72.dp.toPx()
    val tuft = WallkoColors.Grass.copy(alpha = 0.22f)
    val stroke = 2.dp.toPx()
    val blade = 6.dp.toPx()
    val startX = pan.x.mod(spacing) - spacing
    val startY = pan.y.mod(spacing) - spacing
    var row = 0
    var y = startY
    while (y < size.height + spacing) {
        var x = startX + if (row % 2 == 0) 0f else spacing / 2f
        while (x < size.width + spacing) {
            drawLine(tuft, Offset(x, y), Offset(x - blade * 0.5f, y - blade), stroke, StrokeCap.Round)
            drawLine(tuft, Offset(x, y), Offset(x, y - blade * 1.2f), stroke, StrokeCap.Round)
            drawLine(tuft, Offset(x, y), Offset(x + blade * 0.5f, y - blade), stroke, StrokeCap.Round)
            x += spacing
        }
        y += spacing
        row++
    }
}
