package com.example.wallko.ui.walk

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wallko.core.config.GameRules
import com.example.wallko.core.prefs.SettingsStore
import com.example.wallko.core.time.Clock
import com.example.wallko.core.time.zonedNow
import com.example.wallko.data.wallet.WalletRepository
import com.example.wallko.ui.settings.UnitSystem
import com.example.wallko.walk.StepSync
import com.example.wallko.walk.StepSyncState
import com.example.wallko.walk.WalkLiveState
import com.example.wallko.walk.WalkResult
import com.example.wallko.walk.WalkSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalkUiState(
    val live: WalkLiveState? = null,
    val result: WalkResult? = null,
    val tankDrops: Int = 0,
    val tankCapacity: Int = GameRules.Start.TANK_CAPACITY,
    val elixir: Int = 0,
    val elixirProgressM: Double = 0.0,
    val steps: StepSyncState = StepSyncState(),
    /** Local time is in the dawn window right now (idle sky + bubble). */
    val dawnNow: Boolean = false,
    /** Distance units from Settings. */
    val units: UnitSystem = UnitSystem.METRIC,
)

@HiltViewModel
class WalkViewModel @Inject constructor(
    private val session: WalkSession,
    private val stepSync: StepSync,
    walletRepository: WalletRepository,
    settingsStore: SettingsStore,
    private val clock: Clock,
) : ViewModel() {

    private val dawnTicker = flow {
        while (true) {
            emit(GameRules.isDawn(clock.zonedNow().toLocalTime()))
            delay(60_000)
        }
    }

    val uiState: StateFlow<WalkUiState> = combine(
        session.live,
        session.lastSummary,
        walletRepository.observeWallet(),
        stepSync.state,
        dawnTicker,
    ) { live, result, wallet, steps, dawn ->
        WalkUiState(
            live = live,
            result = result,
            tankDrops = wallet.waterDrops,
            tankCapacity = wallet.tankCapacity,
            elixir = wallet.elixir,
            elixirProgressM = wallet.elixirProgressM,
            steps = steps,
            dawnNow = dawn,
        )
    }.combine(settingsStore.units) { state, units -> state.copy(units = UnitSystem.fromStored(units)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalkUiState(live = session.live.value, result = session.lastSummary.value))

    val healthPermissions: Set<String> get() = stepSync.healthPermissions

    fun healthPermissionContract(): ActivityResultContract<Set<String>, Set<String>> = stepSync.permissionContract()

    fun installHealthConnectIntent(): Intent = stepSync.installHealthConnectIntent()

    /** Screen became visible: recover an interrupted walk and refresh passive steps. */
    fun onScreenResumed() {
        session.resumeIfInterrupted()
        refreshSteps()
    }

    /** Call only once ACCESS_FINE_LOCATION is granted. */
    fun onStartWalk() = session.start()

    fun onStopWalk() = session.stop()

    fun onDismissSummary() = session.dismissSummary()

    fun refreshSteps() {
        viewModelScope.launch { runCatching { stepSync.sync() } }
    }
}
