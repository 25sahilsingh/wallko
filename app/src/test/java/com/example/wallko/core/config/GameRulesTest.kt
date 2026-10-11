package com.example.wallko.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime

class GameRulesTest {

    @Test
    fun tankCapacityGrowsAndCaps() {
        assertEquals(50, GameRules.tankCapacityFor(0))
        assertEquals(150, GameRules.tankCapacityFor(10))
        assertEquals(500, GameRules.tankCapacityFor(45))
        assertEquals(500, GameRules.tankCapacityFor(100))
    }

    @Test
    fun stepDropsAreCapped() {
        assertEquals(0, GameRules.dropsForSteps(99))
        assertEquals(12, GameRules.dropsForSteps(1_250))
        assertEquals(150, GameRules.dropsForSteps(15_000))
        assertEquals(150, GameRules.dropsForSteps(40_000))
    }

    @Test
    fun distanceDropsPerFull100m() {
        assertEquals(0, GameRules.dropsForDistance(99.9))
        assertEquals(6, GameRules.dropsForDistance(350.0))
    }

    @Test
    fun dawnWindowIsHalfOpen() {
        assertFalse(GameRules.isDawn(LocalTime.of(3, 59)))
        assertTrue(GameRules.isDawn(LocalTime.of(4, 0)))
        assertTrue(GameRules.isDawn(LocalTime.of(6, 59)))
        assertFalse(GameRules.isDawn(LocalTime.of(7, 0)))
    }

    @Test
    fun confidenceRule() {
        assertTrue(GameRules.isConfident(0.45f, 0.35f))
        assertFalse(GameRules.isConfident(0.34f, 0.0f))
        assertFalse(GameRules.isConfident(0.50f, 0.45f))
    }

    @Test
    fun walkValidity() {
        assertTrue(GameRules.isValidWalk(Duration.ofMinutes(2), 100.0))
        assertFalse(GameRules.isValidWalk(Duration.ofSeconds(119), 500.0))
        assertFalse(GameRules.isValidWalk(Duration.ofMinutes(30), 99.0))
    }
}
