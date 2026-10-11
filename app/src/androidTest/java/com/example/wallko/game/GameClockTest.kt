package com.example.wallko.game

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.wallko.core.db.FirstRunInitializer
import com.example.wallko.core.db.LedgerKind
import com.example.wallko.core.db.PlantState
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.core.prefs.SettingsStore
import com.example.wallko.core.time.Clock
import com.example.wallko.data.garden.AddPlantResult
import com.example.wallko.data.garden.GardenRepository
import com.example.wallko.data.inMemoryDb
import com.example.wallko.data.species
import com.example.wallko.data.wallet.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Mutable fake of core.time.Clock. */
private class TestClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = ZoneOffset.UTC
    fun plusDays(days: Long) { instant += Duration.ofDays(days) }
}

@RunWith(AndroidJUnit4::class)
class GameClockTest {
    private lateinit var db: WallkoDatabase
    private lateinit var storeScope: CoroutineScope
    private lateinit var storeFile: File
    private val clock = TestClock(Instant.parse("2026-10-10T08:00:00Z"))
    private lateinit var garden: GardenRepository
    private lateinit var wallet: WalletRepository
    private lateinit var gameClock: GameClock
    private lateinit var revive: ReviveUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = inMemoryDb()
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        storeFile = File(context.cacheDir, "game_clock_test_${UUID.randomUUID()}.preferences_pb")
        val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = storeScope) { storeFile })
        garden = GardenRepository(db)
        wallet = WalletRepository(db, FirstRunInitializer(db), clock)
        gameClock = GameClock(db, garden, wallet, settings, clock)
        revive = ReviveUseCase(db, garden, wallet, clock)
    }

    @After
    fun tearDown() {
        db.close()
        storeScope.cancel()
        storeFile.delete()
    }

    /** One FAST, thirst-2 plant planted "now"; tank set to [drops]. First catch-up just records the start. */
    private suspend fun plantOne(drops: Int): Long {
        db.plantSpeciesDao().upsertAll(listOf(species(1)))
        wallet.ensureInitialized()
        db.walletDao().upsert(wallet.getWallet().copy(waterDrops = drops))
        val id = (garden.addPlant(1, "a.jpg", null, null, null, "watercolour", clock.now().toEpochMilli()) as AddPlantResult.Added).id
        assertFalse(gameClock.catchUp().changed)
        return id
    }

    private suspend fun state(id: Long) = checkNotNull(garden.getPlant(id))

    @Test
    fun emptyTankWiltsOnDay3AndWithersOnDay7() = runTest {
        val id = plantOne(drops = 0)

        clock.plusDays(2)
        assertFalse(gameClock.catchUp().anyWilted)
        assertEquals(PlantState.SEEDLING, state(id).state)

        clock.plusDays(1)
        val day3 = gameClock.catchUp()
        assertTrue(day3.anyWilted)
        assertEquals(listOf(id), day3.wiltedIds)
        assertEquals(PlantState.WILTING, state(id).state)
        assertEquals(3, state(id).daysUnwatered)
        assertEquals(day3, gameClock.lastCatchUp.value)

        clock.plusDays(4)
        val day7 = gameClock.catchUp()
        assertTrue(day7.anyWithered)
        assertEquals(PlantState.WITHERED, state(id).state)
        assertEquals(7, state(id).daysUnwatered)
        assertTrue(garden.observeGarden().first().isEmpty())
        assertEquals(1, garden.observeCollection().first().size)
    }

    @Test
    fun reviveWithOneElixirRestoresPlantAndWritesLedger() = runTest {
        val id = plantOne(drops = 0)
        clock.plusDays(7)
        gameClock.catchUp()
        assertEquals(PlantState.WITHERED, state(id).state)

        assertTrue(revive.revive(id) is ReviveOutcome.NotEnoughElixir)

        wallet.credit(LedgerKind.NEW_CELL, drops = 0, elixir = 1)
        val outcome = revive.revive(id)
        assertTrue(outcome is ReviveOutcome.Revived)
        assertEquals(0, (outcome as ReviveOutcome.Revived).elixirLeft)

        val plant = state(id)
        assertEquals(PlantState.GROWING, plant.state)
        assertEquals(0, plant.daysUnwatered)
        assertEquals(0, wallet.getWallet().elixir)
        assertEquals(1, garden.observeGarden().first().size)

        val ledger = wallet.observeRecentLedger().first()
        val reviveRow = ledger.single { it.kind == LedgerKind.REVIVE }
        assertEquals(-1, reviveRow.elixir)

        assertEquals(ReviveOutcome.NotWithered, revive.revive(id))
    }

    @Test
    fun catchUpTwiceIsIdempotent() = runTest {
        val id = plantOne(drops = 20)
        clock.plusDays(2)

        val first = gameClock.catchUp()
        assertEquals(2, first.daysProcessed)
        assertEquals(4, first.dropsDrunk)
        val walletAfter = wallet.getWallet()
        val plantAfter = state(id)
        val ledgerAfter = wallet.observeRecentLedger().first()
        assertEquals(16, walletAfter.waterDrops)
        assertEquals(2, ledgerAfter.count { it.kind == LedgerKind.DRINK && it.drops == -2 })

        val second = gameClock.catchUp()
        assertFalse(second.changed)
        assertEquals(walletAfter, wallet.getWallet())
        assertEquals(plantAfter, state(id))
        assertEquals(ledgerAfter, wallet.observeRecentLedger().first())
        assertEquals(0, plantAfter.daysUnwatered)
        assertEquals(2, plantAfter.growthDays)
    }
}
