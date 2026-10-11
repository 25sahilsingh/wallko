package com.example.wallko.ui.collection

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wallko.core.config.GameRules
import com.example.wallko.core.db.OwnedPlantWithSpecies
import com.example.wallko.core.db.PlantState
import com.example.wallko.core.db.Rarity
import com.example.wallko.data.garden.GardenRepository
import com.example.wallko.data.plants.PlantRepository
import com.example.wallko.data.wallet.WalletRepository
import com.example.wallko.game.GameMappers
import com.example.wallko.game.ReviveOutcome
import com.example.wallko.game.ReviveUseCase
import com.example.wallko.ui.components.PlantStage
import com.example.wallko.ui.garden.GardenMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** One card in the collection grid. */
data class CollectionCard(
    val id: Long,
    val speciesIndex: Int,
    val name: String,
    val latin: String,
    val rarity: Rarity,
    val type: String?,
    val stage: PlantStage,
    val growthFraction: Float,
    val spritePath: String?,
    val spriteVersion: Long,
    val foundAt: Long,
) {
    val withered: Boolean get() = stage == PlantStage.WITHERED
}

data class CollectionFilters(val rarity: Rarity? = null, val type: String? = null)

data class CollectionUiState(
    val loading: Boolean = true,
    val cards: List<CollectionCard> = emptyList(),
    /** Distinct species ever owned (withered included). */
    val foundSpecies: Int = 0,
    val totalSpecies: Int = 0,
    val types: List<String> = emptyList(),
    val filters: CollectionFilters = CollectionFilters(),
    val ownedAny: Boolean = false,
    val elixir: Int = 0,
    val reviveCost: Int = GameRules.Elixir.REVIVE_COST,
    val revivingId: Long? = null,
)

/** One-shot UI messages. */
sealed interface CollectionEvent {
    data class Revived(val plantId: Long, val name: String) : CollectionEvent
    data class NotEnoughElixir(val have: Int, val need: Int) : CollectionEvent
    data object AlreadyInGarden : CollectionEvent
    data object NotWithered : CollectionEvent
    data object NotFound : CollectionEvent
    data object Failed : CollectionEvent
}

/** Pure collection helpers (JVM-tested from the garden test package). */
object CollectionMath {
    fun filter(cards: List<CollectionCard>, filters: CollectionFilters): List<CollectionCard> = cards.filter {
        (filters.rarity == null || it.rarity == filters.rarity) &&
            (filters.type == null || it.type.equals(filters.type, ignoreCase = true))
    }

    fun distinctSpecies(cards: List<CollectionCard>): Int = cards.distinctBy { it.speciesIndex }.size

    /** Types present in the collection, sorted, blanks dropped. */
    fun types(cards: List<CollectionCard>): List<String> =
        cards.mapNotNull { it.type?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }.distinct().sorted()

    /** Newest finds first; withered after living ones with the same find time. */
    fun sort(cards: List<CollectionCard>): List<CollectionCard> =
        cards.sortedWith(compareByDescending<CollectionCard> { it.foundAt }.thenBy { it.withered }.thenByDescending { it.id })
}

@HiltViewModel
class CollectionViewModel @Inject constructor(
    garden: GardenRepository,
    plants: PlantRepository,
    walletRepository: WalletRepository,
    private val reviveUseCase: ReviveUseCase,
) : ViewModel() {

    private val filters = MutableStateFlow(CollectionFilters())
    private val revivingId = MutableStateFlow<Long?>(null)
    private val events = Channel<CollectionEvent>(Channel.BUFFERED)

    val eventFlow: Flow<CollectionEvent> = events.receiveAsFlow()

    private val cards = garden.observeCollection()
        .map { rows -> CollectionMath.sort(rows.map(::toCard)) }
        .flowOn(Dispatchers.IO)

    val uiState: StateFlow<CollectionUiState> = combine(
        cards,
        plants.observeCount(),
        filters,
        walletRepository.observeWallet(),
        revivingId,
    ) { all, total, f, wallet, reviving ->
        CollectionUiState(
            loading = false,
            cards = CollectionMath.filter(all, f),
            foundSpecies = CollectionMath.distinctSpecies(all),
            totalSpecies = total,
            types = CollectionMath.types(all),
            filters = f,
            ownedAny = all.isNotEmpty(),
            elixir = wallet.elixir,
            revivingId = reviving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState())

    fun setRarity(rarity: Rarity?) = filters.update { it.copy(rarity = rarity) }

    fun setType(type: String?) = filters.update { it.copy(type = type) }

    fun clearFilters() = filters.update { CollectionFilters() }

    fun revive(plantId: Long) {
        if (revivingId.value != null) return
        revivingId.value = plantId
        viewModelScope.launch {
            val event = try {
                when (val r = reviveUseCase.revive(plantId)) {
                    is ReviveOutcome.Revived -> CollectionEvent.Revived(r.plantId, r.plantName)
                    is ReviveOutcome.NotEnoughElixir -> CollectionEvent.NotEnoughElixir(r.have, r.need)
                    is ReviveOutcome.AlreadyInGarden -> CollectionEvent.AlreadyInGarden
                    ReviveOutcome.NotWithered -> CollectionEvent.NotWithered
                    ReviveOutcome.NotFound -> CollectionEvent.NotFound
                }
            } catch (e: Exception) {
                Log.w(TAG, "revive $plantId failed", e)
                CollectionEvent.Failed
            } finally {
                revivingId.value = null
            }
            events.send(event)
        }
    }

    private fun toCard(row: OwnedPlantWithSpecies): CollectionCard {
        val p = row.plant
        val sprite = p.spritePath?.takeIf { it.isNotBlank() }
        val version = sprite?.let { File(it).lastModified() } ?: 0L
        val hasSprite = sprite != null && version > 0L
        return CollectionCard(
            id = p.id,
            speciesIndex = p.speciesIndex,
            name = GameMappers.displayName(row.species),
            latin = row.species.latin,
            rarity = row.species.rarity,
            type = row.species.type,
            stage = GardenMath.uiStage(p.state, hasSprite),
            growthFraction = if (p.state == PlantState.GROWN) 1f
            else GardenMath.growthFraction(p.growthDays, GameMappers.growthTargetFor(row.species.growth)),
            spritePath = if (hasSprite) sprite else null,
            spriteVersion = version,
            foundAt = p.foundAt,
        )
    }

    private companion object {
        const val TAG = "CollectionViewModel"
    }
}
