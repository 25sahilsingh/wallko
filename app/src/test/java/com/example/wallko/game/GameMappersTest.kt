package com.example.wallko.game

import com.example.wallko.core.config.GameRules
import com.example.wallko.core.db.GrowthSpeed
import com.example.wallko.core.db.LedgerKind
import com.example.wallko.core.db.OwnedPlant
import com.example.wallko.core.db.PlantSpecies
import com.example.wallko.core.db.PlantState
import com.example.wallko.core.db.Rarity
import com.example.wallko.core.db.Wallet
import com.example.wallko.game.GameMappers.toStage
import com.example.wallko.game.GameMappers.toState
import com.example.wallko.game.model.DayReport
import com.example.wallko.game.model.GardenEvent
import com.example.wallko.game.model.PlantStage
import com.example.wallko.game.model.WalletState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GameMappersTest {

    private fun species(rarity: Rarity = Rarity.EPIC, growth: GrowthSpeed = GrowthSpeed.MEDIUM, common: String? = "Rose") =
        PlantSpecies(
            index = 7, latin = "Rosa canina", common = common, family = null, type = null, description = null,
            observations = 1, rarity = rarity, thirst = 3, sun = 2, growth = growth,
            bloom = emptyList(), badges = emptyList(), sources = emptyMap(), dbVersion = 1,
        )

    private fun plant(id: Long = 5, state: PlantState = PlantState.GROWING, lastDrankAt: Long? = 100L) = OwnedPlant(
        id = id, speciesIndex = 7, foundAt = 1, foundLat = null, foundLng = null, placeName = null,
        photoPath = "p.jpg", styleId = "s", plantedAt = 1_000L, lastDrankAt = lastDrankAt, state = state,
        daysUnwatered = 2, growthDays = 4, gardenX = 0, gardenY = 0,
    )

    @Test
    fun stageAndStateEnumsMirrorEachOther() {
        PlantState.entries.forEach { assertEquals(it, it.toStage().toState()) }
        PlantStage.entries.forEach { assertEquals(it, it.toState().toStage()) }
    }

    @Test
    fun growthTargetComesFromGameRules() {
        assertEquals(GameRules.Growth.FAST_DAYS, GameMappers.growthTargetFor(GrowthSpeed.FAST))
        assertEquals(GameRules.Growth.MEDIUM_DAYS, GameMappers.growthTargetFor(GrowthSpeed.MEDIUM))
        assertEquals(GameRules.Growth.SLOW_DAYS, GameMappers.growthTargetFor(GrowthSpeed.SLOW))
    }

    @Test
    fun plantToSnapshotCopiesEngineFields() {
        val snap = GameMappers.toSnapshot(plant(), species())
        assertEquals(5L, snap.id)
        assertEquals(Rarity.EPIC.ordinal, snap.rarityOrdinal)
        assertEquals(3, snap.thirst)
        assertEquals(PlantStage.GROWING, snap.stage)
        assertEquals(2, snap.daysUnwatered)
        assertEquals(4, snap.growthDays)
        assertEquals(GameRules.Growth.MEDIUM_DAYS, snap.growthTarget)
        assertEquals(Instant.ofEpochMilli(1_000L), snap.plantedAt)
        assertEquals(7, snap.speciesIndex)
    }

    @Test
    fun walletRoundTripKeepsIdAndOptionalTick() {
        val w = Wallet(waterDrops = 12, elixir = 2, tankCapacity = 60, elixirProgressM = 40.0, lastTickAt = 5L)
        val state = GameMappers.toWalletState(w)
        assertEquals(WalletState(12, 2, 40.0, 60), state)
        val back = GameMappers.applyWalletState(w, state.copy(drops = 3, capacity = 70), Instant.ofEpochMilli(99L))
        assertEquals(w.copy(waterDrops = 3, tankCapacity = 70, lastTickAt = 99L), back)
        assertEquals(5L, GameMappers.applyWalletState(w, state).lastTickAt)
    }

    @Test
    fun stateUpdatesOnlyForChangedPlantsAndStampLastDrink() {
        val a = plant(id = 1)
        val b = plant(id = 2)
        val dayAt = Instant.ofEpochMilli(86_400_000L)
        val days = listOf(DayReport(LocalDate.of(1970, 1, 2), dayAt, 70, 10, 7, listOf(GardenEvent.Drank(1, 3))))
        val after = listOf(
            GameMappers.toSnapshot(a, species()).copy(daysUnwatered = 0, growthDays = 5),
            GameMappers.toSnapshot(b, species()),
        )
        val updates = GameMappers.stateUpdates(listOf(a, b), after, days)
        assertEquals(1, updates.size)
        val u = updates.single()
        assertEquals(1L, u.id)
        assertEquals(PlantState.GROWING, u.state)
        assertEquals(0, u.daysUnwatered)
        assertEquals(5, u.growthDays)
        assertEquals(dayAt.toEpochMilli(), u.lastDrankAt)
    }

    @Test
    fun toStateUpdateKeepsOldLastDrankWhenNoDrink() {
        val p = plant(lastDrankAt = 42L)
        val snap = GameMappers.toSnapshot(p, species()).copy(stage = PlantStage.WILTING, daysUnwatered = 3)
        val u = GameMappers.toStateUpdate(p, snap)
        assertEquals(PlantState.WILTING, u.state)
        assertEquals(42L, u.lastDrankAt)
    }

    @Test
    fun drinkLedgerIsOneNegativeEntryPerDayWithDrinks() {
        val d1 = DayReport(
            LocalDate.of(2026, 1, 2), Instant.ofEpochMilli(1_000L), 70, 10, 5,
            listOf(GardenEvent.Drank(1, 3), GardenEvent.Drank(2, 2), GardenEvent.Grew(1, PlantStage.GROWING)),
        )
        val d2 = DayReport(LocalDate.of(2026, 1, 3), Instant.ofEpochMilli(2_000L), 70, 0, 0, listOf(GardenEvent.Wilted(1, 3)))
        val entries = GameMappers.drinkLedgerEntries(listOf(d1, d2), mapOf(1L to "Rose"))
        assertEquals(1, entries.size)
        val e = entries.single()
        assertEquals(LedgerKind.DRINK, e.kind)
        assertEquals(-5, e.drops)
        assertEquals(1_000L, e.at)
        assertEquals("Rose 3, #2 2", e.note)
    }

    @Test
    fun displayNamePrefersCommonName() {
        assertEquals("Rose", GameMappers.displayName(species()))
        assertEquals("Rosa canina", GameMappers.displayName(species(common = " ")))
        assertTrue(GameMappers.drinkLedgerEntries(emptyList(), emptyMap()).isEmpty())
        assertNull(GameMappers.lastDrankAt(emptyList())[1L])
    }
}
