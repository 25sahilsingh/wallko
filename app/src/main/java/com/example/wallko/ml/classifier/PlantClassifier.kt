package com.example.wallko.ml.classifier

import android.graphics.Bitmap

/** Identifies the plant in a photo. Swappable (AIY + optional BioCLIP today, see [HybridPlantClassifier]). */
interface PlantClassifier {
    /** [bitmap] should be upright; any size/aspect (it is centre-cropped). */
    suspend fun classify(bitmap: Bitmap): Classification
}

/**
 * One candidate species.
 * @param outputIndex species index in the species JSON. For AIY it is the model output index (0..2100);
 *   BioCLIP rows are mapped to species indices before they become guesses.
 * @param score probability-like value in 0..1.
 */
data class Guess(
    val outputIndex: Int,
    val latin: String,
    val common: String?,
    val score: Float,
)

/** Top-3 guesses, best first, background class excluded. [confident] follows `GameRules.isConfident`. */
data class Classification(
    val top3: List<Guess>,
    val confident: Boolean,
) {
    val best: Guess? get() = top3.firstOrNull()

    companion object {
        /** No guesses: not a plant, or nothing scored high enough. */
        val NONE = Classification(emptyList(), confident = false)
    }
}
