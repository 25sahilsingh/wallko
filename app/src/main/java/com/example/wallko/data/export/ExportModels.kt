package com.example.wallko.data.export

import kotlinx.serialization.Serializable

/*
 * JSON shape of a Wallko data export. These DTOs are a public file format: never rename or remove a
 * field without bumping [WallkoExport.CURRENT_SCHEMA_VERSION]. Times are ISO-8601 instants in UTC
 * (e.g. "2026-10-10T07:12:00.123Z"); calendar days are ISO dates ("2026-10-10").
 */

@Serializable
data class WallkoExport(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val app: String = "Wallko",
    val appVersion: String?,
    val exportedAt: String,
    /** Explains what is (and is not) inside, for whoever opens the file later. */
    val note: String = NOTE,
    val plants: List<PlantExport>,
    val wallet: WalletExport?,
    val ledger: List<LedgerExport>,
    val walks: List<WalkExport>,
    val exploredCells: List<ExploredCellExport>,
    val stepDays: List<StepDayExport>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val NOTE =
            "Photos and plant sprites are not included; photoPath/spritePath are the on-device file paths only."
    }
}

@Serializable
data class PlantExport(
    val id: Long,
    val speciesIndex: Int,
    val latin: String,
    val common: String?,
    val family: String?,
    val rarity: String,
    val state: String,
    val foundAt: String,
    val foundLat: Double?,
    val foundLng: Double?,
    val placeName: String?,
    val plantedAt: String,
    val lastDrankAt: String?,
    val daysUnwatered: Int,
    val growthDays: Int,
    val styleId: String,
    val gardenX: Int,
    val gardenY: Int,
    val onShelf: Boolean,
    /** On-device path of the original photo. The image itself is not exported. */
    val photoPath: String,
    /** On-device path of the painted sprite, if one has been made. Not exported. */
    val spritePath: String?,
)

@Serializable
data class WalletExport(
    val waterDrops: Int,
    val elixir: Int,
    val tankCapacity: Int,
    val elixirProgressM: Double,
    val lastTickAt: String,
)

@Serializable
data class LedgerExport(
    val id: Long,
    val at: String,
    val kind: String,
    val drops: Int,
    val elixir: Int,
    val note: String?,
)

@Serializable
data class WalkExport(
    val id: Long,
    val startedAt: String,
    val endedAt: String?,
    val distanceM: Double,
    val steps: Int,
    val newCells: Int,
    val dropsEarned: Int,
    val elixirEarned: Int,
    val dawn: Boolean,
    val points: List<WalkPointExport>,
)

@Serializable
data class WalkPointExport(
    val at: String,
    val lat: Double,
    val lng: Double,
)

@Serializable
data class ExploredCellExport(
    val cellId: Long,
    val firstVisitedAt: String,
)

@Serializable
data class StepDayExport(
    val date: String,
    val steps: Int,
    val dropsCredited: Int,
)
