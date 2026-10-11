package com.example.wallko.ui.scan

import com.example.wallko.core.config.GameRules
import com.example.wallko.core.db.Rarity
import com.example.wallko.game.ScanToCardUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanMathTest {

    @Test fun normalizeRotation() {
        assertEquals(0, ScanMath.normalizeRotation(0))
        assertEquals(90, ScanMath.normalizeRotation(90))
        assertEquals(270, ScanMath.normalizeRotation(-90))
        assertEquals(180, ScanMath.normalizeRotation(540))
        assertEquals(0, ScanMath.normalizeRotation(359))
        assertEquals(270, ScanMath.normalizeRotation(260))
    }

    @Test fun rotatedSizeSwapsOnQuarterTurns() {
        assertEquals(4000 to 3000, ScanMath.rotatedSize(4000, 3000, 0))
        assertEquals(3000 to 4000, ScanMath.rotatedSize(4000, 3000, 90))
        assertEquals(4000 to 3000, ScanMath.rotatedSize(4000, 3000, 180))
        assertEquals(3000 to 4000, ScanMath.rotatedSize(4000, 3000, 270))
    }

    @Test fun fitWithinNeverUpscalesAndKeepsAspect() {
        assertEquals(800 to 600, ScanMath.fitWithin(800, 600, 1600))
        assertEquals(1600 to 1200, ScanMath.fitWithin(4000, 3000, 1600))
        assertEquals(1200 to 1600, ScanMath.fitWithin(3000, 4000, 1600))
        assertEquals(1600 to 1, ScanMath.fitWithin(10_000, 2, 1600))
    }

    @Test fun photoFileNameIsUtcTimestamp() {
        assertEquals("19700101_000000_000.jpg", ScanMath.photoFileName(0))
        assertEquals("20261010_120000_123.jpg", ScanMath.photoFileName(1_791_633_600_123))
    }

    @Test fun guessTitleAndSubtitle() {
        assertEquals("Rose", ScanMath.guessTitle("Rose", "Rosa canina"))
        assertEquals("Rosa canina", ScanMath.guessSubtitle("Rose", "Rosa canina"))
        assertEquals("Rosa canina", ScanMath.guessTitle("  ", "Rosa canina"))
        assertNull(ScanMath.guessSubtitle(null, "Rosa canina"))
    }

    @Test fun initialSelectionOnlyWhenConfident() {
        assertEquals(0, ScanMath.initialSelection(3, confident = true))
        assertNull(ScanMath.initialSelection(3, confident = false))
        assertNull(ScanMath.initialSelection(0, confident = true))
    }

    @Test fun shortPlaceNamePrefersMostLocal() {
        assertEquals("Mitte", ScanMath.shortPlaceName("Mitte", "Berlin", null, "Berlin", "Germany"))
        assertEquals("Rishikesh", ScanMath.shortPlaceName(" ", "Rishikesh", "Dehradun", "Uttarakhand", "India"))
        assertEquals("India", ScanMath.shortPlaceName(null, null, null, "", "India"))
        assertNull(ScanMath.shortPlaceName(null, null, null, null, null))
    }

    @Test fun rareFindBonusOnlyFirstTimeAndRarePlus() {
        val bonus = GameRules.Earning.RARE_FIND_BONUS
        assertEquals(0, ScanToCardUseCase.rareFindBonus(Rarity.COMMON, foundBefore = false))
        assertEquals(0, ScanToCardUseCase.rareFindBonus(Rarity.UNCOMMON, foundBefore = false))
        assertEquals(bonus, ScanToCardUseCase.rareFindBonus(Rarity.RARE, foundBefore = false))
        assertEquals(bonus, ScanToCardUseCase.rareFindBonus(Rarity.EPIC, foundBefore = false))
        assertEquals(bonus, ScanToCardUseCase.rareFindBonus(Rarity.LEGENDARY, foundBefore = false))
        // Re-scanning a withered species replants it but pays nothing again.
        assertEquals(0, ScanToCardUseCase.rareFindBonus(Rarity.LEGENDARY, foundBefore = true))
    }
}
