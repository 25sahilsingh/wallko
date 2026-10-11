package com.example.wallko.core.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ordered least → most rare, so `ordinal` can be used for rarest-first sorting. */
enum class Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }

enum class GrowthSpeed { FAST, MEDIUM, SLOW }

enum class PlantState { SEEDLING, GROWING, GROWN, WILTING, WITHERED }

enum class LedgerKind { STEPS, WALK_DISTANCE, NEW_CELL, DAWN_BONUS, DRINK, REVIVE, STARTER, RARE_FIND }

/** Bundled, read-only species row. `index` is the classifier output index. */
@Entity(tableName = "plant_species")
data class PlantSpecies(
    @PrimaryKey val index: Int,
    val latin: String,
    val common: String?,
    val family: String?,
    val type: String?,
    val description: String?,
    val observations: Long,
    val rarity: Rarity,
    val thirst: Int,
    val sun: Int,
    val growth: GrowthSpeed,
    val bloom: List<String>,
    val badges: List<String>,
    /** field name → provenance tag (e.g. "inat", "estimated", "default"). */
    val sources: Map<String, String>,
    val dbVersion: Int,
)

@Entity(
    tableName = "owned_plant",
    foreignKeys = [
        ForeignKey(
            entity = PlantSpecies::class,
            parentColumns = ["index"],
            childColumns = ["speciesIndex"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("speciesIndex")],
)
data class OwnedPlant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val speciesIndex: Int,
    val foundAt: Long,
    val foundLat: Double?,
    val foundLng: Double?,
    val placeName: String?,
    val photoPath: String,
    val spritePath: String? = null,
    val styleId: String,
    val plantedAt: Long,
    val lastDrankAt: Long?,
    val state: PlantState = PlantState.SEEDLING,
    /** Consecutive daily ticks without a successful drink. */
    val daysUnwatered: Int = 0,
    /** Days with a successful drink, counted toward GROWN. */
    val growthDays: Int = 0,
    val gardenX: Int,
    val gardenY: Int,
    val onShelf: Boolean = false,
)

@Entity(tableName = "wallet")
data class Wallet(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val waterDrops: Int,
    val elixir: Int,
    val tankCapacity: Int,
    /** Metres walked in never-visited cells not yet converted into elixir. */
    val elixirProgressM: Double = 0.0,
    val lastTickAt: Long,
) {
    companion object { const val SINGLETON_ID = 0 }
}

@Entity(tableName = "ledger_entry", indices = [Index("at")])
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val kind: LedgerKind,
    val drops: Int,
    val elixir: Int = 0,
    val note: String? = null,
)

@Entity(tableName = "walk")
data class Walk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceM: Double = 0.0,
    val steps: Int = 0,
    val newCells: Int = 0,
    val dropsEarned: Int = 0,
    val elixirEarned: Int = 0,
    val dawn: Boolean = false,
)

@Entity(
    tableName = "walk_point",
    foreignKeys = [
        ForeignKey(
            entity = Walk::class,
            parentColumns = ["id"],
            childColumns = ["walkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("walkId")],
)
data class WalkPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val walkId: Long,
    val at: Long,
    val lat: Double,
    val lng: Double,
)

@Entity(tableName = "explored_cell")
data class ExploredCell(
    @PrimaryKey val cellId: Long,
    val firstVisitedAt: Long,
)

/** One row per local calendar day; `epochDay` = LocalDate.toEpochDay(). */
@Entity(tableName = "step_day")
data class StepDay(
    @PrimaryKey val epochDay: Long,
    val steps: Int,
    val dropsCredited: Int,
)
