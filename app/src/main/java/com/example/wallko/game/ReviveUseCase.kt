package com.example.wallko.game

import androidx.room.withTransaction
import com.example.wallko.core.db.LedgerKind
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.core.time.Clock
import com.example.wallko.data.garden.GardenRepository
import com.example.wallko.data.wallet.WalletRepository
import com.example.wallko.game.model.ReviveResult
import javax.inject.Inject

/** Outcome of [ReviveUseCase.revive], for UI messaging. */
sealed interface ReviveOutcome {
    /** The plant is back in the garden. [elixirLeft] is the balance after paying. */
    data class Revived(val plantId: Long, val plantName: String, val elixirLeft: Int) : ReviveOutcome
    data class NotEnoughElixir(val have: Int, val need: Int) : ReviveOutcome
    /** A living plant of the same species is already in the garden (duplicates rule); nothing was spent. */
    data class AlreadyInGarden(val existingPlantId: Long) : ReviveOutcome
    data object NotWithered : ReviveOutcome
    data object NotFound : ReviveOutcome
}

/** Spends elixir to bring a WITHERED plant back to the garden (game-rules.md § Elixir). */
class ReviveUseCase @Inject constructor(
    private val db: WallkoDatabase,
    private val garden: GardenRepository,
    private val wallet: WalletRepository,
    private val clock: Clock,
) {
    suspend fun revive(plantId: Long): ReviveOutcome {
        wallet.ensureInitialized()
        val now = clock.now().toEpochMilli()
        return db.withTransaction {
            val plantDao = db.ownedPlantDao()
            val plant = plantDao.get(plantId) ?: return@withTransaction ReviveOutcome.NotFound
            val species = db.plantSpeciesDao().byIndex(plant.speciesIndex)
                ?: return@withTransaction ReviveOutcome.NotFound
            val w = checkNotNull(db.walletDao().get()) { "wallet missing after init" }

            when (val r = GameEngine.revive(GameMappers.toSnapshot(plant, species), GameMappers.toWalletState(w))) {
                ReviveResult.NotWithered -> ReviveOutcome.NotWithered
                is ReviveResult.NotEnoughElixir -> ReviveOutcome.NotEnoughElixir(r.have, r.need)
                is ReviveResult.Revived -> {
                    plantDao.aliveBySpecies(plant.speciesIndex)?.let {
                        return@withTransaction ReviveOutcome.AlreadyInGarden(it.id)
                    }
                    // Its old spot may have been taken while it was withered.
                    val alive = plantDao.getAlive()
                    val spotTaken = alive.any {
                        it.onShelf == plant.onShelf && it.gardenX == plant.gardenX &&
                            (plant.onShelf || it.gardenY == plant.gardenY)
                    }
                    if (spotTaken) {
                        val spot = garden.nextFreeSpot()
                        plantDao.move(plant.id, spot.x, spot.y, spot.onShelf)
                    }
                    garden.updateStates(listOf(GameMappers.toStateUpdate(plant, r.plant)))
                    val name = GameMappers.displayName(species)
                    val paid = wallet.debitElixir(r.elixirSpent, LedgerKind.REVIVE, note = name, at = now)
                    check(paid) { "elixir balance changed inside transaction" }
                    ReviveOutcome.Revived(plant.id, name, r.wallet.elixir)
                }
            }
        }
    }
}
