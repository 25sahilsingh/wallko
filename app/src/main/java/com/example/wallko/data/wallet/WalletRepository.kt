package com.example.wallko.data.wallet

import androidx.room.withTransaction
import com.example.wallko.core.db.FirstRunInitializer
import com.example.wallko.core.db.LedgerEntry
import com.example.wallko.core.db.LedgerKind
import com.example.wallko.core.db.Wallet
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.core.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** What a [WalletRepository.credit] call actually did. */
data class CreditResult(val wallet: Wallet, val dropsCredited: Int, val dropsOverflow: Int)

@Singleton
class WalletRepository @Inject constructor(
    private val db: WallkoDatabase,
    private val firstRun: FirstRunInitializer,
    private val clock: Clock,
) {
    private val walletDao get() = db.walletDao()
    private val ledgerDao get() = db.ledgerDao()

    private val initMutex = Mutex()
    @Volatile private var initialized = false

    /** Creates the starter wallet once. Called lazily by every read/write here; safe to call directly. */
    suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (!initialized) {
                firstRun.ensureInitialized(clock.now().toEpochMilli())
                initialized = true
            }
        }
    }

    fun observeWallet(): Flow<Wallet> = flow {
        ensureInitialized()
        emitAll(walletDao.observe().filterNotNull())
    }

    suspend fun getWallet(): Wallet {
        ensureInitialized()
        return checkNotNull(walletDao.get()) { "wallet missing after init" }
    }

    fun observeRecentLedger(limit: Int = 50): Flow<List<LedgerEntry>> = ledgerDao.observeRecent(limit)

    /**
     * Adds drops (clamped so the tank never exceeds capacity) and elixir, writing one ledger entry with the
     * drops actually credited. Overflow is reported, not stored.
     */
    suspend fun credit(
        kind: LedgerKind,
        drops: Int,
        elixir: Int = 0,
        note: String? = null,
        at: Long = clock.now().toEpochMilli(),
    ): CreditResult {
        require(drops >= 0 && elixir >= 0) { "credit amounts must be non-negative" }
        ensureInitialized()
        return db.withTransaction {
            val w = checkNotNull(walletDao.get())
            val room = (w.tankCapacity - w.waterDrops).coerceAtLeast(0)
            val credited = drops.coerceAtMost(room)
            val updated = w.copy(waterDrops = w.waterDrops + credited, elixir = w.elixir + elixir)
            walletDao.upsert(updated)
            ledgerDao.insert(LedgerEntry(at = at, kind = kind, drops = credited, elixir = elixir, note = note))
            CreditResult(updated, credited, drops - credited)
        }
    }

    /** Spends elixir; returns false (and writes nothing) if the balance is too low. */
    suspend fun debitElixir(
        amount: Int,
        kind: LedgerKind = LedgerKind.REVIVE,
        note: String? = null,
        at: Long = clock.now().toEpochMilli(),
    ): Boolean {
        require(amount > 0)
        ensureInitialized()
        return db.withTransaction {
            val w = checkNotNull(walletDao.get())
            if (w.elixir < amount) return@withTransaction false
            walletDao.upsert(w.copy(elixir = w.elixir - amount))
            ledgerDao.insert(LedgerEntry(at = at, kind = kind, drops = 0, elixir = -amount, note = note))
            true
        }
    }

    /** Writes the engine's computed wallet and its ledger entries atomically. No clamping is applied here. */
    suspend fun applyTick(newWallet: Wallet, ledgerEntries: List<LedgerEntry>) {
        ensureInitialized()
        db.withTransaction {
            walletDao.upsert(newWallet.copy(id = Wallet.SINGLETON_ID))
            if (ledgerEntries.isNotEmpty()) ledgerDao.insertAll(ledgerEntries)
        }
    }

    suspend fun setElixirProgress(meters: Double) {
        ensureInitialized()
        db.withTransaction {
            val w = checkNotNull(walletDao.get())
            walletDao.upsert(w.copy(elixirProgressM = meters.coerceAtLeast(0.0)))
        }
    }
}
