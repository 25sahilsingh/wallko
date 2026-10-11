package com.example.wallko.walk.model

import java.time.Duration

/** One raw location fix. [speedMps] is the fix's reported speed if the provider had one. */
data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val timeMillis: Long,
    val speedMps: Double? = null,
)

/** Names mirror `core.db.LedgerKind`, so mapping is `LedgerKind.valueOf(kind.name)`. */
enum class RewardKind { WALK_DISTANCE, NEW_CELL, DAWN_BONUS }

/** One ledger line of a walk's drops. */
data class RewardLine(val kind: RewardKind, val drops: Int)

/**
 * Outcome of [com.example.wallko.walk.WalkRewards.summarize].
 *
 * Invalid walks ([valid] = false) keep their stats but earn nothing: all drops are 0, [elixirEarned] is 0,
 * [elixirProgressM] equals the prior progress, and [cellsToMarkExplored] is empty so the player can still
 * earn those cells' bonus on a real walk later.
 */
data class WalkSummary(
    val valid: Boolean,
    val duration: Duration,
    val distanceM: Double,
    val steps: Int,
    val enteredCells: Set<Long>,
    val newCells: Set<Long>,
    val newCellDistanceM: Double,
    val dawn: Boolean,
    /** Non-zero lines only, in order WALK_DISTANCE, NEW_CELL, DAWN_BONUS. Sum = [totalDrops]. */
    val breakdown: List<RewardLine>,
    val totalDrops: Int,
    val elixirEarned: Int,
    /** New carried-over metres toward the next elixir (store on the wallet). */
    val elixirProgressM: Double,
    val cellsToMarkExplored: Set<Long>,
) {
    fun dropsFor(kind: RewardKind): Int = breakdown.filter { it.kind == kind }.sumOf { it.drops }
}
