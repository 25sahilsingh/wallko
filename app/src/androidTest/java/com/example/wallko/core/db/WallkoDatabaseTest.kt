package com.example.wallko.core.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallkoDatabaseTest {
    private lateinit var db: WallkoDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WallkoDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun walletInsertAndRead() = runBlocking {
        assertNull(db.walletDao().get())
        val wallet = Wallet(waterDrops = 20, elixir = 0, tankCapacity = 50, elixirProgressM = 120.5, lastTickAt = 1_000L)
        db.walletDao().upsert(wallet)

        assertEquals(wallet, db.walletDao().get())
        assertEquals(wallet, db.walletDao().observe().first())

        db.walletDao().upsert(wallet.copy(waterDrops = 5))
        assertEquals(5, db.walletDao().get()!!.waterDrops)
    }

    @Test
    fun ownedPlantRequiresExistingSpecies() = runBlocking {
        val orphan = plant(speciesIndex = 42)
        val failed = runCatching { db.ownedPlantDao().insert(orphan) }
        assertTrue(failed.exceptionOrNull() is SQLiteConstraintException)

        val species = species(42)
        db.plantSpeciesDao().upsertAll(listOf(species))
        assertEquals(species, db.plantSpeciesDao().byIndex(42)) // JSON converters round-trip

        val id = db.ownedPlantDao().insert(orphan)
        val stored = db.ownedPlantDao().observeAll().first().single()
        assertEquals(id, stored.id)
        assertEquals(PlantState.SEEDLING, stored.state)
        assertTrue(db.ownedPlantDao().ownsSpecies(42))
    }

    @Test
    fun exploredCellInsertIgnoreKeepsFirstVisit() = runBlocking {
        val dao = db.exploredCellDao()
        assertTrue(dao.insertIgnore(ExploredCell(cellId = 7L, firstVisitedAt = 100L)) != -1L)
        assertEquals(-1L, dao.insertIgnore(ExploredCell(cellId = 7L, firstVisitedAt = 999L)))

        assertEquals(100L, dao.get(7L)!!.firstVisitedAt)
        assertEquals(1, dao.observeCount().first())
    }

    private fun species(index: Int) = PlantSpecies(
        index = index,
        latin = "Monstera deliciosa",
        common = "Swiss cheese plant",
        family = "Araceae",
        type = "herb",
        description = null,
        observations = 12_345,
        rarity = Rarity.UNCOMMON,
        thirst = 3,
        sun = 2,
        growth = GrowthSpeed.MEDIUM,
        bloom = listOf("summer"),
        badges = listOf("houseplant"),
        sources = mapOf("thirst" to "estimated"),
        dbVersion = 1,
    )

    private fun plant(speciesIndex: Int) = OwnedPlant(
        speciesIndex = speciesIndex,
        foundAt = 1L,
        foundLat = null,
        foundLng = null,
        placeName = null,
        photoPath = "photo.jpg",
        styleId = "watercolor",
        plantedAt = 1L,
        lastDrankAt = null,
        gardenX = 0,
        gardenY = 0,
    )
}
