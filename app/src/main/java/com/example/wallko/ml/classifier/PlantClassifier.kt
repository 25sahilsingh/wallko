package com.example.wallko.ml.classifier

import android.graphics.Bitmap

/** Identifies the plant in a photo. Swappable (AIY today, maybe PlantNet later). */
interface PlantClassifier {
    /** [bitmap] should be upright; any size/aspect (it is centre-cropped). */
    suspend fun classify(bitmap: Bitmap): Classification
}

/**
 * One candidate species.
 * @param outputIndex model output index; also the species index in the species JSON (0..2100 for AIY).
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
}
