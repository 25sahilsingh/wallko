package com.example.wallko.ui.card

import java.time.Instant
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Which card stats are estimates (shown with a "~"). */
data class EstimatedFields(
    val thirst: Boolean,
    val sun: Boolean,
    val growth: Boolean,
    val bloom: Boolean,
)

/** Bloom season for display. */
sealed interface BloomText {
    data object Unknown : BloomText
    data object AllYear : BloomText
    /** e.g. "Mar–May, Sep". */
    data class Months(val text: String) : BloomText
}

/** "Found on <date>[ at <place>]" parts; [place] is null when unknown. */
data class FoundOn(val date: String, val place: String?)

/** Pure card formatting (no Android types; JVM-tested). */
object CardFormat {

    /** Provenance tags in the species `sources` map that mean "not real data". */
    private val ESTIMATE_TAGS = setOf("estimated", "default")

    /**
     * True if [field] is an estimate. A field missing from [sources] also counts: the build script
     * only omits a source when the value was filled in by a default.
     */
    fun isEstimated(sources: Map<String, String>, field: String): Boolean {
        val tag = sources[field]?.trim()?.lowercase(Locale.ROOT) ?: return true
        return tag.isEmpty() || tag in ESTIMATE_TAGS
    }

    fun estimatedFields(sources: Map<String, String>): EstimatedFields = EstimatedFields(
        thirst = isEstimated(sources, "thirst"),
        sun = isEstimated(sources, "sun"),
        growth = isEstimated(sources, "growth"),
        bloom = isEstimated(sources, "bloom"),
    )

    /** Parses `java.time.Month` names (case-insensitive); unknown names are dropped. */
    fun parseMonths(names: List<String>): Set<Month> =
        names.mapNotNullTo(HashSet<Month>()) { n -> Month.entries.firstOrNull { it.name.equals(n.trim(), ignoreCase = true) } }

    /**
     * Bloom months as compact runs, wrapping over the new year: [MARCH, APRIL, MAY, SEPTEMBER] → "Mar–May, Sep";
     * [NOVEMBER, DECEMBER, JANUARY] → "Nov–Jan".
     */
    fun bloomText(names: List<String>, locale: Locale = Locale.getDefault()): BloomText {
        val months = parseMonths(names)
        if (months.isEmpty()) return BloomText.Unknown
        if (months.size == 12) return BloomText.AllYear

        val present = BooleanArray(12) { Month.of(it + 1) in months }
        // Start just after a gap so a Dec→Jan run stays in one piece.
        val start = (0 until 12).first { !present[(it + 11) % 12] && present[it] }
        val runs = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < 12) {
            val m = (start + i) % 12
            if (present[m]) {
                var len = 1
                while (i + len < 12 && present[(start + i + len) % 12]) len++
                runs += m to (m + len - 1) % 12
                i += len
            } else i++
        }
        fun short(idx: Int) = Month.of(idx + 1).getDisplayName(TextStyle.SHORT, locale)
        val text = runs.joinToString(", ") { (a, b) -> if (a == b) short(a) else "${short(a)}–${short(b)}" }
        return BloomText.Months(text)
    }

    /** "drought-tolerant" → "Drought tolerant". */
    fun badgeLabel(badge: String): String =
        badge.trim().replace('-', ' ').replace('_', ' ').lowercase(Locale.ROOT)
            .replaceFirstChar { it.titlecase(Locale.ROOT) }

    fun formatDate(epochMillis: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun foundOn(foundAt: Long, placeName: String?, zone: ZoneId, locale: Locale = Locale.getDefault()): FoundOn =
        FoundOn(formatDate(foundAt, zone, locale), placeName?.trim()?.takeIf { it.isNotEmpty() })

    /** 0..1 sprite size fraction for the hero from watered growth days. */
    fun growthFraction(growthDays: Int, targetDays: Int): Float =
        if (targetDays <= 0) 1f else (growthDays.toFloat() / targetDays).coerceIn(0f, 1f)
}
