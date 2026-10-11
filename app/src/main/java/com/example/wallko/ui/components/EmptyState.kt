package com.example.wallko.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.wallko.ui.theme.WallkoShapes
import com.example.wallko.ui.theme.WallkoTheme

/** Drip with a speech bubble, plus an optional call to action. */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    expression: DripExpression = DripExpression.HAPPY,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpeechBubble(message)
        Spacer(Modifier.height(4.dp))
        DripMascot(expression, size = 140.dp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            ToyButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
private fun SpeechBubble(text: String) {
    val bubbleColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = WallkoShapes.Bubble,
            color = bubbleColor,
            shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
        // Rounded tail pointing down at Drip.
        Canvas(Modifier.size(width = 24.dp, height = 12.dp)) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                quadraticTo(size.width * 0.6f, size.height * 0.4f, size.width * 0.5f, size.height)
                quadraticTo(size.width * 0.4f, size.height * 0.4f, 0f, 0f)
                close()
            }
            drawPath(tail, bubbleColor)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF8E7)
@Composable
private fun EmptyStatePreview() {
    WallkoTheme {
        EmptyState(
            message = "Let's find your first plant!",
            actionLabel = "Scan a plant",
            onAction = {},
        )
    }
}
