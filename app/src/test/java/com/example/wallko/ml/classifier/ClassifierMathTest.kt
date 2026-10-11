package com.example.wallko.ml.classifier

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierMathTest {

    @Test
    fun topKOrdersBestFirstAndBreaksTiesByIndex() {
        val scores = floatArrayOf(0.1f, 0.5f, 0.3f, 0.5f, 0.05f)
        assertEquals(listOf(1, 3, 2), ClassifierMath.topK(scores, 3))
    }

    @Test
    fun topKSkipsBackgroundEvenWhenItIsHighest() {
        val scores = floatArrayOf(0.2f, 0.1f, 0.3f, 0.9f) // last = background
        assertEquals(listOf(2, 0, 1), ClassifierMath.topK(scores, 3, excludeIndex = 3))
    }

    @Test
    fun topKHandlesFewerThanK() {
        assertEquals(listOf(0), ClassifierMath.topK(floatArrayOf(0.4f, 0.9f), 3, excludeIndex = 1))
        assertEquals(emptyList<Int>(), ClassifierMath.topK(floatArrayOf(0.4f), 0))
    }

    @Test
    fun withoutBackgroundDropsExactlyOne() {
        val scores = FloatArray(2102) { it / 2102f }
        val kept = ClassifierMath.withoutBackground(scores, 2101)
        assertEquals(2101, kept.size)
        assertTrue(kept.none { it.first == 2101 })
        assertEquals(0, kept.first().first)
    }

    @Test
    fun backgroundFoundByName() {
        assertEquals(2, ClassifierMath.findBackgroundIndex(listOf("Betula lenta", "Urtica dioica", "__background__")))
        assertEquals(0, ClassifierMath.findBackgroundIndex(listOf("background", "Betula lenta")))
        assertEquals(-1, ClassifierMath.findBackgroundIndex(listOf("Betula lenta")))
    }

    @Test
    fun parsesEnglishLabels() {
        assertEquals("Betula lenta" to "Sweet birch", ClassifierMath.parseEnglishLabel("Betula lenta (Sweet birch)"))
        assertEquals("Cynoglossum grande" to "Pacific Hound's Tongue",
            ClassifierMath.parseEnglishLabel("Cynoglossum grande (Pacific Hound's Tongue)"))
        assertEquals("Neomyrtus pedunculata" to null, ClassifierMath.parseEnglishLabel("Neomyrtus pedunculata"))
        assertEquals("Foo bar" to null, ClassifierMath.parseEnglishLabel("Foo bar ()"))
        assertEquals("N/A" to null, ClassifierMath.parseEnglishLabel("N/A"))
    }

    @Test
    fun mergePrefersLatinFileAndDropsNaCommon() {
        assertEquals(SpeciesLabel("Betula lenta", "Sweet birch"),
            ClassifierMath.mergeLabel("Betula lenta", "Betula lenta (Sweet birch)"))
        assertEquals(SpeciesLabel("__background__", null), ClassifierMath.mergeLabel("__background__", "N/A"))
        assertEquals(SpeciesLabel("Neomyrtus pedunculata", null),
            ClassifierMath.mergeLabel("Neomyrtus pedunculata", "Neomyrtus pedunculata"))
        assertEquals(SpeciesLabel("Betula lenta", "Sweet birch"), ClassifierMath.mergeLabel(null, "Betula lenta (Sweet birch)"))
    }

    @Test
    fun splitLabelLinesWithoutTrailingNewline() {
        assertEquals(listOf("a", "b", "c"), ClassifierMath.splitLabelLines("a\nb\nc"))
        assertEquals(listOf("a", "b", "c"), ClassifierMath.splitLabelLines("a\r\nb\r\nc\n"))
        assertEquals(listOf("a", "", "c"), ClassifierMath.splitLabelLines("a\n\nc"))
    }

    @Test
    fun dequantizeUsesParamsOrFallsBackTo255() {
        val raw = byteArrayOf(0, 255.toByte(), 51)
        assertArrayEquals(floatArrayOf(0f, 1f, 0.2f), ClassifierMath.dequantize(raw, 0f, 0), 1e-6f)
        assertArrayEquals(floatArrayOf(0f, 1f, 0.2f), ClassifierMath.dequantize(raw, 1f / 255f, 0), 1e-6f)
    }

    @Test
    fun packRgbDropsAlpha() {
        val bytes = ClassifierMath.packRgb(intArrayOf(0x80FF8001.toInt()))
        assertArrayEquals(byteArrayOf(255.toByte(), 128.toByte(), 1), bytes)
    }

    private val labels = listOf(
        SpeciesLabel("A a", "Aye"), SpeciesLabel("B b", null), SpeciesLabel("C c", "See"), SpeciesLabel("__background__", null),
    )

    @Test
    fun confidentWhenTop1HighAndMarginBig() {
        val c = ClassifierMath.buildClassification(floatArrayOf(0.50f, 0.15f, 0.10f, 0.20f), labels, backgroundIndex = 3)
        assertTrue(c.confident)
        // Background (index 3) is never a guess; 0.15 and 0.10 are below MIN_GUESS_SCORE so they're hidden.
        assertEquals(listOf(0), c.top3.map { it.outputIndex })
        assertEquals(Guess(0, "A a", "Aye", 0.50f), c.best)
    }

    @Test
    fun notConfidentWhenMarginSmallOrTop1Low() {
        assertFalse(ClassifierMath.buildClassification(floatArrayOf(0.40f, 0.35f, 0.1f, 0f), labels, 3).confident)
        assertFalse(ClassifierMath.buildClassification(floatArrayOf(0.30f, 0.05f, 0.01f, 0f), labels, 3).confident)
        // Exactly on the thresholds counts as confident.
        assertTrue(ClassifierMath.buildClassification(floatArrayOf(0.35f, 0.25f, 0.1f, 0f), labels, 3).confident)
    }

    @Test
    fun noGuessesWhenBackgroundScoresHigh() {
        // A curtain photo: background 0.73, species near zero.
        val c = ClassifierMath.buildClassification(floatArrayOf(0.01f, 0f, 0f, 0.73f), labels, 3)
        assertTrue(c.top3.isEmpty())
        assertFalse(c.confident)
        // Exactly on the background threshold also counts as not a plant.
        assertTrue(ClassifierMath.buildClassification(floatArrayOf(0.5f, 0.1f, 0f, 0.30f), labels, 3).top3.isEmpty())
    }

    @Test
    fun weakGuessesAreHidden() {
        // Tulsi (not in the model): best wrong guess 0.11 → nothing to pick.
        assertTrue(ClassifierMath.buildClassification(floatArrayOf(0.11f, 0.08f, 0.07f, 0f), labels, 3).top3.isEmpty())
        // Only guesses at or above 0.30 survive; the weak third one is dropped.
        val c = ClassifierMath.buildClassification(floatArrayOf(0.40f, 0.30f, 0.29f, 0f), labels, 3)
        assertEquals(listOf(0, 1), c.top3.map { it.outputIndex })
    }

    @Test
    fun unknownIndexGetsPlaceholderName() {
        val c = ClassifierMath.buildClassification(floatArrayOf(0f, 0f, 0f, 0f, 0.9f), labels, 3)
        assertEquals("Unknown species #4", c.best?.latin)
        assertNull(c.best?.common)
    }
}
