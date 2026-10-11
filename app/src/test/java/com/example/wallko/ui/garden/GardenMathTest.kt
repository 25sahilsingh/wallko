package com.example.wallko.ui.garden

import com.example.wallko.core.config.GameRules
import com.example.wallko.core.db.PlantState
import com.example.wallko.core.db.Rarity
import com.example.wallko.data.garden.GardenSpot
import com.example.wallko.game.model.GardenEvent
import com.example.wallko.ui.collection.CollectionCard
import com.example.wallko.ui.collection.CollectionFilters
import com.example.wallko.ui.collection.CollectionMath
import com.example.wallko.ui.components.PlantStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GardenMathTest {

    // 400 px wide → 100 px columns; shelves 2 × 100 from y=0; gap 20; grid 6 × 100 from y=220.
    private val layout = GardenLayout(width = 400f, shelfRows = 2, shelfRowHeight = 100f, cellHeight = 100f, gap = 20f)

    @Test fun stageMapping() {
        assertEquals(PlantStage.SEEDLING, GardenMath.uiStage(PlantState.SEEDLING, hasSprite = false))
        assertEquals(PlantStage.GROWING, GardenMath.uiStage(PlantState.SEEDLING, hasSprite = true))
        assertEquals(PlantStage.GROWING, GardenMath.uiStage(PlantState.GROWING, false))
        assertEquals(PlantStage.GROWN, GardenMath.uiStage(PlantState.GROWN, true))
        assertEquals(PlantStage.WILTING, GardenMath.uiStage(PlantState.WILTING, true))
        assertEquals(PlantStage.WITHERED, GardenMath.uiStage(PlantState.WITHERED, true))
    }

    @Test fun growthFraction() {
        val seed = GameRules.Growth.SEEDLING_MAX_GROWTH_DAYS
        val target = GameRules.Growth.MEDIUM_DAYS
        assertEquals(0f, GardenMath.growthFraction(0, target), 1e-6f)
        assertEquals(0f, GardenMath.growthFraction(seed, target), 1e-6f)
        assertEquals(1f, GardenMath.growthFraction(target, target), 1e-6f)
        assertEquals(1f, GardenMath.growthFraction(target + 5, target), 1e-6f)
        val mid = GardenMath.growthFraction((seed + target) / 2, target)
        assertTrue(mid > 0f && mid < 1f)
        assertEquals(1f, GardenMath.growthFraction(0, seed), 1e-6f)
    }

    @Test fun swayPhaseIsStableAndInRange() {
        for (id in 0L..500L) {
            val p = GardenMath.swayPhase(id)
            assertTrue(p >= 0f && p < 1f)
            assertEquals(p, GardenMath.swayPhase(id), 0f)
        }
        assertTrue((1L..20L).map { GardenMath.swayPhase(it) }.distinct().size > 10)
    }

    @Test fun shelfRows() {
        assertEquals(2, GardenMath.shelfRowsFor(null))
        assertEquals(2, GardenMath.shelfRowsFor(0))
        assertEquals(2, GardenMath.shelfRowsFor(6))
        assertEquals(3, GardenMath.shelfRowsFor(7))
        assertEquals(3, GardenMath.shelfRowsFor(10))
    }

    @Test fun layoutCenters() {
        assertEquals(GardenPoint(50f, 50f), layout.center(GardenSpot(0, 0, onShelf = true)))
        assertEquals(GardenPoint(150f, 150f), layout.center(GardenSpot(5, 0, onShelf = true)))
        assertEquals(GardenPoint(350f, 270f), layout.center(GardenSpot(3, 0)))
        assertEquals(GardenPoint(50f, 770f), layout.center(GardenSpot(0, 5)))
        assertEquals(8 + 24, layout.allSpots().size)
        assertEquals(820f, layout.totalHeight, 1e-3f)
    }

    @Test fun snapsToNearestCell() {
        val target = GardenMath.snapToNearestFree(layout, GardenPoint(160f, 390f), emptySet(), GardenSpot(0, 0))
        assertEquals(GardenSpot(1, 1), target)
    }

    @Test fun snapsToShelfSlot() {
        val target = GardenMath.snapToNearestFree(layout, GardenPoint(240f, 160f), emptySet(), GardenSpot(0, 0))
        assertEquals(GardenSpot(6, 0, onShelf = true), target)
    }

    @Test fun skipsOccupiedButAllowsOwnSpot() {
        val occupied = setOf(GardenSpot(1, 1), GardenSpot(0, 0))
        val moved = GardenMath.snapToNearestFree(layout, GardenPoint(150f, 370f), occupied, GardenSpot(0, 0))
        assertTrue(moved != GardenSpot(1, 1) && moved != GardenSpot(0, 0))
        val back = GardenMath.snapToNearestFree(layout, GardenPoint(55f, 265f), occupied, GardenSpot(0, 0))
        assertEquals(GardenSpot(0, 0), back)
    }

    @Test fun shelfOccupancyIgnoresY() {
        val occupied = setOf(GardenSpot(0, 3, onShelf = true))
        val t = GardenMath.snapToNearestFree(layout, GardenPoint(50f, 50f), occupied, GardenSpot(2, 2))
        assertTrue(t != GardenSpot(0, 0, onShelf = true))
    }

    @Test fun drinking() {
        val events = listOf(
            GardenEvent.Drank(1, 3), GardenEvent.Wilted(2, 3), GardenEvent.Drank(3, 0), GardenEvent.Drank(4, 2),
        )
        assertEquals(setOf(1L, 4L), GardenMath.drankIds(events))
        assertEquals(15, GardenMath.tankBefore(10, 5, 50))
        assertEquals(50, GardenMath.tankBefore(48, 5, 50))
        assertEquals(10, GardenMath.tankBefore(10, -3, 50))
    }

    private fun card(id: Long, species: Int, rarity: Rarity, type: String?, stage: PlantStage = PlantStage.GROWN, at: Long = id) =
        CollectionCard(id, species, "n$id", "l$id", rarity, type, stage, 1f, null, 0, at)

    @Test fun collectionFiltersAndCounts() {
        val cards = listOf(
            card(1, 10, Rarity.COMMON, "herb"),
            card(2, 10, Rarity.COMMON, "herb", PlantStage.WITHERED),
            card(3, 11, Rarity.RARE, "Tree"),
            card(4, 12, Rarity.RARE, null),
        )
        assertEquals(3, CollectionMath.distinctSpecies(cards))
        assertEquals(listOf("herb", "tree"), CollectionMath.types(cards))
        assertEquals(listOf(3L, 4L), CollectionMath.filter(cards, CollectionFilters(rarity = Rarity.RARE)).map { it.id })
        assertEquals(listOf(3L), CollectionMath.filter(cards, CollectionFilters(type = "tree")).map { it.id })
        assertEquals(4, CollectionMath.filter(cards, CollectionFilters()).size)
        assertEquals(listOf(4L, 3L, 2L, 1L), CollectionMath.sort(cards).map { it.id })
    }
}
