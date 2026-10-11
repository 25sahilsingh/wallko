package com.example.wallko.ui.map

import com.example.wallko.core.geo.CellBounds
import com.example.wallko.core.geo.cellBounds
import com.example.wallko.core.geo.cellIdOf
import com.example.wallko.walk.WalkLiveState
import com.example.wallko.walk.WalkPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FogGeometryTest {

    private val london = cellIdOf(51.5072, -0.1276)

    // ---- Row-run merging ----

    @Test
    fun `adjacent cells in a row merge into one rectangle`() {
        val ids = listOf(london, london + 1, london + 2)
        val runs = mergeCellRuns(ids)
        assertEquals(1, runs.size)
        val first = cellBounds(london)
        val last = cellBounds(london + 2)
        assertEquals(CellBounds(first.south, first.west, first.north, last.east), runs[0])
    }

    @Test
    fun `gaps and different rows stay separate, duplicates ignored`() {
        val north = cellIdOf(cellBounds(london).north + 0.0001, -0.1276)
        val ids = listOf(london, london, london + 2, north)
        val runs = mergeCellRuns(ids)
        assertEquals(3, runs.size)
    }

    @Test
    fun `empty and invalid input`() {
        assertTrue(mergeCellRuns(emptyList()).isEmpty())
        assertTrue(mergeCellRuns(listOf(Long.MAX_VALUE)).isEmpty())
    }

    // ---- Ring orientation (RFC 7946: exterior CCW, holes CW) ----

    @Test
    fun `fog polygon has CCW outer ring and CW closed holes`() {
        val holes = mergeCellRuns(listOf(london, london + 5))
        val rings = buildFogRings(holes)
        assertEquals(3, rings.size)
        assertTrue("outer must be CCW", signedArea(rings[0]) > 0)
        rings.drop(1).forEach { ring ->
            assertTrue("hole must be CW", signedArea(ring) < 0)
            assertEquals(5, ring.size)
            assertEquals(ring.first(), ring.last())
        }
        assertEquals(rectRing(FOG_WORLD_BOUNDS, clockwise = false), rings[0])
    }

    @Test
    fun `holes outside or touching the outer ring are dropped`() {
        val outer = CellBounds(0.0, 0.0, 10.0, 10.0)
        val inside = CellBounds(1.0, 1.0, 2.0, 2.0)
        val touching = CellBounds(0.0, 1.0, 1.0, 2.0)
        val outside = CellBounds(20.0, 20.0, 21.0, 21.0)
        val rings = buildFogRings(listOf(inside, touching, outside), outer)
        assertEquals(2, rings.size)
        assertEquals(rectRing(inside, clockwise = true), rings[1])
    }

    @Test
    fun `rect ring signed area matches the rectangle`() {
        val b = CellBounds(0.0, 0.0, 2.0, 3.0)
        assertEquals(6.0, signedArea(rectRing(b, clockwise = false)), 1e-12)
        assertEquals(-6.0, signedArea(rectRing(b, clockwise = true)), 1e-12)
    }

    // ---- Distance filter ----

    @Test
    fun `cellsNear keeps only cells within the radius`() {
        val near = cellIdOf(51.51, -0.13) // ~300 m away
        val far = cellIdOf(51.75, -0.13) // ~27 km away
        val result = cellsNear(listOf(near, far), 51.5072, -0.1276, 20_000.0, 10)
        assertEquals(listOf(near), result)
    }

    @Test
    fun `cellsNear caps to the nearest cells`() {
        val ids = (0 until 10).map { cellIdOf(51.5072 + it * 0.001, -0.1276) }
        val result = cellsNear(ids, 51.5072, -0.1276, 20_000.0, 3)
        assertEquals(ids.take(3).toSet(), result.toSet())
    }

    @Test
    fun `selectFogCells passes everything through under the cap`() {
        val ids = listOf(london, london + 1)
        assertEquals(ids, selectFogCells(ids, focus = null, maxCells = 5))
        val far = cellIdOf(10.0, 10.0)
        val filtered = selectFogCells(ids + far, LngLat(-0.1276, 51.5072), maxCells = 2)
        assertEquals(ids.toSet(), filtered.toSet())
    }

    // ---- Equirectangular projection ----

    @Test
    fun `origin projects to zero`() {
        val p = EquirectProjection(51.5, -0.12)
        assertEquals(0.0, p.xMeters(-0.12), 1e-9)
        assertEquals(0.0, p.yMeters(51.5), 1e-9)
    }

    @Test
    fun `degree lengths`() {
        val equator = EquirectProjection(0.0, 0.0)
        assertEquals(111_320.0, equator.yMeters(1.0), 1e-6)
        assertEquals(111_320.0, equator.xMeters(1.0), 1e-6)
        val sixty = EquirectProjection(60.0, 0.0)
        assertEquals(55_660.0, sixty.xMeters(1.0), 1.0) // cos 60° = 0.5
        assertEquals(-111_320.0, sixty.yMeters(59.0), 1e-6)
    }

    @Test
    fun `projection takes the short way over the antimeridian`() {
        val p = EquirectProjection(0.0, 179.9)
        assertEquals(0.2 * 111_320.0, p.xMeters(-179.9), 1e-3)
    }

    @Test
    fun `unproject inverts project`() {
        val p = EquirectProjection(48.85, 2.35)
        val back = p.unproject(p.xMeters(2.40), p.yMeters(48.80))
        assertEquals(2.40, back.lng, 1e-9)
        assertEquals(48.80, back.lat, 1e-9)
    }

    // ---- Elixir pop-up trigger ----

    @Test
    fun `elixir pops only on increases within the same walk`() {
        val tracker = ElixirPopTracker()
        fun live(start: Long, elixir: Int, phase: WalkPhase = WalkPhase.TRACKING) =
            WalkLiveState(walkId = 1, phase = phase, startedAtMillis = start, elixirSoFar = elixir)

        assertNull(tracker.onLive(live(100, 2))) // baseline, even if already > 0
        assertNull(tracker.onLive(live(100, 2)))
        assertEquals(3, tracker.onLive(live(100, 3)))
        assertNull(tracker.onLive(live(100, 3)))
        assertNull(tracker.onLive(live(100, 4, WalkPhase.FINISHING)))
        assertNull(tracker.onLive(null))
        assertNull(tracker.onLive(live(200, 1))) // new walk: new baseline
        assertEquals(2, tracker.onLive(live(200, 2)))
    }
}
