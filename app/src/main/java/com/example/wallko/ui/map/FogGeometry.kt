package com.example.wallko.ui.map

import com.example.wallko.core.geo.CellBounds
import com.example.wallko.core.geo.cellBounds
import com.example.wallko.core.geo.haversineMeters
import kotlin.math.abs
import kotlin.math.cos

/*
 * Pure geometry for the explored map (no Android / MapLibre types, so it is JVM-testable).
 *
 * Coordinates use GeoJSON axis order: x = longitude, y = latitude. Ring orientation follows RFC 7946 §3.1.6:
 * exterior rings counter-clockwise, holes clockwise. A ring is closed (first point repeated at the end).
 */

/** A WGS84 position. */
data class LngLat(val lng: Double, val lat: Double)

/** Fog cut-outs and green patches for the map, plus how many cells are explored in total. */
data class FogShapes(
    /** Explored cells merged into row runs; only the ones near the focus when there are very many. */
    val explored: List<CellBounds>,
    /** Total explored cells (stored + entered during the live walk). */
    val totalCells: Int,
) {
    companion object {
        val EMPTY = FogShapes(emptyList(), 0)
    }
}

/** Fog outer ring: (almost) the whole Web-Mercator world. */
val FOG_WORLD_BOUNDS = CellBounds(south = -85.0, west = -180.0, north = 85.0, east = 180.0)

/** Above this many explored cells only the ones within [FOG_RADIUS_METERS] of the camera are cut out. */
const val MAX_FOG_CELLS = 5_000
const val FOG_RADIUS_METERS = 20_000.0

private const val METERS_PER_DEGREE = 111_320.0

internal fun cellRow(id: Long): Int = (id ushr 32).toInt()
internal fun cellCol(id: Long): Int = (id and 0xFFFF_FFFFL).toInt()

/**
 * Merges cells that sit next to each other in the same row (consecutive columns) into one rectangle, so a
 * long walk along a street becomes a handful of holes instead of hundreds. Invalid ids are skipped.
 * Output is sorted south→north, west→east.
 */
fun mergeCellRuns(cellIds: Collection<Long>): List<CellBounds> {
    if (cellIds.isEmpty()) return emptyList()
    val sorted = cellIds.toLongArray().also { it.sort() } // row-major because id = (row shl 32) or col
    val out = ArrayList<CellBounds>()
    var start = sorted[0]
    var prev = sorted[0]

    fun flush() {
        val first = runCatching { cellBounds(start) }.getOrNull() ?: return
        val last = runCatching { cellBounds(prev) }.getOrNull() ?: return
        out += CellBounds(first.south, first.west, first.north, last.east)
    }

    for (i in 1 until sorted.size) {
        val id = sorted[i]
        if (id == prev) continue
        if (cellRow(id) == cellRow(prev) && cellCol(id) == cellCol(prev) + 1) {
            prev = id
        } else {
            flush()
            start = id
            prev = id
        }
    }
    flush()
    return out
}

/** Closed rectangle ring. Clockwise for holes, counter-clockwise for exterior rings. */
fun rectRing(b: CellBounds, clockwise: Boolean): List<LngLat> {
    val sw = LngLat(b.west, b.south)
    val se = LngLat(b.east, b.south)
    val ne = LngLat(b.east, b.north)
    val nw = LngLat(b.west, b.north)
    return if (clockwise) listOf(sw, nw, ne, se, sw) else listOf(sw, se, ne, nw, sw)
}

/** Shoelace signed area in degree² (x = lng, y = lat): positive = counter-clockwise. */
fun signedArea(ring: List<LngLat>): Double {
    var sum = 0.0
    for (i in 0 until ring.size - 1) {
        val a = ring[i]
        val b = ring[i + 1]
        sum += a.lng * b.lat - b.lng * a.lat
    }
    return sum / 2.0
}

/**
 * Rings of the single fog polygon: [outer] counter-clockwise, then one clockwise hole per entry of [holes]
 * that lies strictly inside [outer] (a hole touching the outer ring would be invalid GeoJSON).
 */
fun buildFogRings(holes: List<CellBounds>, outer: CellBounds = FOG_WORLD_BOUNDS): List<List<LngLat>> {
    val rings = ArrayList<List<LngLat>>(holes.size + 1)
    rings += rectRing(outer, clockwise = false)
    for (h in holes) {
        if (h.west > outer.west && h.east < outer.east && h.south > outer.south && h.north < outer.north) {
            rings += rectRing(h, clockwise = true)
        }
    }
    return rings
}

fun CellBounds.center(): LngLat = LngLat((west + east) / 2.0, (south + north) / 2.0)

/**
 * Cells whose centre is within [radiusM] of ([lat], [lng]); if more than [maxCells] match, the nearest
 * [maxCells] are kept.
 */
fun cellsNear(cellIds: Collection<Long>, lat: Double, lng: Double, radiusM: Double, maxCells: Int): List<Long> {
    val latSlack = radiusM / METERS_PER_DEGREE
    val hits = ArrayList<Pair<Long, Double>>()
    for (id in cellIds) {
        val c = runCatching { cellBounds(id) }.getOrNull()?.center() ?: continue
        if (abs(c.lat - lat) > latSlack) continue // cheap reject before the trig
        val d = haversineMeters(lat, lng, c.lat, c.lng)
        if (d <= radiusM) hits += id to d
    }
    if (hits.size > maxCells) hits.sortBy { it.second }
    return hits.asSequence().take(maxCells).map { it.first }.toList()
}

/**
 * The cells to cut out of the fog: all of them when there are at most [maxCells], otherwise only those
 * near [focus] (or an arbitrary [maxCells] if there is no focus yet).
 */
fun selectFogCells(
    cellIds: Collection<Long>,
    focus: LngLat?,
    maxCells: Int = MAX_FOG_CELLS,
    radiusM: Double = FOG_RADIUS_METERS,
): Collection<Long> = when {
    cellIds.size <= maxCells -> cellIds
    focus == null -> cellIds.take(maxCells)
    else -> cellsNear(cellIds, focus.lat, focus.lng, radiusM, maxCells)
}

/**
 * Simple equirectangular projection around an origin, in metres (x east, y north). Accurate enough for the
 * few kilometres the meadow map shows.
 */
class EquirectProjection(val originLat: Double, val originLng: Double) {
    private val metersPerDegLng = METERS_PER_DEGREE * cos(Math.toRadians(originLat))

    /** Metres east of the origin; takes the short way across the antimeridian. */
    fun xMeters(lng: Double): Double = wrapDegrees(lng - originLng) * metersPerDegLng

    /** Metres north of the origin. */
    fun yMeters(lat: Double): Double = (lat - originLat) * METERS_PER_DEGREE

    fun unproject(xMeters: Double, yMeters: Double): LngLat = LngLat(
        lng = wrapDegrees(originLng + xMeters / metersPerDegLng),
        lat = originLat + yMeters / METERS_PER_DEGREE,
    )

    private fun wrapDegrees(d: Double): Double = (d + 180.0).mod(360.0) - 180.0
}
