package com.example.wallko.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FakeClockTest {

    @Test
    fun advanceByMovesNowForward() {
        val clock = FakeClock(Instant.parse("2026-03-01T10:00:00Z"))
        clock.advanceBy(Duration.ofDays(3).plusHours(2))
        assertEquals(Instant.parse("2026-03-04T12:00:00Z"), clock.now())
    }

    @Test
    fun todayUsesZone() {
        val clock = FakeClock(Instant.parse("2026-03-01T23:30:00Z"), ZoneId.of("Asia/Kolkata"))
        assertEquals(LocalDate.of(2026, 3, 2), clock.today())
    }
}
