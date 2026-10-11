package com.example.wallko.game

import com.example.wallko.core.config.GameRules
import com.example.wallko.game.model.DayReport
import com.example.wallko.game.model.DropCredit
import com.example.wallko.game.model.GardenEvent
import com.example.wallko.game.model.PlantSnapshot
import com.example.wallko.game.model.PlantStage
import com.example.wallko.game.model.ReviveResult
import com.example.wallko.game.model.TickResult
import com.example.wallko.game.model.WalletState
import java.time.Instant
import java.time.ZoneId

/**
 * Pure garden rules: time passing, drinking, wilting, growth, revival, tank capacity.
 * No Android, no Room, no clock — every input is a parameter.
 *
 * Design choices (see also docs/game-rules.md):
 * - **Day boundaries** are local midnights in the [ZoneId] given to [advance]. One tick per local calendar
 *   day crossed; DST days (23 h / 25 h) are still exactly one tick.
 * - **Capacity** counts the distinct species among every plant passed in, withered ones included (they stay in
 *   the collection), so capacity never shrinks when a plant withers, and withering + re-scanning the same
 *   species cannot farm extra capacity.
 * - **Overflow is lost:** drops beyond capacity are discarded by [addDrops] (reported as `lost`).
 * - **Drinking order:** rarest first, ties oldest `plantedAt` first, then lowest id. A plant whose thirst
 *   exceeds what is left goes unwatered, but later (commoner) plants with a smaller thirst may still drink
 *   from the remainder so water is never left unused.
 * - Plants planted after a boundary instant do not take part in that boundary's tick.
 */
object GameEngine {

    /** Tank capacity for a collection: one step per distinct species ever owned (withered included). */
    fun tankCapacity(garden: List<PlantSnapshot>): Int =
        GameRules.tankCapacityFor(garden.distinctBy { it.speciesIndex }.size)

    /** Adds [drops] (≥ 0) to the tank, clamped to capacity. Anything above capacity is lost. */
    fun addDrops(wallet: WalletState, drops: Int): DropCredit {
        val add = drops.coerceAtLeast(0)
        val room = (wallet.capacity - wallet.drops).coerceAtLeast(0)
        val credited = minOf(add, room)
        return DropCredit(wallet.copy(drops = wallet.drops + credited), credited, add - credited)
    }

    /**
     * Applies every local-midnight boundary crossed in (lastTickAt, now]. Same local day, or `now` before
     * `lastTickAt`, changes nothing. The returned `lastTickAt` is the last processed midnight, so calling again
     * with the same `now` is a no-op (idempotent).
     */
    fun advance(
        garden: List<PlantSnapshot>,
        wallet: WalletState,
        lastTickAt: Instant,
        now: Instant,
        zone: ZoneId,
    ): TickResult {
        val lastDate = lastTickAt.atZone(zone).toLocalDate()
        val nowDate = now.atZone(zone).toLocalDate()
        if (!nowDate.isAfter(lastDate)) return TickResult(garden, wallet, emptyList(), lastTickAt)

        var plants = garden
        var w = wallet
        var boundary = lastTickAt
        val days = ArrayList<DayReport>()
        var date = lastDate.plusDays(1)
        while (!date.isAfter(nowDate)) {
            boundary = date.atStartOfDay(zone).toInstant()
            val capacity = tankCapacity(plants)
            w = w.copy(capacity = capacity, drops = w.drops.coerceIn(0, capacity))
            val before = w.drops
            val (newPlants, dropsLeft, events) = tickDay(plants, w.drops, boundary)
            plants = newPlants
            w = w.copy(drops = dropsLeft)
            days += DayReport(date, boundary, capacity, before, dropsLeft, events)
            date = date.plusDays(1)
        }
        return TickResult(plants, w, days, boundary)
    }

    private data class DayOutcome(val plants: List<PlantSnapshot>, val drops: Int, val events: List<GardenEvent>)

    private fun tickDay(plants: List<PlantSnapshot>, startDrops: Int, at: Instant): DayOutcome {
        var drops = startDrops
        val events = ArrayList<GardenEvent>()
        val updated = HashMap<Long, PlantSnapshot>()
        val order = plants
            .filter { it.stage != PlantStage.WITHERED && !it.plantedAt.isAfter(at) }
            .sortedWith(
                compareByDescending<PlantSnapshot> { it.rarityOrdinal }
                    .thenBy { it.plantedAt }
                    .thenBy { it.id },
            )
        for (p in order) {
            val thirst = p.thirst.coerceAtLeast(0)
            if (drops >= thirst) {
                drops -= thirst
                events += GardenEvent.Drank(p.id, thirst)
                val growthDays = p.growthDays + 1
                val newStage = growthStage(growthDays, p.growthTarget)
                val oldGrowth = growthStage(p.growthDays, p.growthTarget)
                if (newStage != oldGrowth) events += GardenEvent.Grew(p.id, newStage)
                updated[p.id] = p.copy(stage = newStage, daysUnwatered = 0, growthDays = growthDays)
            } else {
                val days = p.daysUnwatered + 1
                val stage = when {
                    days >= GameRules.Health.WITHER_AFTER_DAYS -> PlantStage.WITHERED
                    days >= GameRules.Health.WILT_AFTER_DAYS -> PlantStage.WILTING
                    else -> p.stage
                }
                if (stage == PlantStage.WITHERED) events += GardenEvent.Withered(p.id)
                else if (stage == PlantStage.WILTING && p.stage != PlantStage.WILTING) events += GardenEvent.Wilted(p.id, days)
                updated[p.id] = p.copy(stage = stage, daysUnwatered = days)
            }
        }
        return DayOutcome(plants.map { updated[it.id] ?: it }, drops, events)
    }

    /** Healthy growth stage for a number of watered days. */
    fun growthStage(growthDays: Int, growthTarget: Int): PlantStage = when {
        growthDays >= growthTarget -> PlantStage.GROWN
        growthDays > GameRules.Growth.SEEDLING_MAX_GROWTH_DAYS -> PlantStage.GROWING
        else -> PlantStage.SEEDLING
    }

    /**
     * Spends [GameRules.Elixir.REVIVE_COST] elixir to bring a WITHERED plant back: GROWN if it had reached its
     * growth target, otherwise GROWING. growthDays are kept; daysUnwatered resets to 0.
     */
    fun revive(plant: PlantSnapshot, wallet: WalletState): ReviveResult {
        if (plant.stage != PlantStage.WITHERED) return ReviveResult.NotWithered
        val cost = GameRules.Elixir.REVIVE_COST
        if (wallet.elixir < cost) return ReviveResult.NotEnoughElixir(wallet.elixir, cost)
        val stage = if (plant.growthDays >= plant.growthTarget) PlantStage.GROWN else PlantStage.GROWING
        return ReviveResult.Revived(
            plant = plant.copy(stage = stage, daysUnwatered = 0),
            wallet = wallet.copy(elixir = wallet.elixir - cost),
            elixirSpent = cost,
        )
    }
}
