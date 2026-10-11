package com.example.wallko.walk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalkStepCounterTest {

    @Test fun firstReadingIsBaselineOnly() {
        val c = WalkStepCounter().onReading(12_000)
        assertEquals(0, c.steps)
        assertEquals(12_000L, c.lastReading)
    }

    @Test fun countsDeltas() {
        val c = WalkStepCounter().onReading(1_000).onReading(1_050).onReading(1_200)
        assertEquals(200, c.steps)
    }

    @Test fun counterResetMidWalkKeepsCountedStepsAndAddsNewOnes() {
        // 300 steps, reboot (counter restarts at 0), then 40 more steps.
        val c = WalkStepCounter().onReading(5_000).onReading(5_300).onReading(40)
        assertEquals(340, c.steps)
        assertEquals(380, c.onReading(80).steps)
    }

    @Test fun resumeContinuesFromSavedTotal() {
        val c = WalkStepCounter.resume(250)
        assertNull(c.lastReading)
        assertEquals(250, c.onReading(9_000).steps)
        assertEquals(300, c.onReading(9_000).onReading(9_050).steps)
    }

    @Test fun absurdJumpIgnoredButRebaselines() {
        val c = WalkStepCounter().onReading(10).onReading(10 + MAX_STEP_DELTA + 1)
        assertEquals(0, c.steps)
        assertEquals(10, c.onReading(10 + MAX_STEP_DELTA + 11).steps)
    }

    @Test fun negativeReadingIgnored() {
        val c = WalkStepCounter().onReading(100).onReading(-1)
        assertEquals(100L, c.lastReading)
    }
}

class SensorStepsTest {
    private val hour = 3_600_000L
    private val day = 20_000L
    private val midnight = day * 24 * hour

    private fun read(prev: SensorStepDay?, counter: Long, now: Long, boot: Long, today: Long = day, startOfToday: Long = midnight) =
        SensorSteps.update(prev, counter, now, boot, today, startOfToday)

    @Test fun firstReadBootedTodayCountsAllSinceBoot() {
        val d = read(null, counter = 2_000, now = midnight + 10 * hour, boot = midnight + 2 * hour)
        assertEquals(2_000, d.stepsToday)
        assertEquals(2_000L, d.lastCounter)
    }

    @Test fun firstReadBootedYesterdayCountsOnlyTodaysShare() {
        // Booted 10 h before midnight, read 10 h after: half the span is today.
        val d = read(null, counter = 4_000, now = midnight + 10 * hour, boot = midnight - 10 * hour)
        assertEquals(2_000, d.stepsToday)
    }

    @Test fun sameBootAddsDelta() {
        val boot = midnight - 30 * hour
        val first = read(null, 10_000, midnight + 8 * hour, boot)
        val second = read(first, 10_500, midnight + 9 * hour, boot + 5) // boot estimate jitters a little
        assertEquals(first.stepsToday + 500, second.stepsToday)
    }

    @Test fun rebootDetectedByBootTimeEvenIfCounterIsHigher() {
        val first = SensorStepDay(day, stepsToday = 1_000, lastCounter = 500, lastReadAtMillis = midnight + 8 * hour, bootAtMillis = midnight - 50 * hour)
        // Rebooted at 09:00; 800 steps since then (counter happens to exceed the old 500).
        val second = read(first, 800, midnight + 12 * hour, boot = midnight + 9 * hour)
        assertEquals(1_800, second.stepsToday)
    }

    @Test fun counterDropWithoutBootChangeIsSensorReset() {
        val boot = midnight - 5 * hour
        val first = SensorStepDay(day, 3_000, lastCounter = 9_000, lastReadAtMillis = midnight + 8 * hour, bootAtMillis = boot)
        val second = read(first, 120, midnight + 9 * hour, boot)
        assertEquals(3_120, second.stepsToday)
    }

    @Test fun newDayStartsFromZeroAndSplitsOvernightSpan() {
        val boot = midnight - 50 * hour
        // Last read yesterday at 22:00; now 02:00 today; 400 steps in between → half today.
        val yesterday = SensorStepDay(day - 1, 9_000, 20_000, midnight - 2 * hour, boot)
        val today = read(yesterday, 20_400, midnight + 2 * hour, boot)
        assertEquals(200, today.stepsToday)
        assertEquals(day, today.epochDay)
    }

    @Test fun wallClockChangeWithSameBootCountIsNotAReboot() {
        val boot = midnight - 30 * hour
        val first = SensorSteps.update(null, 10_000, midnight + 8 * hour, boot, day, midnight, bootCount = 7)
        // User moved the clock 2 h forward: boot estimate jumps 2 h, but the boot count is unchanged.
        val second = SensorSteps.update(first, 10_300, midnight + 11 * hour, boot + 2 * hour, day, midnight, bootCount = 7)
        assertEquals(first.stepsToday + 300, second.stepsToday)
        assertEquals(7, second.bootCount)
    }

    @Test fun bootCountChangeIsAReboot() {
        val boot = midnight - 30 * hour
        val first = SensorSteps.update(null, 10_000, midnight + 8 * hour, boot, day, midnight, bootCount = 7)
        // Rebooted at 09:00 (counter restarted); 12_000 > 10_000 so only the boot count reveals it.
        val second = SensorSteps.update(first, 12_000, midnight + 12 * hour, midnight + 9 * hour, day, midnight, bootCount = 8)
        assertEquals(first.stepsToday + 12_000, second.stepsToday)
    }

    @Test fun unknownStoredBootCountFallsBackToBootTime() {
        val boot = midnight - 30 * hour
        val legacy = SensorStepDay(day, 1_000, lastCounter = 10_000, lastReadAtMillis = midnight + 8 * hour, bootAtMillis = boot)
        assertEquals(SensorSteps.UNKNOWN_BOOT_COUNT, legacy.bootCount)
        val same = SensorSteps.update(legacy, 10_200, midnight + 9 * hour, boot + 5, day, midnight, bootCount = 7)
        assertEquals(1_200, same.stepsToday)
        assertEquals(7, same.bootCount)
        assertEquals(true, SensorSteps.isReboot(legacy, boot + 2 * hour, bootCount = 7))
    }

    @Test fun repeatedReadWithSameCounterIsIdempotent() {
        val boot = midnight
        val a = read(null, 1_000, midnight + hour, boot)
        val b = read(a, 1_000, midnight + 2 * hour, boot)
        val c = read(b, 1_000, midnight + 3 * hour, boot)
        assertEquals(a.stepsToday, c.stepsToday)
    }
}
