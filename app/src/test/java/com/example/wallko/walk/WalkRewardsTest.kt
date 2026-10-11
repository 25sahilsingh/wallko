package com.example.wallko.walk

import com.example.wallko.core.config.GameRules
import com.example.wallko.core.geo.cellBounds
import com.example.wallko.core.geo.cellIdOf
import com.example.wallko.walk.model.GeoPoint
import com.example.wallko.walk.model.RewardKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.ceil

class WalkRewardsTest {

    private val utc: ZoneId = ZoneOffset.UTC

    /** Same metres-per-degree as Cells.kt, so "+100 m north" is exactly one cell row. */
    private val metersPerDegLat = 111_320.0
    private val baseCell = cellIdOf(30.0869, 78.2676)
    private val centerLat = cellBounds(baseCell).let { (it.south + it.north) / 2 }
    private val lng = cellBounds(baseCell).let { (it.west + it.east) / 2 }
    private val t0 = Instant.parse("2026-10-10T05:00:00Z").toEpochMilli()

    /** Builds a path of points at [northMeters] offsets from the base cell centre, timed at a 1.4 m/s stroll. */
    private fun stroll(
        vararg northMeters: Double,
        startMillis: Long = t0,
        accuracy: Double = 5.0,
    ): List<GeoPoint> {
        var t = startMillis
        var prev: Double? = null
        return northMeters.map { m ->
            prev?.let { t += ceil(kotlin.math.abs(m - it) / 1.4 * 1000).toLong() }
            prev = m
            GeoPoint(centerLat + m / metersPerDegLat, lng, accuracy, t)
        }
    }

    private fun cellAt(northMeters: Double) = cellIdOf(centerLat + northMeters / metersPerDegLat, lng)

    private fun fold(points: List<GeoPoint>, known: Set<Long> = emptySet()) =
        points.fold(WalkAccumulator.start(known)) { acc, p -> acc.add(p) }

    /** Five points per cell (offsets −40..+40 m) through [cells] consecutive cells starting at row offset [firstRow]. */
    private fun cellSweep(firstRow: Int, cells: Int): DoubleArray =
        (0 until cells).flatMap { c -> listOf(-40.0, -20.0, 0.0, 20.0, 40.0).map { (firstRow + c) * 100.0 + it } }
            .toDoubleArray()

    // ---- accumulator ----

    @Test fun drivingSegmentIgnored() {
        val walk = stroll(0.0, 20.0)
        val last = walk.last()
        val car = GeoPoint(centerLat + 420.0 / metersPerDegLat, lng, 5.0, last.timeMillis + 15_000) // 400 m in 15 s
        val afterCar = GeoPoint(centerLat + 440.0 / metersPerDegLat, lng, 5.0, car.timeMillis + 15_000)
        val acc = fold(walk + car + afterCar)
        assertEquals(1, acc.ignoredFast)
        // Only the two 20 m walking segments count; the post-drive segment is measured from the car point.
        assertEquals(40.0, acc.distanceM, 0.5)
    }

    @Test fun reportedSpeedOverridesComputed() {
        val pts = stroll(0.0, 10.0)
        val fast = pts[1].copy(speedMps = GameRules.Walk.MAX_SPEED_KMH / 3.6 + 1.0)
        val acc = fold(listOf(pts[0], fast))
        assertEquals(1, acc.ignoredFast)
        assertEquals(0.0, acc.distanceM, 0.0)
    }

    @Test fun badAccuracyJitterIgnored() {
        val pts = stroll(0.0, 20.0, 40.0)
        val jitter = GeoPoint(centerLat + 300.0 / metersPerDegLat, lng, GameRules.Walk.MAX_ACCURACY_METERS + 1, pts[1].timeMillis + 1)
        val acc = fold(listOf(pts[0], pts[1], jitter, pts[2]))
        assertEquals(1, acc.ignoredInaccurate)
        assertEquals(3, acc.acceptedPoints)
        assertEquals(40.0, acc.distanceM, 0.5)
        assertEquals(setOf(baseCell), acc.enteredCells)
    }

    @Test fun exactlyMaxAccuracyIsAccepted() {
        val pts = stroll(0.0, 20.0, accuracy = GameRules.Walk.MAX_ACCURACY_METERS)
        assertEquals(2, fold(pts).acceptedPoints)
    }

    @Test fun singlePointCellVisitNotCounted() {
        // Two points in the base cell, one point in the next row, back to the base cell.
        val acc = fold(stroll(0.0, 20.0, 100.0, 20.0))
        assertEquals(setOf(baseCell), acc.enteredCells)
        assertFalse(cellAt(100.0) in acc.enteredCells)
    }

