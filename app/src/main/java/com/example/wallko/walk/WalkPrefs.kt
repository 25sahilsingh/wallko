package com.example.wallko.walk

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The walk in progress, persisted so a killed service/process can resume or settle it. */
data class ActiveWalk(
    val walkId: Long,
    val startedAtMillis: Long,
    /** Steps counted so far (saved with each point batch). */
    val steps: Int,
)

/**
 * Tiny synchronous key/value store for walk bookkeeping (active walk, step-sensor baseline).
 * SharedPreferences rather than DataStore because the service needs it synchronously in onStartCommand.
 */
@Singleton
class WalkPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("wallko_walk", Context.MODE_PRIVATE)

    fun activeWalk(): ActiveWalk? {
        val id = prefs.getLong(K_WALK_ID, -1L)
        if (id < 0) return null
        return ActiveWalk(id, prefs.getLong(K_WALK_START, 0L), prefs.getInt(K_WALK_STEPS, 0))
    }

    fun setActiveWalk(walk: ActiveWalk) = prefs.edit(commit = true) {
        putLong(K_WALK_ID, walk.walkId)
        putLong(K_WALK_START, walk.startedAtMillis)
        putInt(K_WALK_STEPS, walk.steps)
    }

    fun updateActiveSteps(steps: Int) {
        if (prefs.contains(K_WALK_ID)) prefs.edit { putInt(K_WALK_STEPS, steps) }
    }

    fun clearActiveWalk() = prefs.edit(commit = true) {
        remove(K_WALK_ID)
        remove(K_WALK_START)
        remove(K_WALK_STEPS)
    }

    fun sensorStepDay(): SensorStepDay? {
        if (!prefs.contains(K_SENSOR_DAY)) return null
        return SensorStepDay(
            epochDay = prefs.getLong(K_SENSOR_DAY, 0L),
            stepsToday = prefs.getInt(K_SENSOR_STEPS, 0),
            lastCounter = prefs.getLong(K_SENSOR_COUNTER, 0L),
            lastReadAtMillis = prefs.getLong(K_SENSOR_READ_AT, 0L),
            bootAtMillis = prefs.getLong(K_SENSOR_BOOT_AT, 0L),
            bootCount = prefs.getInt(K_SENSOR_BOOT_COUNT, SensorSteps.UNKNOWN_BOOT_COUNT),
        )
    }

    fun setSensorStepDay(day: SensorStepDay) = prefs.edit {
        putLong(K_SENSOR_DAY, day.epochDay)
        putInt(K_SENSOR_STEPS, day.stepsToday)
        putLong(K_SENSOR_COUNTER, day.lastCounter)
        putLong(K_SENSOR_READ_AT, day.lastReadAtMillis)
        putLong(K_SENSOR_BOOT_AT, day.bootAtMillis)
        putInt(K_SENSOR_BOOT_COUNT, day.bootCount)
    }

    private companion object {
        const val K_WALK_ID = "active_walk_id"
        const val K_WALK_START = "active_walk_started_at"
        const val K_WALK_STEPS = "active_walk_steps"
        const val K_SENSOR_DAY = "sensor_epoch_day"
        const val K_SENSOR_STEPS = "sensor_steps_today"
        const val K_SENSOR_COUNTER = "sensor_last_counter"
        const val K_SENSOR_READ_AT = "sensor_last_read_at"
        const val K_SENSOR_BOOT_AT = "sensor_boot_at"
        const val K_SENSOR_BOOT_COUNT = "sensor_boot_count"
    }
}
