package com.example.wallko.data.walks

import androidx.room.withTransaction
import com.example.wallko.core.db.ExploredCell
import com.example.wallko.core.db.StepDay
import com.example.wallko.core.db.Walk
import com.example.wallko.core.db.WalkPoint
import com.example.wallko.core.db.WalkWithPoints
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.core.time.Clock
import com.example.wallko.core.time.today
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** One accepted GPS fix. */
data class TrackPoint(val at: Long, val lat: Double, val lng: Double)

/** Final numbers written when a walk ends. */
data class WalkTotals(
    val distanceM: Double,
    val steps: Int,
    val newCells: Int,
    val dropsEarned: Int,
    val elixirEarned: Int,
    val dawn: Boolean,
)

@Singleton
class WalkRepository @Inject constructor(
    private val db: WallkoDatabase,
    private val clock: Clock,
) {
    private val walkDao get() = db.walkDao()
    private val cellDao get() = db.exploredCellDao()
    private val stepDao get() = db.stepDayDao()

    // ---- Walks ----

    suspend fun startWalk(startedAt: Long = clock.now().toEpochMilli()): Long =
        walkDao.insert(Walk(startedAt = startedAt, endedAt = null))

    suspend fun appendPoints(walkId: Long, points: List<TrackPoint>) {
        if (points.isEmpty()) return
        walkDao.insertPoints(points.map { WalkPoint(walkId = walkId, at = it.at, lat = it.lat, lng = it.lng) })
    }

    /** @return false if no walk with [walkId] exists. */
    suspend fun finishWalk(walkId: Long, totals: WalkTotals, endedAt: Long = clock.now().toEpochMilli()): Boolean =
        walkDao.finish(
            id = walkId,
            endedAt = endedAt,
            distanceM = totals.distanceM,
            steps = totals.steps,
            newCells = totals.newCells,
            dropsEarned = totals.dropsEarned,
            elixirEarned = totals.elixirEarned,
            dawn = totals.dawn,
        ) > 0

    suspend fun getWalk(walkId: Long): Walk? = walkDao.get(walkId)

    /** Newest first. */
    fun observeWalks(): Flow<List<Walk>> = walkDao.observeAll()

    /** Walk with its points sorted by time. */
    fun observeWalk(walkId: Long): Flow<WalkWithPoints?> =
        walkDao.observeWithPoints(walkId).map { it?.copy(points = it.points.sortedBy(WalkPoint::at)) }

    // ---- Explored cells ----

    suspend fun knownCellIds(): Set<Long> = cellDao.allIds().toHashSet()

    /** Insert-ignore; first visit time is kept. @return the ids that were newly explored by this call. */
    suspend fun markExplored(ids: Collection<Long>, at: Long = clock.now().toEpochMilli()): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        val distinct = ids.distinct()
        val rowIds = db.withTransaction { cellDao.insertAllIgnore(distinct.map { ExploredCell(it, at) }) }
        return distinct.filterIndexed { i, _ -> rowIds[i] != -1L }.toSet()
    }

    fun observeExploredCells(): Flow<List<ExploredCell>> = cellDao.observeAll()

    fun observeExploredCount(): Flow<Int> = cellDao.observeCount()

    // ---- Passive steps ----

    suspend fun getStepDay(date: LocalDate = clock.today()): StepDay? = stepDao.get(date.toEpochDay())

    fun observeStepDay(date: LocalDate): Flow<StepDay?> = stepDao.observe(date.toEpochDay())

    suspend fun upsertStepDay(day: StepDay) = stepDao.upsert(day)
}
