package com.example.wallko.ui.garden

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wallko.R
import com.example.wallko.data.garden.GardenSpot
import com.example.wallko.ui.card.PlantCardSheet
import com.example.wallko.ui.components.DripExpression
import com.example.wallko.ui.components.ElixirBadge
import com.example.wallko.ui.components.EmptyState
import com.example.wallko.ui.components.FallingDrops
import com.example.wallko.ui.components.PlantStage
import com.example.wallko.ui.components.PotFace
import com.example.wallko.ui.components.PottedPlant
import com.example.wallko.ui.components.TankGauge
import com.example.wallko.ui.components.TankGaugeVariant
import com.example.wallko.ui.components.defaultFace
import com.example.wallko.ui.components.rememberLoopOrStill
import com.example.wallko.ui.components.rememberReducedMotion
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoMotion
import com.example.wallko.ui.theme.WallkoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The garden tab: a scrolling meadow with two shelf rows and the 4 × 6 ground grid. Long-press a pot to drag it
 * (snaps to the nearest free cell or shelf slot); tap it for its card. Plays the drinking moment after a catch-up.
 *
 * [onFindPlant] opens the Scan tab (empty state). [onOpenCollection] / [onOpenSettings] are the top-bar buttons.
 */
@Composable
fun GardenScreen(
    onFindPlant: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCollection: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: GardenViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    GardenContent(
        state = state,
        onFindPlant = onFindPlant,
        onOpenCollection = onOpenCollection,
        onOpenSettings = onOpenSettings,
        onPlantClick = { selectedId = it },
        onMove = viewModel::move,
        onDrinkingShown = viewModel::onDrinkingShown,
        modifier = modifier,
    )

    selectedId?.let { id -> PlantCardSheet(plantId = id, onDismiss = { selectedId = null }) }
}

