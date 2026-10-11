package com.example.wallko.walk

import com.example.wallko.core.config.GameRules
import com.example.wallko.core.geo.cellIdOf
import com.example.wallko.core.geo.haversineMeters
import com.example.wallko.walk.model.GeoPoint
import com.example.wallko.walk.model.RewardKind
import com.example.wallko.walk.model.RewardLine
import com.example.wallko.walk.model.WalkSummary
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Immutable running state of one GPS walk. Start with [WalkAccumulator.start] and fold every fix in with [add].
 *
 * Rules:
 * - Fixes with accuracy worse than [GameRules.Walk.MAX_ACCURACY_METERS] are dropped entirely (no effect at all).
 * - Fixes not later than the previous accepted fix are dropped (duplicates / out of order).
 * - A segment faster than [GameRules.Walk.MAX_SPEED_KMH] (reported speed if present, else haversine/time)
 *   adds no distance and breaks the cell streak; the fix still becomes the new anchor so the next walking
 *   segment is measured from where the car stopped, not from before the drive.
 * - A cell is "entered" once [GameRules.Walk.CONSECUTIVE_POINTS_PER_CELL] consecutive accepted fixes fall in it.
 *   It is "new" if it is not in [knownCells] (cells explored before this walk).
 * - Elixir distance: a segment whose two ends lie in the same new cell counts. While the cell's streak is
 *   still short of entry the distance is held as pending and only added once the cell is entered (dropped
 *   if the streak breaks first). Segments that cross a cell edge are not counted toward elixir.
 *   Re-walking inside a cell that was new at walk start keeps counting for the whole walk.
 */
data class WalkAccumulator(
    val knownCells: Set<Long>,
    val distanceM: Double = 0.0,
    val newCellDistanceM: Double = 0.0,
    /** Entered cells in order of entry. */
    val enteredCells: Set<Long> = emptySet(),
    /** Subset of [enteredCells] not in [knownCells]. */
    val newCells: Set<Long> = emptySet(),
    val acceptedPoints: Int = 0,
    val ignoredInaccurate: Int = 0,
    val ignoredFast: Int = 0,
    val ignoredOutOfOrder: Int = 0,
    val lastPoint: GeoPoint? = null,
    private val streakCell: Long? = null,
    private val streakCount: Int = 0,
    private val pendingNewDistanceM: Double = 0.0,
) {

    fun add(point: GeoPoint): WalkAccumulator {
        if (point.accuracyM > GameRules.Walk.MAX_ACCURACY_METERS) return copy(ignoredInaccurate = ignoredInaccurate + 1)
        val prev = lastPoint ?: return copy(lastPoint = point, acceptedPoints = 1).withStreak(cellIdOf(point.lat, point.lng), 0.0)
        val dtMs = point.timeMillis - prev.timeMillis
        if (dtMs <= 0) return copy(ignoredOutOfOrder = ignoredOutOfOrder + 1)

        val d = haversineMeters(prev.lat, prev.lng, point.lat, point.lng)
        val speedMps = point.speedMps ?: (d / (dtMs / 1000.0))
        if (speedMps * MPS_TO_KMH > GameRules.Walk.MAX_SPEED_KMH) {
            return copy(
                lastPoint = point,
                ignoredFast = ignoredFast + 1,
                streakCell = null,
                streakCount = 0,
                pendingNewDistanceM = 0.0,
            )
        }
        val cell = cellIdOf(point.lat, point.lng)
        val sameCellSegment = streakCell == cell && cellIdOf(prev.lat, prev.lng) == cell
        val newCellMeters = if (sameCellSegment && cell !in knownCells) d else 0.0
        return copy(
            lastPoint = point,
            acceptedPoints = acceptedPoints + 1,
            distanceM = distanceM + d,
        ).withStreak(cell, newCellMeters)
    }

    /** Advances the cell streak with [cell]; [segmentNewMeters] is this segment's elixir-eligible distance. */
    private fun withStreak(cell: Long, segmentNewMeters: Double): WalkAccumulator {
        val count = if (cell == streakCell) streakCount + 1 else 1
        val pending = (if (cell == streakCell) pendingNewDistanceM else 0.0) + segmentNewMeters
        val alreadyEntered = cell in enteredCells
        val nowEntered = alreadyEntered || count >= GameRules.Walk.CONSECUTIVE_POINTS_PER_CELL
        if (!nowEntered) return copy(streakCell = cell, streakCount = count, pendingNewDistanceM = pending)
        val isNew = cell !in knownCells
        return copy(
            streakCell = cell,
            streakCount = count,
            pendingNewDistanceM = 0.0,
            newCellDistanceM = newCellDistanceM + pending,
            enteredCells = if (alreadyEntered) enteredCells else enteredCells + cell,
            newCells = if (isNew && !alreadyEntered) newCells + cell else newCells,
        )
    }

    companion object {
        private const val MPS_TO_KMH = 3.6

        /** Empty accumulator; [knownCells] = every cell explored before this walk. */
        fun start(knownCells: Set<Long>): WalkAccumulator = WalkAccumulator(knownCells = knownCells)
    }
}

