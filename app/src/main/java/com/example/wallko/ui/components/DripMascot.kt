package com.example.wallko.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.wallko.R
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoTheme

enum class DripExpression { HAPPY, CURIOUS, WORRIED, SLEEPY, CHEERING }

private val Body = WallkoColors.Sky
private val BodyShade = WallkoColors.SkyDeep
private val Face = WallkoColors.Ink
private val Blush = WallkoColors.Berry.copy(alpha = 0.35f)
private val Shine = Color.White.copy(alpha = 0.45f)

/** Drip, the watering-can mascot. Drawn entirely on a Canvas in a 100 x 100 unit box. */
@Composable
fun DripMascot(
    expression: DripExpression,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
) {
    val description = stringResource(R.string.drip_description)
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = description },
    ) {
        val canvas = this.size
        val unit = canvas.minDimension / 100f
        withTransform({
            translate(
                left = (canvas.width - 100f * unit) / 2f,
                top = (canvas.height - 100f * unit) / 2f,
            )
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            // Curious Drip tilts its head.
            val tilt = if (expression == DripExpression.CURIOUS) -8f else 0f
            rotate(tilt, pivot = Offset(50f, 64f)) {
                drawCan()
                drawFace(expression)
            }
            drawExtras(expression)
        }
    }
}

private fun DrawScope.drawCan() {
    val outline = Stroke(width = 2.5f, join = StrokeJoin.Round, cap = StrokeCap.Round)

    // Handle: a thick rounded arch on the left.
    drawArc(
        color = BodyShade,
        startAngle = 90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(8f, 46f),
        size = Size(30f, 34f),
        style = Stroke(width = 7f, cap = StrokeCap.Round),
    )

    // Spout: a tapered tube rising to the right, ending in a round rose.
    val spout = Path().apply {
        moveTo(70f, 60f)
        lineTo(87f, 38f)
        quadraticTo(92f, 36f, 94f, 42f)
        lineTo(74f, 76f)
        close()
    }
    drawPath(spout, Body)
    drawPath(spout, BodyShade, style = outline)
    drawCircle(BodyShade, radius = 6f, center = Offset(91f, 38f))
    drawCircle(Body, radius = 3.5f, center = Offset(91f, 38f))

    // Lid and knob.
    drawRoundRect(BodyShade, Offset(30f, 30f), Size(38f, 12f), CornerRadius(6f))
    drawCircle(BodyShade, radius = 5f, center = Offset(49f, 27f))
    drawCircle(Shine, radius = 1.6f, center = Offset(47.5f, 25.5f))

    // Body: a chubby rounded tub, slightly wider at the bottom.
    val body = Path().apply {
        moveTo(34f, 38f)
        lineTo(64f, 38f)
        quadraticTo(76f, 38f, 77f, 52f)
        lineTo(78f, 80f)
        quadraticTo(78f, 92f, 66f, 92f)
        lineTo(32f, 92f)
        quadraticTo(20f, 92f, 20f, 80f)
        lineTo(21f, 52f)
        quadraticTo(22f, 38f, 34f, 38f)
        close()
    }
    drawPath(body, Body)
    // Darker belly band near the base gives it some weight.
    drawRoundRect(BodyShade.copy(alpha = 0.45f), Offset(21f, 82f), Size(57f, 10f), CornerRadius(8f))
    drawPath(body, BodyShade, style = outline)

    // Shine streak.
    drawRoundRect(Shine, Offset(26f, 46f), Size(4.5f, 16f), CornerRadius(2.25f))
}

