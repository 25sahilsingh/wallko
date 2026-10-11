package com.example.wallko.data.export

import com.example.wallko.core.db.ExploredCell
import com.example.wallko.core.db.GrowthSpeed
import com.example.wallko.core.db.LedgerEntry
import com.example.wallko.core.db.LedgerKind
import com.example.wallko.core.db.OwnedPlant
import com.example.wallko.core.db.OwnedPlantWithSpecies
import com.example.wallko.core.db.PlantSpecies
import com.example.wallko.core.db.PlantState
import com.example.wallko.core.db.Rarity
import com.example.wallko.core.db.StepDay
import com.example.wallko.core.db.Walk
import com.example.wallko.core.db.WalkPoint
import com.example.wallko.core.db.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ExportMapperTest {
    private val t0 = Instant.parse("2026-10-10T07:00:00Z").toEpochMilli()
    private val exportedAt = Instant.parse("2026-10-11T12:34:56.789Z")

    private val species = PlantSpecies(
        index = 7, latin = "Bellis perennis", common = "Daisy", family = "Asteraceae", type = "herb",
        description = null, observations = 1_000, rarity = Rarity.COMMON, thirst = 2, sun = 3,
        growth = GrowthSpeed.FAST, bloom = listOf("spring"), badges = emptyList(), sources = emptyMap(), dbVersion = 1,
    )
    private val plant = OwnedPlant(
        id = 3, speciesIndex = 7, foundAt = t0, foundLat = 51.5, foundLng = -0.12, placeName = "Park",
        photoPath = "/data/photos/3.jpg", spritePath = null, styleId = "WATERCOLOUR", plantedAt = t0,
        lastDrankAt = null, state = PlantState.WILTING, daysUnwatered = 3, growthDays = 2, gardenX = 1, gardenY = 2,
    )

    private fun snapshot() = ExportSnapshot(
        plants = listOf(OwnedPlantWithSpecies(plant, species)),
        wallet = Wallet(waterDrops = 40, elixir = 1, tankCapacity = 100, elixirProgressM = 120.5, lastTickAt = t0),
        ledger = listOf(LedgerEntry(id = 1, at = t0, kind = LedgerKind.STARTER, drops = 20, note = "hello")),
        walks = listOf(
            Walk(id = 1, startedAt = t0, endedAt = t0 + 600_000, distanceM = 812.0, steps = 1_000, dawn = true),
            Walk(id = 2, startedAt = t0 + 3_600_000, endedAt = null),
        ),
        walkPoints = listOf(
            WalkPoint(id = 11, walkId = 1, at = t0 + 2_000, lat = 1.0, lng = 2.0),
            WalkPoint(id = 10, walkId = 1, at = t0 + 1_000, lat = 3.0, lng = 4.0),
        ),
        exploredCells = listOf(ExploredCell(cellId = 99L, firstVisitedAt = t0)),
        stepDays = listOf(StepDay(epochDay = LocalDate.of(2026, 10, 10).toEpochDay(), steps = 4_321, dropsCredited = 4)),
    )

    @Test fun mapsEveryTable() {
        val e = ExportMapper.toExport(snapshot(), exportedAt, "1.0")
        assertEquals(WallkoExport.CURRENT_SCHEMA_VERSION, e.schemaVersion)
        assertEquals("2026-10-11T12:34:56.789Z", e.exportedAt)
        assertEquals("1.0", e.appVersion)

        val p = e.plants.single()
        assertEquals("Bellis perennis", p.latin)
        assertEquals("Daisy", p.common)
        assertEquals("COMMON", p.rarity)
        assertEquals("WILTING", p.state)
        assertEquals("2026-10-10T07:00:00Z", p.foundAt)
        assertNull(p.lastDrankAt)
        assertEquals("/data/photos/3.jpg", p.photoPath)

        assertEquals(40, e.wallet!!.waterDrops)
        assertEquals("STARTER", e.ledger.single().kind)
        assertEquals("hello", e.ledger.single().note)
        assertEquals(99L, e.exploredCells.single().cellId)
        assertEquals("2026-10-10", e.stepDays.single().date)
        assertEquals(4_321, e.stepDays.single().steps)
    }

    @Test fun walkPointsAreGroupedAndSorted() {
        val e = ExportMapper.toExport(snapshot(), exportedAt, null)
        val first = e.walks.first { it.id == 1L }
        assertEquals(listOf(3.0, 1.0), first.points.map { it.lat })
        assertEquals("2026-10-10T07:00:01Z", first.points.first().at)
        assertTrue(first.dawn)
        val second = e.walks.first { it.id == 2L }
        assertTrue(second.points.isEmpty())
        assertNull(second.endedAt)
    }

    @Test fun blankVersionAndMissingWalletBecomeNull() {
        val e = ExportMapper.toExport(snapshot().copy(wallet = null), exportedAt, " ")
        assertNull(e.appVersion)
        assertNull(e.wallet)
    }

    @Test fun jsonIsPrettyAndRoundTrips() {
        val e = ExportMapper.toExport(snapshot(), exportedAt, "1.0")
        val text = ExportMapper.encode(e)
        assertTrue(text.contains("\n    \"schemaVersion\": 1"))
        assertTrue(text.contains("\"spritePath\": null"))
        assertTrue(text.contains("\"note\":"))
        assertFalse(text.contains("\"photo\":"))
        assertEquals(e, ExportMapper.decode(text))
    }

    @Test fun emptyDatabaseStillExports() {
        val empty = ExportSnapshot(emptyList(), null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val e = ExportMapper.toExport(empty, exportedAt, "1.0")
        assertTrue(e.plants.isEmpty() && e.walks.isEmpty())
        assertEquals(e, ExportMapper.decode(ExportMapper.encode(e)))
    }

    @Test fun fileName() {
        assertEquals("wallko-export-2026-10-10.json", ExportMapper.fileName(LocalDate.of(2026, 10, 10)))
    }
}
