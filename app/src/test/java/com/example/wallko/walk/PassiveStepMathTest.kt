package com.example.wallko.walk

import com.example.wallko.core.db.Walk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveStepMathTest {
    private val hour = 3_600_000L
    private val dayStart = 20_000L * 24 * hour
    private val dayEnd = dayStart + 24 * hour

    private fun paid(id: Long, start: Long, end: Long, steps: Int = 1_000) =
        Walk(id = id, startedAt = start, endedAt = end, distanceM = 800.0, steps = steps, dropsEarned = 16)

    private fun invalid(id: Long, start: Long, end: Long, steps: Int = 0) =
        Walk(id = id, startedAt = start, endedAt = end, distanceM = 40.0, steps = steps, dropsEarned = 0)

    // ---- which walks count ----

    @Test fun onlySettledWalksThatPaidCount() {
        assertTrue(PassiveStepMath.isPaidWalk(paid(1, dayStart, dayStart + hour)))
        // Invalid walk, including a legacy row that still stored its steps.
        assertFalse(PassiveStepMath.isPaidWalk(invalid(2, dayStart, dayStart + hour, steps = 500)))
        assertFalse(PassiveStepMath.isPaidWalk(Walk(id = 3, startedAt = dayStart, endedAt = null)))
    }

    @Test fun invalidWalkLeavesItsStepsPassive() {
        val spans = PassiveStepMath.spans(listOf(invalid(1, dayStart + hour, dayStart + 2 * hour, steps = 500)), null, null, 0, dayEnd)
        assertTrue(spans.isEmpty())
        assertEquals(0, PassiveStepMath.sensorStepsOn(spans, dayStart, dayEnd))
    }

    // ---- walk in progress (bug: mid-walk sync paid walk steps as passive) ----

    @Test fun activeWalkIsExcludedUpToNow() {
        val start = dayStart + 10 * hour
        val now = start + hour
        val running = Walk(id = 5, startedAt = start, endedAt = null)
        val spans = PassiveStepMath.spans(listOf(running), activeWalkId = 5, activeStartMillis = start, activeSteps = 1_200, nowMillis = now)
        assertEquals(listOf(WalkSpan(start, now, 1_200)), spans)
        assertEquals(listOf(TimeWindow(start, now)), PassiveStepMath.windowsOn(spans, dayStart, dayEnd))
        assertEquals(1_200, PassiveStepMath.sensorStepsOn(spans, dayStart, dayEnd))
    }

    @Test fun activeWalkAlreadySettledUsesItsRowOnly() {
        val start = dayStart + 10 * hour
        val settledValid = paid(5, start, start + hour, steps = 1_000)
        val spans = PassiveStepMath.spans(listOf(settledValid), 5, start, 1_000, start + 2 * hour)
        assertEquals(listOf(WalkSpan(start, start + hour, 1_000)), spans)

        // Settled but invalid: nothing excluded any more, so its steps become passive on the next sync.
        val settledInvalid = invalid(5, start, start + hour)
        assertTrue(PassiveStepMath.spans(listOf(settledInvalid), 5, start, 1_000, start + 2 * hour).isEmpty())
    }

    @Test fun activeWalkWithClockBehindStartIsZeroLengthButStillCountsSensorSteps() {
        val start = dayStart + 10 * hour
        val spans = PassiveStepMath.spans(emptyList(), 9, start, 300, nowMillis = start - 1_000)
        assertEquals(listOf(WalkSpan(start, start, 300)), spans)
        assertTrue(PassiveStepMath.windowsOn(spans, dayStart, dayEnd).isEmpty())
        assertEquals(300, PassiveStepMath.sensorStepsOn(spans, dayStart, dayEnd))
    }

    // ---- day clipping (bug: midnight-crossing walk booked entirely to its start day) ----

    @Test fun walkCrossingMidnightIsClippedToEachDay() {
        val walk = paid(1, dayEnd - hour, dayEnd + 3 * hour, steps = 4_000)
        val spans = PassiveStepMath.spans(listOf(walk), null, null, 0, dayEnd + 5 * hour)

        assertEquals(listOf(TimeWindow(dayEnd - hour, dayEnd)), PassiveStepMath.windowsOn(spans, dayStart, dayEnd))
        assertEquals(listOf(TimeWindow(dayEnd, dayEnd + 3 * hour)), PassiveStepMath.windowsOn(spans, dayEnd, dayEnd + 24 * hour))

        // Sensor fallback prorates by time: 1 h of 4 h before midnight.
        assertEquals(1_000, PassiveStepMath.sensorStepsOn(spans, dayStart, dayEnd))
        assertEquals(3_000, PassiveStepMath.sensorStepsOn(spans, dayEnd, dayEnd + 24 * hour))
    }

    @Test fun sensorProrationRoundsUpSoNothingIsPaidTwice() {
        val walk = paid(1, dayEnd - hour, dayEnd + 2 * hour, steps = 1_000)
        val spans = PassiveStepMath.spans(listOf(walk), null, null, 0, dayEnd + 5 * hour)
        val before = PassiveStepMath.sensorStepsOn(spans, dayStart, dayEnd)
        val after = PassiveStepMath.sensorStepsOn(spans, dayEnd, dayEnd + 24 * hour)
        assertEquals(334, before)
        assertEquals(667, after)
        assertTrue(before + after >= 1_000)
    }

    @Test fun walksOnOtherDaysAreIgnored() {
        val spans = PassiveStepMath.spans(listOf(paid(1, dayStart - 3 * hour, dayStart - hour)), null, null, 0, dayEnd)
        assertTrue(PassiveStepMath.windowsOn(spans, dayStart, dayEnd).isEmpty())
        assertEquals(0, PassiveStepMath.sensorStepsOn(spans, dayStart, dayEnd))
    }

    // ---- Health Connect windows ----

    @Test fun overlappingWindowsAreMergedAndSorted() {
        val spans = listOf(
            WalkSpan(dayStart + 5 * hour, dayStart + 6 * hour, 0),
            WalkSpan(dayStart + hour, dayStart + 3 * hour, 0),
            WalkSpan(dayStart + 2 * hour, dayStart + 4 * hour, 0),
            WalkSpan(dayStart + 4 * hour, dayStart + 4 * hour + 1, 0), // touches the previous window
        )
        assertEquals(
            listOf(TimeWindow(dayStart + hour, dayStart + 4 * hour + 1), TimeWindow(dayStart + 5 * hour, dayStart + 6 * hour)),
            PassiveStepMath.windowsOn(spans, dayStart, dayEnd),
        )
    }

    /** Health Connect without ACTIVITY_RECOGNITION: the walk row has steps = 0 but its time window still counts. */
    @Test fun paidWalkWithZeroSensorStepsStillHasAWindow() {
        val walk = paid(1, dayStart + 8 * hour, dayStart + 9 * hour, steps = 0)
        val spans = PassiveStepMath.spans(listOf(walk), null, null, 0, dayEnd)
        assertEquals(listOf(TimeWindow(dayStart + 8 * hour, dayStart + 9 * hour)), PassiveStepMath.windowsOn(spans, dayStart, dayEnd))
    }
}
