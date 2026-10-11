package com.example.wallko.data.plants

/**
 * Classifier output index → species index.
 *
 * The AIY plants model has 2,102 outputs. Outputs 0..2100 are species, and the build
 * pipeline stores each one with `PlantSpecies.index` == model output index (identity).
 * Output 2101 is `__background__` ("N/A" in the English labels). It is not a species
 * and maps to null. The tooling finds it by name, and it happens to be the last label.
 */
object LabelMapping {
    const val MODEL_OUTPUT_COUNT = 2102
    const val BACKGROUND_OUTPUT_INDEX = 2101
    const val SPECIES_COUNT = 2101

    fun labelIndexToSpeciesIndex(outputIndex: Int): Int? =
        if (outputIndex in 0 until MODEL_OUTPUT_COUNT && outputIndex != BACKGROUND_OUTPUT_INDEX) outputIndex else null
}
