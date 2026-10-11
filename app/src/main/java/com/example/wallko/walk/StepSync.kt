package com.example.wallko.walk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.room.withTransaction
import com.example.wallko.core.db.LedgerKind
import com.example.wallko.core.db.StepDay
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.core.time.Clock
import com.example.wallko.core.time.today
import com.example.wallko.data.walks.WalkRepository
import com.example.wallko.data.wallet.WalletRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthConnectStatus {
    /** SDK_AVAILABLE: client can be created. */
    AVAILABLE,

    /** SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED: Health Connect app missing or too old (Android 10–13). */
    UPDATE_REQUIRED,

    /** SDK_UNAVAILABLE: not supported on this device. */
    UNAVAILABLE,
}

enum class StepSource { HEALTH_CONNECT, DEVICE_SENSOR, NONE }

data class StepSyncState(
    val healthConnect: HealthConnectStatus = HealthConnectStatus.UNAVAILABLE,
    val healthPermissionGranted: Boolean = false,
    val source: StepSource = StepSource.NONE,
    /** Today's steps from [source]; null if no source could be read. */
    val stepsToday: Int? = null,
    /** All passive-step drops credited today (StepDay.dropsCredited). */
    val dropsToday: Int = 0,
    /** Drops credited by the most recent sync (0 when nothing new). */
    val lastCredited: Int = 0,
    val syncing: Boolean = false,
    /** True once at least one sync finished. */
    val loaded: Boolean = false,
)

/**
 * Passive daily steps → drops.
 *
 * Source: Health Connect (StepsRecord COUNT_TOTAL from local midnight) when available and permitted; otherwise
 * the TYPE_STEP_COUNTER sensor with a stored baseline (risks.md fallback), read whenever [sync] runs.
 * Credits `WalkRewards.dailyStepDrops` (excluding steps taken during paid GPS walks and the walk in progress, see
 * [PassiveStepMath]) to the wallet as STEPS
 * and records the running total in that day's [StepDay], in one transaction. Idempotent: call it as often as
 * you like (app open, screen resume); only the not-yet-credited difference is ever paid.
 * With Health Connect, yesterday is topped up too so steps taken late last night aren't lost.
 */
