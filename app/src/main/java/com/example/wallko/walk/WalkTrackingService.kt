package com.example.wallko.walk

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.wallko.MainActivity
import com.example.wallko.R
import com.example.wallko.core.config.GameRules
import com.example.wallko.core.prefs.SettingsStore
import com.example.wallko.core.time.Clock
import com.example.wallko.data.walks.TrackPoint
import com.example.wallko.data.walks.WalkRepository
import com.example.wallko.data.wallet.WalletRepository
import com.example.wallko.ui.settings.UnitSystem
import com.example.wallko.walk.model.GeoPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Foreground "location" service that tracks one user-started walk.
 *
 * - Fused Location, high accuracy, every [GameRules.Walk.LOCATION_INTERVAL] / [GameRules.Walk.LOCATION_MIN_DISTANCE_METERS].
 * - Every fix is folded into a [WalkAccumulator]; fixes that pass the accuracy/order checks are saved in batches.
 * - Steps come from the TYPE_STEP_COUNTER sensor delta (needs ACTIVITY_RECOGNITION; otherwise steps stay null).
 * - Stops on [ACTION_STOP] (UI or notification) or after [GameRules.Walk.MAX_DURATION]; then [WalkFinisher] pays out.
 * - If the process dies, the walk stays in [WalkPrefs]; a sticky restart or [WalkSession.resumeIfInterrupted]
 *   replays the saved points and carries on (or settles it if it is too old / can't go foreground).
 */
@AndroidEntryPoint
class WalkTrackingService : Service() {

    @Inject lateinit var session: WalkSession
    @Inject lateinit var walkRepository: WalkRepository
    @Inject lateinit var walletRepository: WalletRepository
    @Inject lateinit var finisher: WalkFinisher
    @Inject lateinit var prefs: WalkPrefs
    @Inject lateinit var stepSensor: StepSensor
    @Inject lateinit var clock: Clock
    @Inject lateinit var settingsStore: SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var fused: FusedLocationProviderClient

    /** Mutable walk state; only touched on the main thread. */
    private class Tracking(
        val walkId: Long,
        val startedAt: Instant,
        val priorElixirM: Double,
        var acc: WalkAccumulator,
        var steps: WalkStepCounter,
        val stepsEnabled: Boolean,
        val pending: MutableList<TrackPoint> = mutableListOf(),
    )

    private var tracking: Tracking? = null
    private var setupJob: Job? = null
    private var stopping = false
    private var locationUpdatesOn = false
    private var lastNotificationText: String? = null

    /** Distance units from Settings, kept current while the service runs (read on the main thread). */
    private var units = UnitSystem.METRIC
    private val workers = mutableListOf<Job>()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onLocation)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        ensureChannel(this)
        scope.launch { settingsStore.units.collect { units = UnitSystem.fromStored(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val running = tracking != null || setupJob?.isActive == true
                when {
                    running -> requestStop(clock.now())
                    // Nothing running in this process (e.g. stale notification after a kill): settle the saved walk.
                    prefs.activeWalk() != null -> {
                        begin(resume = true)
                        requestStop(clock.now())
                    }
                    else -> stopSelf()
                }
            }
            ACTION_START -> begin(resume = false)
            // ACTION_RESUME, or a null intent from a START_STICKY restart.
            else -> begin(resume = true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        workers.forEach { it.cancel() }
        if (!stopping) session.publish(null) // walk stays in WalkPrefs and is resumed later
        scope.cancel()
        super.onDestroy()
    }

    // ---- Start / resume ----

    private fun begin(resume: Boolean) {
        if (tracking != null || setupJob?.isActive == true || stopping) return
        val active = prefs.activeWalk()
        val wentForeground = goForeground(placeholderNotification(active?.startedAtMillis ?: clock.now().toEpochMilli()))

        if (resume && active == null) {
            session.publish(null)
            stopSelfCompat()
            return
        }
        if (!wentForeground || !hasLocationPermission()) {
            // Can't track (no permission, or not allowed to go foreground from here). Settle a walk in progress.
            setupJob = scope.launch {
                if (active != null) {
                    tracking = restore(active) ?: run { prefs.clearActiveWalk(); null }
                }
                setupJob = null
                if (tracking != null) requestStop(clock.now()) else {
                    session.publish(null)
                    stopSelfCompat()
                }
            }
            return
        }

        setupJob = scope.launch {
            val t = runCatching { (if (active != null) restore(active) else null) ?: createWalk() }
                .onFailure { Log.e(TAG, "could not start walk", it) }
                .getOrNull()
            setupJob = null
            if (t == null) {
                session.publish(null)
                stopSelfCompat()
                return@launch
            }
            tracking = t
            if (stopping) return@launch // a stop arrived during setup; requestStop settles it
            val maxEnd = t.startedAt + GameRules.Walk.MAX_DURATION
            if (!clock.now().isBefore(maxEnd)) {
                // Resumed long after the limit: settle at the last saved fix.
                val lastFix = t.acc.lastPoint?.let { Instant.ofEpochMilli(it.timeMillis) } ?: t.startedAt
                requestStop(minOf(maxOf(lastFix, t.startedAt), maxEnd))
                return@launch
            }
            startLocationUpdates()
            if (t.stepsEnabled) {
                workers += scope.launch {
                    stepSensor.readings().collect { counter ->
                        tracking?.let { it.steps = it.steps.onReading(counter) }
                        publish()
                    }
                }
            }
            workers += scope.launch { tickLoop() }
            publish()
        }
    }

    private suspend fun createWalk(): Tracking {
        val now = clock.now()
        val prior = walletRepository.getWallet().elixirProgressM
        val known = walkRepository.knownCellIds()
        val id = walkRepository.startWalk(now.toEpochMilli())
        prefs.setActiveWalk(ActiveWalk(id, now.toEpochMilli(), steps = 0))
        return Tracking(id, now, prior, WalkAccumulator.start(known), WalkStepCounter(), stepSensor.canRead())
    }

    /** Rebuilds the accumulator from saved points. Null if the walk row is gone or already settled. */
    private suspend fun restore(active: ActiveWalk): Tracking? {
        val walk = walkRepository.getWalk(active.walkId)
        if (walk == null || walk.endedAt != null) {
            prefs.clearActiveWalk()
            return null
        }
        val known = walkRepository.knownCellIds()
        val points = walkRepository.observeWalk(active.walkId).first()?.points.orEmpty()
        // Saved points already passed the accuracy filter; their reported speed is not stored, so the
        // accumulator falls back to distance / time for the driving check.
        val acc = points.fold(WalkAccumulator.start(known)) { a, p -> a.add(GeoPoint(p.lat, p.lng, 0.0, p.at)) }
        val prior = walletRepository.getWallet().elixirProgressM
        return Tracking(
            walkId = active.walkId,
            startedAt = Instant.ofEpochMilli(active.startedAtMillis),
            priorElixirM = prior,
            acc = acc,
            steps = WalkStepCounter.resume(active.steps),
            stepsEnabled = stepSensor.canRead() || active.steps > 0,
        )
    }

    // ---- Location ----

    @SuppressLint("MissingPermission") // checked by hasLocationPermission() before every call
    private fun startLocationUpdates() {
        if (locationUpdatesOn || !hasLocationPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GameRules.Walk.LOCATION_INTERVAL.toMillis())
            .setMinUpdateDistanceMeters(GameRules.Walk.LOCATION_MIN_DISTANCE_METERS)
            .setMinUpdateIntervalMillis(GameRules.Walk.LOCATION_INTERVAL.toMillis())
            .setWaitForAccurateLocation(false)
            .build()
        runCatching {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            locationUpdatesOn = true
        }.onFailure { Log.e(TAG, "requestLocationUpdates failed", it) }
    }

    private fun stopLocationUpdates() {
        if (!locationUpdatesOn) return
        locationUpdatesOn = false
        runCatching { fused.removeLocationUpdates(locationCallback) }
    }

    private fun onLocation(location: Location) {
        val t = tracking ?: return
        if (stopping) return
        val point = location.toGeoPoint()
        val before = t.acc
        t.acc = before.add(point)
        if (t.acc.lastPoint != before.lastPoint) {
            t.pending += TrackPoint(point.timeMillis, point.lat, point.lng)
            if (t.pending.size >= POINT_BATCH) flush(t)
        }
        publish()
    }

    private fun Location.toGeoPoint() = GeoPoint(
        lat = latitude,
        lng = longitude,
        accuracyM = if (hasAccuracy()) accuracy.toDouble() else Double.MAX_VALUE,
        timeMillis = time,
        speedMps = if (hasSpeed()) speed.toDouble() else null,
    )

    /** Saves pending points (fire and forget) and the step total. */
    private fun flush(t: Tracking) {
        prefs.updateActiveSteps(t.steps.steps)
        if (t.pending.isEmpty()) return
        val batch = t.pending.toList()
        t.pending.clear()
        scope.launch {
            runCatching { walkRepository.appendPoints(t.walkId, batch) }
                .onFailure { Log.e(TAG, "appendPoints failed", it) }
        }
    }

    // ---- Ticking: elapsed time, periodic save, max duration ----

    private suspend fun tickLoop() {
        var seconds = 0
        while (scope.isActive && !stopping) {
            delay(1_000)
            val t = tracking ?: continue
            if (!clock.now().isBefore(t.startedAt + GameRules.Walk.MAX_DURATION)) {
                requestStop(t.startedAt + GameRules.Walk.MAX_DURATION)
                return
            }
            if (++seconds % FLUSH_EVERY_SECONDS == 0) flush(t)
            publish()
        }
    }

    // ---- Publishing ----

    private fun liveState(t: Tracking, phase: WalkPhase): WalkLiveState {
        val now = clock.now()
        val elapsed = Duration.between(t.startedAt, now).let { if (it.isNegative) Duration.ZERO else it }
            .coerceAtMost(GameRules.Walk.MAX_DURATION)
        val dawn = GameRules.isDawn(t.startedAt.atZone(clock.zone()).toLocalTime())
        val elixirM = LiveWalkMath.elixirMeters(t.priorElixirM, t.acc)
        return WalkLiveState(
            walkId = t.walkId,
            phase = phase,
            startedAtMillis = t.startedAt.toEpochMilli(),
            elapsed = elapsed,
            distanceM = t.acc.distanceM,
            steps = if (t.stepsEnabled) t.steps.steps else null,
            dropsSoFar = LiveWalkMath.projectedDrops(t.acc, dawn),
            newCells = t.acc.newCells.size,
            isDawn = dawn,
            counting = LiveWalkMath.isCounting(elapsed, t.acc),
            elixirMeters = elixirM,
            elixirSoFar = LiveWalkMath.elixirCount(elixirM),
            elixirFraction = LiveWalkMath.elixirFraction(elixirM),
            gpsReady = t.acc.acceptedPoints > 0,
            lastFix = t.acc.lastPoint,
        )
    }

    private fun publish() {
        val t = tracking ?: return
        if (stopping) return
        val state = liveState(t, WalkPhase.TRACKING)
        session.publish(state)
        updateNotification(state)
    }

    // ---- Stop ----

    private fun requestStop(endAt: Instant) {
        if (stopping) return
        stopping = true
        stopLocationUpdates()
        workers.forEach { it.cancel() }
        workers.clear()
        scope.launch {
            setupJob?.join()
            val t = tracking
            if (t == null) {
                session.publish(null)
                stopSelfCompat()
                return@launch
            }
            session.publish(liveState(t, WalkPhase.FINISHING))
            val end = endAt.coerceIn(t.startedAt, t.startedAt + GameRules.Walk.MAX_DURATION)
            val result = withContext(NonCancellable) {
                runCatching { walkRepository.appendPoints(t.walkId, t.pending.toList()) }
                t.pending.clear()
                runCatching { finisher.finish(t.walkId, t.acc, t.startedAt, end, t.steps.steps) }
                    .onFailure { Log.e(TAG, "finish failed", it) }
                    .getOrElse { WalkResult(t.walkId, null, 0, 0, end.toEpochMilli(), error = it.message ?: "error") }
            }
            prefs.clearActiveWalk()
            tracking = null
            session.publishResult(result)
            session.publish(null)
            stopSelfCompat()
        }
    }

    private fun stopSelfCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- Foreground + notification ----

    private fun goForeground(notification: Notification): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        true
    } catch (e: Exception) {
        // SecurityException (no location permission, API 34+) or ForegroundServiceStartNotAllowedException.
        Log.w(TAG, "startForeground refused", e)
        false
    }

    private fun placeholderNotification(startedAtMillis: Long): Notification =
        buildNotification(getString(R.string.walk_notification_starting), WalkFormat.notificationText(0.0, 0, null, units), startedAtMillis)

    @SuppressLint("MissingPermission") // guarded by canPostNotifications()
    private fun updateNotification(state: WalkLiveState) {
        val text = WalkFormat.notificationText(state.distanceM, state.dropsSoFar, state.steps, units)
        val title = WalkFormat.notificationTitle(state.isDawn, state.counting)
        val key = "$title|$text"
        if (key == lastNotificationText || !canPostNotifications()) return
        lastNotificationText = key
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(title, text, state.startedAtMillis))
    }

    private fun buildNotification(title: String, text: String, startedAtMillis: Long): Notification {
        val open = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            REQUEST_STOP,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_walk_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(startedAtMillis)
            .setUsesChronometer(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, getString(R.string.walk_notification_stop), stop)
            .build()
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "WalkTrackingService"
        const val ACTION_START = "com.example.wallko.walk.START"
        const val ACTION_STOP = "com.example.wallko.walk.STOP"
        const val ACTION_RESUME = "com.example.wallko.walk.RESUME"
        const val CHANNEL_ID = "wallko_walk"
        const val NOTIFICATION_ID = 4_201
        private const val REQUEST_OPEN = 1
        private const val REQUEST_STOP = 2
        private const val POINT_BATCH = 12
        private const val FLUSH_EVERY_SECONDS = 30

        fun startIntent(context: Context) = Intent(context, WalkTrackingService::class.java).setAction(ACTION_START)
        fun stopIntent(context: Context) = Intent(context, WalkTrackingService::class.java).setAction(ACTION_STOP)
        fun resumeIntent(context: Context) = Intent(context, WalkTrackingService::class.java).setAction(ACTION_RESUME)

        fun ensureChannel(context: Context) {
            NotificationManagerCompat.from(context).createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.walk_channel_name))
                    .setDescription(context.getString(R.string.walk_channel_description))
                    .setShowBadge(false)
                    .build(),
            )
        }
    }
}
