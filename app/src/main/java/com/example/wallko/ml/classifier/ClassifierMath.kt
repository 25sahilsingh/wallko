package com.example.wallko.ml.classifier

import com.example.wallko.core.config.GameRules

/** A label row: scientific name plus optional common name. */
data class SpeciesLabel(val latin: String, val common: String?)

/** Pure helpers for the classifier (no Android), unit-tested on the JVM. */
object ClassifierMath {

    private val TRAILING_PARENS = Regex("""^(.*?)\s*\(([^()]*)\)\s*$""")
    private val NO_COMMON_NAME = setOf("", "n/a", "na", "none", "-")

    /** "Betula lenta (Sweet birch)" → ("Betula lenta", "Sweet birch"); "Betula lenta" → ("Betula lenta", null). */
    fun parseEnglishLabel(line: String): Pair<String, String?> {
        val trimmed = line.trim()
        val m = TRAILING_PARENS.matchEntire(trimmed) ?: return trimmed to null
        val latin = m.groupValues[1].trim()
        val common = m.groupValues[2].trim().takeUnless { it.lowercase() in NO_COMMON_NAME }
        return (latin.ifEmpty { trimmed }) to common
    }

    /**
     * Merges a scientific-name row with its English row. The scientific file wins for the latin name;
     * the English row only contributes the common name. "N/A" English rows give no common name.
     */
    fun mergeLabel(latinLine: String?, englishLine: String?): SpeciesLabel {
        val (enLatin, common) = englishLine?.let(::parseEnglishLabel) ?: ("" to null)
        val latin = latinLine?.trim().takeUnless { it.isNullOrEmpty() } ?: enLatin
        return SpeciesLabel(latin, common?.takeUnless { it.equals(latin, ignoreCase = true) })
    }

    fun mergeLabels(latin: List<String>, english: List<String>?): List<SpeciesLabel> =
        List(maxOf(latin.size, english?.size ?: 0)) { i -> mergeLabel(latin.getOrNull(i), english?.getOrNull(i)) }

    /**
     * Splits file text into label rows. Handles a missing trailing newline and CRLF, and drops only
     * trailing blank lines so indices stay aligned with model outputs.
     */
    fun splitLabelLines(text: String): List<String> =
        text.removePrefix("﻿").split("\n").map { it.removeSuffix("\r") }.dropLastWhile { it.isBlank() }

    /** Index of the background row ("__background__", "background"; case-insensitive), or -1. */
    fun findBackgroundIndex(latinLabels: List<String>): Int =
        latinLabels.indexOfFirst { it.trim().trim('_').equals("background", ignoreCase = true) }

    /** uint8 → float: `(q - zeroPoint) * scale`, or q / 255 if the model has no quantisation params. */
    fun dequantize(raw: ByteArray, scale: Float, zeroPoint: Int): FloatArray {
        val s = if (scale > 0f) scale else 1f / 255f
        val z = if (scale > 0f) zeroPoint else 0
        return FloatArray(raw.size) { ((raw[it].toInt() and 0xFF) - z) * s }
    }

    /** ARGB pixels → packed RGB bytes (uint8), row-major, as the AIY input expects. */
    fun packRgb(pixels: IntArray, out: ByteArray = ByteArray(pixels.size * 3)): ByteArray {
        require(out.size >= pixels.size * 3)
        var o = 0
        for (p in pixels) {
            out[o++] = (p shr 16 and 0xFF).toByte()
            out[o++] = (p shr 8 and 0xFF).toByte()
            out[o++] = (p and 0xFF).toByte()
        }
        return out
    }

    /** Indices of the [k] highest scores, best first (ties → lower index), skipping [excludeIndex]. */
    fun topK(scores: FloatArray, k: Int, excludeIndex: Int = -1): List<Int> {
        if (k <= 0) return emptyList()
        val best = ArrayList<Int>(k + 1)
        for (i in scores.indices) {
            if (i == excludeIndex) continue
            val s = scores[i]
            if (best.size == k && s <= scores[best.last()]) continue
            var pos = best.size
            while (pos > 0 && scores[best[pos - 1]] < s) pos--
            best.add(pos, i)
            if (best.size > k) best.removeAt(best.size - 1)
        }
        return best
    }

    /** All (outputIndex, score) pairs except the background row. */
    fun withoutBackground(scores: FloatArray, backgroundIndex: Int): List<Pair<Int, Float>> =
        scores.indices.filter { it != backgroundIndex }.map { it to scores[it] }

    /** True when the background ("not a plant") row scores at or above [GameRules.Ml.MAX_BACKGROUND_SCORE]. */
    fun isBackground(scores: FloatArray, backgroundIndex: Int): Boolean =
        (scores.getOrNull(backgroundIndex) ?: 0f) >= GameRules.Ml.MAX_BACKGROUND_SCORE

    /**
     * Top-3 (background dropped) + confidence rule from [GameRules.isConfident].
     * Only guesses scoring at least [GameRules.Ml.MIN_GUESS_SCORE] are kept, and none at all when the
     * background row scores high (not a plant), so random photos and wild guesses can't be collected.
     * The confidence rule still uses the unfiltered runner-up score for its margin.
     */
    fun buildClassification(scores: FloatArray, labels: List<SpeciesLabel>, backgroundIndex: Int, k: Int = 3): Classification {
        if (isBackground(scores, backgroundIndex)) return Classification.NONE
        val ranked = topK(scores, k, backgroundIndex).map { i ->
            val label = labels.getOrNull(i)
            Guess(
                outputIndex = i,
                latin = label?.latin ?: "Unknown species #$i",
                common = label?.common,
                score = scores[i],
            )
        }
        return filterGuesses(ranked)
    }

    /**
     * Keeps the [ranked] (best-first, unfiltered) guesses scoring at least [GameRules.Ml.MIN_GUESS_SCORE].
     * [GameRules.isConfident] uses the unfiltered runner-up for its margin.
     */
    fun filterGuesses(ranked: List<Guess>): Classification {
        val top = ranked.filter { it.score >= GameRules.Ml.MIN_GUESS_SCORE }
        val top1 = top.getOrNull(0)?.score ?: 0f
        val top2 = ranked.getOrNull(1)?.score ?: 0f
        return Classification(top, confident = top.isNotEmpty() && GameRules.isConfident(top1, top2))
    }
}