@Singleton
class StepSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WallkoDatabase,
    private val walkRepository: WalkRepository,
    private val walletRepository: WalletRepository,
    private val stepSensor: StepSensor,
    private val prefs: WalkPrefs,
    private val walkSession: WalkSession,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(StepSyncState())
    val state: StateFlow<StepSyncState> = _state.asStateFlow()

    /** Health Connect permissions Wallko asks for. */
    val healthPermissions: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    fun healthConnectStatus(): HealthConnectStatus =
        when (HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.UPDATE_REQUIRED
            else -> HealthConnectStatus.UNAVAILABLE
        }

    /** Launch with [healthPermissions]; the result is the set actually granted. Then call [sync]. */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /** Play Store page that installs / updates Health Connect (for [HealthConnectStatus.UPDATE_REQUIRED]). */
    fun installHealthConnectIntent(): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    suspend fun hasHealthPermission(): Boolean {
        if (healthConnectStatus() != HealthConnectStatus.AVAILABLE) return false
        return runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(healthPermissions)
        }.getOrDefault(false)
    }

    /** Reads today's steps and credits any new drops. Safe to call any number of times. */
    suspend fun sync(): StepSyncState = mutex.withLock {
        _state.update { it.copy(syncing = true) }
        val zone = clock.zone()
        val today = clock.today()
        val status = healthConnectStatus()
        var granted = false
        var source = StepSource.NONE
        var steps: Int? = null
        var credited = 0

        if (status == HealthConnectStatus.AVAILABLE) {
            runCatching {
                val client = HealthConnectClient.getOrCreate(context)
                granted = client.permissionController.getGrantedPermissions().containsAll(healthPermissions)
                if (granted) {
                    val spans = walkSpans()
                    val yesterday = today.minusDays(1)
                    if (walkRepository.getStepDay(yesterday) != null) {
                        val walkSteps = healthConnectWalkSteps(client, spans, yesterday, zone)
                        credited += creditDay(yesterday, readHealthConnectSteps(client, yesterday, zone), walkSteps)
                    }
                    val todaySteps = readHealthConnectSteps(client, today, zone)
                    credited += creditDay(today, todaySteps, healthConnectWalkSteps(client, spans, today, zone))
                    steps = todaySteps
                    source = StepSource.HEALTH_CONNECT
                }
            }.onFailure { Log.w(TAG, "Health Connect read failed; falling back to sensor", it) }
        }

        if (steps == null && stepSensor.canRead()) {
            runCatching {
                val counter = stepSensor.readOnce()
                if (counter != null) {
                    val spans = walkSpans()
                    val nowMillis = clock.now().toEpochMilli()
                    val day = SensorSteps.update(
                        prev = prefs.sensorStepDay(),
                        counter = counter,
                        nowMillis = nowMillis,
                        bootAtMillis = stepSensor.bootAtMillis(nowMillis),
                        todayEpochDay = today.toEpochDay(),
                        startOfTodayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli(),
                        bootCount = stepSensor.bootCount(),
                    )
                    prefs.setSensorStepDay(day)
                    val (from, to) = dayBounds(today, zone)
                    credited += creditDay(today, day.stepsToday, PassiveStepMath.sensorStepsOn(spans, from, to))
                    steps = day.stepsToday
                    source = StepSource.DEVICE_SENSOR
                }
            }.onFailure { Log.w(TAG, "step sensor read failed", it) }
        }

        val stored = walkRepository.getStepDay(today)
        val newState = StepSyncState(
            healthConnect = status,
            healthPermissionGranted = granted,
            source = source,
            stepsToday = steps ?: stored?.steps,
            dropsToday = stored?.dropsCredited ?: 0,
            lastCredited = credited,
            syncing = false,
            loaded = true,
        )
        _state.value = newState
        newState
    }

    private suspend fun readHealthConnectSteps(client: HealthConnectClient, date: LocalDate, zone: ZoneId): Int =
        readHealthConnectSteps(client, date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())

    /** HC COUNT_TOTAL over [from, to), with [to] clipped to now. */
    private suspend fun readHealthConnectSteps(client: HealthConnectClient, from: Instant, to: Instant): Int {
        val start = from
        val end = minOf(to, clock.now()).let { if (it.isAfter(start)) it else start.plusMillis(1) }
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        return (result[StepsRecord.COUNT_TOTAL] ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Credits the not-yet-paid drops for [date] given the day's total [steps] and the [walkSteps] of it that GPS
     * walks pay for instead; returns drops credited (before tank clamping).
     */
    private suspend fun creditDay(date: LocalDate, steps: Int, walkSteps: Int): Int {
        walletRepository.ensureInitialized()
        return db.withTransaction {
            val day = walkRepository.getStepDay(date)
            val already = day?.dropsCredited ?: 0
            val effectiveSteps = maxOf(steps, day?.steps ?: 0)
            val drops = WalkRewards.dailyStepDrops(effectiveSteps, already, walkSteps)
            if (day == null || drops > 0 || effectiveSteps != day.steps) {
                walkRepository.upsertStepDay(StepDay(date.toEpochDay(), effectiveSteps, already + drops))
            }
            if (drops > 0) walletRepository.credit(LedgerKind.STEPS, drops, note = "steps $date")
            drops
        }
    }

    /**
     * Walks whose steps are not passive: paid settled walks plus the walk in progress (from [WalkPrefs] and
     * [WalkSession], so a sync mid-walk — app foreground, Walk screen resume — doesn't pay its steps as passive).
     * The active walk is read before the walk table so a walk settled in between is still covered by its row.
     * Once a walk ends, an invalid one drops out of the list and its steps are credited on the next sync.
     */
    private suspend fun walkSpans(): List<WalkSpan> {
        val persisted = prefs.activeWalk()
        val live = walkSession.live.value
        val activeId = persisted?.walkId ?: live?.walkId
        val activeStart = listOfNotNull(persisted?.startedAtMillis, live?.startedAtMillis).minOrNull()
        val activeSteps = maxOf(persisted?.steps ?: 0, live?.steps ?: 0)
        val walks = walkRepository.observeWalks().first()
        return PassiveStepMath.spans(walks, activeId, activeStart, activeSteps, clock.now().toEpochMilli())
    }

    /** Health Connect steps taken inside walk [spans] on [date] (each walk window clipped to the day). */
    private suspend fun healthConnectWalkSteps(client: HealthConnectClient, spans: List<WalkSpan>, date: LocalDate, zone: ZoneId): Int {
        val (from, to) = dayBounds(date, zone)
        var total = 0L
        for (w in PassiveStepMath.windowsOn(spans, from, to)) {
            total += readHealthConnectSteps(client, Instant.ofEpochMilli(w.startMillis), Instant.ofEpochMilli(w.endMillis))
        }
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
        date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    private companion object {
        const val TAG = "StepSync"
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    }
}
