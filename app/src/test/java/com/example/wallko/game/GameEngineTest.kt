package com.example.wallko.game

import com.example.wallko.core.config.GameRules
import com.example.wallko.game.model.GardenEvent
import com.example.wallko.game.model.PlantSnapshot
import com.example.wallko.game.model.PlantStage
import com.example.wallko.game.model.ReviveResult
import com.example.wallko.game.model.WalletState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class GameEngineTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val day0: Instant = LocalDateTime.of(2026, 10, 1, 12, 0).toInstant(ZoneOffset.UTC)
    private val planted: Instant = day0.minusSeconds(3600)

    private fun plant(
        id: Long,
        rarity: Int = 0,
        thirst: Int = 2,
        stage: PlantStage = PlantStage.SEEDLING,
        daysUnwatered: Int = 0,
        growthDays: Int = 0,
        target: Int = GameRules.Growth.FAST_DAYS,
        plantedAt: Instant = planted,
        speciesIndex: Int = id.toInt(), // distinct species per plant unless a test says otherwise
    ) = PlantSnapshot(id, rarity, thirst, stage, daysUnwatered, growthDays, target, plantedAt, speciesIndex)

    private fun wallet(drops: Int, elixir: Int = 0, capacity: Int = GameRules.Start.TANK_CAPACITY) =
        WalletState(drops, elixir, 0.0, capacity)

    private fun daysLater(n: Long): Instant = day0.plusSeconds(n * 86_400)

    private fun advanceDays(garden: List<PlantSnapshot>, w: WalletState, n: Long) =
        GameEngine.advance(garden, w, day0, daysLater(n), utc)

    // ---- wilt / wither ----

    @Test fun dryTankWiltsOnDay3() {
        val p = plant(1)
        val r2 = advanceDays(listOf(p), wallet(0), 2)
        assertEquals(PlantStage.SEEDLING, r2.garden[0].stage)
        assertEquals(2, r2.garden[0].daysUnwatered)

        val r3 = advanceDays(listOf(p), wallet(0), GameRules.Health.WILT_AFTER_DAYS.toLong())
        assertEquals(PlantStage.WILTING, r3.garden[0].stage)
        assertEquals(1, r3.events.count { it is GardenEvent.Wilted })
    }

    @Test fun dryTankWithersOnDay7() {
        val p = plant(1)
        val r6 = advanceDays(listOf(p), wallet(0), 6)
        assertEquals(PlantStage.WILTING, r6.garden[0].stage)
        val r7 = advanceDays(listOf(p), wallet(0), GameRules.Health.WITHER_AFTER_DAYS.toLong())
        assertEquals(PlantStage.WITHERED, r7.garden[0].stage)
        assertEquals(listOf(GardenEvent.Withered(1)), r7.days.last().events)
        // Wilted emitted once only, on day 3.
        assertEquals(1, r7.events.count { it is GardenEvent.Wilted })
    }

    @Test fun witheredPlantNoLongerDrinks() {
        val p = plant(1, stage = PlantStage.WITHERED, daysUnwatered = 7)
        val r = advanceDays(listOf(p), wallet(20), 3)
        assertEquals(20, r.wallet.drops)
        assertEquals(p, r.garden[0])
        assertTrue(r.events.isEmpty())
    }

    @Test fun singleDrinkResetsCounterAndClearsWilting() {
        val p = plant(1, thirst = 3, stage = PlantStage.WILTING, daysUnwatered = 6, growthDays = 2)
        val r = advanceDays(listOf(p), wallet(3), 1)
        val after = r.garden[0]
        assertEquals(0, after.daysUnwatered)
        assertEquals(3, after.growthDays)
        assertEquals(PlantStage.GROWN, after.stage)
        assertEquals(0, r.wallet.drops)
        assertTrue(GardenEvent.Drank(1, 3) in r.events)

        // Next day without water: counter starts again from 1, not 7.
        val r2 = GameEngine.advance(r.garden, r.wallet, r.lastTickAt, daysLater(2), utc)
        assertEquals(1, r2.garden[0].daysUnwatered)
        assertEquals(PlantStage.GROWN, r2.garden[0].stage)
    }

    // ---- allocation ----

    @Test fun rarestFirstWithNearlyEmptyTank() {
        val common = plant(1, rarity = 0, thirst = 3)
        val legendary = plant(2, rarity = 4, thirst = 3, plantedAt = planted.plusSeconds(10))
        val epic = plant(3, rarity = 3, thirst = 3)
        val r = advanceDays(listOf(common, legendary, epic), wallet(4), 1)
        val drank = r.events.filterIsInstance<GardenEvent.Drank>().map { it.plantId }
        assertEquals(listOf(2L), drank)
        assertEquals(1, r.wallet.drops)
        assertEquals(1, r.garden.first { it.id == 3L }.daysUnwatered)
        assertEquals(1, r.garden.first { it.id == 1L }.daysUnwatered)
    }

    @Test fun tiesGoToOldestPlanted() {
        val young = plant(1, rarity = 2, thirst = 2, plantedAt = planted)
        val old = plant(2, rarity = 2, thirst = 2, plantedAt = planted.minusSeconds(86_400))
        val r = advanceDays(listOf(young, old), wallet(2), 1)
        assertEquals(listOf(GardenEvent.Drank(2, 2)), r.events.filterIsInstance<GardenEvent.Drank>())
    }

    @Test fun leftoverWaterServesSmallerThirstPlant() {
        val rare = plant(1, rarity = 2, thirst = 5)
        val common = plant(2, rarity = 0, thirst = 1)
        val r = advanceDays(listOf(rare, common), wallet(2), 1)
        assertEquals(listOf(GardenEvent.Drank(2, 1)), r.events.filterIsInstance<GardenEvent.Drank>())
        assertEquals(1, r.wallet.drops)
    }

    @Test fun plantPlantedAfterBoundaryDoesNotDrinkThatBoundary() {
        val late = plant(1, plantedAt = daysLater(1).plusSeconds(60)) // Oct 2 12:01, after the Oct 2 midnight
        val r = GameEngine.advance(listOf(late), wallet(50), day0, daysLater(2), utc)
        // Midnights crossed: Oct 2 and Oct 3. Plant planted Oct 2 12:01 -> drinks only on Oct 3.
        assertEquals(1, r.events.count { it is GardenEvent.Drank })
        assertEquals(1, r.garden[0].growthDays)
    }

    // ---- catch-up / time ----

    @Test fun catchUpOverTenDays() {
        val thirsty = plant(1, rarity = 1, thirst = 5, target = GameRules.Growth.SLOW_DAYS)
        // Capacity for 1 plant = 60; start with 22 drops: 4 drinks of 5, then dry.
        val r = advanceDays(listOf(thirsty), wallet(22), 10)
        assertEquals(10, r.days.size)
        assertEquals(4, r.events.count { it is GardenEvent.Drank })
        assertEquals(2, r.wallet.drops)
        assertEquals(GameRules.tankCapacityFor(1), r.wallet.capacity)
        val p = r.garden[0]
        assertEquals(4, p.growthDays)
        assertEquals(6, p.daysUnwatered)
        assertEquals(PlantStage.WILTING, p.stage)
        assertEquals(LocalDate.of(2026, 10, 11).atStartOfDay(utc).toInstant(), r.lastTickAt)
        assertEquals(LocalDate.of(2026, 10, 2), r.days.first().date)
    }

    @Test fun sameDayCallDoesNothing() {
        val g = listOf(plant(1))
        val r = GameEngine.advance(g, wallet(10), day0, day0.plusSeconds(11 * 3600), utc) // 23:00 same day
        assertFalse(r.changed)
        assertEquals(g, r.garden)
        assertEquals(day0, r.lastTickAt)
    }

    @Test fun clockGoingBackwardsDoesNothing() {
        val r = GameEngine.advance(listOf(plant(1)), wallet(10), day0, day0.minusSeconds(3 * 86_400), utc)
        assertFalse(r.changed)
        assertEquals(day0, r.lastTickAt)
    }

    @Test fun idempotentForSameNow() {
        val g = listOf(plant(1), plant(2, rarity = 3))
        val now = daysLater(3).plusSeconds(3600)
        val first = GameEngine.advance(g, wallet(5), day0, now, utc)
        val second = GameEngine.advance(first.garden, first.wallet, first.lastTickAt, now, utc)
        assertFalse(second.changed)
        assertEquals(first.garden, second.garden)
        assertEquals(first.wallet, second.wallet)
        assertEquals(first.lastTickAt, second.lastTickAt)
    }

    @Test fun splitCallsEqualOneCall() {
        val g = listOf(plant(1, thirst = 3), plant(2, rarity = 2, thirst = 4))
        val now = daysLater(5)
        val once = GameEngine.advance(g, wallet(20), day0, now, utc)
        val a = GameEngine.advance(g, wallet(20), day0, daysLater(2).plusSeconds(100), utc)
        val b = GameEngine.advance(a.garden, a.wallet, a.lastTickAt, now, utc)
        assertEquals(once.garden, b.garden)
        assertEquals(once.wallet, b.wallet)
    }

    @Test fun dstShortAndLongDaysAreOneTickEach() {
        val berlin = ZoneId.of("Europe/Berlin")
        // DST ends 2026-10-25 (25 h day). Last tick at 23:30 on Oct 24, now 00:30 on Oct 26.
        val last = LocalDateTime.of(2026, 10, 24, 23, 30).atZone(berlin).toInstant()
        val now = LocalDateTime.of(2026, 10, 26, 0, 30).atZone(berlin).toInstant()
        val r = GameEngine.advance(listOf(plant(1)), wallet(50), last, now, berlin)
        assertEquals(listOf(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26)), r.days.map { it.date })
        assertEquals(LocalDate.of(2026, 10, 26).atStartOfDay(berlin).toInstant(), r.lastTickAt)

        // DST starts 2026-03-29 (23 h day): 00:30 Mar 29 -> 23:30 Mar 29 is the same day, no tick.
        val s = LocalDateTime.of(2026, 3, 29, 0, 30).atZone(berlin).toInstant()
        val e = LocalDateTime.of(2026, 3, 29, 23, 30).atZone(berlin).toInstant()
        assertFalse(GameEngine.advance(listOf(plant(1)), wallet(50), s, e, berlin).changed)
    }

    @Test fun dayBoundaryFollowsLocalZoneNotUtc() {
        val kolkata = ZoneId.of("Asia/Kolkata") // UTC+5:30
        // 18:00Z = 23:30 IST; 19:00Z = 00:30 IST next day -> one tick, though the UTC date did not change.
        val last = Instant.parse("2026-10-01T18:00:00Z")
        val now = Instant.parse("2026-10-01T19:00:00Z")
        val r = GameEngine.advance(listOf(plant(1)), wallet(50), last, now, kolkata)
        assertEquals(1, r.days.size)
        assertEquals(Instant.parse("2026-10-01T18:30:00Z"), r.lastTickAt)
        // In UTC the same pair is the same day.
        assertFalse(GameEngine.advance(listOf(plant(1)), wallet(50), last, now, utc).changed)
    }

    // ---- growth ----

    @Test fun growthOnlyOnDrinkDays() {
        // Thirst 5, 10 drops: drinks on days 1 and 2 only.
        val p = plant(1, thirst = 5, target = GameRules.Growth.FAST_DAYS)
        val r = advanceDays(listOf(p), wallet(10), 4) // days 3-4 dry: 2 unwatered, not yet wilting
        assertEquals(2, r.garden[0].growthDays)
        assertEquals(PlantStage.GROWING, r.garden[0].stage)

        // Refill and one more day -> 3rd watered day -> GROWN.
        val refilled = GameEngine.addDrops(r.wallet, 5).wallet
        val r2 = GameEngine.advance(r.garden, refilled, r.lastTickAt, daysLater(5), utc)
        assertEquals(3, r2.garden[0].growthDays)
        assertEquals(PlantStage.GROWN, r2.garden[0].stage)
        assertTrue(GardenEvent.Grew(1, PlantStage.GROWN) in r2.events)
    }

    @Test fun seedlingThenGrowingThenGrown() {
        val p = plant(1, thirst = 1, target = GameRules.Growth.FAST_DAYS)
        val stages = (1L..3L).map { advanceDays(listOf(p), wallet(50), it).garden[0].stage }
        assertEquals(listOf(PlantStage.SEEDLING, PlantStage.GROWING, PlantStage.GROWN), stages)
        val events = advanceDays(listOf(p), wallet(50), 3).events.filterIsInstance<GardenEvent.Grew>()
        assertEquals(listOf(GardenEvent.Grew(1, PlantStage.GROWING), GardenEvent.Grew(1, PlantStage.GROWN)), events)
    }

    // ---- capacity / overflow ----

    @Test fun capacityAndOverflow() {
        assertEquals(GameRules.Start.TANK_CAPACITY, GameEngine.tankCapacity(emptyList()))
        val many = (1L..100L).map { plant(it) }
        assertEquals(GameRules.Drinking.MAX_TANK_CAPACITY, GameEngine.tankCapacity(many))
        val c = GameEngine.addDrops(wallet(45, capacity = 50), 12)
        assertEquals(50, c.wallet.drops)
        assertEquals(5, c.credited)
        assertEquals(7, c.lost)
        assertEquals(0, GameEngine.addDrops(wallet(10), -3).credited)
    }

    @Test fun tickUpdatesCapacityFromGarden() {
        val g = (1L..3L).map { plant(it, thirst = 1) }
        val r = advanceDays(g, wallet(10, capacity = 50), 1)
        assertEquals(GameRules.tankCapacityFor(3), r.wallet.capacity)
        assertEquals(GameRules.tankCapacityFor(3), r.days[0].capacity)
    }

    @Test fun capacityCountsDistinctSpeciesNotRows() {
        // Species 7 withered and was re-scanned: two rows, one species.
        val withered = plant(1, stage = PlantStage.WITHERED, daysUnwatered = 7, speciesIndex = 7)
        val replanted = plant(2, speciesIndex = 7)
        val other = plant(3, speciesIndex = 9)
        assertEquals(GameRules.tankCapacityFor(1), GameEngine.tankCapacity(listOf(withered, replanted)))
        assertEquals(60, GameEngine.tankCapacity(listOf(withered, replanted)))
        assertEquals(70, GameEngine.tankCapacity(listOf(withered, replanted, other)))
        // Many rows of few species never reach the cap.
        val farmed = (1L..100L).map { plant(it, speciesIndex = (it % 3).toInt()) }
        assertEquals(80, GameEngine.tankCapacity(farmed))
    }

    @Test fun witheredSpeciesStillCountsTowardCapacity() {
        val gone = plant(1, stage = PlantStage.WITHERED, daysUnwatered = 7, speciesIndex = 4)
        assertEquals(60, GameEngine.tankCapacity(listOf(gone)))
        val r = advanceDays(listOf(gone), wallet(55, capacity = 60), 1)
        assertEquals(60, r.wallet.capacity)
        assertEquals(55, r.wallet.drops) // nothing drinks, nothing is clamped away
    }

    @Test fun tickCapacityIgnoresDuplicateSpeciesRows() {
        val g = listOf(
            plant(1, thirst = 1, stage = PlantStage.WITHERED, daysUnwatered = 7, speciesIndex = 5),
            plant(2, thirst = 1, speciesIndex = 5),
        )
        val r = advanceDays(g, wallet(10, capacity = 50), 1)
        assertEquals(GameRules.tankCapacityFor(1), r.wallet.capacity)
        assertEquals(GameRules.tankCapacityFor(1), r.days[0].capacity)
    }

    // ---- revive ----

    @Test fun reviveWitheredToGrowing() {
        val p = plant(1, stage = PlantStage.WITHERED, daysUnwatered = 7, growthDays = 1)
        val r = GameEngine.revive(p, wallet(0, elixir = 2)) as ReviveResult.Revived
        assertEquals(PlantStage.GROWING, r.plant.stage)
        assertEquals(0, r.plant.daysUnwatered)
        assertEquals(1, r.plant.growthDays)
        assertEquals(2 - GameRules.Elixir.REVIVE_COST, r.wallet.elixir)
    }

    @Test fun reviveKeepsGrown() {
        val p = plant(1, stage = PlantStage.WITHERED, daysUnwatered = 9, growthDays = 5, target = 3)
        val r = GameEngine.revive(p, wallet(0, elixir = 1)) as ReviveResult.Revived
        assertEquals(PlantStage.GROWN, r.plant.stage)
        assertEquals(5, r.plant.growthDays)
    }

    @Test fun reviveFailures() {
        val withered = plant(1, stage = PlantStage.WITHERED, daysUnwatered = 7)
        assertEquals(
            ReviveResult.NotEnoughElixir(0, GameRules.Elixir.REVIVE_COST),
            GameEngine.revive(withered, wallet(0, elixir = 0)),
        )
        assertEquals(ReviveResult.NotWithered, GameEngine.revive(plant(2, stage = PlantStage.WILTING), wallet(0, elixir = 5)))
    }

    @Test fun revivedPlantDrinksAgain() {
        val p = plant(1, stage = PlantStage.WITHERED, daysUnwatered = 7, growthDays = 2)
        val rv = GameEngine.revive(p, wallet(10, elixir = 1)) as ReviveResult.Revived
        val r = advanceDays(listOf(rv.plant), rv.wallet, 1)
        assertEquals(1, r.events.count { it is GardenEvent.Drank })
    }
}
