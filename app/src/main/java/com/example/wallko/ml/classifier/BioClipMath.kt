package com.example.wallko.ml.classifier

import com.example.wallko.core.config.GameRules
import kotlin.math.exp

/**
 * BioCLIP output for one photo: a probability per text-table row, the species index each row maps
 * to (-1 = never a guess), and the row labels.
 */
class BioClipScores(
    val probs: FloatArray,
    val rowSpecies: IntArray,
    val labels: List<SpeciesLabel>,
)

/** Pure helpers for BioCLIP (no Android), unit-tested on the JVM. See docs/ml-research.md. */
object BioClipMath {

    /** CLIP normalisation (OpenAI CLIP mean/std, which BioCLIP was trained with), RGB order. */
    val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
    val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

    /**
     * Rows in the text table whose name is an older synonym of another row's species. Both rows were
     * kept in the table, so their scores are summed under the accepted name's species.
     */
    val LATIN_SYNONYMS = mapOf("Sansevieria trifasciata" to "Dracaena trifasciata")

    /** "Epipremnum aureum|Money plant (Golden pothos)" → label; a blank or "N/A" common part → no common name. */
    fun parseLabelLine(line: String): SpeciesLabel {
        val latin = line.substringBefore('|').trim()
        val common = line.substringAfter('|', "").trim()
            .takeUnless { it.isEmpty() || it.equals("n/a", ignoreCase = true) || it.equals(latin, ignoreCase = true) }
        return SpeciesLabel(latin, common)
    }

    /** Size of a [width]×[height] image scaled so its short side is [target]; long side truncated like torchvision's Resize. */
    fun shortSideTo(width: Int, height: Int, target: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && target > 0)
        val k = target.toFloat() / minOf(width, height)
        return maxOf(target, (width * k).toInt()) to maxOf(target, (height * k).toInt())
    }

    /** ARGB pixels of a [size]² crop → NCHW float input (`/255`, then CLIP mean/std per channel). */
    fun normalize(pixels: IntArray, size: Int, out: FloatArray = FloatArray(3 * size * size)): FloatArray {
        val plane = size * size
        require(pixels.size >= plane && out.size >= 3 * plane)
        for (i in 0 until plane) {
            val p = pixels[i]
            out[i] = ((p shr 16 and 0xFF) / 255f - MEAN[0]) / STD[0]
            out[plane + i] = ((p shr 8 and 0xFF) / 255f - MEAN[1]) / STD[1]
            out[2 * plane + i] = ((p and 0xFF) / 255f - MEAN[2]) / STD[2]
        }
        return out
    }

    /**
     * `softmax(scale × table·embedding)` over every row of [table] (rows × [embedding].size, row-major).
     * Both sides are already L2-normalised, so the dot product is the cosine similarity.
     */
    fun rowProbabilities(embedding: FloatArray, table: FloatArray, scale: Float = GameRules.Ml.BIOCLIP_LOGIT_SCALE): FloatArray {
        val dim = embedding.size
        require(dim > 0 && table.size % dim == 0) { "Table size ${table.size} is not a multiple of $dim" }
        val logits = FloatArray(table.size / dim) { r ->
            var s = 0f
            val off = r * dim
            for (j in 0 until dim) s += embedding[j] * table[off + j]
            scale * s
        }
        return softmax(logits)
    }

    /** Numerically stable softmax (subtracts the max first). */
    fun softmax(logits: FloatArray): FloatArray {
        if (logits.isEmpty()) return logits
        val m = logits.max()
        val e = FloatArray(logits.size) { exp(logits[it] - m) }
        val sum = e.sum()
        return FloatArray(e.size) { e[it] / sum }
    }

    /**
     * Species index for each text-table row, -1 if the row can never be a guess.
     *
     * The first [aiyRowCount] rows are AIY's label lines in AIY order, so they map to the same species
     * index (identity), except [aiyBackgroundIndex]. Later rows (houseplants) are matched by Latin name
     * (case-insensitive, via [LATIN_SYNONYMS]) against [speciesByLatin], the catalogue; a houseplant that
     * AIY already has therefore lands on the AIY species. Rows with no catalogue entry map to -1.
     */
    fun rowSpecies(
        labels: List<SpeciesLabel>,
        aiyRowCount: Int,
        aiyBackgroundIndex: Int,
        speciesByLatin: Map<String, Int>,
    ): IntArray {
        val byKey = speciesByLatin.entries.associate { latinKey(it.key) to it.value }
        return IntArray(labels.size) { r ->
            when {
                r == aiyBackgroundIndex -> -1
                r < aiyRowCount -> r
                else -> {
                    val latin = labels[r].latin
                    byKey[latinKey(LATIN_SYNONYMS[latin] ?: latin)] ?: -1
                }
            }
        }
    }

    /**
     * True if the first rows of the BioCLIP table are AIY's labels in AIY order (background row ignored),
     * which [rowSpecies]'s identity mapping relies on. A table built from another label file fails this.
     */
    fun aiyRowsMatch(labels: List<SpeciesLabel>, aiyLabels: List<SpeciesLabel>, aiyBackgroundIndex: Int): Boolean =
        labels.size >= aiyLabels.size && aiyLabels.indices.all { r ->
            r == aiyBackgroundIndex || latinKey(labels[r].latin) == latinKey(aiyLabels[r].latin)
        }

    private fun latinKey(latin: String) = latin.trim().lowercase()

    /**
     * Top-[k] species from BioCLIP row probabilities. Rows that map to the same species (a houseplant
     * that AIY also lists, or a synonym) are summed first, because their identical or near-identical
     * prompts split the probability between them. Then the usual [ClassifierMath.filterGuesses] rule
     * (min score + confidence). The name comes from the species' first row.
     */
    fun buildClassification(scores: BioClipScores, k: Int = 3): Classification {
        val perSpecies = LinkedHashMap<Int, Float>()
        val firstRow = HashMap<Int, Int>()
        for (r in scores.probs.indices) {
            val s = scores.rowSpecies.getOrElse(r) { -1 }
            if (s < 0) continue
            perSpecies[s] = (perSpecies[s] ?: 0f) + scores.probs[r]
            firstRow.putIfAbsent(s, r)
        }
        val species = perSpecies.keys.toIntArray()
        val summed = FloatArray(species.size) { perSpecies.getValue(species[it]) }
        val ranked = ClassifierMath.topK(summed, k).map { i ->
            val label = scores.labels.getOrNull(firstRow.getValue(species[i]))
            Guess(
                outputIndex = species[i],
                latin = label?.latin ?: "Unknown species #${species[i]}",
                common = label?.common,
                score = summed[i],
            )
        }
        return ClassifierMath.filterGuesses(ranked)
    }

    /**
     * Hybrid rule: AIY decides whether the photo is a plant at all (its background row); BioCLIP, when
     * available, names it. [bioClip] is only called for plants; if it returns null (model files missing
     * or it failed) the AIY guesses are used unchanged.
     */
    suspend fun hybridClassification(
        aiyScores: FloatArray,
        aiyLabels: List<SpeciesLabel>,
        aiyBackgroundIndex: Int,
        bioClip: suspend () -> BioClipScores?,
    ): Classification {
        if (ClassifierMath.isBackground(aiyScores, aiyBackgroundIndex)) return Classification.NONE
        val bio = bioClip() ?: return ClassifierMath.buildClassification(aiyScores, aiyLabels, aiyBackgroundIndex)
        return buildClassification(bio)
    }
}
