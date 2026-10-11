package com.example.wallko.ui.garden

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wallko.core.config.GameRules
import com.example.wallko.core.db.OwnedPlantWithSpecies
import com.example.wallko.core.db.PlantState
import com.example.wallko.data.garden.GardenRepository
import com.example.wallko.data.garden.GardenSpot
import com.example.wallko.data.wallet.WalletRepository
import com.example.wallko.game.GameClock
import com.example.wallko.game.GameMappers
import com.example.wallko.ui.components.PlantStage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** One pot in the garden. [spriteVersion] changes whenever the sprite file is rewritten (style change). */
data class GardenPlantUi(
    val id: Long,
    val name: String,
    val stage: PlantStage,
    val growthFraction: Float,
    val swayPhase: Float,
    val spot: GardenSpot,
    val spritePath: String?,
    val spriteVersion: Long,
) {
    val wilting: Boolean get() = stage == PlantStage.WILTING
}

/** The once-per-catch-up drinking animation. [key] is unique per catch-up. */
data class DrinkingMoment(val key: String, val drankIds: Set<Long>, val tankBefore: Int)

data class GardenUiState(
    val loading: Boolean = true,
    val plants: List<GardenPlantUi> = emptyList(),
    val drops: Int = 0,
    val capacity: Int = GameRules.Start.TANK_CAPACITY,
    val elixir: Int = 0,
    val elixirProgress: Float = 0f,
    val drinking: DrinkingMoment? = null,
)

@HiltViewModel
class GardenViewModel @Inject constructor(
    private val garden: GardenRepository,
    walletRepository: WalletRepository,
    private val gameClock: GameClock,
) : ViewModel() {

    /** Optimistic positions for pots just dropped, until Room reports them. */
    private val pendingMoves = MutableStateFlow<Map<Long, GardenSpot>>(emptyMap())

    private val plants = garden.observeGarden()
        .map { rows -> rows.map(::toUi) }
        .flowOn(Dispatchers.IO) // File.lastModified() per sprite
        .onEach { list ->
            pendingMoves.update { pending ->
                pending.filterNot { (id, spot) -> list.any { it.id == id && it.spot == spot } }
            }
        }

    val uiState: StateFlow<GardenUiState> = combine(
        plants,
        walletRepository.observeWallet(),
        pendingMoves,
        gameClock.lastCatchUp,
    ) { list, wallet, pending, catchUp ->
        val shown = if (pending.isEmpty()) list else list.map { p -> pending[p.id]?.let { p.copy(spot = it) } ?: p }
        val drank = catchUp?.let { GardenMath.drankIds(it.latestDayEvents) }.orEmpty()
        GardenUiState(
            loading = false,
            plants = shown,
            drops = wallet.waterDrops,
            capacity = wallet.tankCapacity,
            elixir = wallet.elixir,
            elixirProgress = (wallet.elixirProgressM / GameRules.Elixir.METERS_IN_NEW_CELLS_PER_ELIXIR).toFloat()
                .coerceIn(0f, 1f),
            drinking = if (catchUp != null && drank.isNotEmpty()) {
                DrinkingMoment(
                    key = "${catchUp.latestDay}-${catchUp.daysProcessed}-${catchUp.dropsDrunk}",
                    drankIds = drank,
                    tankBefore = GardenMath.tankBefore(wallet.waterDrops, catchUp.dropsDrunk, wallet.tankCapacity),
                )
            } else {
                null
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GardenUiState())

    init {
        // A catch-up with nothing to drink-animate (e.g. only wilting) needs no moment; clear it.
        viewModelScope.launch {
            gameClock.lastCatchUp.collect { result ->
                if (result != null && GardenMath.drankIds(result.latestDayEvents).isEmpty()) {
                    gameClock.consumeLastCatchUp()
                }
            }
        }
    }

    /** The screen finished playing the drinking moment. */
    fun onDrinkingShown() = gameClock.consumeLastCatchUp()

    fun move(id: Long, spot: GardenSpot) {
        pendingMoves.update { it + (id to spot) }
        viewModelScope.launch {
            try {
                garden.move(id, spot.x, spot.y, spot.onShelf)
            } catch (e: Exception) {
                Log.w(TAG, "move failed for $id", e)
                pendingMoves.update { it - id }
            }
        }
    }

    private fun toUi(row: OwnedPlantWithSpecies): GardenPlantUi {
        val p = row.plant
        val sprite = p.spritePath?.takeIf { it.isNotBlank() }
        val version = sprite?.let { File(it).lastModified() } ?: 0L
        val hasSprite = sprite != null && version > 0L
        val stage = GardenMath.uiStage(p.state, hasSprite)
        val target = GameMappers.growthTargetFor(row.species.growth)
        return GardenPlantUi(
            id = p.id,
            name = GameMappers.displayName(row.species),
            stage = stage,
            growthFraction = if (p.state == PlantState.GROWN) 1f else GardenMath.growthFraction(p.growthDays, target),
            swayPhase = GardenMath.swayPhase(p.id),
            spot = GardenMath.normalize(GardenSpot(p.gardenX, p.gardenY, p.onShelf)),
            spritePath = if (hasSprite) sprite else null,
            spriteVersion = version,
        )
    }

    private companion object {
        const val TAG = "GardenViewModel"
    }
}
