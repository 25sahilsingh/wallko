package com.example.wallko.data.plants

import android.content.Context
import com.example.wallko.core.db.PlantSpecies
import com.example.wallko.core.db.PlantSpeciesDao
import com.example.wallko.core.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Read-only species catalogue, seeded from the bundled `assets/plants/species.json`. */
@Singleton
class PlantRepository @Inject constructor(
    private val dao: PlantSpeciesDao,
    private val settings: SettingsStore,
    @ApplicationContext private val context: Context,
) {
    private val io: CoroutineDispatcher = Dispatchers.IO

    private val seedMutex = Mutex()

    /**
     * Seeds or reseeds Room when the bundled dbVersion is newer than the stored one, or when
     * the table is empty. Upserts in place, so owned plants (FK on index) are untouched.
     * @return true if rows were written.
     */
    suspend fun ensureSeeded(): Boolean = seedMutex.withLock {
        withContext(io) {
            val stored = settings.speciesDbVersion.first()
            val head = context.assets.open(SpeciesJsonMapper.ASSET_PATH).use { s ->
                val buf = ByteArray(HEAD_BYTES)
                String(buf, 0, s.read(buf).coerceAtLeast(0), Charsets.UTF_8)
            }
            val peeked = SpeciesJsonMapper.peekDbVersion(head)
            if (peeked != null && peeked <= stored && dao.count() > 0) return@withContext false

            val text = context.assets.open(SpeciesJsonMapper.ASSET_PATH).bufferedReader().use { it.readText() }
            val file = SpeciesJsonMapper.parse(text)
            if (file.dbVersion <= stored && dao.count() > 0) return@withContext false
            dao.upsertAll(SpeciesJsonMapper.toEntities(file))
            settings.setSpeciesDbVersion(file.dbVersion)
            true
        }
    }

    fun observeAll(): Flow<List<PlantSpecies>> = dao.observeAll()

    fun observeByIndex(index: Int): Flow<PlantSpecies?> = dao.observe(index)

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun byIndex(index: Int): PlantSpecies? = dao.byIndex(index)

    /** Latin name → species index for every catalogue row (seeds first if needed). */
    suspend fun speciesIndexByLatin(): Map<String, Int> {
        ensureSeeded()
        return dao.latinNames().associate { it.latin to it.index }
    }

    /** Classifier guess index → species index; null for the background class. */
    fun labelIndexToSpeciesIndex(outputIndex: Int): Int? = LabelMapping.labelIndexToSpeciesIndex(outputIndex)

    private companion object {
        const val HEAD_BYTES = 64
    }
}
