package com.example.wallko.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wallko.R
import com.example.wallko.core.db.Rarity
import com.example.wallko.ui.card.PlantCardSheet
import com.example.wallko.ui.components.DripExpression
import com.example.wallko.ui.components.EmptyState
import com.example.wallko.ui.components.PlantStage
import com.example.wallko.ui.components.PotFace
import com.example.wallko.ui.components.PottedPlant
import com.example.wallko.ui.components.RarityRibbon
import com.example.wallko.ui.components.SparkleSwirl
import com.example.wallko.ui.components.ToyButton
import com.example.wallko.ui.components.defaultFace
import com.example.wallko.ui.components.label
import com.example.wallko.ui.components.paletteColor
import com.example.wallko.ui.garden.rememberSpriteBitmap
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoShapes
import com.example.wallko.ui.theme.WallkoTheme
import kotlinx.coroutines.delay
import java.text.NumberFormat

/** Every owned card, withered ones greyed with a revive button. Reached from the Garden top bar. */
@Composable
fun CollectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var sparkle by remember { mutableStateOf<SparkleTrigger?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            val message = when (event) {
                is CollectionEvent.Revived -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    sparkle = SparkleTrigger(event.plantId, System.nanoTime())
                    context.getString(R.string.collection_revived, event.name)
                }
                is CollectionEvent.NotEnoughElixir ->
                    context.resources.getQuantityString(
                        R.plurals.collection_not_enough_elixir, event.need, event.need, event.have,
                    )
                CollectionEvent.AlreadyInGarden -> context.getString(R.string.collection_already_in_garden)
                CollectionEvent.NotWithered -> context.getString(R.string.collection_not_withered)
                CollectionEvent.NotFound -> context.getString(R.string.collection_not_found)
                CollectionEvent.Failed -> context.getString(R.string.collection_revive_failed)
            }
            snackbar.showSnackbar(message)
        }
    }
    // Revive face sequence: surprised while the sparkles swirl, then back to the stage's own face.
    LaunchedEffect(sparkle) {
        if (sparkle != null) {
            delay(SPARKLE_MS)
            sparkle = null
        }
    }

    Box(modifier.fillMaxSize()) {
        CollectionContent(
            state = state,
            sparkle = sparkle,
            onBack = onBack,
            onRarity = viewModel::setRarity,
            onType = viewModel::setType,
            onCardClick = { selectedId = it },
            onRevive = viewModel::revive,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    selectedId?.let { id -> PlantCardSheet(plantId = id, onDismiss = { selectedId = null }) }
}

/** Which card is sparkling; [nonce] restarts the swirl on repeated revives. */
internal data class SparkleTrigger(val plantId: Long, val nonce: Long)

@Composable
internal fun CollectionContent(
    state: CollectionUiState,
    sparkle: SparkleTrigger?,
    onBack: () -> Unit,
    onRarity: (Rarity?) -> Unit,
    onType: (String?) -> Unit,
    onCardClick: (Long) -> Unit,
    onRevive: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.collection_back))
            }
            Text(
                stringResource(R.string.collection_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        ProgressHeader(state.foundSpecies, state.totalSpecies)

        if (state.ownedAny) {
            FilterRow(
                selectedRarity = state.filters.rarity,
                types = state.types,
                selectedType = state.filters.type,
                onRarity = onRarity,
                onType = onType,
            )
        }

        when {
            state.loading -> Unit
            !state.ownedAny -> EmptyState(
                message = stringResource(R.string.collection_empty),
                expression = DripExpression.CURIOUS,
                modifier = Modifier.fillMaxWidth(),
            )
            state.cards.isEmpty() -> EmptyState(
                message = stringResource(R.string.collection_empty_filtered),
                expression = DripExpression.WORRIED,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.cards, key = { it.id }) { card ->
                    CollectionCardTile(
                        card = card,
                        sparkle = sparkle?.takeIf { it.plantId == card.id },
                        reviving = state.revivingId == card.id,
                        reviveEnabled = state.revivingId == null,
                        reviveCost = state.reviveCost,
                        onClick = { onCardClick(card.id) },
                        onRevive = { onRevive(card.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(found: Int, total: Int) {
    val fmt = remember { NumberFormat.getIntegerInstance() }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(
            pluralStringResource(R.plurals.collection_progress, total, fmt.format(found), fmt.format(total)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) (found.toFloat() / total).coerceIn(0f, 1f) else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            color = WallkoColors.Grass,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    selectedRarity: Rarity?,
    types: List<String>,
    selectedType: String?,
    onRarity: (Rarity?) -> Unit,
    onType: (String?) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        // Wraps instead of scrolling: six rarity chips don't fit one line on a phone, and a
        // half-visible "Epic" at the edge looked broken rather than scrollable.
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WallkoChip(stringResource(R.string.collection_filter_all), selectedRarity == null, WallkoColors.Sunflower) {
                onRarity(null)
            }
            Rarity.entries.forEach { r ->
                WallkoChip(r.label(), selectedRarity == r, r.paletteColor()) {
                    onRarity(if (selectedRarity == r) null else r)
                }
            }
        }
        if (types.size > 1) {
            Spacer(Modifier.height(4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    WallkoChip(stringResource(R.string.collection_filter_all), selectedType == null, WallkoColors.Sunflower) {
                        onType(null)
                    }
                }
                items(types) { t ->
                    WallkoChip(t.replaceFirstChar { it.uppercase() }, selectedType == t, WallkoColors.Sky) {
                        onType(if (selectedType == t) null else t)
                    }
                }
            }
        }
    }
}

@Composable
private fun WallkoChip(label: String, selected: Boolean, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Bold) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = WallkoColors.Ink,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionCardTile(
    card: CollectionCard,
    sparkle: SparkleTrigger?,
    reviving: Boolean,
    reviveEnabled: Boolean,
    reviveCost: Int,
    onClick: () -> Unit,
    onRevive: () -> Unit,
) {
    val sprite by rememberSpriteBitmap(card.spritePath, card.spriteVersion)
    val face = when {
        sparkle != null -> PotFace.SURPRISED
        else -> card.stage.defaultFace()
    }
    // Clickable Surface: ripple is clipped to the card shape and the tile's texts merge into one
    // TalkBack node ("Rose, Rosa canina, Rarity: Rare, Withered"); the Revive button stays separate.
    Surface(
        onClick = onClick,
        shape = WallkoShapes.Card,
        color = if (card.withered) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Column(
                Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val dim = if (card.withered) Modifier.alpha(0.6f) else Modifier
                PottedPlant(
                    stage = card.stage,
                    sprite = sprite,
                    size = 96.dp,
                    growthFraction = card.growthFraction,
                    swayPhase = (card.id % 10) / 10f,
                    face = face,
                    contentDescription = card.name,
                    // The name is read from the Text below; the pot itself is decorative here.
                    modifier = dim.clearAndSetSemantics {},
                )
                Text(
                    card.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = dim,
                )
                Text(
                    card.latin,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = dim,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    RarityRibbon(card.rarity, compact = true, modifier = dim)
                    // Not dimmed: onSurfaceVariant at 60 % alpha falls below 4.5:1 in both themes.
                    if (card.withered) {
                        Text(
                            stringResource(R.string.collection_withered),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (card.withered) {
                    Spacer(Modifier.height(8.dp))
                    ToyButton(
                        text = pluralStringResource(R.plurals.collection_revive, reviveCost, reviveCost),
                        onClick = onRevive,
                        enabled = reviveEnabled && !reviving,
                        containerColor = WallkoColors.Lavender,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            SparkleSwirl(
                trigger = sparkle,
                modifier = Modifier.matchParentSize(),
                center = androidx.compose.ui.geometry.Offset(0.5f, 0.35f),
            )
        }
    }
}

private const val SPARKLE_MS = 1_400L

// ---------------------------------------------------------------------------------------------
// Previews

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun CollectionPreview() {
    val cards = listOf(
        CollectionCard(1, 1, "Rose", "Rosa canina", Rarity.RARE, "shrub", PlantStage.GROWN, 1f, null, 0, 2),
        CollectionCard(2, 2, "Fern", "Polypodium vulgare", Rarity.COMMON, "fern", PlantStage.WITHERED, 1f, null, 0, 1),
        CollectionCard(3, 3, "Oak", "Quercus robur", Rarity.LEGENDARY, "tree", PlantStage.SEEDLING, 0f, null, 0, 0),
    )
    WallkoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CollectionContent(
                state = CollectionUiState(
                    loading = false, cards = cards, foundSpecies = 42, totalSpecies = 2101,
                    types = listOf("fern", "shrub", "tree"), ownedAny = true, elixir = 1,
                ),
                sparkle = null, onBack = {}, onRarity = {}, onType = {}, onCardClick = {}, onRevive = {},
            )
        }
    }
}
