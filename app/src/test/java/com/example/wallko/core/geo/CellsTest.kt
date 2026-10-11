package com.example.wallko.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

class CellsTest {

    private val places = listOf(
        "equator" to (0.0005 to 0.0005),
        "60N" to (60.0 to 10.0),
        "southern" to (-33.8688 to 151.2093), // Sydney
        "western" to (40.7128 to -74.0060),   // New York
        "south-west" to (-34.6037 to -58.3816), // Buenos Aires
    )

    private fun north(lat: Double, m: Double) = lat + m / 111_320.0
    private fun east(lat: Double, lng: Double, m: Double) = lng + m / (111_320.0 * cos(Math.toRadians(lat)))

    private fun centreOf(lat: Double, lng: Double): Pair<Double, Double> {
        val b = cellBounds(cellIdOf(lat, lng))
        return (b.south + b.north) / 2 to (b.west + b.east) / 2
    }

    @Test
    fun samePointSameId() {
        for ((name, p) in places) {
            assertEquals(name, cellIdOf(p.first, p.second), cellIdOf(p.first, p.second))
        }
    }

    @Test
    fun pointsFiftyMetersApartInsideOneCellShareId() {
        for ((name, p) in places) {
            val (lat, lng) = centreOf(p.first, p.second)
            val id = cellIdOf(lat, lng)
            assertEquals("$name N-S", cellIdOf(north(lat, -25.0), lng), cellIdOf(north(lat, 25.0), lng))
            assertEquals("$name E-W", cellIdOf(lat, east(lat, lng, -25.0)), cellIdOf(lat, east(lat, lng, 25.0)))
            assertEquals(name, id, cellIdOf(north(lat, 25.0), lng))
        }
    }

    @Test
    fun pointsHundredFiftyMetersApartDiffer() {
        for ((name, p) in places) {
            val (lat, lng) = p
            assertNotEquals("$name N", cellIdOf(lat, lng), cellIdOf(north(lat, 150.0), lng))
            assertNotEquals("$name S", cellIdOf(lat, lng), cellIdOf(north(lat, -150.0), lng))
            assertNotEquals("$name E", cellIdOf(lat, lng), cellIdOf(lat, east(lat, lng, 150.0)))
            assertNotEquals("$name W", cellIdOf(lat, lng), cellIdOf(lat, east(lat, lng, -150.0)))
        }
    }

    @Test
    fun boundsContainSourcePointAndAreAbout100m() {
        for ((name, p) in places) {
            val (lat, lng) = p
            val b = cellBounds(cellIdOf(lat, lng))
            assertTrue(name, b.contains(lat, lng))
            val height = haversineMeters(b.south, b.west, b.north, b.west)
            val midLat = (b.south + b.north) / 2
            val width = haversineMeters(midLat, b.west, midLat, b.east)
            assertEquals("$name height", 100.0, height, 1.0)
            assertEquals("$name width", 100.0, width, 1.0)
        }
    }

    @Test
    fun antimeridianWrapsSanely() {
        assertEquals(cellIdOf(10.0, 180.0), cellIdOf(10.0, -180.0))
        assertEquals(cellIdOf(10.0, 170.0), cellIdOf(10.0, 170.0 - 360.0))
        assertEquals(cellIdOf(10.0, 45.0), cellIdOf(10.0, 45.0 + 720.0))
        val east = cellBounds(cellIdOf(10.0, 179.99999))
        val west = cellBounds(cellIdOf(10.0, -179.99999))
        assertTrue(east.west < east.east && east.east <= 180.0)
        assertTrue(west.west >= -180.0 && west.west < west.east)
        assertTrue(east.contains(10.0, 179.99999))
        assertTrue(west.contains(10.0, -179.99999))
    }

    @Test
    fun polesDoNotCrash() {
        assertTrue(cellBounds(cellIdOf(90.0, 0.0)).contains(90.0, 0.0))
        assertTrue(cellBounds(cellIdOf(-90.0, 0.0)).contains(-90.0, 0.0))
    }

    @Test
    fun haversineLondonToParis() {
        val d = haversineMeters(51.5074, -0.1278, 48.8566, 2.3522)
        val expected = 343_500.0
        assertTrue("got $d", abs(d - expected) / expected < 0.005)
    }

    @Test
    fun haversineOneDegreeOfLatitude() {
        val d = haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue("got $d", abs(d - 111_195.0) / 111_195.0 < 0.005)
    }
}
