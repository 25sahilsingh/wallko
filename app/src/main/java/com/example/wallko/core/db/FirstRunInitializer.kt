package com.example.wallko.core.db

import androidx.room.withTransaction
import com.example.wallko.core.config.GameRules
import javax.inject.Inject
import javax.inject.Singleton

/** Creates the starter wallet (and its STARTER ledger entry) once. Idempotent. */
@Singleton
class FirstRunInitializer @Inject constructor(
    private val db: WallkoDatabase,
) {
    /** @return true if the starter state was created by this call. */
    suspend fun ensureInitialized(now: Long): Boolean = db.withTransaction {
        val walletDao = db.walletDao()
        if (walletDao.get() != null) return@withTransaction false
        walletDao.upsert(
            Wallet(
                waterDrops = GameRules.Start.STARTER_DROPS,
                elixir = 0,
                tankCapacity = GameRules.Start.TANK_CAPACITY,
                lastTickAt = now,
            ),
        )
        db.ledgerDao().insert(
            LedgerEntry(at = now, kind = LedgerKind.STARTER, drops = GameRules.Start.STARTER_DROPS),
        )
        true
    }
}
