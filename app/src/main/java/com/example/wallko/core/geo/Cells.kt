package com.example.wallko.core.geo

import com.example.wallko.core.config.GameRules
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Stable ~100 m × 100 m exploration cells.
 *
 * Latitude is cut into rows exactly CELL_SIZE m tall (row 0 starts at the south pole). Each row is cut
 * into `n` equal longitude columns, `n` chosen so a column is ~CELL_SIZE m wide at the row's centre
 * latitude. Columns start at −180° and wrap, so the antimeridian is an ordinary column edge.
 * Cell id = (row shl 32) or col.
 */

private const val METERS_PER_DEGREE_LAT = 111_320.0
private const val EARTH_RADIUS_METERS = 6_371_008.8
private const val CELL_SIZE = GameRules.Walk.CELL_SIZE_METERS
private const val ROW_HEIGHT_DEG = CELL_SIZE / METERS_PER_DEGREE_LAT
private val ROW_COUNT = ceil(180.0 / ROW_HEIGHT_DEG).toInt()

/** Edges of a cell in degrees. A cell never spans the antimeridian, so west < east always. */
data class CellBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(lat: Double, lng: Double): Boolean = lat in south..north && lng in west..east
}

fun cellIdOf(lat: Double, lng: Double): Long {
    val row = rowOf(lat)
    val n = columnsIn(row)
    val col = floor((normalizeLng(lng) + 180.0) / 360.0 * n).toInt().mod(n)
    return (row.toLong() shl 32) or col.toLong()
}

fun cellBounds(id: Long): CellBounds {
    val row = (id ushr 32).toInt()
    val col = (id and 0xFFFF_FFFFL).toInt()
    require(row in 0 until ROW_COUNT) { "Bad cell id $id" }
    val n = columnsIn(row)
    require(col in 0 until n) { "Bad cell id $id" }
    val south = -90.0 + row * ROW_HEIGHT_DEG
    val width = 360.0 / n
    val west = -180.0 + col * width
    // Pin the outermost edges exactly so float rounding never leaves a sliver uncovered.
    val north = if (row == ROW_COUNT - 1) 90.0 else south + ROW_HEIGHT_DEG
    val east = if (col == n - 1) 180.0 else west + width
    return CellBounds(south, west, north, east)
}

/** Great-circle distance in meters on a sphere of mean Earth radius. */
fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val sinDLat = sin(Math.toRadians(lat2 - lat1) / 2)
    val sinDLng = sin(Math.toRadians(lng2 - lng1) / 2)
    val a = sinDLat * sinDLat + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sinDLng * sinDLng
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

private fun rowOf(lat: Double): Int =
    floor((lat.coerceIn(-90.0, 90.0) + 90.0) / ROW_HEIGHT_DEG).toInt().coerceIn(0, ROW_COUNT - 1)

/** Longitude columns in [row]; at least 1 at the polar caps. */
private fun columnsIn(row: Int): Int {
    val centreLat = -90.0 + (row + 0.5) * ROW_HEIGHT_DEG
    val circumference = 360.0 * METERS_PER_DEGREE_LAT * cos(Math.toRadians(centreLat))
    return floor(circumference / CELL_SIZE).toInt().coerceAtLeast(1)
}

/** Maps any longitude into [-180, 180). */
private fun normalizeLng(lng: Double): Double = (lng + 180.0).mod(360.0) - 180.0
