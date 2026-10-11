package com.example.wallko.walk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.wallko.core.time.Clock
import com.example.wallko.walk.model.GeoPoint
import com.example.wallko.walk.model.WalkSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

enum class WalkPhase {
    /** Service is up, walk row being created / restored. */
    STARTING,

    /** Receiving location fixes. */
    TRACKING,

    /** Stopped; rewards being written. */
    FINISHING,
}

/** Snapshot of the running walk, republished by [WalkTrackingService] on every fix, step, and second. */
data class WalkLiveState(
    val walkId: Long?,
    val phase: WalkPhase,
    val startedAtMillis: Long,
    val elapsed: Duration = Duration.ZERO,
    val distanceM: Double = 0.0,
    /** Steps from the device step counter during this walk; null if the sensor is missing or not permitted. */
    val steps: Int? = null,
    /** Drops this walk pays if stopped now (once [counting]); see [LiveWalkMath.projectedDrops]. */
    val dropsSoFar: Int = 0,
    val newCells: Int = 0,
    /** Walk started in the dawn window: all walk drops ×2. */
    val isDawn: Boolean = false,
    /** Long enough (time + distance) to earn anything. */
    val counting: Boolean = false,
    /** Carried-over + this walk's metres toward elixir. */
    val elixirMeters: Double = 0.0,
    /** Elixir that will be brewed from [elixirMeters] (counting carried-over progress). */
    val elixirSoFar: Int = 0,
    /** 0..1 toward the next elixir. */
    val elixirFraction: Float = 0f,
    /** At least one accurate fix received. */
    val gpsReady: Boolean = false,
    /** Most recent accurate fix (for the map). */
    val lastFix: GeoPoint? = null,
)

/** What a finished walk paid out. [summary] is null only if settling failed ([error] set). */
data class WalkResult(
    val walkId: Long,
    val summary: WalkSummary?,
    /** Drops that actually went into the tank (the tank caps at capacity). */
    val dropsCredited: Int,
    /** Drops earned but spilled because the tank was full. */
    val dropsOverflow: Int,
    val endedAtMillis: Long,
    val error: String? = null,
)

/**
 * Process-wide handle on the walk: the UI reads [live] / [lastSummary] and calls [start] / [stop];
 * [WalkTrackingService] is the only writer. State lives here (not in a ViewModel) so it survives rotation and
 * screen changes; a killed process is recovered from [WalkPrefs] via [resumeIfInterrupted].
 */
@Singleton
class WalkSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: WalkPrefs,
    private val clock: Clock,
) {
    private val _live = MutableStateFlow<WalkLiveState?>(null)
    private val _lastSummary = MutableStateFlow<WalkResult?>(null)

    /** Non-null while a walk is starting, running, or being settled. */
    val live: StateFlow<WalkLiveState?> = _live.asStateFlow()

    /** Result of the most recent finished walk until [dismissSummary]. In-memory only. */
    val lastSummary: StateFlow<WalkResult?> = _lastSummary.asStateFlow()

    val isActive: Boolean get() = _live.value != null

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Starts a new walk. Call from the foreground UI after ACCESS_FINE_LOCATION is granted. */
    fun start() {
        if (isActive || !hasLocationPermission()) return
        _lastSummary.value = null
        _live.value = WalkLiveState(walkId = null, phase = WalkPhase.STARTING, startedAtMillis = clock.now().toEpochMilli())
        ContextCompat.startForegroundService(context, WalkTrackingService.startIntent(context))
    }

    /** Stops the running walk; rewards appear in [lastSummary]. */
    fun stop() {
        if (!isActive) return
        context.startService(WalkTrackingService.stopIntent(context))
    }

    /**
     * If the process died mid-walk (live state lost but a walk is still marked active), restart the service so it
     * resumes tracking or settles the walk. Call from the foreground UI. @return true if a recovery was started.
     */
    fun resumeIfInterrupted(): Boolean {
        val active = prefs.activeWalk() ?: return false
        if (isActive) return false
        _live.value = WalkLiveState(walkId = active.walkId, phase = WalkPhase.STARTING, startedAtMillis = active.startedAtMillis)
        ContextCompat.startForegroundService(context, WalkTrackingService.resumeIntent(context))
        return true
    }

    fun dismissSummary() {
        _lastSummary.value = null
    }

    internal fun publish(state: WalkLiveState?) {
        _live.value = state
    }

    internal fun publishResult(result: WalkResult) {
        _lastSummary.value = result
    }
}
