package com.example.wallko.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Small shared drawing helpers for the components in this package (drops, sparkles, suns, "zzz").
 * Names are deliberately distinct from the private helpers in DripMascot.kt.
 */

/** Darker tones derived from the palette, for outlines and contrast on Cream. */
internal object Tones {
    val LavenderDeep = lerp(WallkoColors.Lavender, WallkoColors.Ink, 0.3f)
    val SunflowerDeep = lerp(WallkoColors.Sunflower, WallkoColors.Terracotta, 0.3f)
    val PotRim = lerp(WallkoColors.Terracotta, WallkoColors.Soil, 0.35f)
    val Dirt = lerp(WallkoColors.Soil, WallkoColors.Ink, 0.3f)
    val PhotoFrame = Color(0xFFFFFDF7)
}

/** Teardrop whose pointed tip is at [tip] and whose round bottom is [height] below it. */
internal fun teardropPath(tip: Offset, height: Float): Path {
    val (x, y) = tip
    val w = height * 0.5f
    return Path().apply {
        moveTo(x, y)
        cubicTo(x + w, y + height * 0.6f, x + w, y + height, x, y + height)
        cubicTo(x - w, y + height, x - w, y + height * 0.6f, x, y)
        close()
    }
}

internal fun DrawScope.drawWaterDrop(
    tip: Offset,
    height: Float,
    alpha: Float = 1f,
    fill: Color = WallkoColors.Sky,
    outline: Color = WallkoColors.SkyDeep,
) {
    val drop = teardropPath(tip, height)
    drawPath(drop, fill, alpha = alpha)
    drawPath(drop, outline, alpha = alpha, style = Stroke(width = height * 0.12f, join = StrokeJoin.Round))
    // Little shine.
    drawCircle(Color.White, radius = height * 0.09f, center = Offset(tip.x - height * 0.16f, tip.y + height * 0.62f), alpha = 0.7f * alpha)
}

/** Four-pointed twinkle star. */
internal fun DrawScope.drawSparkleStar(center: Offset, radius: Float, color: Color, alpha: Float = 1f) {
    if (radius <= 0f) return
    val (cx, cy) = center
    val star = Path().apply {
        moveTo(cx, cy - radius)
        quadraticTo(cx, cy, cx + radius, cy)
        quadraticTo(cx, cy, cx, cy + radius)
        quadraticTo(cx, cy, cx - radius, cy)
        quadraticTo(cx, cy, cx, cy - radius)
        close()
    }
    drawPath(star, color, alpha = alpha)
}

/** A "z" letter drawn with round strokes. */
internal fun DrawScope.drawSleepyZ(topLeft: Offset, size: Float, color: Color, strokeWidth: Float, alpha: Float = 1f) {
    val (x, y) = topLeft
    val z = Path().apply {
        moveTo(x, y)
        lineTo(x + size, y)
        lineTo(x, y + size)
        lineTo(x + size, y + size)
    }
    drawPath(z, color, alpha = alpha, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Round sun with [rays] rounded rays, rotated by [rayRotationDegrees]. */
internal fun DrawScope.drawSunGlyph(
    center: Offset,
    radius: Float,
    color: Color = WallkoColors.Sunflower,
    rays: Int = 8,
    rayRotationDegrees: Float = 0f,
) {
    val rayStroke = radius * 0.28f
    for (i in 0 until rays) {
        val a = (rayRotationDegrees / 180f * PI.toFloat()) + i * 2f * PI.toFloat() / rays
        val dir = Offset(cos(a), sin(a))
        drawLine(
            color = color,
            start = center + dir * (radius * 1.3f),
            end = center + dir * (radius * 1.75f),
            strokeWidth = rayStroke,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(color, radius = radius, center = center)
    drawCircle(Color.White.copy(alpha = 0.45f), radius = radius * 0.25f, center = center + Offset(-radius * 0.35f, -radius * 0.35f))
}

/**
 * Colour matrix that first moves saturation to [saturation] (1 = unchanged, 0 = grey) and then
 * multiplies each channel by a scale. Used for the Wilt tint and the withered desaturation.
 */
internal fun saturationTintMatrix(saturation: Float, scaleR: Float, scaleG: Float, scaleB: Float): ColorMatrix {
    val inv = 1f - saturation
    val r = 0.213f * inv
    val g = 0.715f * inv
    val b = 0.072f * inv
    return ColorMatrix(
        floatArrayOf(
            (r + saturation) * scaleR, g * scaleR, b * scaleR, 0f, 0f,
            r * scaleG, (g + saturation) * scaleG, b * scaleG, 0f, 0f,
            r * scaleB, g * scaleB, (b + saturation) * scaleB, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

/** Light + dark previews for the components in this package. */
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFFF8E7)
@Preview(
    name = "Dark",
    showBackground = true,
    backgroundColor = 0xFF2B2A33,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class ComponentPreviews

/** Theme + surface wrapper used by the previews in this package. */
@Composable
internal fun ComponentPreviewSurface(content: @Composable () -> Unit) {
    WallkoTheme {
        Surface {
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}
