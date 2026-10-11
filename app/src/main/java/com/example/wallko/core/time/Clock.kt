package com.example.wallko.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/** Injectable source of "now". Game logic must take time from here, never from the system directly. */
interface Clock {
    fun now(): Instant
    fun zone(): ZoneId
}

fun Clock.zonedNow(): ZonedDateTime = now().atZone(zone())

fun Clock.today(): LocalDate = zonedNow().toLocalDate()

/** Real wall-clock time in the device's current default zone. */
class SystemClock @Inject constructor() : Clock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
