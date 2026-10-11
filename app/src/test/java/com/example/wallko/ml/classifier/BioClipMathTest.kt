package com.example.wallko.ml.classifier

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BioClipMathTest {

    // A tiny AIY: 3 species + background at index 3.
    private val aiyLabels = listOf(
        SpeciesLabel("Betula lenta", "Sweet birch"),
        SpeciesLabel("Urtica dioica", "Stinging nettle"),
        SpeciesLabel("Cocos nucifera", "Coconut palm"),
        SpeciesLabel("__background__", null),
    )

    // BioCLIP table: AIY's rows (background as "N/A"), then houseplants.
    private val bioLabels = listOf(
        SpeciesLabel("Betula lenta", "Sweet birch"),
        SpeciesLabel("Urtica dioica", "Stinging nettle"),
        SpeciesLabel("Cocos nucifera", "Coconut palm"),
        SpeciesLabel("N/A", null),
        SpeciesLabel("Dracaena sanderiana", "Lucky bamboo"),
        SpeciesLabel("Cocos nucifera", "Coconut palm"), // also an AIY row
        SpeciesLabel("Sansevieria trifasciata", "Snake plant"), // synonym of the next row
        SpeciesLabel("Dracaena trifasciata", "Snake plant"),
        SpeciesLabel("Nothus ignotus", "Not in catalogue"),
    )

    private val catalogue = mapOf(
        "Betula lenta" to 0, "Urtica dioica" to 1, "Cocos nucifera" to 2,
        "Dracaena sanderiana" to 2102, "Dracaena trifasciata" to 2103,
    )

    private val rows = BioClipMath.rowSpecies(bioLabels, aiyRowCount = 4, aiyBackgroundIndex = 3, speciesByLatin = catalogue)

    private fun scores(vararg p: Float) = BioClipScores(p, rows, bioLabels)

    @Test
    fun parsesLabelLines() {
        assertEquals(SpeciesLabel("Epipremnum aureum", "Money plant (Golden pothos)"),
            BioClipMath.parseLabelLine("Epipremnum aureum|Money plant (Golden pothos)"))
        assertEquals(SpeciesLabel("N/A", null), BioClipMath.parseLabelLine("N/A|"))
        assertEquals(SpeciesLabel("Aloe vera", null), BioClipMath.parseLabelLine("Aloe vera|Aloe vera"))
        assertEquals(SpeciesLabel("Foo bar", null), BioClipMath.parseLabelLine("Foo bar"))
    }

    @Test
    fun rowsMapToSpecies() {
        // AIY rows: identity, background never a guess; extras by latin (synonym → accepted name); unknown → -1.
        assertArrayEquals(intArrayOf(0, 1, 2, -1, 2102, 2, 2103, 2103, -1), rows)
    }

    @Test
    fun aiyRowsMustMatchAiyLabels() {
        assertTrue(BioClipMath.aiyRowsMatch(bioLabels, aiyLabels, 3))
        val shuffled = listOf(bioLabels[1], bioLabels[0]) + bioLabels.drop(2)
        assertFalse(BioClipMath.aiyRowsMatch(shuffled, aiyLabels, 3))
        assertFalse(BioClipMath.aiyRowsMatch(bioLabels.take(2), aiyLabels, 3))
    }

    @Test
    fun softmaxIsStableAndSumsToOne() {
        val p = BioClipMath.softmax(floatArrayOf(1000f, 1000f, 0f))
        assertEquals(0.5f, p[0], 1e-6f)
        assertEquals(0.5f, p[1], 1e-6f)
        assertEquals(0f, p[2], 1e-6f)
        assertEquals(1f, BioClipMath.softmax(floatArrayOf(0.1f, -2f, 3f)).sum(), 1e-5f)
    }

    @Test
    fun rowProbabilitiesUseScaledDotProducts() {
        val embedding = floatArrayOf(1f, 0f)
        val table = floatArrayOf(1f, 0f, 0.99f, 0.141f, 0f, 1f)
        val p = BioClipMath.rowProbabilities(embedding, table, scale = 100f)
        // logits 100, 99, 0 → e^0 : e^-1 : ~0
        assertEquals(1f / (1f + kotlin.math.exp(-1f)), p[0], 1e-5f)
        assertEquals(0f, p[2], 1e-6f)
    }

    @Test
    fun shortSideResize() {
        assertEquals(298 to 224, BioClipMath.shortSideTo(1600, 1200, 224))
        assertEquals(224 to 298, BioClipMath.shortSideTo(1200, 1600, 224))
        assertEquals(224 to 224, BioClipMath.shortSideTo(100, 100, 224))
    }

    @Test
    fun normalizeIsNchwWithClipMeanStd() {
        val out = BioClipMath.normalize(intArrayOf(0xFFFF0000.toInt(), 0xFF000000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()), 2)
        assertEquals((1f - BioClipMath.MEAN[0]) / BioClipMath.STD[0], out[0], 1e-6f) // R plane, pixel 0
        assertEquals(-BioClipMath.MEAN[0] / BioClipMath.STD[0], out[1], 1e-6f)
        assertEquals((1f - BioClipMath.MEAN[1]) / BioClipMath.STD[1], out[4 + 2], 1e-6f) // G plane, pixel 2
        assertEquals((1f - BioClipMath.MEAN[2]) / BioClipMath.STD[2], out[8 + 3], 1e-6f) // B plane, pixel 3
    }

    @Test
    fun confidentHouseplantGetsItsSpeciesIndex() {
        val c = BioClipMath.buildClassification(scores(0.01f, 0.01f, 0f, 0f, 0.97f, 0f, 0f, 0f, 0.01f))
        assertEquals(2102, c.best?.outputIndex)
        assertEquals("Lucky bamboo", c.best?.common)
        assertTrue(c.confident)
    }

    @Test
    fun duplicateRowsAreSummed() {
        // Coconut is both an AIY row and a houseplant row; each gets half. Summed it is one confident guess.
        val c = BioClipMath.buildClassification(scores(0f, 0.05f, 0.45f, 0f, 0f, 0.45f, 0f, 0f, 0.05f))
        assertEquals(listOf(2), c.top3.map { it.outputIndex })
        assertEquals(0.90f, c.best!!.score, 1e-6f)
        assertTrue(c.confident)
        // Synonym rows are summed too, under the first row's name.
        val snake = BioClipMath.buildClassification(scores(0f, 0f, 0f, 0f, 0f, 0f, 0.3f, 0.4f, 0f))
        assertEquals(2103, snake.best?.outputIndex)
        assertEquals(0.7f, snake.best!!.score, 1e-6f)
    }

    @Test
    fun backgroundAndUnmappedRowsAreNeverGuesses() {
        val c = BioClipMath.buildClassification(scores(0f, 0f, 0f, 0.6f, 0f, 0f, 0f, 0f, 0.4f))
        assertTrue(c.top3.isEmpty())
        assertFalse(c.confident)
    }

    @Test
    fun weakBioClipGuessesAreFilteredLikeAiy() {
        // A room: BioCLIP spreads its score; nothing reaches MIN_GUESS_SCORE.
        val c = BioClipMath.buildClassification(scores(0.25f, 0.25f, 0.25f, 0f, 0.25f, 0f, 0f, 0f, 0f))
        assertTrue(c.top3.isEmpty())
        // Not confident when the margin is small, but both guesses are shown.
        val close = BioClipMath.buildClassification(scores(0.45f, 0.40f, 0f, 0f, 0.15f, 0f, 0f, 0f, 0f))
        assertEquals(listOf(0, 1), close.top3.map { it.outputIndex })
        assertFalse(close.confident)
    }

    @Test
    fun hybridSkipsBioClipForNonPlants() = runTest {
        var called = false
        val c = BioClipMath.hybridClassification(floatArrayOf(0.1f, 0f, 0f, 0.7f), aiyLabels, 3) {
            called = true
            scores(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f)
        }
        assertTrue(c.top3.isEmpty())
        assertFalse(called)
    }

    @Test
    fun hybridUsesBioClipNamesForPlants() = runTest {
        // AIY: lucky bamboo isn't in it, best guess 0.05 (would show nothing), background low.
        val c = BioClipMath.hybridClassification(floatArrayOf(0.05f, 0.04f, 0f, 0.1f), aiyLabels, 3) {
            scores(0f, 0f, 0f, 0f, 0.99f, 0f, 0f, 0f, 0.01f)
        }
        assertEquals(2102, c.best?.outputIndex)
        assertTrue(c.confident)
    }

    @Test
    fun hybridFallsBackToAiyWhenBioClipUnavailable() = runTest {
        val aiy = floatArrayOf(0.96f, 0.02f, 0f, 0.01f)
        val c = BioClipMath.hybridClassification(aiy, aiyLabels, 3) { null }
        assertEquals(ClassifierMath.buildClassification(aiy, aiyLabels, 3), c)
        assertEquals("Sweet birch", c.best?.common)
        assertNull(c.top3.getOrNull(1))
    }
}
