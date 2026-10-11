package com.example.wallko.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.wallko.core.db.GrowthSpeed
import com.example.wallko.core.db.PlantSpecies
import com.example.wallko.core.db.Rarity
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.core.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FixedClock(var instant: Instant = Instant.parse("2026-10-10T08:00:00Z")) : Clock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = ZoneOffset.UTC
}

fun inMemoryDb(): WallkoDatabase = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext(),
    WallkoDatabase::class.java,
).build()

fun species(index: Int) = PlantSpecies(
    index = index,
    latin = "Species $index",
    common = null,
    family = null,
    type = null,
    description = null,
    observations = 100,
    rarity = Rarity.COMMON,
    thirst = 2,
    sun = 2,
    growth = GrowthSpeed.FAST,
    bloom = emptyList(),
    badges = emptyList(),
    sources = emptyMap(),
    dbVersion = 1,
)
