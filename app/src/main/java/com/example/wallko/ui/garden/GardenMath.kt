package com.example.wallko.ui.garden

import com.example.wallko.core.config.GameRules
import com.example.wallko.core.db.PlantState
import com.example.wallko.data.garden.GardenRepository
import com.example.wallko.data.garden.GardenSpot
import com.example.wallko.game.model.GardenEvent
import com.example.wallko.ui.components.PlantStage
import kotlin.math.ceil

/** A point in garden-layout pixels. Plain floats so the math stays JVM-testable. */
data class GardenPoint(val x: Float, val y: Float)

/**
 * Pure garden helpers: DB state → UI stage, growth fraction, sway phase, and the pot layout / snap math.
 *
 * Shelf slots are stored the way [GardenRepository.nextFreeSpot] writes them: `onShelf = true`, `gardenX` = linear
 * slot index, `gardenY` = 0. On screen slot `s` sits in shelf row `s / columns`, column `s % columns`.
 */
object GardenMath {

    const val COLUMNS = GardenRepository.GRID_WIDTH
    const val GRID_ROWS = GardenRepository.GRID_HEIGHT
    const val MIN_SHELF_ROWS = 2

    /**
     * DB state → UI stage. A SEEDLING whose sprite has landed is shown as GROWING at its smallest size, so the
     * sprite "blooms into the pot" (ml-pipeline.md: "Garden shows a seedling until this lands, then blooms it in").
     */
    fun uiStage(state: PlantState, hasSprite: Boolean): PlantStage = when (state) {
        PlantState.SEEDLING -> if (hasSprite) PlantStage.GROWING else PlantStage.SEEDLING
        PlantState.GROWING -> PlantStage.GROWING
        PlantState.GROWN -> PlantStage.GROWN
        PlantState.WILTING -> PlantStage.WILTING
        PlantState.WITHERED -> PlantStage.WITHERED
    }

    /**
     * 0..1 progress through the GROWING stage: 0 right after the seedling days, 1 at [target] watered days.
     * Targets at or below the seedling days count as fully grown.
     */
    fun growthFraction(growthDays: Int, target: Int): Float {
        val seedling = GameRules.Growth.SEEDLING_MAX_GROWTH_DAYS
        if (target <= seedling) return 1f
        return ((growthDays - seedling).toFloat() / (target - seedling)).coerceIn(0f, 1f)
    }

    /** Stable per-plant sway offset in [0, 1), so neighbouring pots don't rock in lockstep. */
    fun swayPhase(id: Long): Float {
        // Fibonacci hashing spreads consecutive ids evenly.
        val h = (id * -7046029254386353131L) ushr 40
        return (h % 1000L).toFloat() / 1000f
    }

    /** Shelf rows to draw: at least [MIN_SHELF_ROWS], and always one free slot after the highest used one. */
    fun shelfRowsFor(maxUsedShelfSlot: Int?, columns: Int = COLUMNS): Int {
        if (maxUsedShelfSlot == null) return MIN_SHELF_ROWS
        val needed = ceil((maxUsedShelfSlot + 2).toDouble() / columns).toInt()
        return maxOf(MIN_SHELF_ROWS, needed)
    }

    /** Shelf spots compare by slot only; ground spots by (x, y). */
    fun normalize(spot: GardenSpot): GardenSpot = if (spot.onShelf) spot.copy(y = 0) else spot

    /** Plants that drank on the latest processed day. */
    fun drankIds(events: List<GardenEvent>): Set<Long> =
        events.filterIsInstance<GardenEvent.Drank>().filter { it.drops > 0 }.mapTo(LinkedHashSet()) { it.plantId }

    /** Tank level to show before the drinking animation: what's left now plus what was drunk, within capacity. */
    fun tankBefore(dropsNow: Int, dropsDrunk: Int, capacity: Int): Int =
        (dropsNow + dropsDrunk.coerceAtLeast(0)).coerceAtMost(capacity.coerceAtLeast(dropsNow))

    /** Nearest free spot to [point] (by pot centre); [current] (the dragged pot's own spot) counts as free. */
    fun snapToNearestFree(
        layout: GardenLayout,
        point: GardenPoint,
        occupied: Set<GardenSpot>,
        current: GardenSpot,
    ): GardenSpot {
        val taken = occupied.mapTo(HashSet()) { normalize(it) }
        val self = normalize(current)
        var best: GardenSpot? = null
        var bestDist = Float.MAX_VALUE
        for (spot in layout.allSpots()) {
            if (spot != self && spot in taken) continue
            val c = layout.center(spot)
            val dx = c.x - point.x
            val dy = c.y - point.y
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                best = spot
            }
        }
        return best ?: current
    }
}

/**
 * Pixel layout of the meadow, top to bottom: [topPadding], [shelfRows] shelf rows of [shelfRowHeight],
 * [gap], [gridRows] ground rows of [cellHeight], [bottomPadding]. All rows have [columns] equal columns.
 * [center] is the centre of the pot box for a spot.
 */
data class GardenLayout(
    val width: Float,
    val shelfRows: Int,
    val shelfRowHeight: Float,
    val cellHeight: Float,
    val topPadding: Float = 0f,
    val gap: Float = 0f,
    val bottomPadding: Float = 0f,
    val columns: Int = GardenMath.COLUMNS,
    val gridRows: Int = GardenMath.GRID_ROWS,
) {
    val cellWidth: Float get() = width / columns
    val shelfTop: Float get() = topPadding
    val gridTop: Float get() = topPadding + shelfRows * shelfRowHeight + gap
    val totalHeight: Float get() = gridTop + gridRows * cellHeight + bottomPadding

    fun center(spot: GardenSpot): GardenPoint = if (spot.onShelf) {
        val row = spot.x / columns
        val col = spot.x % columns
        GardenPoint((col + 0.5f) * cellWidth, shelfTop + (row + 0.5f) * shelfRowHeight)
    } else {
        GardenPoint((spot.x + 0.5f) * cellWidth, gridTop + (spot.y + 0.5f) * cellHeight)
    }

    fun shelfSpots(): List<GardenSpot> = (0 until shelfRows * columns).map { GardenSpot(it, 0, onShelf = true) }

    fun gridSpots(): List<GardenSpot> =
        (0 until gridRows).flatMap { y -> (0 until columns).map { x -> GardenSpot(x, y) } }

    fun allSpots(): List<GardenSpot> = shelfSpots() + gridSpots()
}
