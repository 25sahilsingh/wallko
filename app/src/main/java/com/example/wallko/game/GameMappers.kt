package com.example.wallko.game

import com.example.wallko.core.config.GameRules
import com.example.wallko.core.db.GrowthSpeed
import com.example.wallko.core.db.LedgerEntry
import com.example.wallko.core.db.LedgerKind
import com.example.wallko.core.db.OwnedPlant
import com.example.wallko.core.db.OwnedPlantWithSpecies
import com.example.wallko.core.db.PlantSpecies
import com.example.wallko.core.db.PlantState
import com.example.wallko.core.db.Wallet
import com.example.wallko.data.garden.PlantStateUpdate
import com.example.wallko.game.model.DayReport
import com.example.wallko.game.model.GardenEvent
import com.example.wallko.game.model.PlantSnapshot
import com.example.wallko.game.model.PlantStage
import com.example.wallko.game.model.WalletState
import java.time.Instant

/**
 * Pure Room entity <-> engine snapshot mapping. No Android, no I/O; unit-tested on the JVM.
 */
object GameMappers {

    fun PlantState.toStage(): PlantStage = PlantStage.valueOf(name)

    fun PlantStage.toState(): PlantState = PlantState.valueOf(name)

    /** Watered days needed for GROWN for a species' growth speed. */
    fun growthTargetFor(speed: GrowthSpeed): Int = when (speed) {
        GrowthSpeed.FAST -> GameRules.Growth.FAST_DAYS
        GrowthSpeed.MEDIUM -> GameRules.Growth.MEDIUM_DAYS
        GrowthSpeed.SLOW -> GameRules.Growth.SLOW_DAYS
    }

    fun toSnapshot(plant: OwnedPlant, species: PlantSpecies): PlantSnapshot = PlantSnapshot(
        id = plant.id,
        rarityOrdinal = species.rarity.ordinal,
        thirst = species.thirst,
        stage = plant.state.toStage(),
        daysUnwatered = plant.daysUnwatered,
        growthDays = plant.growthDays,
        growthTarget = growthTargetFor(species.growth),
        plantedAt = Instant.ofEpochMilli(plant.plantedAt),
        speciesIndex = plant.speciesIndex,
    )

    fun toSnapshot(row: OwnedPlantWithSpecies): PlantSnapshot = toSnapshot(row.plant, row.species)

    fun toWalletState(wallet: Wallet): WalletState = WalletState(
        drops = wallet.waterDrops,
        elixir = wallet.elixir,
        elixirProgressM = wallet.elixirProgressM,
        capacity = wallet.tankCapacity,
    )

    /** Writes the engine's wallet back onto the stored row, keeping its id. */
    fun applyWalletState(wallet: Wallet, state: WalletState, lastTickAt: Instant? = null): Wallet = wallet.copy(
        waterDrops = state.drops,
        elixir = state.elixir,
        elixirProgressM = state.elixirProgressM,
        tankCapacity = state.capacity,
        lastTickAt = lastTickAt?.toEpochMilli() ?: wallet.lastTickAt,
    )

    /** Plant id -> epoch millis of the latest day boundary on which that plant drank. */
    fun lastDrankAt(days: List<DayReport>): Map<Long, Long> {
        val out = HashMap<Long, Long>()
        for (day in days) for (e in day.events) {
            if (e is GardenEvent.Drank) out[e.plantId] = day.at.toEpochMilli()
        }
        return out
    }

    /**
     * State updates for every plant whose engine-owned fields changed (or that drank). Plants untouched by the
     * tick produce no update.
     */
    fun stateUpdates(
        before: List<OwnedPlant>,
        after: List<PlantSnapshot>,
        days: List<DayReport>,
    ): List<PlantStateUpdate> {
        val byId = before.associateBy { it.id }
        val drank = lastDrankAt(days)
        return after.mapNotNull { snap ->
            val old = byId[snap.id] ?: return@mapNotNull null
            val update = toStateUpdate(old, snap, drank[snap.id])
            val unchanged = update.state == old.state &&
                update.daysUnwatered == old.daysUnwatered &&
                update.growthDays == old.growthDays &&
                update.lastDrankAt == old.lastDrankAt
            if (unchanged) null else update
        }
    }

    /** [drankAt] overrides the stored lastDrankAt when the plant drank during this tick. */
    fun toStateUpdate(old: OwnedPlant, snap: PlantSnapshot, drankAt: Long? = null): PlantStateUpdate = PlantStateUpdate(
        id = old.id,
        state = snap.stage.toState(),
        daysUnwatered = snap.daysUnwatered,
        growthDays = snap.growthDays,
        lastDrankAt = drankAt ?: old.lastDrankAt,
    )

    /**
     * One aggregated DRINK ledger row per day on which anything drank. `drops` is negative (water left the
     * tank); the note lists the plants, e.g. "Rose 2, Fern 1".
     */
    fun drinkLedgerEntries(days: List<DayReport>, names: Map<Long, String>): List<LedgerEntry> = days.mapNotNull { day ->
        val drinks = day.events.filterIsInstance<GardenEvent.Drank>().filter { it.drops > 0 }
        val total = drinks.sumOf { it.drops }
        if (total <= 0) return@mapNotNull null
        LedgerEntry(
            at = day.at.toEpochMilli(),
            kind = LedgerKind.DRINK,
            drops = -total,
            note = drinks.joinToString(", ") { "${names[it.plantId] ?: "#${it.plantId}"} ${it.drops}" },
        )
    }

    /** Display name for a species: common name if present, else Latin. */
    fun displayName(species: PlantSpecies): String = species.common?.takeIf { it.isNotBlank() } ?: species.latin
}
