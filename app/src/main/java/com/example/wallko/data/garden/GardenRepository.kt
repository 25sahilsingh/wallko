package com.example.wallko.data.garden

import androidx.room.withTransaction
import com.example.wallko.core.config.GameRules
import com.example.wallko.core.db.FirstRunInitializer
import com.example.wallko.core.db.OwnedPlant
import com.example.wallko.core.db.OwnedPlantWithSpecies
import com.example.wallko.core.db.PlantState
import com.example.wallko.core.db.WallkoDatabase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Result of [GardenRepository.addPlant]. Duplicates rule (decisions.md): an owned, living species is not added again. */
sealed interface AddPlantResult {
    val id: Long
    data class Added(override val id: Long) : AddPlantResult
    data class AlreadyOwned(override val id: Long) : AddPlantResult
}

/** A spot in the garden grid, or a slot on the shelf when [onShelf]. */
data class GardenSpot(val x: Int, val y: Int, val onShelf: Boolean = false)

/** Engine output for one plant; only these fields are written by [GardenRepository.updateStates]. */
data class PlantStateUpdate(
    val id: Long,
    val state: PlantState,
    val daysUnwatered: Int,
    val growthDays: Int,
    val lastDrankAt: Long?,
)

@Singleton
class GardenRepository @Inject constructor(
    private val db: WallkoDatabase,
) {
    private val dao get() = db.ownedPlantDao()

    /** Every owned plant, including withered ones (the collection). */
    fun observeCollection(): Flow<List<OwnedPlantWithSpecies>> = dao.observeAllWithSpecies()

    /** Non-withered plants (the garden). */
    fun observeGarden(): Flow<List<OwnedPlantWithSpecies>> = dao.observeAliveWithSpecies()

    fun observePlant(id: Long): Flow<OwnedPlantWithSpecies?> = dao.observeWithSpecies(id)

    suspend fun getPlant(id: Long): OwnedPlant? = dao.get(id)

    /** True if any plant of this species was ever owned (withered included). */
    suspend fun isOwned(speciesIndex: Int): Boolean = dao.ownsSpecies(speciesIndex)

    /** True if any owned plant (withered included) points at this photo; such a file must never be deleted. */
    suspend fun isPhotoReferenced(photoPath: String): Boolean = dao.photoReferenced(photoPath)

    /** True if a living (non-withered) plant of this species is in the garden. */
    suspend fun isInGarden(speciesIndex: Int): Boolean = dao.aliveBySpecies(speciesIndex) != null

    /**
     * Plants a newly scanned species as a SEEDLING at the next free spot (or the shelf when the grid is full).
     * If a living plant of the species already exists, nothing is written and [AddPlantResult.AlreadyOwned] is
     * returned. A withered species may be planted again.
     *
     * In the same transaction the wallet's tank capacity is raised to [GameRules.tankCapacityFor] the distinct
     * species now owned, so the bigger tank applies immediately (not only at the next midnight tick).
     */
    suspend fun addPlant(
        speciesIndex: Int,
        photoPath: String,
        lat: Double?,
        lng: Double?,
        placeName: String?,
        styleId: String,
        now: Long,
    ): AddPlantResult = db.withTransaction {
        dao.aliveBySpecies(speciesIndex)?.let { return@withTransaction AddPlantResult.AlreadyOwned(it.id) }
        val spot = nextFreeSpotInternal()
        val id = dao.insert(
            OwnedPlant(
                speciesIndex = speciesIndex,
                foundAt = now,
                foundLat = lat,
                foundLng = lng,
                placeName = placeName,
                photoPath = photoPath,
                styleId = styleId,
                plantedAt = now,
                lastDrankAt = now,
                state = PlantState.SEEDLING,
                gardenX = spot.x,
                gardenY = spot.y,
                onShelf = spot.onShelf,
            ),
        )
        syncTankCapacityInternal(now)
        AddPlantResult.Added(id)
    }

    /**
     * Sets Wallet.tankCapacity from the distinct species ever owned (same rule as GameEngine.tankCapacity).
     * Never lowers it. Must run inside a transaction. Creates the starter wallet first if it doesn't exist yet.
     */
    private suspend fun syncTankCapacityInternal(now: Long) {
        FirstRunInitializer(db).ensureInitialized(now)
        val walletDao = db.walletDao()
        val w = walletDao.get() ?: return
        val capacity = maxOf(w.tankCapacity, GameRules.tankCapacityFor(dao.countDistinctSpecies()))
        if (capacity != w.tankCapacity) walletDao.upsert(w.copy(tankCapacity = capacity))
    }

    suspend fun setSprite(id: Long, path: String?) = dao.setSprite(id, path)

    /** Stores the position as given; grid snapping is the UI's job. */
    suspend fun move(id: Long, gardenX: Int, gardenY: Int, onShelf: Boolean) = dao.move(id, gardenX, gardenY, onShelf)

    /** Applies engine tick results atomically. */
    suspend fun updateStates(updates: List<PlantStateUpdate>) {
        if (updates.isEmpty()) return
        db.withTransaction {
            updates.forEach { dao.updateState(it.id, it.state, it.daysUnwatered, it.growthDays, it.lastDrankAt) }
        }
    }

    /** First empty grid cell (row-major), else the next shelf slot. */
    suspend fun nextFreeSpot(): GardenSpot = db.withTransaction { nextFreeSpotInternal() }

    private suspend fun nextFreeSpotInternal(): GardenSpot {
        val alive = dao.getAlive()
        val taken = alive.filter { !it.onShelf }.mapTo(HashSet()) { it.gardenX to it.gardenY }
        for (y in 0 until GRID_HEIGHT) for (x in 0 until GRID_WIDTH) {
            if ((x to y) !in taken) return GardenSpot(x, y)
        }
        val shelfTaken = alive.filter { it.onShelf }.mapTo(HashSet()) { it.gardenX }
        val slot = generateSequence(0) { it + 1 }.first { it !in shelfTaken }
        return GardenSpot(slot, 0, onShelf = true)
    }

    companion object {
        // TODO: move to GameRules (e.g. GameRules.Garden) once the garden UI settles on a size.
        const val GRID_WIDTH = 4
        const val GRID_HEIGHT = 6
    }
}
