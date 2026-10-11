package com.example.wallko.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PlantSpecies::class,
        OwnedPlant::class,
        Wallet::class,
        LedgerEntry::class,
        Walk::class,
        WalkPoint::class,
        ExploredCell::class,
        StepDay::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WallkoDatabase : RoomDatabase() {
    abstract fun plantSpeciesDao(): PlantSpeciesDao
    abstract fun ownedPlantDao(): OwnedPlantDao
    abstract fun walletDao(): WalletDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun walkDao(): WalkDao
    abstract fun exploredCellDao(): ExploredCellDao
    abstract fun stepDayDao(): StepDayDao

    companion object { const val NAME = "wallko.db" }
}
