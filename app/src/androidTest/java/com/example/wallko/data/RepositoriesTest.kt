package com.example.wallko.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.wallko.core.config.GameRules
import com.example.wallko.core.db.FirstRunInitializer
import com.example.wallko.core.db.LedgerKind
import com.example.wallko.core.db.PlantState
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.data.garden.AddPlantResult
import com.example.wallko.data.garden.GardenRepository
import com.example.wallko.data.garden.PlantStateUpdate
import com.example.wallko.data.wallet.WalletRepository
import com.example.wallko.data.walks.TrackPoint
import com.example.wallko.data.walks.WalkRepository
import com.example.wallko.data.walks.WalkTotals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoriesTest {
    private lateinit var db: WallkoDatabase
    private val clock = FixedClock()
    private lateinit var garden: GardenRepository
    private lateinit var wallet: WalletRepository
    private lateinit var walks: WalkRepository

    @Before
    fun setUp() {
        db = inMemoryDb()
        garden = GardenRepository(db)
        wallet = WalletRepository(db, FirstRunInitializer(db), clock)
        walks = WalkRepository(db, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun addPlantDedupesLivingSpecies() = runTest {
        db.plantSpeciesDao().upsertAll(listOf(species(1), species(2)))

        val first = garden.addPlant(1, "a.jpg", 1.0, 2.0, "Park", "watercolor", now = 10L)
        assertTrue(first is AddPlantResult.Added)
        val dup = garden.addPlant(1, "b.jpg", null, null, null, "watercolor", now = 20L)
        assertEquals(AddPlantResult.AlreadyOwned(first.id), dup)

        val other = garden.addPlant(2, "c.jpg", null, null, null, "watercolor", now = 30L)
        assertTrue(other is AddPlantResult.Added)

        val all = garden.observeCollection().first()
        assertEquals(2, all.size)
        val p = all.first { it.plant.id == first.id }
        assertEquals(PlantState.SEEDLING, p.plant.state)
        assertEquals(10L, p.plant.lastDrankAt)
        assertEquals(1, p.species.index)
        // Different grid cells.
        val spots = all.map { it.plant.gardenX to it.plant.gardenY }.toSet()
        assertEquals(2, spots.size)

        // A withered species may be planted again and leaves the garden.
        garden.updateStates(listOf(PlantStateUpdate(first.id, PlantState.WITHERED, 7, 0, 10L)))
        assertEquals(1, garden.observeGarden().first().size)
        assertTrue(garden.isOwned(1))
        assertFalse(garden.isInGarden(1))
        val replant = garden.addPlant(1, "d.jpg", null, null, null, "watercolor", now = 40L)
        assertTrue(replant is AddPlantResult.Added)
        assertNotEquals(first.id, replant.id)
    }

    @Test
    fun addPlantRaisesTankCapacityImmediatelyByDistinctSpecies() = runTest {
        db.plantSpeciesDao().upsertAll(listOf(species(1), species(2)))
        assertEquals(GameRules.Start.TANK_CAPACITY, wallet.getWallet().tankCapacity)

        val first = garden.addPlant(1, "a.jpg", null, null, null, "watercolor", now = 10L)
        assertEquals(60, wallet.getWallet().tankCapacity)

        // A walk the same day can now fill past 50.
        val credit = wallet.credit(LedgerKind.WALK_DISTANCE, 100)
        assertEquals(60, credit.wallet.waterDrops)

        // Duplicate living species: nothing changes.
        garden.addPlant(1, "b.jpg", null, null, null, "watercolor", now = 20L)
        assertEquals(60, wallet.getWallet().tankCapacity)

        // Withered and re-scanned: a second row of the same species doesn't farm capacity.
        garden.updateStates(listOf(PlantStateUpdate(first.id, PlantState.WITHERED, 7, 0, 10L)))
        assertTrue(garden.addPlant(1, "c.jpg", null, null, null, "watercolor", now = 30L) is AddPlantResult.Added)
        assertEquals(60, wallet.getWallet().tankCapacity)

        garden.addPlant(2, "d.jpg", null, null, null, "watercolor", now = 40L)
        assertEquals(GameRules.tankCapacityFor(2), wallet.getWallet().tankCapacity)
    }

    @Test
    fun addPlantCreatesStarterWalletIfMissing() = runTest {
        db.plantSpeciesDao().upsertAll(listOf(species(1)))
        garden.addPlant(1, "a.jpg", null, null, null, "watercolor", now = 10L)
        val w = wallet.getWallet()
        assertEquals(60, w.tankCapacity)
        assertEquals(GameRules.Start.STARTER_DROPS, w.waterDrops)
    }

    @Test
    fun photoReferencedOnlyWhileAPlantUsesIt() = runTest {
        db.plantSpeciesDao().upsertAll(listOf(species(1)))
        assertFalse(garden.isPhotoReferenced("/files/a.jpg"))
        val p = garden.addPlant(1, "/files/a.jpg", null, null, null, "watercolor", now = 10L)
        assertTrue(garden.isPhotoReferenced("/files/a.jpg"))
        assertFalse(garden.isPhotoReferenced("/files/b.jpg"))
        // Still referenced after withering (the collection keeps the card).
        garden.updateStates(listOf(PlantStateUpdate(p.id, PlantState.WITHERED, 7, 0, 10L)))
        assertTrue(garden.isPhotoReferenced("/files/a.jpg"))
    }

    @Test
    fun creditClampsToCapacityAndWritesLedger() = runTest {
        val start = wallet.getWallet()
        assertEquals(GameRules.Start.STARTER_DROPS, start.waterDrops)

        val result = wallet.credit(LedgerKind.WALK_DISTANCE, drops = 100, elixir = 1, note = "walk", at = 500L)
        assertEquals(start.tankCapacity, result.wallet.waterDrops)
        assertEquals(start.tankCapacity - start.waterDrops, result.dropsCredited)
        assertEquals(100 - result.dropsCredited, result.dropsOverflow)
        assertEquals(1, result.wallet.elixir)
        assertEquals(result.wallet, wallet.observeWallet().first())

        val ledger = wallet.observeRecentLedger().first()
        assertEquals(listOf(LedgerKind.WALK_DISTANCE, LedgerKind.STARTER), ledger.map { it.kind })
        assertEquals(result.dropsCredited, ledger.first().drops)
        assertEquals(500L, ledger.first().at)

        // Full tank: credits nothing but still logs.
        assertEquals(0, wallet.credit(LedgerKind.STEPS, drops = 5).dropsCredited)

        assertTrue(wallet.debitElixir(1))
        assertFalse(wallet.debitElixir(1))
        assertEquals(0, wallet.getWallet().elixir)
    }

    @Test
    fun markExploredIsIdempotent() = runTest {
        assertEquals(setOf(1L, 2L), walks.markExplored(listOf(1L, 2L, 2L), at = 100L))
        assertEquals(setOf(3L), walks.markExplored(listOf(2L, 3L), at = 200L))
        assertEquals(emptySet<Long>(), walks.markExplored(listOf(1L, 2L, 3L), at = 300L))

        assertEquals(setOf(1L, 2L, 3L), walks.knownCellIds())
        assertEquals(3, walks.observeExploredCount().first())
        assertEquals(100L, walks.observeExploredCells().first().first { it.cellId == 2L }.firstVisitedAt)
    }

    @Test
    fun finishingAWalkStoresTotalsAndPoints() = runTest {
        val id = walks.startWalk(startedAt = 1_000L)
        walks.appendPoints(id, listOf(TrackPoint(2_000L, 1.0, 1.0), TrackPoint(1_500L, 0.5, 0.5)))
        walks.appendPoints(id, listOf(TrackPoint(3_000L, 2.0, 2.0)))

        val totals = WalkTotals(distanceM = 640.0, steps = 900, newCells = 4, dropsEarned = 24, elixirEarned = 1, dawn = true)
        assertTrue(walks.finishWalk(id, totals, endedAt = 4_000L))
        assertFalse(walks.finishWalk(id + 99, totals, endedAt = 4_000L))

        val stored = walks.observeWalk(id).first()!!
        assertEquals(4_000L, stored.walk.endedAt)
        assertEquals(640.0, stored.walk.distanceM, 0.0)
        assertEquals(24, stored.walk.dropsEarned)
        assertTrue(stored.walk.dawn)
        assertEquals(listOf(1_500L, 2_000L, 3_000L), stored.points.map { it.at })
        assertEquals(listOf(id), walks.observeWalks().first().map { it.id })
    }
}