    @Test fun firstPointAloneDoesNotEnter() {
        val acc = fold(stroll(0.0))
        assertTrue(acc.enteredCells.isEmpty())
    }

    @Test fun newVersusKnownCells() {
        val known = setOf(baseCell)
        val acc = fold(stroll(*cellSweep(0, 2)), known)
        assertEquals(setOf(baseCell, cellAt(100.0)), acc.enteredCells)
        assertEquals(setOf(cellAt(100.0)), acc.newCells)
        // Only the 4 inner 20 m segments of the new cell count toward elixir.
        assertEquals(80.0, acc.newCellDistanceM, 0.5)
    }

    @Test fun knownCellsEarnNoElixirDistance() {
        val sweep = cellSweep(0, 3)
        val known = sweep.map { cellAt(it) }.toSet()
        val acc = fold(stroll(*sweep), known)
        assertTrue(acc.newCells.isEmpty())
        assertEquals(0.0, acc.newCellDistanceM, 0.0)
        assertEquals(3, acc.enteredCells.size)
    }

    // ---- summarize ----

    @Test fun elixirCarriesOverAcrossTwoWalks() {
        val per = GameRules.Elixir.METERS_IN_NEW_CELLS_PER_ELIXIR
        val walk1 = stroll(*cellSweep(0, 4))
        val acc1 = fold(walk1)
        assertEquals(4, acc1.newCells.size)
        assertEquals(320.0, acc1.newCellDistanceM, 1.0)
        val s1 = summarize(acc1, walk1, prior = 400.0, startHourUtc = 9)
        assertTrue(s1.valid)
        assertEquals(1, s1.elixirEarned)
        assertEquals(400.0 + acc1.newCellDistanceM - per, s1.elixirProgressM, 1e-9)

        val walk2 = stroll(*cellSweep(4, 4), startMillis = walk1.last().timeMillis + 3_600_000)
        val acc2 = fold(walk2, known = s1.cellsToMarkExplored)
        assertEquals(4, acc2.newCells.size)
        val s2 = summarize(acc2, walk2, prior = s1.elixirProgressM, startHourUtc = 10)
        assertEquals(1, s2.elixirEarned)
        assertEquals(s1.elixirProgressM + acc2.newCellDistanceM - per, s2.elixirProgressM, 1e-9)
        assertTrue(s2.elixirProgressM in 0.0..per)
    }

    @Test fun repeatWalkOverSameCellsEarnsNoCellBonus() {
        val walk = stroll(*cellSweep(0, 4))
        val first = summarize(fold(walk), walk, prior = 0.0, startHourUtc = 9)
        val again = summarize(fold(walk, known = first.cellsToMarkExplored), walk, prior = first.elixirProgressM, startHourUtc = 9)
        assertEquals(0, again.dropsFor(RewardKind.NEW_CELL))
        assertEquals(first.elixirProgressM, again.elixirProgressM, 0.0)
        assertEquals(first.dropsFor(RewardKind.WALK_DISTANCE), again.dropsFor(RewardKind.WALK_DISTANCE))
    }

    @Test fun dawnDoublesWalkDrops() {
        val walk = stroll(*cellSweep(0, 4))
        val acc = fold(walk)
        val day = summarize(acc, walk, prior = 0.0, startHourUtc = 9)
        val dawn = summarize(acc, walk, prior = 0.0, startHourUtc = 5)
        val distanceDrops = GameRules.dropsForDistance(acc.distanceM)
        val cellDrops = 4 * GameRules.Earning.DROPS_PER_NEW_CELL
        assertEquals(6, distanceDrops) // ~380 m -> 3 full blocks
        assertFalse(day.dawn)
        assertEquals(distanceDrops + cellDrops, day.totalDrops)
        assertEquals(0, day.dropsFor(RewardKind.DAWN_BONUS))
        assertTrue(dawn.dawn)
        assertEquals((distanceDrops + cellDrops) * GameRules.Earning.DAWN_MULTIPLIER, dawn.totalDrops)
        assertEquals(distanceDrops, dawn.dropsFor(RewardKind.WALK_DISTANCE))
        assertEquals(cellDrops, dawn.dropsFor(RewardKind.NEW_CELL))
        assertEquals(distanceDrops + cellDrops, dawn.dropsFor(RewardKind.DAWN_BONUS))
        assertEquals(dawn.totalDrops, dawn.breakdown.sumOf { it.drops })
    }

