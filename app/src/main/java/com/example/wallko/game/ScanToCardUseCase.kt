package com.example.wallko.game

import android.content.Context
import androidx.room.withTransaction
import com.example.wallko.core.config.GameRules
import com.example.wallko.core.db.LedgerKind
import com.example.wallko.core.db.Rarity
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.core.prefs.SettingsStore
import com.example.wallko.core.time.Clock
import com.example.wallko.data.garden.AddPlantResult
import com.example.wallko.data.garden.GardenRepository
import com.example.wallko.data.plants.PlantRepository
import com.example.wallko.data.wallet.WalletRepository
import com.example.wallko.ml.stylize.StyleId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Where the plant was found. All fields optional: location is never required to scan. */
data class FoundPlace(val lat: Double?, val lng: Double?, val placeName: String?) {
    companion object {
        val Unknown = FoundPlace(null, null, null)
    }
}

/** Outcome of [ScanToCardUseCase.confirm]. */
sealed interface ScanToCardResult {
    /** A new seedling was planted. [bonusDrops] is what actually landed in the tank (0 if none / tank full). */
    data class NewCard(
        val plantId: Long,
        val speciesIndex: Int,
        val displayName: String,
        val bonusDrops: Int,
    ) : ScanToCardResult

    /** Duplicates rule (decisions.md): a living plant of this species is already in the garden. Nothing written. */
    data class AlreadyInGarden(
        val plantId: Long,
        val speciesIndex: Int,
        val displayName: String,
    ) : ScanToCardResult

    /** The guess does not map to a species row (background class, or the catalogue isn't seeded). */
    data class UnknownSpecies(val outputIndex: Int) : ScanToCardResult
}

/**
 * Confirmed scan guess → card (phases.md Phase 2):
 * duplicate check → plant a SEEDLING at the next free spot with the current sprite style →
 * one-time Rare+ find bonus → queue sprite generation.
 *
 * Planting and the bonus are one Room transaction, and that transaction plus the sprite enqueue run
 * [NonCancellable]: once the plant row exists, the bonus and sprite job are never lost to the caller backing out.
 */
class ScanToCardUseCase @Inject constructor(
    private val db: WallkoDatabase,
    private val plants: PlantRepository,
    private val garden: GardenRepository,
    private val wallet: WalletRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
) {

    /**
     * @param outputIndex classifier output index of the guess the player picked.
     * @param photoPath absolute path of the saved JPEG. The caller owns deleting it if the result isn't
     *   [ScanToCardResult.NewCard], and must still skip the delete if an owned plant references the path
     *   ([GardenRepository.isPhotoReferenced]).
     */
    suspend fun confirm(outputIndex: Int, photoPath: String, place: FoundPlace = FoundPlace.Unknown): ScanToCardResult {
        val speciesIndex = plants.labelIndexToSpeciesIndex(outputIndex)
            ?: return ScanToCardResult.UnknownSpecies(outputIndex)
        val species = plants.byIndex(speciesIndex)
            ?: run { plants.ensureSeeded(); plants.byIndex(speciesIndex) }
            ?: return ScanToCardResult.UnknownSpecies(outputIndex)
        val name = GameMappers.displayName(species)

        val styleId = currentStyleId()
        val now = clock.now().toEpochMilli()

        // Everything from here writes state; finish it even if the caller is cancelled mid-way.
        return withContext(NonCancellable) {
            wallet.ensureInitialized() // outside the transaction: it takes a Mutex
            val (added, credited) = db.withTransaction {
                // "Ever found before" must be read before planting. ownsSpecies includes withered plants, so a
                // re-scanned withered species doesn't pay the Rare+ bonus twice. (No per-species ledger link exists.)
                val foundBefore = garden.isOwned(speciesIndex)
                val added = garden.addPlant(
                    speciesIndex = speciesIndex,
                    photoPath = photoPath,
                    lat = place.lat,
                    lng = place.lng,
                    placeName = place.placeName,
                    styleId = styleId.name,
                    now = now,
                )
                val bonus = if (added is AddPlantResult.Added) rareFindBonus(species.rarity, foundBefore) else 0
                // addPlant has already raised the tank capacity, so the bonus credits against the new tank.
                val credited = if (bonus > 0) {
                    wallet.credit(LedgerKind.RARE_FIND, bonus, note = name, at = now).dropsCredited
                } else 0
                added to credited
            }
            when (added) {
                is AddPlantResult.AlreadyOwned -> {
                    val existing = garden.getPlant(added.id)
                    if (existing?.photoPath == photoPath) {
                        // Retry of this same scan whose earlier attempt committed but then failed (e.g. the
                        // enqueue threw): show the card, and make sure its sprite is coming. Bonus already paid.
                        if (existing.spritePath == null) enqueueSprite(added.id)
                        ScanToCardResult.NewCard(added.id, speciesIndex, name, 0)
                    } else {
                        ScanToCardResult.AlreadyInGarden(added.id, speciesIndex, name)
                    }
                }
                is AddPlantResult.Added -> {
                    enqueueSprite(added.id)
                    ScanToCardResult.NewCard(added.id, speciesIndex, name, credited)
                }
            }
        }
    }

    /** Current Settings paint style; unknown/unset falls back the same way SpriteWorker does. */
    private suspend fun currentStyleId(): StyleId = SpriteWorker.styleIdFromName(settings.spriteStyleId.first())

    /** Unique work "sprite_<plantId>", input "plantId" (see [SpriteWorker.enqueue]). */
    private fun enqueueSprite(plantId: Long) = SpriteWorker.enqueue(context, plantId)

    companion object {
        /** game-rules.md: Rare-or-better card found → one-time +10 drops, only the first time the species is ever found. */
        fun rareFindBonus(rarity: Rarity, foundBefore: Boolean): Int =
            if (!foundBefore && rarity >= Rarity.RARE) GameRules.Earning.RARE_FIND_BONUS else 0
    }
}
