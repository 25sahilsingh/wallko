package com.example.wallko.core.db

import androidx.room.Embedded
import androidx.room.Relation

/** An owned plant with its bundled species row joined. */
data class OwnedPlantWithSpecies(
    @Embedded val plant: OwnedPlant,
    @Relation(parentColumn = "speciesIndex", entityColumn = "index")
    val species: PlantSpecies,
)

/** Just a species' index and Latin name (BioCLIP row → species lookup). */
data class SpeciesLatinName(val index: Int, val latin: String)

/** A walk with its GPS points (unordered from Room; sort by `at` before drawing). */
data class WalkWithPoints(
    @Embedded val walk: Walk,
    @Relation(parentColumn = "id", entityColumn = "walkId")
    val points: List<WalkPoint>,
)
