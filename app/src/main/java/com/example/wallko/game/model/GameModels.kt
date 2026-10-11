package com.example.wallko.game.model

import java.time.Instant
import java.time.LocalDate

/**
 * Plain snapshots the pure [com.example.wallko.game.GameEngine] works on. Repositories map Room entities
 * to and from these. Enum names mirror `core.db.PlantState` exactly, so mapping is
 * `PlantState.valueOf(stage.name)` / `PlantStage.valueOf(state.name)`.
 */
enum class PlantStage { SEEDLING, GROWING, GROWN, WILTING, WITHERED }

/**
 * One owned plant as the engine sees it.
 *
 * @param rarityOrdinal `Rarity.ordinal` (higher = rarer); used for rarest-first drinking.
 * @param thirst drops drunk per day.
 * @param growthTarget watered days needed for GROWN (GameRules.Growth.* for the species' speed).
 * @param speciesIndex catalogue species; tank capacity counts distinct species, not plants.
 */
data class PlantSnapshot(
    val id: Long,
    val rarityOrdinal: Int,
    val thirst: Int,
    val stage: PlantStage,
    val daysUnwatered: Int,
    val growthDays: Int,
    val growthTarget: Int,
    val plantedAt: Instant,
    val speciesIndex: Int,
)

/** The player's water and elixir. [capacity] is the current tank capacity in drops. */
data class WalletState(
    val drops: Int,
    val elixir: Int,
    val elixirProgressM: Double,
    val capacity: Int,
)

/** Something that happened to one plant at a daily tick. */
sealed interface GardenEvent {
    val plantId: Long

    /** The plant drank [drops] from the tank (one ledger DRINK entry per event, drops negative there). */
    data class Drank(override val plantId: Long, val drops: Int) : GardenEvent

    /** Plant just crossed into WILTING (emitted once, on the transition day). */
    data class Wilted(override val plantId: Long, val daysUnwatered: Int) : GardenEvent

    /** Plant just became WITHERED (emitted once). */
    data class Withered(override val plantId: Long) : GardenEvent

    /** Plant moved up a growth stage (to GROWING or GROWN). */
    data class Grew(override val plantId: Long, val to: PlantStage) : GardenEvent
}

/** Everything that happened at one local-midnight boundary. [at] is that boundary instant (use for ledger rows). */
data class DayReport(
    val date: LocalDate,
    val at: Instant,
    val capacity: Int,
    val dropsBefore: Int,
    val dropsAfter: Int,
    val events: List<GardenEvent>,
)

/**
 * Result of [com.example.wallko.game.GameEngine.advance]. Persist [garden], [wallet] and [lastTickAt]
 * atomically together with ledger rows for [days].
 */
data class TickResult(
    val garden: List<PlantSnapshot>,
    val wallet: WalletState,
    val days: List<DayReport>,
    val lastTickAt: Instant,
) {
    val events: List<GardenEvent> get() = days.flatMap { it.events }
    val dropsDrunk: Int get() = days.sumOf { d -> d.events.sumOf { if (it is GardenEvent.Drank) it.drops else 0 } }
    val changed: Boolean get() = days.isNotEmpty()
}

/** Outcome of [com.example.wallko.game.GameEngine.revive]. */
sealed interface ReviveResult {
    data class Revived(val plant: PlantSnapshot, val wallet: WalletState, val elixirSpent: Int) : ReviveResult
    data object NotWithered : ReviveResult
    data class NotEnoughElixir(val have: Int, val need: Int) : ReviveResult
}

/** Outcome of [com.example.wallko.game.GameEngine.addDrops]. [lost] drops did not fit in the tank. */
data class DropCredit(val wallet: WalletState, val credited: Int, val lost: Int)
