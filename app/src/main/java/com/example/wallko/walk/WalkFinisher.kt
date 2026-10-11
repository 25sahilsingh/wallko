package com.example.wallko.walk

import androidx.room.withTransaction
import com.example.wallko.core.db.LedgerKind
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.core.time.Clock
import com.example.wallko.data.walks.WalkRepository
import com.example.wallko.data.walks.WalkTotals
import com.example.wallko.data.wallet.WalletRepository
import com.example.wallko.walk.model.RewardKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settles a finished walk in one Room transaction: summarize → one ledger credit per reward line (elixir rides on
 * the WALK_DISTANCE line) → elixir progress → explored cells → walk row. Idempotent: a walk whose row already has
 * `endedAt` is not paid twice.
 */
@Singleton
class WalkFinisher @Inject constructor(
    private val db: WallkoDatabase,
    private val walkRepository: WalkRepository,
    private val walletRepository: WalletRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    suspend fun finish(
        walkId: Long,
        acc: WalkAccumulator,
        startedAt: Instant,
        endedAt: Instant,
        steps: Int,
    ): WalkResult = mutex.withLock {
        walletRepository.ensureInitialized()
        val endMillis = endedAt.toEpochMilli()
        db.withTransaction {
            val wallet = walletRepository.getWallet()
            val summary = WalkRewards.summarize(acc, startedAt, endedAt, clock.zone(), steps, wallet.elixirProgressM)
            val walk = walkRepository.getWalk(walkId)
            if (walk == null || walk.endedAt != null) {
                return@withTransaction WalkResult(walkId, summary, 0, 0, endMillis, error = "already settled")
            }

            val note = "walk #$walkId"
            var credited = 0
            var overflow = 0
            var elixirLeft = summary.elixirEarned
            for (line in summary.breakdown) {
                val elixir = if (line.kind == RewardKind.WALK_DISTANCE) elixirLeft else 0
                elixirLeft -= elixir
                val r = walletRepository.credit(LedgerKind.valueOf(line.kind.name), line.drops, elixir, note, endMillis)
                credited += r.dropsCredited
                overflow += r.dropsOverflow
            }
            if (elixirLeft > 0) {
                walletRepository.credit(LedgerKind.WALK_DISTANCE, 0, elixirLeft, note, endMillis)
            }
            if (summary.valid) walletRepository.setElixirProgress(summary.elixirProgressM)
            walkRepository.markExplored(summary.cellsToMarkExplored, endMillis)
            walkRepository.finishWalk(
                walkId,
                WalkTotals(
                    distanceM = summary.distanceM,
                    // Only a valid (paid) walk's steps replace passive steps; an invalid walk pays nothing, so its
                    // steps must stay creditable as passive steps (StepSync subtracts Walk.steps).
                    steps = if (summary.valid) summary.steps else 0,
                    newCells = if (summary.valid) summary.newCells.size else 0,
                    dropsEarned = summary.totalDrops,
                    elixirEarned = summary.elixirEarned,
                    dawn = summary.dawn,
                ),
                endMillis,
            )
            WalkResult(walkId, summary, credited, overflow, endMillis)
        }
    }
}
