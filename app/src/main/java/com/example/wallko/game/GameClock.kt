package com.example.wallko.game

import android.util.Log
import androidx.room.withTransaction
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.core.prefs.SettingsStore
import com.example.wallko.core.time.Clock
import com.example.wallko.data.garden.GardenRepository
import com.example.wallko.data.wallet.WalletRepository
import com.example.wallko.game.model.GardenEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What one [GameClock.catchUp] applied.
 *
 * @param latestDayEvents events of the most recent day processed (for the drinking animation).
 * @param wiltedIds plants that crossed into WILTING on any processed day.
 * @param witheredIds plants that became WITHERED on any processed day.
 * @param plantNames display names for every plant mentioned in the events.
 */
data class CatchUpResult(
    val daysProcessed: Int,
    val latestDay: LocalDate?,
    val latestDayEvents: List<GardenEvent>,
    val wiltedIds: List<Long>,
    val witheredIds: List<Long>,
    val dropsDrunk: Int,
    val plantNames: Map<Long, String>,
) {
    val changed: Boolean get() = daysProcessed > 0
    val anyWilted: Boolean get() = wiltedIds.isNotEmpty()
    val anyWithered: Boolean get() = witheredIds.isNotEmpty()

    companion object {
        val NONE = CatchUpResult(0, null, emptyList(), emptyList(), emptyList(), 0, emptyMap())
    }
}

/**
 * Lazily applies the time that passed since the last processed tick (architecture.md: "time is computed lazily").
 * Called on app open ([onAppForeground]) and by [DailyTickWorker]. A [Mutex] stops the two from double-running.
 *
 * The authoritative "last tick" is `Wallet.lastTickAt`, written in the same Room transaction as the plant and
 * wallet changes; [SettingsStore.lastProcessedTickAt] mirrors it afterwards. The max of both is used, so a crash
 * between the commit and the DataStore write can never replay a day.
 */
@Singleton
class GameClock @Inject constructor(
    private val db: WallkoDatabase,
    private val garden: GardenRepository,
    private val wallet: WalletRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _lastCatchUp = MutableStateFlow<CatchUpResult?>(null)

    /** Latest catch-up that changed something; null until then or after [consumeLastCatchUp]. */
    val lastCatchUp: StateFlow<CatchUpResult?> = _lastCatchUp.asStateFlow()

    /** Call when the app comes to the foreground. Fire-and-forget; errors are logged, never thrown. */
    fun onAppForeground() {
        scope.launch {
            try {
                catchUp()
            } catch (e: Exception) {
                Log.w(TAG, "catch-up on foreground failed", e)
            }
        }
    }

    /** UI calls this once the drinking animation has played. */
    fun consumeLastCatchUp() {
        _lastCatchUp.value = null
    }

    suspend fun catchUp(): CatchUpResult = mutex.withLock {
        wallet.ensureInitialized()
        val now = clock.now()
        val stored = settings.lastProcessedTickAt.first()
        if (stored == null) {
            settings.setLastProcessedTickAt(now.toEpochMilli())
            return@withLock CatchUpResult.NONE
        }

        val result = db.withTransaction {
            val w = checkNotNull(db.walletDao().get()) { "wallet missing after init" }
            val lastTickAt = Instant.ofEpochMilli(maxOf(stored, w.lastTickAt))

            // Every owned plant (withered too): capacity counts distinct species in the whole collection; the engine skips withered.
            val plants = db.ownedPlantDao().getAll()
            val speciesDao = db.plantSpeciesDao()
            val species = plants.map { it.speciesIndex }.distinct()
                .mapNotNull { idx -> speciesDao.byIndex(idx)?.let { idx to it } }
                .toMap()
            val known = plants.filter { it.speciesIndex in species }
            val snapshots = known.map { GameMappers.toSnapshot(it, species.getValue(it.speciesIndex)) }
            val names = known.associate { it.id to GameMappers.displayName(species.getValue(it.speciesIndex)) }

            val tick = GameEngine.advance(snapshots, GameMappers.toWalletState(w), lastTickAt, now, clock.zone())
            if (!tick.changed) return@withTransaction null

            garden.updateStates(GameMappers.stateUpdates(known, tick.garden, tick.days))
            wallet.applyTick(
                GameMappers.applyWalletState(w, tick.wallet, tick.lastTickAt),
                GameMappers.drinkLedgerEntries(tick.days, names),
            )

            val events = tick.events
            val latest = tick.days.last()
            CatchUpResult(
                daysProcessed = tick.days.size,
                latestDay = latest.date,
                latestDayEvents = latest.events,
                wiltedIds = events.filterIsInstance<GardenEvent.Wilted>().map { it.plantId }.distinct(),
                witheredIds = events.filterIsInstance<GardenEvent.Withered>().map { it.plantId }.distinct(),
                dropsDrunk = tick.dropsDrunk,
                plantNames = names.filterKeys { id -> events.any { it.plantId == id } },
            ) to tick.lastTickAt
        } ?: return@withLock CatchUpResult.NONE

        settings.setLastProcessedTickAt(result.second.toEpochMilli())
        _lastCatchUp.value = result.first
        result.first
    }

    private companion object {
        const val TAG = "GameClock"
    }
}
