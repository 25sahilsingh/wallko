package com.example.wallko.ui.card

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.wallko.R
import com.example.wallko.ui.components.ConfettiBurst
import com.example.wallko.ui.components.EstimatedMark
import com.example.wallko.ui.components.GrowthRow
import com.example.wallko.ui.components.PlantStage
import com.example.wallko.ui.components.PolaroidPhoto
import com.example.wallko.ui.components.PottedPlant
import com.example.wallko.ui.components.RarityRibbon
import com.example.wallko.ui.components.SunRow
import com.example.wallko.ui.components.ThirstRow
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoShapes
import java.io.File
import kotlinx.coroutines.delay

/**
 * Public entry point: the plant card as a bottom sheet, for an owned plant id.
 *
 * @param isNewCard plays the new-card moment (slide/flip in with a spring, confetti, sprite bloom).
 * @param bonusDrops shows a "+N drops for a rare find!" line when > 0.
 * @param message optional bubble above the card (e.g. "Rose is already in your garden!").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantCardSheet(
    plantId: Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isNewCard: Boolean = false,
    bonusDrops: Int = 0,
    message: String? = null,
) {
    val viewModel: CardViewModel = hiltViewModel(key = "card_$plantId")
    LaunchedEffect(plantId) { viewModel.load(plantId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = WallkoShapes.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CardStateContent(
            state = state,
            isNewCard = isNewCard,
            bonusDrops = bonusDrops,
            message = message,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

/** Full-page variant (for a nav route). */
@Composable
fun CardScreen(
    plantId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CardViewModel = hiltViewModel(key = "card_$plantId")
    LaunchedEffect(plantId) { viewModel.load(plantId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding()) {
            IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.card_back))
            }
            CardStateContent(state = state, isNewCard = false, bonusDrops = 0, message = null)
        }
    }
}

@Composable
private fun CardStateContent(
    state: CardUiState,
    isNewCard: Boolean,
    bonusDrops: Int,
    message: String?,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CardUiState.Loading -> Box(
            modifier.fillMaxWidth().heightIn(min = 240.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = WallkoColors.Grass)
        }
        CardUiState.NotFound -> Box(
            modifier.fillMaxWidth().heightIn(min = 200.dp).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.card_not_found), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        is CardUiState.Loaded -> PlantCardContent(state.card, isNewCard, bonusDrops, message, modifier)
    }
}

/** The card body. Stateless apart from its entrance animations. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlantCardContent(
    card: CardModel,
    isNewCard: Boolean,
    bonusDrops: Int,
    message: String?,
    modifier: Modifier = Modifier,
) {
    // New card: slides up from below with a spring while flipping in.
    val enter = remember { Animatable(if (isNewCard) 0f else 1f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }
    // Sprite bloom: scale 0 → 1 with overshoot on the new-card moment, and again when the real sprite lands.
    val bloom = remember { Animatable(if (isNewCard) 0f else 1f) }
    val bloomKey = remember { mutableKey(card.spritePath) }
    LaunchedEffect(card.spritePath) {
        val spriteJustArrived = bloomKey.previous == null && card.spritePath != null && bloomKey.seen
        bloomKey.previous = card.spritePath
        bloomKey.seen = true
        if (spriteJustArrived) bloom.snapTo(0f)
        if (bloom.value < 1f) {
            delay(if (isNewCard && !spriteJustArrived) 250 else 0)
            bloom.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val t = enter.value
                translationY = (1f - t) * 420.dp.toPx()
                rotationY = (1f - t) * -70f
                alpha = t.coerceIn(0f, 1f)
                cameraDistance = 12f * density
            },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (message != null) Banner(message, WallkoColors.Sky)
            if (isNewCard) Banner(stringResource(R.string.card_new), WallkoColors.Berry)
            if (bonusDrops > 0) {
                Banner(pluralStringResource(R.plurals.ui_card_rare_bonus, bonusDrops, bonusDrops), WallkoColors.Sunflower, Icons.Rounded.WaterDrop)
            }

            val sprite = rememberSprite(card.spritePath)
            val stage = PlantStage.valueOf(card.state.name)
            PottedPlant(
                stage = stage,
                sprite = sprite,
                size = 180.dp,
                growthFraction = card.growthFraction,
                contentDescription = stringResource(R.string.card_hero_description, card.name),
                modifier = Modifier.graphicsLayer {
                    scaleX = bloom.value
                    scaleY = bloom.value
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
            )

            Text(
                card.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            card.latin?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
            }
            RarityRibbon(card.rarity)
            card.family?.let {
                Text(stringResource(R.string.card_family, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Surface(
                shape = WallkoShapes.Card,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThirstRow(level = card.thirst, estimated = card.estimated.thirst, label = stringResource(R.string.card_thirst))
                    SunRow(level = card.sun, estimated = card.estimated.sun, label = stringResource(R.string.card_sun))
                    GrowthRow(speed = card.growth, estimated = card.estimated.growth, label = stringResource(R.string.card_growth))
                    BloomRow(card.bloom, card.estimated.bloom)
                }
            }

            if (card.badges.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    card.badges.forEach { Badge(it) }
                }
            }

            card.description?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }

            val foundText = card.foundOn.place
                ?.let { stringResource(R.string.card_found_on_at, card.foundOn.date, it) }
                ?: stringResource(R.string.card_found_on, card.foundOn.date)
            PolaroidPhoto(
                photoPath = card.photoPath,
                caption = card.foundOn.date,
                subCaption = card.foundOn.place,
                width = 220.dp,
                contentDescription = stringResource(R.string.scan_result_photo),
            )
            Text(foundText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }

        if (isNewCard) {
            ConfettiBurst(
                trigger = card.plantId,
                origin = Offset(0.5f, 0.2f),
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Tracks the previous sprite path so the bloom replays when a seedling's sprite first lands. */
private class SpriteKey(var previous: String?, var seen: Boolean)

private fun mutableKey(initial: String?) = SpriteKey(initial, false)

@Composable
private fun rememberSprite(path: String?): ImageBitmap? {
    val context = LocalContext.current
    val sprite by produceState<ImageBitmap?>(initialValue = null, path) {
        value = path?.let { p ->
            val request = ImageRequest.Builder(context).data(File(p)).allowHardware(false).build()
            (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()?.asImageBitmap()
        }
    }
    return sprite
}

@Composable
private fun BloomRow(bloom: BloomText, estimated: Boolean) {
    val text = when (bloom) {
        BloomText.Unknown -> stringResource(R.string.card_bloom_unknown)
        BloomText.AllYear -> stringResource(R.string.card_bloom_all_year)
        is BloomText.Months -> bloom.text
    }
    // Merged so TalkBack reads "Blooms, Mar–May"; the "~" hint stays its own focusable button.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Icon(Icons.Rounded.LocalFlorist, contentDescription = null, tint = WallkoColors.Berry, modifier = Modifier.padding(end = 8.dp))
        Text(
            stringResource(R.string.card_bloom_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (estimated) EstimatedMark(hint = stringResource(R.string.card_estimated_hint))
    }
}

@Composable
private fun Badge(label: String) {
    // Was Ink on 50 % Lavender, which turns muddy (~2:1) over the dark sheet; the tertiary container pair
    // is Lavender-tinted in both themes with a matching readable text colour.
    Surface(shape = WallkoShapes.Button, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Banner(text: String, color: Color, icon: ImageVector? = null) {
    Surface(shape = WallkoShapes.Bubble, color = color.copy(alpha = 0.35f)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, contentDescription = null, tint = WallkoColors.SkyDeep, modifier = Modifier.padding(end = 6.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