private fun DrawScope.drawFace(expression: DripExpression) {
    val line = Stroke(width = 2.4f, cap = StrokeCap.Round)
    val leftEye = Offset(40f, 62f)
    val rightEye = Offset(58f, 62f)

    // Cheeks.
    drawCircle(Blush, radius = 4.5f, center = Offset(33f, 70f))
    drawCircle(Blush, radius = 4.5f, center = Offset(65f, 70f))

    when (expression) {
        DripExpression.HAPPY -> {
            drawDotEye(leftEye, 3.6f)
            drawDotEye(rightEye, 3.6f)
            drawArc(Face, 20f, 140f, false, Offset(43f, 64f), Size(12f, 9f), style = line)
        }

        DripExpression.CURIOUS -> {
            drawDotEye(leftEye, 3.2f)
            drawDotEye(rightEye, 4.4f)
            // One raised eyebrow.
            drawLine(Face, Offset(54f, 53f), Offset(62f, 51f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawCircle(Face, radius = 2.6f, center = Offset(49f, 73f), style = Stroke(width = 2.2f))
        }

        DripExpression.WORRIED -> {
            drawDotEye(leftEye, 3.4f)
            drawDotEye(rightEye, 3.4f)
            // Brows tilted up toward the middle.
            drawLine(Face, Offset(35f, 55f), Offset(43f, 53f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawLine(Face, Offset(55f, 53f), Offset(63f, 55f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawArc(Face, 200f, 140f, false, Offset(44f, 71f), Size(10f, 7f), style = line)
        }

        DripExpression.SLEEPY -> {
            // Closed eyes: little downward curves.
            drawArc(Face, 20f, 140f, false, Offset(36f, 59f), Size(8f, 5f), style = line)
            drawArc(Face, 20f, 140f, false, Offset(54f, 59f), Size(8f, 5f), style = line)
            drawOval(Face, Offset(47f, 70f), Size(4f, 3f))
        }

        DripExpression.CHEERING -> {
            // Happy "^ ^" eyes.
            drawArc(Face, 200f, 140f, false, Offset(35.5f, 60f), Size(9f, 7f), style = line)
            drawArc(Face, 200f, 140f, false, Offset(53.5f, 60f), Size(9f, 7f), style = line)
            // Big open smile with a tongue.
            drawArc(Face, 0f, 180f, true, Offset(42f, 64f), Size(14f, 12f))
            drawArc(WallkoColors.Berry, 0f, 180f, true, Offset(45.5f, 70f), Size(7f, 5f))
        }
    }
}

private fun DrawScope.drawDotEye(center: Offset, radius: Float) {
    drawCircle(Face, radius = radius, center = center)
    drawCircle(Color.White, radius = radius * 0.35f, center = center + Offset(radius * 0.35f, -radius * 0.35f))
}

private fun DrawScope.drawExtras(expression: DripExpression) {
    when (expression) {
        DripExpression.WORRIED -> drawSweatDrop(Offset(78f, 30f))
        DripExpression.SLEEPY -> {
            drawZ(Offset(72f, 18f), 7f)
            drawZ(Offset(84f, 8f), 5f)
        }
        DripExpression.CHEERING -> {
            drawSparkle(Offset(14f, 30f), 7f, WallkoColors.Sunflower)
            drawSparkle(Offset(86f, 16f), 6f, WallkoColors.Sunflower)
            drawSparkle(Offset(24f, 14f), 4f, WallkoColors.Berry)
        }
        DripExpression.CURIOUS -> drawSparkle(Offset(84f, 18f), 5f, WallkoColors.Sunflower)
        DripExpression.HAPPY -> Unit
    }
}

private fun DrawScope.drawSweatDrop(top: Offset) {
    val (x, y) = top
    val drop = Path().apply {
        moveTo(x, y)
        cubicTo(x + 5f, y + 7f, x + 5f, y + 11f, x, y + 11f)
        cubicTo(x - 5f, y + 11f, x - 5f, y + 7f, x, y)
        close()
    }
    drawPath(drop, WallkoColors.Sky)
    drawPath(drop, WallkoColors.SkyDeep, style = Stroke(width = 1.5f, join = StrokeJoin.Round))
}

private fun DrawScope.drawZ(topLeft: Offset, size: Float) {
    val (x, y) = topLeft
    val z = Path().apply {
        moveTo(x, y)
        lineTo(x + size, y)
        lineTo(x, y + size)
        lineTo(x + size, y + size)
    }
    drawPath(z, WallkoColors.Lavender, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val (cx, cy) = center
    val star = Path().apply {
        moveTo(cx, cy - radius)
        quadraticTo(cx, cy, cx + radius, cy)
        quadraticTo(cx, cy, cx, cy + radius)
        quadraticTo(cx, cy, cx - radius, cy)
        quadraticTo(cx, cy, cx, cy - radius)
        close()
    }
    drawPath(star, color)
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF8E7)
@Composable
private fun DripMascotPreview() {
    WallkoTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DripExpression.entries.forEach { expression ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DripMascot(expression, size = 96.dp)
                    Text(expression.name)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF2B2A33)
@Composable
private fun DripMascotDarkPreview() {
    WallkoTheme(darkTheme = true) {
        Surface {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DripExpression.entries.forEach { DripMascot(it, size = 72.dp) }
            }
        }
    }
}
