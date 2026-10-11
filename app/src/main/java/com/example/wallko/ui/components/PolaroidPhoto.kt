package com.example.wallko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.wallko.R
import com.example.wallko.ui.theme.WallkoColors
import java.io.File

private val FrameShape = RoundedCornerShape(8.dp)
private val PhotoShape = RoundedCornerShape(4.dp)

/**
 * The user's original photo in a slightly rotated polaroid frame with a strip of tape,
 * a caption (e.g. the date) and an optional sub-caption (e.g. the place name).
 * The frame stays paper-coloured in both themes, so its text is always Ink.
 *
 * @param photoPath absolute file path of the saved photo; null or unreadable shows a placeholder.
 */
@Composable
fun PolaroidPhoto(
    photoPath: String?,
    caption: String,
    modifier: Modifier = Modifier,
    subCaption: String? = null,
    width: Dp = 200.dp,
    tilt: Float = -3f,
    contentDescription: String = stringResource(R.string.ui_polaroid_description),
) {
    val model = remember(photoPath) { photoPath?.let { File(it) } }
    val description = listOfNotNull(contentDescription, caption, subCaption).joinToString(", ")
    Box(
        modifier
            .rotate(tilt)
            .clearAndSetSemantics { this.contentDescription = description },
    ) {
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(width)
                .shadow(6.dp, FrameShape)
                .clip(FrameShape)
                .background(Tones.PhotoFrame)
                .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(PhotoShape)
                    .background(WallkoColors.PeachSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFlorist,
                    contentDescription = null,
                    tint = WallkoColors.Peach,
                    modifier = Modifier.size(width / 4),
                )
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.titleSmall,
                color = WallkoColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subCaption != null) {
                Text(
                    text = subCaption,
                    style = MaterialTheme.typography.bodySmall,
                    color = WallkoColors.Soil,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // A strip of tape holding the photo down.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .rotate(4f)
                .size(width = width * 0.3f, height = 18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(WallkoColors.Sunflower.copy(alpha = 0.55f)),
        )
    }
}

@ComponentPreviews
@Composable
private fun PolaroidPhotoPreview() {
    ComponentPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PolaroidPhoto(photoPath = null, caption = "12 Oct 2026", subCaption = "Riverside Park", width = 160.dp)
            PolaroidPhoto(photoPath = null, caption = "3 Sep 2026", width = 140.dp, tilt = 4f)
        }
    }
}
