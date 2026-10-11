package com.example.wallko.ui.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wallko.core.db.GrowthSpeed
import com.example.wallko.core.db.OwnedPlantWithSpecies
import com.example.wallko.core.db.PlantState
import com.example.wallko.core.db.Rarity
import com.example.wallko.core.time.Clock
import com.example.wallko.data.garden.GardenRepository
import com.example.wallko.game.GameMappers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/** Everything the card shows, already formatted except for string resources. */
data class CardModel(
    val plantId: Long,
    val name: String,
    /** Latin name; null when it is already the title. */
    val latin: String?,
    val family: String?,
    val rarity: Rarity,
    val state: PlantState,
    val growthFraction: Float,
    val spritePath: String?,
    val photoPath: String,
    val thirst: Int,
    val sun: Int,
    val growth: GrowthSpeed,
    val estimated: EstimatedFields,
    val bloom: BloomText,
    val badges: List<String>,
    val description: String?,
    val foundOn: FoundOn,
)

sealed interface CardUiState {
    data object Loading : CardUiState
    data object NotFound : CardUiState
    data class Loaded(val card: CardModel) : CardUiState
}

fun OwnedPlantWithSpecies.toCardModel(zone: ZoneId, locale: Locale = Locale.getDefault()): CardModel {
    val name = GameMappers.displayName(species)
    return CardModel(
        plantId = plant.id,
        name = name,
        latin = species.latin.takeIf { it != name },
        family = species.family,
        rarity = species.rarity,
        state = plant.state,
        growthFraction = CardFormat.growthFraction(plant.growthDays, GameMappers.growthTargetFor(species.growth)),
        spritePath = plant.spritePath,
        photoPath = plant.photoPath,
        thirst = species.thirst,
        sun = species.sun,
        growth = species.growth,
        estimated = CardFormat.estimatedFields(species.sources),
        bloom = CardFormat.bloomText(species.bloom, locale),
        badges = species.badges.map(CardFormat::badgeLabel).filter { it.isNotEmpty() },
        description = species.description,
        foundOn = CardFormat.foundOn(plant.foundAt, plant.placeName, zone, locale),
    )
}

/** Loads one owned plant's card. Call [load] with the plant id; the card updates live (e.g. when the sprite lands). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CardViewModel @Inject constructor(
    private val garden: GardenRepository,
    private val clock: Clock,
) : ViewModel() {

    private val plantId = MutableStateFlow<Long?>(null)

    val state: StateFlow<CardUiState> = plantId
        .filterNotNull()
        .flatMapLatest { id -> garden.observePlant(id) }
        .map { row -> row?.let { CardUiState.Loaded(it.toCardModel(clock.zone())) } ?: CardUiState.NotFound }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardUiState.Loading)

    fun load(id: Long) {
        plantId.value = id
    }
}