/** Pure walk-reward and step-reward formulas. All numbers come from [GameRules]. */
object WalkRewards {

    /**
     * Turns a finished walk into rewards.
     *
     * - Valid only if ≥ [GameRules.Walk.MIN_DURATION] and ≥ [GameRules.Walk.MIN_DISTANCE_METERS] (counted distance).
     * - Drops = distance drops + new-cell drops; the **whole walk** is doubled if its **start** time
     *   (local, in [zone]) is in the dawn window. Start time is used so a walk begun at 06:50 keeps its bonus,
     *   and a walk begun at 03:59 does not get one by lasting past 04:00; it is also what the player sees
     *   when deciding to go. The extra is a separate DAWN_BONUS line.
     * - Elixir: ([priorElixirProgressM] + new-cell metres) / 500 m, remainder carried.
     */
    fun summarize(
        acc: WalkAccumulator,
        start: Instant,
        end: Instant,
        zone: ZoneId,
        steps: Int,
        priorElixirProgressM: Double,
    ): WalkSummary {
        val duration = Duration.between(start, end).let { if (it.isNegative) Duration.ZERO else it }
        val valid = GameRules.isValidWalk(duration, acc.distanceM)
        val dawn = GameRules.isDawn(start.atZone(zone).toLocalTime())
        val prior = priorElixirProgressM.coerceAtLeast(0.0)
        if (!valid) {
            return WalkSummary(
                valid = false, duration = duration, distanceM = acc.distanceM, steps = steps,
                enteredCells = acc.enteredCells, newCells = acc.newCells, newCellDistanceM = acc.newCellDistanceM,
                dawn = dawn, breakdown = emptyList(), totalDrops = 0, elixirEarned = 0,
                elixirProgressM = prior, cellsToMarkExplored = emptySet(),
            )
        }
        val distanceDrops = GameRules.dropsForDistance(acc.distanceM)
        val cellDrops = acc.newCells.size * GameRules.Earning.DROPS_PER_NEW_CELL
        val dawnBonus = if (dawn) (distanceDrops + cellDrops) * (GameRules.Earning.DAWN_MULTIPLIER - 1) else 0
        val breakdown = listOf(
            RewardLine(RewardKind.WALK_DISTANCE, distanceDrops),
            RewardLine(RewardKind.NEW_CELL, cellDrops),
            RewardLine(RewardKind.DAWN_BONUS, dawnBonus),
        ).filter { it.drops > 0 }

        val per = GameRules.Elixir.METERS_IN_NEW_CELLS_PER_ELIXIR
        val progress = prior + acc.newCellDistanceM
        val elixir = (progress / per).toInt()
        return WalkSummary(
            valid = true, duration = duration, distanceM = acc.distanceM, steps = steps,
            enteredCells = acc.enteredCells, newCells = acc.newCells, newCellDistanceM = acc.newCellDistanceM,
            dawn = dawn, breakdown = breakdown, totalDrops = breakdown.sumOf { it.drops },
            elixirEarned = elixir, elixirProgressM = progress - elixir * per,
            cellsToMarkExplored = acc.enteredCells,
        )
    }

    /**
     * Incremental passive-step drops to credit now for one local day.
     *
     * Steps taken during GPS walks ([walkStepsToday]) are excluded because walk distance replaces them.
     * The remaining steps are capped at [GameRules.Earning.DAILY_STEP_CAP]. Returns the day's step drops
     * minus what was already credited, never negative (if walk steps grow later, nothing is clawed back).
     */
    fun dailyStepDrops(stepsToday: Int, alreadyCreditedDrops: Int, walkStepsToday: Int): Int {
        val passive = (stepsToday - walkStepsToday.coerceAtLeast(0)).coerceAtLeast(0)
        return (GameRules.dropsForSteps(passive) - alreadyCreditedDrops).coerceAtLeast(0)
    }
}
