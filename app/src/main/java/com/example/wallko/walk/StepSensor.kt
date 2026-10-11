package com.example.wallko.walk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Thin wrapper over the hardware TYPE_STEP_COUNTER sensor (cumulative steps since boot). */
@Singleton
class StepSensor @Inject constructor(@ApplicationContext private val context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val isAvailable: Boolean get() = sensor != null

    /** ACTIVITY_RECOGNITION is a runtime permission from API 29 (our minSdk). */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    fun canRead(): Boolean = isAvailable && hasPermission()

    /** Wall-clock time of the current boot; the counter restarts from 0 at this moment. */
    fun bootAtMillis(nowMillis: Long): Long = nowMillis - SystemClock.elapsedRealtime()

    /** Settings.Global.BOOT_COUNT (API 24+): increments on every boot, unaffected by wall-clock changes. */
    fun bootCount(): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, SensorSteps.UNKNOWN_BOOT_COUNT) }
            .getOrDefault(SensorSteps.UNKNOWN_BOOT_COUNT)

    /** Raw counter values, delivered on the main thread while collected. Empty if unavailable. */
    fun readings(): Flow<Long> {
        val sm = sensorManager
        val s = sensor
        if (sm == null || s == null || !hasPermission()) return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    event.values.firstOrNull()?.let { trySend(it.toLong()) }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (!sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)) close()
            awaitClose { sm.unregisterListener(listener) }
        }
    }

    /**
     * One counter value, or null if the sensor is missing, not permitted, or silent for [timeoutMs].
     * Most devices deliver the current value right after registration.
     */
    suspend fun readOnce(timeoutMs: Long = 3_000): Long? {
        if (!canRead()) return null
        return withTimeoutOrNull(timeoutMs) { readings().firstOrNull() }
    }
}