    @Test fun dawnUsesStartTimeInLocalZone() {
        val walk = stroll(*cellSweep(0, 4))
        val acc = fold(walk)
        val kolkata = ZoneId.of("Asia/Kolkata")
        // 00:29Z = 05:59 IST (dawn), even though it is not dawn in UTC; walk lasts past 07:00 IST.
        val start = Instant.parse("2026-10-10T00:29:00Z")
        val s = WalkRewards.summarize(acc, start, start.plus(Duration.ofHours(2)), kolkata, 0, 0.0)
        assertTrue(s.dawn)
        // 06:59 counts, 07:00 does not; 03:59 does not.
        assertTrue(summarizeAt(acc, "2026-10-10T06:59:00Z").dawn)
        assertFalse(summarizeAt(acc, "2026-10-10T07:00:00Z").dawn)
        assertFalse(summarizeAt(acc, "2026-10-10T03:59:00Z").dawn)
    }

    @Test fun shortWalkInvalid() {
        // Long enough in distance but under 2 minutes.
        val walk = stroll(*cellSweep(0, 4))
        val acc = fold(walk)
        val start = Instant.ofEpochMilli(walk.first().timeMillis)
        val s = WalkRewards.summarize(acc, start, start.plus(GameRules.Walk.MIN_DURATION).minusSeconds(1), utc, 120, 250.0)
        assertFalse(s.valid)
        assertEquals(0, s.totalDrops)
        assertTrue(s.breakdown.isEmpty())
        assertEquals(0, s.elixirEarned)
        assertEquals(250.0, s.elixirProgressM, 0.0)
        assertTrue(s.cellsToMarkExplored.isEmpty())
        // Stats survive.
        assertEquals(acc.distanceM, s.distanceM, 0.0)
        assertEquals(4, s.newCells.size)
        assertEquals(120, s.steps)

        // Long enough in time but under 100 m.
        val tiny = stroll(0.0, 20.0, 40.0)
        val t = Instant.ofEpochMilli(tiny.first().timeMillis)
        assertFalse(WalkRewards.summarize(fold(tiny), t, t.plus(Duration.ofMinutes(10)), utc, 0, 0.0).valid)
    }

    @Test fun minimumDurationWithEnoughDistanceIsValid() {
        val walk = stroll(-40.0, -20.0, 0.0, 20.0, 40.0, 60.0, 80.0) // ~120 m
        val acc = fold(walk)
        val start = Instant.ofEpochMilli(walk.first().timeMillis)
        val s = WalkRewards.summarize(acc, start, start.plus(GameRules.Walk.MIN_DURATION), utc, 0, 0.0)
        assertTrue(s.valid)
        assertEquals(GameRules.dropsForDistance(acc.distanceM), s.dropsFor(RewardKind.WALK_DISTANCE))
    }

    // ---- steps ----

    @Test fun dailyStepDrops() {
        assertEquals(50, WalkRewards.dailyStepDrops(5_000, 0, 0))
        assertEquals(20, WalkRewards.dailyStepDrops(5_000, 30, 0))
        assertEquals(1, WalkRewards.dailyStepDrops(199, 0, 0))
        // Cap.
        val capDrops = GameRules.dropsForSteps(GameRules.Earning.DAILY_STEP_CAP)
        assertEquals(capDrops, WalkRewards.dailyStepDrops(40_000, 0, 0))
        assertEquals(0, WalkRewards.dailyStepDrops(40_000, capDrops, 0))
        // Walk steps excluded before the cap: 20,000 - 3,000 = 17,000 -> capped.
        assertEquals(capDrops, WalkRewards.dailyStepDrops(20_000, 0, 3_000))
        // 8,000 - 3,000 = 5,000 -> 50.
        assertEquals(50, WalkRewards.dailyStepDrops(8_000, 0, 3_000))
        // Never negative if walk steps grew after an earlier credit.
        assertEquals(0, WalkRewards.dailyStepDrops(8_000, 60, 3_000))
        assertEquals(0, WalkRewards.dailyStepDrops(1_000, 0, 5_000))
    }

    // ---- helpers ----

    private fun summarize(acc: WalkAccumulator, pts: List<GeoPoint>, prior: Double, startHourUtc: Int) =
        WalkRewards.summarize(
            acc,
            Instant.parse("2026-10-10T%02d:00:00Z".format(startHourUtc)),
            Instant.parse("2026-10-10T%02d:00:00Z".format(startHourUtc))
                .plusMillis(pts.last().timeMillis - pts.first().timeMillis),
            utc,
            steps = 0,
            priorElixirProgressM = prior,
        )

    private fun summarizeAt(acc: WalkAccumulator, startIso: String) = Instant.parse(startIso).let {
        WalkRewards.summarize(acc, it, it.plus(Duration.ofMinutes(30)), utc, 0, 0.0)
    }
}
