package com.example.wallko.ui.map

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.wallko.ui.theme.WallkoColors

/** On-map marker size. The marker's anchor is the bottom-centre (Drip's shadow). */
val DripMarkerSize: Dp = 52.dp

/**
 * A simplified, happy Drip standing on a soft shadow, drawn into the current DrawScope's size
 * (100 × 100 unit box, centred). The full [com.example.wallko.ui.components.DripMascot] is a composable, so the
 * map draws this lighter twin instead; it is shared by the MapLibre symbol bitmap and the meadow map.
 */
fun DrawScope.drawDripMarker() {
    val unit = size.minDimension / 100f
    withTransform({
        translate((size.width - 100f * unit) / 2f, (size.height - 100f * unit) / 2f)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        val body = WallkoColors.Sky
        val shade = WallkoColors.SkyDeep
        val outline = Stroke(width = 3f, join = StrokeJoin.Round, cap = StrokeCap.Round)

        // Ground shadow (the anchor point is the bottom-centre of the box).
        drawOval(WallkoColors.Ink.copy(alpha = 0.18f), Offset(24f, 88f), Size(52f, 10f))
        // White halo so Drip pops on both fog and map.
        drawCircle(Color.White.copy(alpha = 0.85f), radius = 40f, center = Offset(50f, 54f))

        // Handle.
        drawArc(shade, 90f, 180f, false, Offset(14f, 46f), Size(26f, 28f), style = Stroke(width = 6f, cap = StrokeCap.Round))
        // Spout with rose.
        val spout = Path().apply {
            moveTo(66f, 62f)
            lineTo(82f, 40f)
            quadraticTo(87f, 38f, 88f, 44f)
            lineTo(70f, 76f)
            close()
        }
        drawPath(spout, body)
        drawPath(spout, shade, style = outline)
        drawCircle(shade, radius = 5.5f, center = Offset(85f, 40f))
        drawCircle(body, radius = 3f, center = Offset(85f, 40f))
        // Lid and knob.
        drawRoundRect(shade, Offset(32f, 30f), Size(34f, 11f), CornerRadius(5.5f))
        drawCircle(shade, radius = 4.5f, center = Offset(49f, 27f))
        // Body tub.
        val tub = Path().apply {
            moveTo(35f, 38f)
            lineTo(63f, 38f)
            quadraticTo(74f, 38f, 74f, 51f)
            lineTo(74f, 77f)
            quadraticTo(74f, 88f, 63f, 88f)
            lineTo(35f, 88f)
            quadraticTo(24f, 88f, 24f, 77f)
            lineTo(24f, 51f)
            quadraticTo(24f, 38f, 35f, 38f)
            close()
        }
        drawPath(tub, body)
        drawPath(tub, shade, style = outline)
        drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(29f, 45f), Size(4.5f, 14f), CornerRadius(2.25f))
        // Face: dot eyes, blush, smile.
        val ink = WallkoColors.Ink
        drawCircle(WallkoColors.Berry.copy(alpha = 0.35f), radius = 4f, center = Offset(34f, 70f))
        drawCircle(WallkoColors.Berry.copy(alpha = 0.35f), radius = 4f, center = Offset(64f, 70f))
        drawCircle(ink, radius = 3.6f, center = Offset(41f, 62f))
        drawCircle(ink, radius = 3.6f, center = Offset(57f, 62f))
        drawCircle(Color.White, radius = 1.2f, center = Offset(42.2f, 60.8f))
        drawCircle(Color.White, radius = 1.2f, center = Offset(58.2f, 60.8f))
        drawArc(ink, 20f, 140f, false, Offset(43f, 64f), Size(12f, 9f), style = Stroke(width = 2.6f, cap = StrokeCap.Round))
    }
}

/**
 * Renders [drawDripMarker] to an ARGB bitmap for a MapLibre style image. The bitmap's density is set so
 * MapLibre shows it at [size] dp.
 */
fun renderDripMarkerBitmap(density: Density, densityDpi: Int, size: Dp = DripMarkerSize): Bitmap {
    val px = with(density) { size.roundToPx() }.coerceAtLeast(1)
    val image = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
        drawDripMarker()
    }
    return image.asAndroidBitmap().also { it.density = densityDpi }
}
