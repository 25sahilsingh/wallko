package com.example.wallko.data.export

import com.example.wallko.core.db.ExploredCell
import com.example.wallko.core.db.LedgerEntry
import com.example.wallko.core.db.OwnedPlantWithSpecies
import com.example.wallko.core.db.StepDay
import com.example.wallko.core.db.Walk
import com.example.wallko.core.db.WalkPoint
import com.example.wallko.core.db.Wallet
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/** Raw rows read from Room in one transaction. */
data class ExportSnapshot(
    val plants: List<OwnedPlantWithSpecies>,
    val wallet: Wallet?,
    val ledger: List<LedgerEntry>,
    val walks: List<Walk>,
    val walkPoints: List<WalkPoint>,
    val exploredCells: List<ExploredCell>,
    val stepDays: List<StepDay>,
)

/** Pure entity → DTO mapping and JSON encoding. No Android, no I/O. */
object ExportMapper {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
    }

    fun toExport(snapshot: ExportSnapshot, exportedAt: Instant, appVersion: String?): WallkoExport {
        val pointsByWalk = snapshot.walkPoints.groupBy { it.walkId }
        return WallkoExport(
            appVersion = appVersion?.takeIf { it.isNotBlank() },
            exportedAt = exportedAt.toString(),
            plants = snapshot.plants.map { it.toExport() },
            wallet = snapshot.wallet?.toExport(),
            ledger = snapshot.ledger.map { it.toExport() },
            walks = snapshot.walks.map { walk ->
                walk.toExport(pointsByWalk[walk.id].orEmpty().sortedWith(compareBy<WalkPoint>({ it.at }, { it.id })))
            },
            exploredCells = snapshot.exploredCells.map { ExploredCellExport(it.cellId, iso(it.firstVisitedAt)) },
            stepDays = snapshot.stepDays.map {
                StepDayExport(LocalDate.ofEpochDay(it.epochDay).toString(), it.steps, it.dropsCredited)
            },
        )
    }

    fun encode(export: WallkoExport): String = json.encodeToString(WallkoExport.serializer(), export)

    fun decode(text: String): WallkoExport = json.decodeFromString(WallkoExport.serializer(), text)

    /** "wallko-export-2026-10-10.json". */
    fun fileName(today: LocalDate): String = "wallko-export-$today.json"

    private fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    private fun OwnedPlantWithSpecies.toExport() = PlantExport(
        id = plant.id,
        speciesIndex = plant.speciesIndex,
        latin = species.latin,
        common = species.common,
        family = species.family,
        rarity = species.rarity.name,
        state = plant.state.name,
        foundAt = iso(plant.foundAt),
        foundLat = plant.foundLat,
        foundLng = plant.foundLng,
        placeName = plant.placeName,
        plantedAt = iso(plant.plantedAt),
        lastDrankAt = plant.lastDrankAt?.let(::iso),
        daysUnwatered = plant.daysUnwatered,
        growthDays = plant.growthDays,
        styleId = plant.styleId,
        gardenX = plant.gardenX,
        gardenY = plant.gardenY,
        onShelf = plant.onShelf,
        photoPath = plant.photoPath,
        spritePath = plant.spritePath,
    )

    private fun Wallet.toExport() = WalletExport(
        waterDrops = waterDrops,
        elixir = elixir,
        tankCapacity = tankCapacity,
        elixirProgressM = elixirProgressM,
        lastTickAt = iso(lastTickAt),
    )

    private fun LedgerEntry.toExport() = LedgerExport(id, iso(at), kind.name, drops, elixir, note)

    private fun Walk.toExport(points: List<WalkPoint>) = WalkExport(
        id = id,
        startedAt = iso(startedAt),
        endedAt = endedAt?.let(::iso),
        distanceM = distanceM,
        steps = steps,
        newCells = newCells,
        dropsEarned = dropsEarned,
        elixirEarned = elixirEarned,
        dawn = dawn,
        points = points.map { WalkPointExport(iso(it.at), it.lat, it.lng) },
    )
}