@Composable
internal fun GardenContent(
    state: GardenUiState,
    onFindPlant: () -> Unit,
    onOpenCollection: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlantClick: (Long) -> Unit,
    onMove: (Long, GardenSpot) -> Unit,
    onDrinkingShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Drinking moment: hold the tank at its pre-drink level, drop water into the pots, then let the tank drain.
    val drinking = state.drinking
    var tankReleased by remember(drinking?.key) { mutableStateOf(false) }
    var dropsTrigger by remember(drinking?.key) { mutableStateOf<Any?>(null) }
    val latestOnShown by rememberUpdatedState(onDrinkingShown)
    LaunchedEffect(drinking?.key) {
        if (drinking == null) return@LaunchedEffect
        delay(DRINK_START_DELAY_MS)
        dropsTrigger = drinking.key
        delay(DRINK_TANK_DELAY_MS)
        tankReleased = true
        delay(DRINK_FINISH_DELAY_MS)
        latestOnShown()
    }
    val shownDrops = if (drinking != null && !tankReleased) drinking.tankBefore else state.drops

    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> MeadowBackdrop(Modifier.fillMaxSize())
            state.plants.isEmpty() -> EmptyGarden(onFindPlant, Modifier.fillMaxSize())
            else -> Meadow(
                plants = state.plants,
                drinkingIds = drinking?.drankIds.orEmpty(),
                dropsTrigger = dropsTrigger,
                onPlantClick = onPlantClick,
                onMove = onMove,
                modifier = Modifier.fillMaxSize(),
            )
        }
        GardenTopBar(
            drops = shownDrops,
            capacity = state.capacity,
            elixir = state.elixir,
            elixirProgress = state.elixirProgress,
            onOpenCollection = onOpenCollection,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun GardenTopBar(
    drops: Int,
    capacity: Int,
    elixir: Int,
    elixirProgress: Float,
    onOpenCollection: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(Icons.Rounded.CollectionsBookmark, stringResource(R.string.garden_open_collection), onOpenCollection)
        Spacer(Modifier.width(8.dp))
        RoundIconButton(Icons.Rounded.Settings, stringResource(R.string.garden_open_settings), onOpenSettings)
        Spacer(Modifier.weight(1f))
        ElixirBadge(count = elixir, progressToNext = elixirProgress)
        Spacer(Modifier.width(8.dp))
        TankGauge(drops = drops, capacity = capacity, variant = TankGaugeVariant.Compact)
    }
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 2.dp) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun EmptyGarden(onFindPlant: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        MeadowBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = TOP_BAR_SPACE),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PottedPlant(stage = PlantStage.SEEDLING, sprite = null, size = 110.dp, face = PotFace.CURIOUS)
            EmptyState(
                message = stringResource(R.string.garden_empty),
                expression = DripExpression.HAPPY,
                actionLabel = stringResource(R.string.garden_empty_action),
                onAction = onFindPlant,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Meadow + pots

@Composable
private fun Meadow(
    plants: List<GardenPlantUi>,
    drinkingIds: Set<Long>,
    dropsTrigger: Any?,
    onPlantClick: (Long) -> Unit,
    onMove: (Long, GardenSpot) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val viewportPx = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else 0f
        val cellW = widthPx / GardenMath.COLUMNS
        val potPx = cellW * POT_FRACTION
        val maxShelf = plants.filter { it.spot.onShelf }.maxOfOrNull { it.spot.x }
        val layout = with(density) {
            GardenLayout(
                width = widthPx,
                shelfRows = GardenMath.shelfRowsFor(maxShelf),
                shelfRowHeight = potPx * 1.15f,
                cellHeight = cellW * 0.95f,
                topPadding = TOP_BAR_SPACE.toPx(),
                gap = 28.dp.toPx(),
                bottomPadding = 40.dp.toPx(),
            )
        }
        val contentHeight = with(density) { maxOf(layout.totalHeight, viewportPx).toDp() }
        val occupied = plants.mapTo(HashSet()) { it.spot }

        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(contentHeight),
            ) {
                MeadowCanvas(layout, potPx, Modifier.fillMaxSize())
                plants.forEach { plant ->
                    androidx.compose.runtime.key(plant.id) {
                        GardenPot(
                            plant = plant,
                            layout = layout,
                            potPx = potPx,
                            occupied = occupied,
                            drinking = plant.id in drinkingIds,
                            dropsTrigger = dropsTrigger,
                            onClick = { onPlantClick(plant.id) },
                            onMove = { onMove(plant.id, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GardenPot(
    plant: GardenPlantUi,
    layout: GardenLayout,
    potPx: Float,
    occupied: Set<GardenSpot>,
    drinking: Boolean,
    dropsTrigger: Any?,
    onClick: () -> Unit,
    onMove: (GardenSpot) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sprite by rememberSpriteBitmap(plant.spritePath, plant.spriteVersion)

    val home = layout.center(plant.spot).let { Offset(it.x, it.y) }
    val pos = remember { Animatable(home, Offset.VectorConverter) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val bounce = remember { Animatable(1f) }

    // Glide to the (new) home spot whenever it changes and we're not holding the pot.
    LaunchedEffect(home) {
        if (!dragging) pos.animateTo(home, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }

    val latestPlant by rememberUpdatedState(plant)
    val latestLayout by rememberUpdatedState(layout)
    val latestOccupied by rememberUpdatedState(occupied)
    val latestOnMove by rememberUpdatedState(onMove)
    val latestOnClick by rememberUpdatedState(onClick)

    val lift by animateFloatAsState(if (dragging) 1.12f else 1f, label = "potLift")
    val potSize = with(density) { potPx.toDp() }
    val name = plant.name
    val description = stringResource(R.string.garden_plant_description, name)
    val wiltingState = stringResource(R.string.garden_wilting_badge)
    val openCardLabel = stringResource(R.string.garden_open_card)

    val tap = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch { bounce.animateTo(1f, WallkoMotion.TapBounce) }
        latestOnClick()
    }

    Box(
        Modifier
            .offset {
                val p = pos.value + dragDelta
                IntOffset((p.x - potPx / 2f).roundToInt(), (p.y - potPx / 2f).roundToInt())
            }
            .size(potSize)
            .zIndex(if (dragging) 2f else if (drinking) 1f else 0f)
            .graphicsLayer {
                val s = bounce.value
                scaleY = s * lift
                scaleX = (2f - s) * lift
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .pointerInput(plant.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragDelta = Offset.Zero
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDelta += amount
                    },
                    onDragEnd = {
                        val drop = pos.value + dragDelta
                        val current = latestPlant.spot
                        val target = GardenMath.snapToNearestFree(
                            latestLayout,
                            GardenPoint(drop.x, drop.y),
                            latestOccupied,
                            current,
                        )
                        // A light tick as the pot settles into its (new or old) spot.
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        scope.launch {
                            pos.snapTo(drop)
                            dragDelta = Offset.Zero
                            dragging = false
                            if (target != current) {
                                latestOnMove(target)
                            } else {
                                val back = latestLayout.center(current)
                                pos.animateTo(Offset(back.x, back.y), spring(dampingRatio = 0.7f))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            pos.snapTo(pos.value + dragDelta)
                            dragDelta = Offset.Zero
                            dragging = false
                            val back = latestLayout.center(latestPlant.spot)
                            pos.animateTo(Offset(back.x, back.y), spring(dampingRatio = 0.7f))
                        }
                    },
                )
            }
            .pointerInput(plant.id) {
                // Inner detector runs before the drag one; a long-press release must not also open the card.
                detectTapGestures(onTap = { if (!dragging) tap() })
            }
            .clearAndSetSemantics {
                contentDescription = description
                if (plant.wilting) stateDescription = wiltingState
                role = Role.Button
                onClick(label = openCardLabel) { tap(); true }
            },
    ) {
        PottedPlant(
            stage = plant.stage,
            sprite = sprite,
            size = potSize,
            growthFraction = plant.growthFraction,
            swayPhase = plant.swayPhase,
            face = if (drinking && plant.stage != PlantStage.WILTING) PotFace.HAPPY else plant.stage.defaultFace(),
            contentDescription = name,
        )
        if (plant.wilting) {
            WiltBubble(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp),
            )
        }
        if (drinking) {
            FallingDrops(
                trigger = dropsTrigger,
                modifier = Modifier.fillMaxSize(),
                lanes = DrinkLanes,
                landFraction = 0.64f,
                dropCount = 6,
                durationMillis = 1_600,
            )
        }
    }
}

/** The little "!" bubble over a wilting pot, bobbing gently. */
@Composable
private fun WiltBubble(modifier: Modifier = Modifier) {
    val bob by rememberLoopOrStill(
        reducedMotion = rememberReducedMotion(),
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "wiltBob",
    )
    val label = stringResource(R.string.garden_wilting_badge)
    Box(
        modifier
            .graphicsLayer { translationY = -bob * 4.dp.toPx() }
            .size(26.dp)
            .clip(CircleShape)
            .background(WallkoColors.Sunflower)
            .border(2.dp, WallkoColors.Terracotta, CircleShape)
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // Always Ink on Sunflower, readable in both themes.
        Text("!", color = WallkoColors.Ink, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
    }
}

// ---------------------------------------------------------------------------------------------
// Meadow drawing

/** Meadow without shelves or cells (loading and empty states). */
@Composable
private fun MeadowBackdrop(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else w * 2f
        val layout = with(density) {
            GardenLayout(
                width = w,
                shelfRows = 0,
                shelfRowHeight = 0f,
                cellHeight = 0f,
                topPadding = h * 0.28f,
                bottomPadding = h * 0.72f,
            )
        }
        MeadowCanvas(layout, potPx = 0f, modifier = Modifier.fillMaxSize(), drawCells = false)
    }
}

@Composable
private fun MeadowCanvas(
    layout: GardenLayout,
    potPx: Float,
    modifier: Modifier = Modifier,
    drawCells: Boolean = true,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val drift by rememberLoopOrStill(
        reducedMotion = rememberReducedMotion(),
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CLOUD_PERIOD_MS, easing = LinearEasing)),
        label = "cloudDrift",
    )
    val flowers = remember(layout.gridRows, layout.columns) { meadowFlowers(layout.columns, layout.gridRows) }

    Canvas(modifier) {
        val skyTop = lerp(WallkoColors.Sky, WallkoColors.Cream, 0.45f)
        val skyBottom = lerp(WallkoColors.Sky, WallkoColors.Cream, 0.8f)
        val grassTop = lerp(WallkoColors.Grass, WallkoColors.Cream, 0.45f)
        val grassBottom = lerp(WallkoColors.Grass, WallkoColors.GrassDeep, 0.25f)
        val horizon = (layout.gridTop - layout.gap / 2f).coerceIn(0f, size.height)
        val hf = if (size.height > 0f) horizon / size.height else 0.3f

        drawRect(
            Brush.verticalGradient(
                0f to skyTop,
                (hf - 0.001f).coerceAtLeast(0f) to skyBottom,
                hf to grassTop,
                1f to grassBottom,
            ),
        )
        // Rolling hill edge along the horizon.
        val hill = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, horizon)
            val bumps = 3
            val step = size.width / bumps
            for (i in 0 until bumps) {
                quadraticTo(step * (i + 0.5f), horizon - 18.dp.toPx(), step * (i + 1), horizon)
            }
            lineTo(size.width, horizon + 2f)
            lineTo(0f, horizon + 2f)
            close()
        }
        drawPath(hill, grassTop)

        drawClouds(drift, horizon)
        if (drawCells) {
            drawShelves(layout, potPx)
            drawCellPatches(layout, potPx)
        }
        drawFlowers(flowers, layout, horizon)
        if (dark) drawRect(WallkoColors.NightBackground.copy(alpha = 0.38f))
    }
}

private fun DrawScope.drawClouds(drift: Float, horizon: Float) {
    if (horizon < 40.dp.toPx()) return
    val clouds = listOf(Triple(0.1f, 0.22f, 1f), Triple(0.55f, 0.12f, 0.75f), Triple(0.85f, 0.38f, 0.6f))
    clouds.forEachIndexed { i, (x0, yf, scale) ->
        val speed = 1f + i * 0.35f
        val span = size.width + 160.dp.toPx()
        val x = ((x0 * span + drift * speed * span) % span) - 80.dp.toPx()
        val y = horizon * yf + 24.dp.toPx()
        val r = 18.dp.toPx() * scale
        val c = Color.White.copy(alpha = 0.85f)
        drawCircle(c, r, Offset(x, y))
        drawCircle(c, r * 1.3f, Offset(x + r * 1.2f, y - r * 0.4f))
        drawCircle(c, r, Offset(x + r * 2.4f, y))
        drawRoundRect(c, Offset(x, y - r * 0.2f), Size(r * 2.4f, r * 1.2f), CornerRadius(r * 0.6f))
    }
}

private fun DrawScope.drawShelves(layout: GardenLayout, potPx: Float) {
    val plank = lerp(WallkoColors.Soil, WallkoColors.Peach, 0.35f)
    val edge = WallkoColors.Soil
    val thick = 10.dp.toPx()
    val inset = 10.dp.toPx()
    for (row in 0 until layout.shelfRows) {
        val centerY = layout.shelfTop + (row + 0.5f) * layout.shelfRowHeight
        val top = centerY + potPx * 0.46f
        // Brackets.
        listOf(0.18f, 0.82f).forEach { fx ->
            val bx = size.width * fx
            drawRoundRect(edge, Offset(bx - 4.dp.toPx(), top), Size(8.dp.toPx(), thick * 2f), CornerRadius(4.dp.toPx()))
        }
        drawRoundRect(edge, Offset(inset, top + 3.dp.toPx()), Size(size.width - inset * 2, thick), CornerRadius(thick / 2))
        drawRoundRect(plank, Offset(inset, top), Size(size.width - inset * 2, thick), CornerRadius(thick / 2))
    }
}

private fun DrawScope.drawCellPatches(layout: GardenLayout, potPx: Float) {
    val patch = lerp(WallkoColors.GrassDeep, WallkoColors.Soil, 0.4f).copy(alpha = 0.28f)
    val ring = Color.White.copy(alpha = 0.18f)
    for (spot in layout.gridSpots()) {
        val c = layout.center(spot)
        val w = layout.cellWidth * 0.72f
        val h = 16.dp.toPx()
        val top = c.y + potPx * 0.44f - h / 2f
        drawOval(patch, Offset(c.x - w / 2f, top), Size(w, h))
        drawOval(ring, Offset(c.x - w / 2f, top), Size(w, h), style = Stroke(1.5.dp.toPx()))
    }
}

/** A deterministic sprinkle of flowers and grass tufts between the cells. fx, fy are cell-relative. */
private data class MeadowFlower(val col: Float, val row: Float, val color: Int, val tuft: Boolean)

private fun meadowFlowers(columns: Int, rows: Int): List<MeadowFlower> {
    val rnd = Random(7)
    val count = (columns * maxOf(rows, 4) * 1.6f).toInt()
    return List(count) {
        MeadowFlower(
            col = rnd.nextFloat() * columns,
            row = rnd.nextFloat() * maxOf(rows, 4),
            color = rnd.nextInt(4),
            tuft = rnd.nextFloat() < 0.45f,
        )
    }
}

private fun DrawScope.drawFlowers(flowers: List<MeadowFlower>, layout: GardenLayout, horizon: Float) {
    val petalColors = listOf(WallkoColors.Berry, WallkoColors.Sunflower, WallkoColors.Lavender, Color.White)
    val cellW = size.width / layout.columns
    val bottom = size.height - 8.dp.toPx()
    val rows = maxOf(layout.gridRows, 4)
    val rowH = ((bottom - horizon - 20.dp.toPx()) / rows).coerceAtLeast(1f)
    flowers.forEach { f ->
        // Keep flowers off the pot centres: nudge to the cell edges.
        val cx = (f.col.toInt() + if (f.col % 1f < 0.5f) 0.08f else 0.92f) * cellW
        val cy = horizon + 20.dp.toPx() + f.row * rowH
        if (cy > bottom) return@forEach
        if (f.tuft) {
            val g = WallkoColors.GrassDeep
            val s = 6.dp.toPx()
            for (k in -1..1) {
                drawLine(g, Offset(cx, cy), Offset(cx + k * s * 0.6f, cy - s * (1.2f - 0.2f * k * k)), 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        } else {
            val r = 3.dp.toPx()
            val petal = petalColors[f.color]
            for (k in 0 until 5) {
                val a = k * 2f * PI.toFloat() / 5f
                drawCircle(petal, r, Offset(cx + r * 1.2f * kotlin.math.cos(a), cy + r * 1.2f * sin(a)))
            }
            drawCircle(WallkoColors.Sunflower, r * 0.8f, Offset(cx, cy))
        }
    }
}

private val DrinkLanes = listOf(0.42f, 0.5f, 0.58f)
private val TOP_BAR_SPACE = 72.dp
private const val POT_FRACTION = 0.82f
private const val CLOUD_PERIOD_MS = 60_000
private const val DRINK_START_DELAY_MS = 500L
private const val DRINK_TANK_DELAY_MS = 900L
private const val DRINK_FINISH_DELAY_MS = 1_500L

// ---------------------------------------------------------------------------------------------
// Previews

private fun previewPlants(): List<GardenPlantUi> = listOf(
    GardenPlantUi(1, "Fern", PlantStage.SEEDLING, 0f, 0.1f, GardenSpot(0, 0), null, 0),
    GardenPlantUi(2, "Rose", PlantStage.GROWING, 0.5f, 0.4f, GardenSpot(2, 1), null, 0),
    GardenPlantUi(3, "Daisy", PlantStage.WILTING, 1f, 0.7f, GardenSpot(1, 0, onShelf = true), null, 0),
)

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun GardenPreview() {
    WallkoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            GardenContent(
                state = GardenUiState(loading = false, plants = previewPlants(), drops = 32, capacity = 80, elixir = 1),
                onFindPlant = {}, onOpenCollection = {}, onOpenSettings = {},
                onPlantClick = {}, onMove = { _, _ -> }, onDrinkingShown = {},
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun GardenEmptyPreview() {
    WallkoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            GardenContent(
                state = GardenUiState(loading = false),
                onFindPlant = {}, onOpenCollection = {}, onOpenSettings = {},
                onPlantClick = {}, onMove = { _, _ -> }, onDrinkingShown = {},
            )
        }
    }
}
