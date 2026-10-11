package com.example.wallko.di

import android.content.Context
import androidx.room.Room
import com.example.wallko.core.db.ExploredCellDao
import com.example.wallko.core.db.LedgerDao
import com.example.wallko.core.db.OwnedPlantDao
import com.example.wallko.core.db.PlantSpeciesDao
import com.example.wallko.core.db.StepDayDao
import com.example.wallko.core.db.WalkDao
import com.example.wallko.core.db.WalletDao
import com.example.wallko.core.db.WallkoDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WallkoDatabase =
        Room.databaseBuilder(context, WallkoDatabase::class.java, WallkoDatabase.NAME).build()

    @Provides @Singleton
    fun providePlantSpeciesDao(db: WallkoDatabase): PlantSpeciesDao = db.plantSpeciesDao()

    @Provides @Singleton
    fun provideOwnedPlantDao(db: WallkoDatabase): OwnedPlantDao = db.ownedPlantDao()

    @Provides @Singleton
    fun provideWalletDao(db: WallkoDatabase): WalletDao = db.walletDao()

    @Provides @Singleton
    fun provideLedgerDao(db: WallkoDatabase): LedgerDao = db.ledgerDao()

    @Provides @Singleton
    fun provideWalkDao(db: WallkoDatabase): WalkDao = db.walkDao()

    @Provides @Singleton
    fun provideExploredCellDao(db: WallkoDatabase): ExploredCellDao = db.exploredCellDao()

    @Provides @Singleton
    fun provideStepDayDao(db: WallkoDatabase): StepDayDao = db.stepDayDao()
}
