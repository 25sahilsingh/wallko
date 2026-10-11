package com.example.wallko.walk

import com.example.wallko.core.geo.cellBounds
import com.example.wallko.core.geo.cellIdOf
import com.example.wallko.walk.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class LiveWalkMathTest {
    private val metersPerDegLat = 111_320.0
    private val base = cellIdOf(30.0869, 78.2676)
    private val lat0 = cellBounds(base).let { (it.south + it.north) / 2 }
    private val lng = cellBounds(base).let { (it.west + it.east) / 2 }

    /** Straight walk north, a fix every 10 m at 1.25 m/s. */
    private fun walk(meters: Int, startMillis: Long): WalkAccumulator =
        (0..meters / 10).fold(WalkAccumulator.start(emptySet())) { acc, i ->
            acc.add(GeoPoint(lat0 + i * 10 / metersPerDegLat, lng, 5.0, startMillis + i * 8_000L))
        }

    private fun checkMatchesSummary(startIso: String) {
        val start = Instant.parse(startIso)
        val acc = walk(650, start.toEpochMilli())
        val end = start.plus(Duration.ofMinutes(10))
        val dawn = com.example.wallko.core.config.GameRules.isDawn(start.atZone(ZoneOffset.UTC).toLocalTime())
        val summary = WalkRewards.summarize(acc, start, end, ZoneOffset.UTC, 0, 0.0)
        assertTrue(summary.valid)
        assertEquals(summary.totalDrops, LiveWalkMath.projectedDrops(acc, dawn))
    }

    @Test fun projectedDropsMatchSummaryDaytime() = checkMatchesSummary("2026-10-10T12:00:00Z")

    @Test fun projectedDropsMatchSummaryAtDawn() = checkMatchesSummary("2026-10-10T05:00:00Z")

    @Test fun countingNeedsTimeAndDistance() {
        val acc = walk(150, 0)
        assertFalse(LiveWalkMath.isCounting(Duration.ofSeconds(119), acc))
        assertTrue(LiveWalkMath.isCounting(Duration.ofMinutes(2), acc))
        assertFalse(LiveWalkMath.isCounting(Duration.ofMinutes(5), walk(50, 0)))
    }

    @Test fun elixirProgress() {
        assertEquals(0, LiveWalkMath.elixirCount(499.9))
        assertEquals(1, LiveWalkMath.elixirCount(500.0))
        assertEquals(0.5f, LiveWalkMath.elixirFraction(750.0), 1e-6f)
        assertEquals(0f, LiveWalkMath.elixirFraction(-10.0), 1e-6f)
        val acc = walk(300, 0)
        assertEquals(200.0 + acc.newCellDistanceM, LiveWalkMath.elixirMeters(200.0, acc), 1e-9)
    }
}
