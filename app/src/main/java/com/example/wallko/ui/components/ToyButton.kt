package com.example.wallko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoShapes
import com.example.wallko.ui.theme.WallkoTheme

private val ToyButtonHeight = 56.dp
private val EdgeHeight = 2.dp

/**
 * Chunky, fully round "toy" button: soft drop shadow plus a 2 dp darker bottom edge
 * that the face sinks into while pressed. At least 56 dp tall; grows (and the label wraps)
 * at large font scales instead of clipping.
 */
@Composable
fun ToyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = WallkoColors.Grass,
    contentColor: Color = WallkoColors.Ink,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val edgeColor = lerp(containerColor, Color.Black, 0.2f)

    Box(
        modifier = modifier
            .heightIn(min = ToyButtonHeight)
            .alpha(if (enabled) 1f else 0.5f)
            .shadow(
                elevation = if (pressed) 2.dp else 6.dp,
                shape = WallkoShapes.Button,
                ambientColor = edgeColor,
                spotColor = edgeColor,
            )
            .clip(WallkoShapes.Button)
            .background(edgeColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ToyButtonHeight)
                .padding(top = if (pressed) EdgeHeight else 0.dp, bottom = if (pressed) 0.dp else EdgeHeight)
                .clip(WallkoShapes.Button)
                .background(containerColor)
                .padding(horizontal = 28.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF8E7)
@Composable
private fun ToyButtonPreview() {
    WallkoTheme {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ToyButton("Scan a plant", onClick = {}, icon = Icons.Rounded.CameraAlt)
            ToyButton("Start walk", onClick = {}, containerColor = WallkoColors.Sky)
            ToyButton("Disabled", onClick = {}, enabled = false)
        }
    }
}
