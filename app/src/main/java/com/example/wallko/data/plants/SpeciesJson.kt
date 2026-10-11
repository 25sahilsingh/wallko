package com.example.wallko.data.plants

import com.example.wallko.core.config.GameRules
import com.example.wallko.core.db.GrowthSpeed
import com.example.wallko.core.db.PlantSpecies
import com.example.wallko.core.db.Rarity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bundled `assets/plants/species.json`, written by `tools/plantdb/build.py`.
 * Row keys match [PlantSpecies]; null fields are omitted in the file, and the per-row
 * `dbVersion` normally comes from the top-level value.
 */
@Serializable
data class SpeciesFileJson(
    val dbVersion: Int,
    val species: List<SpeciesJson>,
)

@Serializable
data class SpeciesJson(
    /** Classifier output index (background label excluded). */
    val index: Int,
    val latin: String,
    val common: String? = null,
    val family: String? = null,
    val type: String? = null,
    val description: String? = null,
    val observations: Long = 0,
    val rarity: String = "COMMON",
    val thirst: Int = DEFAULT_THIRST,
    val sun: Int = DEFAULT_SUN,
    val growth: String = "MEDIUM",
    /** `java.time.Month` names, e.g. "MARCH". Empty = unknown or never flowers. */
    val bloom: List<String> = emptyList(),
    val badges: List<String> = emptyList(),
    /** field name → provenance (perenual|inaturalist|gbif|wikidata|estimated|default|model). */
    val sources: Map<String, String> = emptyMap(),
    val dbVersion: Int? = null,
) {
    companion object {
        const val DEFAULT_THIRST = 3
        const val DEFAULT_SUN = 2
        val SUN_RANGE: IntRange = 1..3
    }
}

/** Pure parsing and mapping, kept free of Android types so it is unit-testable. */
object SpeciesJsonMapper {
    const val ASSET_PATH = "plants/species.json"

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }
    private val versionRegex = Regex("\"dbVersion\"\\s*:\\s*(\\d+)")

    fun parse(text: String): SpeciesFileJson = json.decodeFromString(SpeciesFileJson.serializer(), text)

    /**
     * Cheap read of `dbVersion` from the start of the file (build.py writes it first),
     * so an up-to-date install doesn't have to parse 2,101 rows. Null if not found.
     */
    fun peekDbVersion(head: String): Int? = versionRegex.find(head)?.groupValues?.get(1)?.toIntOrNull()

    fun toEntities(file: SpeciesFileJson): List<PlantSpecies> = file.species.map { it.toEntity(file.dbVersion) }
}

/** Maps a JSON row to the Room entity, clamping ranges and tolerating unknown enum names. */
fun SpeciesJson.toEntity(fileDbVersion: Int): PlantSpecies = PlantSpecies(
    index = index,
    latin = latin,
    common = common?.takeIf { it.isNotBlank() },
    family = family?.takeIf { it.isNotBlank() },
    type = type?.takeIf { it.isNotBlank() },
    description = description?.takeIf { it.isNotBlank() },
    observations = observations.coerceAtLeast(0),
    rarity = enumOrDefault(rarity, Rarity.COMMON),
    thirst = thirst.coerceIn(GameRules.Drinking.THIRST_RANGE),
    sun = sun.coerceIn(SpeciesJson.SUN_RANGE),
    growth = enumOrDefault(growth, GrowthSpeed.MEDIUM),
    bloom = bloom,
    badges = badges,
    sources = sources,
    dbVersion = dbVersion ?: fileDbVersion,
)

private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: default
