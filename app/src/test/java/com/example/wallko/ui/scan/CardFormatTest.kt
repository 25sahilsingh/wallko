package com.example.wallko.ui.scan

import com.example.wallko.ui.card.BloomText
import com.example.wallko.ui.card.CardFormat
import com.example.wallko.ui.card.EstimatedFields
import com.example.wallko.ui.card.FoundOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Month
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class CardFormatTest {
    private val us = Locale.US

    @Test fun estimatedFromSourcesMap() {
        val sources = mapOf(
            "thirst" to "perenual",
            "sun" to "estimated",
            "growth" to "DEFAULT",
            "bloom" to "inaturalist",
        )
        assertEquals(EstimatedFields(thirst = false, sun = true, growth = true, bloom = false), CardFormat.estimatedFields(sources))
    }

    @Test fun missingOrBlankSourceCountsAsEstimated() {
        assertTrue(CardFormat.isEstimated(emptyMap(), "thirst"))
        assertTrue(CardFormat.isEstimated(mapOf("thirst" to " "), "thirst"))
        assertFalse(CardFormat.isEstimated(mapOf("thirst" to "perenual"), "thirst"))
    }

    @Test fun parseMonthsIgnoresJunk() {
        assertEquals(setOf(Month.MARCH, Month.APRIL), CardFormat.parseMonths(listOf("march", " APRIL ", "Smarch")))
    }

    @Test fun bloomTextRuns() {
        assertEquals(BloomText.Unknown, CardFormat.bloomText(emptyList(), us))
        assertEquals(BloomText.Unknown, CardFormat.bloomText(listOf("nope"), us))
        assertEquals(BloomText.AllYear, CardFormat.bloomText(Month.entries.map { it.name }, us))
        assertEquals(BloomText.Months("Jun"), CardFormat.bloomText(listOf("JUNE"), us))
        assertEquals(
            BloomText.Months("Mar–May, Sep"),
            CardFormat.bloomText(listOf("SEPTEMBER", "MAY", "MARCH", "APRIL"), us),
        )
    }

    @Test fun bloomTextWrapsOverNewYear() {
        assertEquals(BloomText.Months("Nov–Feb"), CardFormat.bloomText(listOf("JANUARY", "FEBRUARY", "NOVEMBER", "DECEMBER"), us))
        assertEquals(BloomText.Months("Jun, Dec–Jan"), CardFormat.bloomText(listOf("JANUARY", "JUNE", "DECEMBER"), us))
    }

    @Test fun badgeLabels() {
        assertEquals("Drought tolerant", CardFormat.badgeLabel("drought-tolerant"))
        assertEquals("Pollinator friendly", CardFormat.badgeLabel("POLLINATOR_FRIENDLY"))
        assertEquals("Carnivorous", CardFormat.badgeLabel("carnivorous"))
    }

    @Test fun foundOnUsesZoneAndTrimsPlace() {
        // 2026-10-10T23:30Z is already the 11th in Kolkata.
        val millis = 1_791_675_000_000L
        assertEquals(FoundOn("Oct 10, 2026", null), CardFormat.foundOn(millis, "  ", ZoneOffset.UTC, us))
        assertEquals(
            FoundOn("Oct 11, 2026", "Rishikesh"),
            CardFormat.foundOn(millis, " Rishikesh ", ZoneId.of("Asia/Kolkata"), us),
        )
    }

    @Test fun growthFractionClamps() {
        assertEquals(0f, CardFormat.growthFraction(0, 7), 0f)
        assertEquals(0.5f, CardFormat.growthFraction(7, 14), 1e-6f)
        assertEquals(1f, CardFormat.growthFraction(20, 3), 0f)
        assertEquals(1f, CardFormat.growthFraction(0, 0), 0f)
    }
}
