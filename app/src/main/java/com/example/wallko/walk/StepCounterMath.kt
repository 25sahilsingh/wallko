package com.example.wallko.walk

import kotlin.math.abs

/*
 * Pure helpers for Android's TYPE_STEP_COUNTER sensor, which reports a cumulative step count since the
 * last reboot (it resets to 0 on reboot, and on some devices when the sensor hub restarts).
 */

/** Largest believable jump between two readings; anything above is treated as sensor garbage. */
internal const val MAX_STEP_DELTA = 100_000L

/**
 * Steps taken during one walk, folded from raw counter readings.
 *
 * The first reading only sets the baseline. A reading lower than the previous one means the counter was reset
 * (reboot or sensor restart); the new value is then counted as steps since that reset, so nothing already
 * counted is lost. Use [resume] after a service restart to continue from an earlier total.
 */
data class WalkStepCounter(
    val steps: Int = 0,
    val lastReading: Long? = null,
) {
    fun onReading(counter: Long): WalkStepCounter {
        if (counter < 0) return this
        val last = lastReading ?: return copy(lastReading = counter)
        val delta = if (counter >= last) counter - last else counter
        if (delta > MAX_STEP_DELTA) return copy(lastReading = counter)
        val total = (steps.toLong() + delta).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return copy(steps = total, lastReading = counter)
    }

    companion object {
        /** Continue a walk that already counted [stepsSoFar]; the next reading becomes the new baseline. */
        fun resume(stepsSoFar: Int): WalkStepCounter = WalkStepCounter(steps = stepsSoFar.coerceAtLeast(0))
    }
}

/**
 * Stored baseline for the Health Connect fallback: today's steps estimated from the step counter sensor,
 * read whenever the app opens.
 */
data class SensorStepDay(
    val epochDay: Long,
    val stepsToday: Int,
    val lastCounter: Long,
    val lastReadAtMillis: Long,
    /** Wall-clock time of the boot the [lastCounter] belongs to (now − elapsedRealtime). */
    val bootAtMillis: Long,
    /** Settings.Global.BOOT_COUNT of that boot; [SensorSteps.UNKNOWN_BOOT_COUNT] if it could not be read. */
    val bootCount: Int = SensorSteps.UNKNOWN_BOOT_COUNT,
)

object SensorSteps {
    /** Boot-time estimates drift by a few ms between reads; a bigger jump means a reboot. */
    const val BOOT_TOLERANCE_MS = 60_000L

    /** Boot count could not be read (or was stored before boot counts were tracked). */
    const val UNKNOWN_BOOT_COUNT = -1

    /**
     * Reboot detection. When both boot counts are known they decide alone, so a manual or NTP wall-clock change
     * (which moves the boot-time estimate) is not mistaken for a reboot. Otherwise falls back to the boot-time
     * estimate with [BOOT_TOLERANCE_MS].
     */
    fun isReboot(prev: SensorStepDay?, bootAtMillis: Long, bootCount: Int): Boolean = when {
        prev == null -> true
        bootCount >= 0 && prev.bootCount >= 0 -> bootCount != prev.bootCount
        else -> abs(bootAtMillis - prev.bootAtMillis) > BOOT_TOLERANCE_MS
    }

    /**
     * Folds a new counter reading into the stored baseline and returns the new baseline.
     *
     * - Same boot, counter grew: the delta since the last read is new steps.
     * - Reboot ([isReboot]: boot count changed, or boot time moved when counts are unknown) or first read ever: the counter itself is the steps since boot.
     * - Counter went down without a reboot (sensor reset): the counter is the steps since that reset.
     * - When the counted span started before today's midnight, only the share of steps proportional to the
     *   part of the span that falls today is credited to today (we can't know when exactly they were taken).
     * - On a new day the running total starts from 0; it never goes down within a day.
     */
    fun update(
        prev: SensorStepDay?,
        counter: Long,
        nowMillis: Long,
        bootAtMillis: Long,
        todayEpochDay: Long,
        startOfTodayMillis: Long,
        bootCount: Int = UNKNOWN_BOOT_COUNT,
    ): SensorStepDay {
        val c = counter.coerceAtLeast(0)
        val rebooted = isReboot(prev, bootAtMillis, bootCount)
        val (fromCounter, fromTime) = when {
            rebooted -> 0L to bootAtMillis
            c < prev!!.lastCounter -> 0L to prev.lastReadAtMillis
            else -> prev.lastCounter to prev.lastReadAtMillis
        }
        val delta = (c - fromCounter).coerceIn(0, MAX_STEP_DELTA)
        val todayShare = when {
            fromTime >= startOfTodayMillis || nowMillis <= fromTime -> delta
            nowMillis <= startOfTodayMillis -> 0L
            else -> delta * (nowMillis - startOfTodayMillis) / (nowMillis - fromTime)
        }
        val carried = if (prev != null && prev.epochDay == todayEpochDay) prev.stepsToday.toLong() else 0L
        return SensorStepDay(
            epochDay = todayEpochDay,
            stepsToday = (carried + todayShare).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            lastCounter = c,
            lastReadAtMillis = nowMillis,
            bootAtMillis = bootAtMillis,
            bootCount = bootCount,
        )
    }
}
