package com.example.wallko.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantSpeciesDao {
    @Query("SELECT COUNT(*) FROM plant_species")
    suspend fun count(): Int

    @Query("SELECT * FROM plant_species ORDER BY `index`")
    fun observeAll(): Flow<List<PlantSpecies>>

    @Query("SELECT * FROM plant_species WHERE `index` = :index")
    suspend fun byIndex(index: Int): PlantSpecies?

    @Query("SELECT * FROM plant_species WHERE `index` = :index")
    fun observe(index: Int): Flow<PlantSpecies?>

    @Query("SELECT COUNT(*) FROM plant_species")
    fun observeCount(): Flow<Int>

    @Query("SELECT `index`, latin FROM plant_species")
    suspend fun latinNames(): List<SpeciesLatinName>

    /** Reseeding replaces rows in place; owned plants keep pointing at the same index. */
    @Upsert
    suspend fun upsertAll(species: List<PlantSpecies>)
}

@Dao
interface OwnedPlantDao {
    @Query("SELECT * FROM owned_plant ORDER BY plantedAt")
    fun observeAll(): Flow<List<OwnedPlant>>

    @Query("SELECT * FROM owned_plant")
    suspend fun getAll(): List<OwnedPlant>

    /** Every owned plant with its species, oldest first (data export). */
    @Transaction
    @Query("SELECT * FROM owned_plant ORDER BY plantedAt, id")
    suspend fun getAllWithSpecies(): List<OwnedPlantWithSpecies>

    @Query("SELECT * FROM owned_plant WHERE id = :id")
    fun observe(id: Long): Flow<OwnedPlant?>

    @Query("SELECT EXISTS(SELECT 1 FROM owned_plant WHERE speciesIndex = :speciesIndex)")
    suspend fun ownsSpecies(speciesIndex: Int): Boolean

    /** Distinct species ever owned, withered included (drives tank capacity). */
    @Query("SELECT COUNT(DISTINCT speciesIndex) FROM owned_plant")
    suspend fun countDistinctSpecies(): Int

    /** True if any owned plant (withered included) uses this photo file. */
    @Query("SELECT EXISTS(SELECT 1 FROM owned_plant WHERE photoPath = :photoPath)")
    suspend fun photoReferenced(photoPath: String): Boolean

    @Insert
    suspend fun insert(plant: OwnedPlant): Long

    @Update
    suspend fun update(plant: OwnedPlant)

    @Update
    suspend fun updateAll(plants: List<OwnedPlant>)

    @Query("SELECT * FROM owned_plant WHERE id = :id")
    suspend fun get(id: Long): OwnedPlant?

    @Transaction
    @Query("SELECT * FROM owned_plant ORDER BY plantedAt")
    fun observeAllWithSpecies(): Flow<List<OwnedPlantWithSpecies>>

    @Transaction
    @Query("SELECT * FROM owned_plant WHERE state != 'WITHERED' ORDER BY plantedAt")
    fun observeAliveWithSpecies(): Flow<List<OwnedPlantWithSpecies>>

    @Transaction
    @Query("SELECT * FROM owned_plant WHERE id = :id")
    fun observeWithSpecies(id: Long): Flow<OwnedPlantWithSpecies?>

    /** First non-withered plant of this species, if any (duplicate check). */
    @Query("SELECT * FROM owned_plant WHERE speciesIndex = :speciesIndex AND state != 'WITHERED' ORDER BY id LIMIT 1")
    suspend fun aliveBySpecies(speciesIndex: Int): OwnedPlant?

    @Query("SELECT * FROM owned_plant WHERE state != 'WITHERED'")
    suspend fun getAlive(): List<OwnedPlant>

    @Query("UPDATE owned_plant SET spritePath = :spritePath WHERE id = :id")
    suspend fun setSprite(id: Long, spritePath: String?)

    @Query("UPDATE owned_plant SET gardenX = :x, gardenY = :y, onShelf = :onShelf WHERE id = :id")
    suspend fun move(id: Long, x: Int, y: Int, onShelf: Boolean)

