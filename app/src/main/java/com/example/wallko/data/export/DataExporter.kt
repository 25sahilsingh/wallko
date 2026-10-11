package com.example.wallko.data.export

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.wallko.core.db.ExploredCellDao
import com.example.wallko.core.db.LedgerDao
import com.example.wallko.core.db.OwnedPlantDao
import com.example.wallko.core.db.StepDayDao
import com.example.wallko.core.db.WalkDao
import com.example.wallko.core.db.WalletDao
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.core.time.Clock
import com.example.wallko.core.time.today
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** What a finished export contained, for the confirmation message. */
data class ExportSummary(val plants: Int, val walks: Int, val bytes: Int)

/**
 * Writes everything the player owns to one pretty-printed JSON document chosen through the
 * Storage Access Framework. Photos and sprites are not copied; only their paths are listed.
 */
@Singleton
class DataExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WallkoDatabase,
    private val ownedPlantDao: OwnedPlantDao,
    private val walletDao: WalletDao,
    private val ledgerDao: LedgerDao,
    private val walkDao: WalkDao,
    private val exploredCellDao: ExploredCellDao,
    private val stepDayDao: StepDayDao,
    private val clock: Clock,
) {
    /** Suggested file name for the SAF "create document" picker. */
    fun suggestedFileName(): String = ExportMapper.fileName(clock.today())

    /** One consistent read of every table the export covers. */
    suspend fun snapshot(): ExportSnapshot = db.withTransaction {
        ExportSnapshot(
            plants = ownedPlantDao.getAllWithSpecies(),
            wallet = walletDao.get(),
            ledger = ledgerDao.getAll(),
            walks = walkDao.getAll(),
            walkPoints = walkDao.getAllPoints(),
            exploredCells = exploredCellDao.getAll(),
            stepDays = stepDayDao.getAll(),
        )
    }

    suspend fun buildExport(): WallkoExport =
        ExportMapper.toExport(snapshot(), clock.now(), appVersion())

    /** Serialises and writes to [uri]. Throws on failure; the caller shows the message. */
    suspend fun exportTo(uri: Uri): ExportSummary {
        val export = buildExport()
        val bytes = withContext(Dispatchers.Default) { ExportMapper.encode(export).toByteArray(Charsets.UTF_8) }
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            // "wt" truncates if the user picked an existing file; some providers only accept "w".
            val out = (runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull() ?: resolver.openOutputStream(uri, "w"))
                ?: throw IOException("Could not open $uri for writing")
            out.use { it.write(bytes) }
        }
        return ExportSummary(plants = export.plants.size, walks = export.walks.size, bytes = bytes.size)
    }

    private fun appVersion(): String? = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: Exception) {
        null
    }
}
