package com.example.wallko.data.plants

/**
 * Classifier output index → species index.
 *
 * The AIY plants model has 2,102 outputs. Outputs 0..2100 are species, and the build
 * pipeline stores each one with `PlantSpecies.index` == model output index (identity).
 * Output 2101 is `__background__` ("N/A" in the English labels). It is not a species
 * and maps to null. The tooling finds it by name, and it happens to be the last label.
 *
 * Species that AIY doesn't know (houseplants named by BioCLIP) follow from [FIRST_EXTRA_INDEX] on,
 * leaving 2101 unused. BioCLIP guesses already carry a species index, so they map to themselves too.
 */
object LabelMapping {
    const val MODEL_OUTPUT_COUNT = 2102
    const val BACKGROUND_OUTPUT_INDEX = 2101
    /** AIY species (0..2100). */
    const val SPECIES_COUNT = 2101
    /** First species index that isn't an AIY output (`tools/plantdb/extra_species.json`). */
    const val FIRST_EXTRA_INDEX = 2102

    fun labelIndexToSpeciesIndex(outputIndex: Int): Int? =
        if (outputIndex >= 0 && outputIndex != BACKGROUND_OUTPUT_INDEX) outputIndex else null
}
