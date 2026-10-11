package com.example.wallko.walk

import com.example.wallko.core.db.Walk

/**
 * A stretch of time whose steps are (or will be) paid by GPS walk distance, so they must not also be paid as
 * passive steps (game-rules.md: walk distance replaces the steps taken during the walk).
 *
 * @param sensorSteps step-counter steps recorded for the walk; only used by the sensor fallback.
 */
data class WalkSpan(val startMillis: Long, val endMillis: Long, val sensorSteps: Int)

/** Half-open time window [startMillis, endMillis). */
data class TimeWindow(val startMillis: Long, val endMillis: Long)

/** Pure helpers deciding which steps of a day belong to GPS walks. */
object PassiveStepMath {

    /**
     * True for a settled walk that paid distance drops. That is how a valid walk is recognised in the walk
     * table: invalid walks are stored with dropsEarned = 0 (and steps = 0), while every valid walk is
     * ≥ 100 m and so earns ≥ 2 distance drops. Using dropsEarned also covers invalid walks stored before
     * WalkFinisher started zeroing their steps.
     */
    fun isPaidWalk(walk: Walk): Boolean = walk.endedAt != null && walk.dropsEarned > 0

    /**
     * Spans to exclude from passive crediting: every paid settled walk, plus the walk in progress
     * ([activeStartMillis] up to [nowMillis], with [activeSteps] counted so far) if there is one.
     * The active walk is skipped if its row is already settled (it is then covered by the settled walk, or not
     * excluded at all if it turned out invalid). Unpaid walks are left out, so their steps stay passive.
     */
    fun spans(
        walks: List<Walk>,
        activeWalkId: Long?,
        activeStartMillis: Long?,
        activeSteps: Int,
        nowMillis: Long,
    ): List<WalkSpan> {
        val settled = walks.filter(::isPaidWalk).map { WalkSpan(it.startedAt, it.endedAt!!, it.steps.coerceAtLeast(0)) }
        val activeSettled = activeWalkId != null && walks.any { it.id == activeWalkId && it.endedAt != null }
        if (activeStartMillis == null || activeSettled) return settled
        return settled + WalkSpan(activeStartMillis, maxOf(nowMillis, activeStartMillis), activeSteps.coerceAtLeast(0))
    }

    /**
     * [spans] clipped to the day [dayStartMillis, dayEndMillis), empty ones dropped, overlaps merged, sorted.
     * Health Connect step totals over these windows are the steps to subtract for that day.
     */
    fun windowsOn(spans: List<WalkSpan>, dayStartMillis: Long, dayEndMillis: Long): List<TimeWindow> {
        val clipped = spans
            .map { TimeWindow(maxOf(it.startMillis, dayStartMillis), minOf(it.endMillis, dayEndMillis)) }
            .filter { it.endMillis > it.startMillis }
            .sortedBy { it.startMillis }
        val merged = mutableListOf<TimeWindow>()
        for (w in clipped) {
            val last = merged.lastOrNull()
            if (last != null && w.startMillis <= last.endMillis) {
                merged[merged.lastIndex] = TimeWindow(last.startMillis, maxOf(last.endMillis, w.endMillis))
            } else {
                merged += w
            }
        }
        return merged
    }

    /**
     * Sensor fallback: walk steps that fall on the day, each span's [WalkSpan.sensorSteps] prorated by the share
     * of its duration inside [dayStartMillis, dayEndMillis) (the sensor can't say when exactly steps were taken), rounded up.
     * A zero-length span counts fully on the day it starts.
     */
    fun sensorStepsOn(spans: List<WalkSpan>, dayStartMillis: Long, dayEndMillis: Long): Int {
        var total = 0L
        for (s in spans) {
            val duration = s.endMillis - s.startMillis
            if (duration <= 0) {
                if (s.startMillis in dayStartMillis until dayEndMillis) total += s.sensorSteps
                continue
            }
            val overlap = minOf(s.endMillis, dayEndMillis) - maxOf(s.startMillis, dayStartMillis)
            if (overlap <= 0) continue
            // Rounded up so a split walk never under-subtracts (which would pay a step twice); at most 1 step per day.
            total += if (overlap >= duration) s.sensorSteps.toLong() else (s.sensorSteps.toLong() * overlap + duration - 1) / duration
        }
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