    /** Engine-owned fields only, so a concurrent move/sprite write is never clobbered. */
    @Query(
        "UPDATE owned_plant SET state = :state, daysUnwatered = :daysUnwatered, " +
            "growthDays = :growthDays, lastDrankAt = :lastDrankAt WHERE id = :id",
    )
    suspend fun updateState(id: Long, state: PlantState, daysUnwatered: Int, growthDays: Int, lastDrankAt: Long?)
}

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallet WHERE id = 0")
    fun observe(): Flow<Wallet?>

    @Query("SELECT * FROM wallet WHERE id = 0")
    suspend fun get(): Wallet?

    @Upsert
    suspend fun upsert(wallet: Wallet)
}

@Dao
interface LedgerDao {
    @Insert
    suspend fun insert(entry: LedgerEntry): Long

    @Insert
    suspend fun insertAll(entries: List<LedgerEntry>)

    @Query("SELECT * FROM ledger_entry ORDER BY at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<LedgerEntry>>

    /** Full ledger, oldest first (data export). */
    @Query("SELECT * FROM ledger_entry ORDER BY at, id")
    suspend fun getAll(): List<LedgerEntry>
}

@Dao
interface WalkDao {
    @Insert
    suspend fun insert(walk: Walk): Long

    @Update
    suspend fun update(walk: Walk)

    @Query("SELECT * FROM walk WHERE id = :id")
    suspend fun get(id: Long): Walk?

    @Query("SELECT * FROM walk ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<Walk>>

    /** Every walk, oldest first (data export). */
    @Query("SELECT * FROM walk ORDER BY startedAt, id")
    suspend fun getAll(): List<Walk>

    /** Every GPS point of every walk (data export). */
    @Query("SELECT * FROM walk_point ORDER BY walkId, at, id")
    suspend fun getAllPoints(): List<WalkPoint>

    @Insert
    suspend fun insertPoints(points: List<WalkPoint>)

    @Query("SELECT * FROM walk_point WHERE walkId = :walkId ORDER BY at")
    fun observePoints(walkId: Long): Flow<List<WalkPoint>>

    @Query("SELECT * FROM walk WHERE id = :id")
    fun observe(id: Long): Flow<Walk?>

    @Transaction
    @Query("SELECT * FROM walk WHERE id = :id")
    fun observeWithPoints(id: Long): Flow<WalkWithPoints?>

    @Query(
        "UPDATE walk SET endedAt = :endedAt, distanceM = :distanceM, steps = :steps, newCells = :newCells, " +
            "dropsEarned = :dropsEarned, elixirEarned = :elixirEarned, dawn = :dawn WHERE id = :id",
    )
    suspend fun finish(
        id: Long,
        endedAt: Long,
        distanceM: Double,
        steps: Int,
        newCells: Int,
        dropsEarned: Int,
        elixirEarned: Int,
        dawn: Boolean,
    ): Int
}

@Dao
interface ExploredCellDao {
    /** Returns -1 for cells already explored, so the first visit time is kept. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(cell: ExploredCell): Long

    @Query("SELECT COUNT(*) FROM explored_cell")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM explored_cell")
    fun observeAll(): Flow<List<ExploredCell>>

    @Query("SELECT * FROM explored_cell WHERE cellId = :cellId")
    suspend fun get(cellId: Long): ExploredCell?

    /** Row ids per input; -1 where the cell was already explored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(cells: List<ExploredCell>): List<Long>

    @Query("SELECT cellId FROM explored_cell")
    suspend fun allIds(): List<Long>

    /** Every explored cell, first visited first (data export). */
    @Query("SELECT * FROM explored_cell ORDER BY firstVisitedAt, cellId")
    suspend fun getAll(): List<ExploredCell>
}

@Dao
interface StepDayDao {
    @Upsert
    suspend fun upsert(day: StepDay)

    @Query("SELECT * FROM step_day WHERE epochDay = :epochDay")
    suspend fun get(epochDay: Long): StepDay?

    @Query("SELECT * FROM step_day WHERE epochDay = :epochDay")
    fun observe(epochDay: Long): Flow<StepDay?>

    /** Every recorded day, oldest first (data export). */
    @Query("SELECT * FROM step_day ORDER BY epochDay")
    suspend fun getAll(): List<StepDay>
}
