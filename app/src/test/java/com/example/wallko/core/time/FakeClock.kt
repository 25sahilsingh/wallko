package com.example.wallko.core.time

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Mutable test clock. Defaults to 2026-01-01T00:00Z in UTC. */
class FakeClock(
    var instant: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    var zoneId: ZoneId = ZoneOffset.UTC,
) : Clock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId

    fun advanceBy(duration: Duration) {
        instant += duration
    }
}
