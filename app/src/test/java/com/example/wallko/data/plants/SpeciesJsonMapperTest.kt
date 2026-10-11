package com.example.wallko.data.plants

import com.example.wallko.core.db.GrowthSpeed
import com.example.wallko.core.db.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class SpeciesJsonMapperTest {

    private val sample = """
        {"dbVersion":2026101010,"species":[
         {"index":0,"latin":"Betula lenta","common":"Sweet birch","family":"Betulaceae","type":"tree",
          "description":"A birch whose twigs smell of wintergreen.","observations":18765,"rarity":"UNCOMMON",
          "thirst":3,"sun":3,"growth":"FAST","bloom":["APRIL","MAY"],"badges":["pollinator-friendly"],
          "sources":{"common":"model","thirst":"perenual"},"futureField":42},
         {"index":12,"latin":"Drosera spatulata"}
        ]}
    """.trimIndent()

    @Test
    fun mapsAllFields() {
        val file = SpeciesJsonMapper.parse(sample)
        val birch = SpeciesJsonMapper.toEntities(file)[0]
        assertEquals(0, birch.index)
        assertEquals("Betula lenta", birch.latin)
        assertEquals("Sweet birch", birch.common)
        assertEquals("Betulaceae", birch.family)
        assertEquals("tree", birch.type)
        assertEquals("A birch whose twigs smell of wintergreen.", birch.description)
        assertEquals(18765L, birch.observations)
        assertEquals(Rarity.UNCOMMON, birch.rarity)
        assertEquals(3, birch.thirst)
        assertEquals(3, birch.sun)
        assertEquals(GrowthSpeed.FAST, birch.growth)
        assertEquals(listOf("APRIL", "MAY"), birch.bloom)
        assertEquals(listOf("pollinator-friendly"), birch.badges)
        assertEquals(mapOf("common" to "model", "thirst" to "perenual"), birch.sources)
        assertEquals(2026101010, birch.dbVersion)
    }

    @Test
    fun omittedFieldsGetDefaults() {
        val sundew = SpeciesJsonMapper.toEntities(SpeciesJsonMapper.parse(sample))[1]
        assertNull(sundew.common)
        assertNull(sundew.family)
        assertNull(sundew.type)
        assertNull(sundew.description)
        assertEquals(0L, sundew.observations)
        assertEquals(Rarity.COMMON, sundew.rarity)
        assertEquals(SpeciesJson.DEFAULT_THIRST, sundew.thirst)
        assertEquals(SpeciesJson.DEFAULT_SUN, sundew.sun)
        assertEquals(GrowthSpeed.MEDIUM, sundew.growth)
        assertTrue(sundew.bloom.isEmpty() && sundew.badges.isEmpty() && sundew.sources.isEmpty())
        assertEquals(2026101010, sundew.dbVersion)
    }

    @Test
    fun clampsRangesAndToleratesBadEnums() {
        val row = SpeciesJson(
            index = 5, latin = "X y", common = " ", observations = -3, rarity = "legendary",
            thirst = 9, sun = 0, growth = "turbo", dbVersion = 7,
        ).toEntity(fileDbVersion = 1)
        assertNull(row.common)
        assertEquals(0L, row.observations)
        assertEquals(Rarity.LEGENDARY, row.rarity)
        assertEquals(5, row.thirst)
        assertEquals(1, row.sun)
        assertEquals(GrowthSpeed.MEDIUM, row.growth)
        assertEquals(7, row.dbVersion)
    }

    @Test
    fun peeksDbVersionFromFileHead() {
        assertEquals(2026101010, SpeciesJsonMapper.peekDbVersion("""{"dbVersion":2026101010,"species":[{"ind"""))
        assertNull(SpeciesJsonMapper.peekDbVersion("""{"species":["""))
    }

    @Test
    fun labelIndexMapsToSpeciesIndexExceptBackground() {
        assertEquals(0, LabelMapping.labelIndexToSpeciesIndex(0))
        assertEquals(2100, LabelMapping.labelIndexToSpeciesIndex(2100))
        assertNull(LabelMapping.labelIndexToSpeciesIndex(LabelMapping.BACKGROUND_OUTPUT_INDEX))
        assertNull(LabelMapping.labelIndexToSpeciesIndex(-1))
        // Houseplants (BioCLIP-only species) sit after the background index.
        assertEquals(LabelMapping.FIRST_EXTRA_INDEX, LabelMapping.labelIndexToSpeciesIndex(LabelMapping.FIRST_EXTRA_INDEX))
    }

    /** Validates the real bundled asset when tests run from the app module directory. */
    @Test
    fun bundledAssetHasAiyIdentityRowsThenExtras() {
        val asset = listOf("src/main/assets/plants/species.json", "app/src/main/assets/plants/species.json")
            .map(::File).firstOrNull { it.exists() }
        assumeTrue("species.json not found from working dir", asset != null)
        val file = SpeciesJsonMapper.parse(asset!!.readText())
        assertEquals(SpeciesJsonMapper.peekDbVersion(asset.readText().take(64)), file.dbVersion)
        val rows = SpeciesJsonMapper.toEntities(file)
        val aiy = rows.take(LabelMapping.SPECIES_COUNT)
        aiy.forEachIndexed { i, s ->
            assertEquals(i, s.index)
            assertEquals(i, LabelMapping.labelIndexToSpeciesIndex(i))
        }
        rows.drop(LabelMapping.SPECIES_COUNT).forEachIndexed { i, s ->
            assertEquals(LabelMapping.FIRST_EXTRA_INDEX + i, s.index)
            assertEquals(s.index, LabelMapping.labelIndexToSpeciesIndex(s.index))
        }
        assertTrue(rows.all { it.latin.isNotBlank() && it.latin != "__background__" })
        // BioCLIP rows are matched to species by latin name, so names must be unique.
        assertEquals(rows.size, rows.map { it.latin.lowercase() }.toSet().size)
    }
}
