package com.example.wallko.game

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.wallko.core.db.FirstRunInitializer
import com.example.wallko.core.db.LedgerKind
import com.example.wallko.core.db.PlantState
import com.example.wallko.core.db.Rarity
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.core.prefs.SettingsStore
import com.example.wallko.data.FixedClock
import com.example.wallko.data.garden.GardenRepository
import com.example.wallko.data.garden.PlantStateUpdate
import com.example.wallko.data.inMemoryDb
import com.example.wallko.data.plants.PlantRepository
import com.example.wallko.data.species
import com.example.wallko.data.wallet.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScanToCardUseCaseTest {
    private lateinit var context: Context
    private lateinit var db: WallkoDatabase
    private lateinit var storeScope: CoroutineScope
    private lateinit var storeFile: File
    private val clock = FixedClock()
    private lateinit var garden: GardenRepository
    private lateinit var wallet: WalletRepository
    private lateinit var useCase: ScanToCardUseCase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = inMemoryDb()
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        storeFile = File(context.cacheDir, "scan_to_card_test_${UUID.randomUUID()}.preferences_pb")
        val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = storeScope) { storeFile })
        garden = GardenRepository(db)
        wallet = WalletRepository(db, FirstRunInitializer(db), clock)
        val plants = PlantRepository(db.plantSpeciesDao(), settings, context)
        useCase = ScanToCardUseCase(db, plants, garden, wallet, settings, clock, context)
    }

    @After
    fun tearDown() {
        db.close()
        storeScope.cancel()
        storeFile.delete()
    }

    private fun spriteWorkQueued(plantId: Long): Boolean =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(SpriteWorker.workName(plantId)).get().isNotEmpty()

    @Test
    fun rareScanPlantsRaisesCapacityPaysBonusAndQueuesSprite() = runTest {
        db.plantSpeciesDao().upsertAll(listOf(species(5).copy(rarity = Rarity.RARE)))

        val card = useCase.confirm(5, "/files/rare.jpg") as ScanToCardResult.NewCard

        val w = wallet.getWallet()
        assertEquals(60, w.tankCapacity)
        assertEquals(20 + 10, w.waterDrops)
        assertEquals(10, card.bonusDrops)
        assertEquals(1, db.ledgerDao().getAll().count { it.kind == LedgerKind.RARE_FIND })
        assertTrue(spriteWorkQueued(card.plantId))
        assertTrue(garden.isPhotoReferenced("/files/rare.jpg"))
    }

    @Test
    fun bonusFillsTheNewlyRaisedTank() = runTest {
        db.plantSpeciesDao().upsertAll(listOf(species(5).copy(rarity = Rarity.EPIC)))
        wallet.ensureInitialized()
        db.walletDao().upsert(wallet.getWallet().copy(waterDrops = 50)) // full at the old cap of 50

        val card = useCase.confirm(5, "/files/epic.jpg") as ScanToCardResult.NewCard

        assertEquals(10, card.bonusDrops)
        assertEquals(60, wallet.getWallet().waterDrops)
    }

    @Test
    fun retryOfACommittedScanShowsTheCardInsteadOfDuplicate() = runTest {
        db.plantSpeciesDao().upsertAll(listOf(species(5).copy(rarity = Rarity.RARE)))
        val first = useCase.confirm(5, "/files/same.jpg") as ScanToCardResult.NewCard

        // Same photo again (the VM's "retry" after a failure that happened post-commit).
        val retry = useCase.confirm(5, "/files/same.jpg")
        assertEquals(ScanToCardResult.NewCard(first.plantId, 5, first.displayName, 0), retry)
        assertEquals(30, wallet.getWallet().waterDrops) // bonus not paid twice

        // A genuinely new photo of a living species is still a duplicate.
        assertTrue(useCase.confirm(5, "/files/other.jpg") is ScanToCardResult.AlreadyInGarden)
    }

    @Test
    fun witheredRescanDoesNotFarmCapacityOrBonus() = runTest {
        db.plantSpeciesDao().upsertAll(listOf(species(5).copy(rarity = Rarity.RARE)))
        val first = useCase.confirm(5, "/files/a.jpg") as ScanToCardResult.NewCard
        garden.updateStates(listOf(PlantStateUpdate(first.plantId, PlantState.WITHERED, 7, 0, null)))

        val again = useCase.confirm(5, "/files/b.jpg") as ScanToCardResult.NewCard

        assertEquals(0, again.bonusDrops)
        assertEquals(60, wallet.getWallet().tankCapacity)
        assertTrue(spriteWorkQueued(again.plantId))
    }
}
