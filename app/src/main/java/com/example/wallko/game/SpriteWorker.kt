package com.example.wallko.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.data.garden.GardenRepository
import com.example.wallko.ml.sprite.SpriteMaker
import com.example.wallko.ml.stylize.StyleId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Turns an owned plant's photo into its potted sprite (ml-pipeline.md § Make the potted sprite) and stores the
 * path on the plant. Input: [KEY_PLANT_ID]; optional [KEY_STYLE_ID] re-paints in that style and saves it as the
 * plant's style (used when the style changes in Settings).
 *
 * Missing plant → success (nothing to do). Missing/undecodable photo → failure (retrying can't help).
 * Anything else retries with exponential backoff, at most [MAX_ATTEMPTS] runs in total.
 */
@HiltWorker
class SpriteWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val spriteMaker: SpriteMaker,
    private val garden: GardenRepository,
    private val db: WallkoDatabase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val plantId = inputData.getLong(KEY_PLANT_ID, -1L)
        if (plantId < 0) return Result.failure()
        val plant = garden.getPlant(plantId) ?: return Result.success()
        val override = inputData.getString(KEY_STYLE_ID)?.let { styleIdFromName(it) }
        val style = override ?: styleIdFromName(plant.styleId)

        val photo = withContext(Dispatchers.IO) { decodePhoto(plant.photoPath) }
        if (photo == null) {
            Log.w(TAG, "photo missing or undecodable for plant $plantId: ${plant.photoPath}")
            return Result.failure()
        }

        return try {
            val sprite = spriteMaker.make(photo, style)
            val file = spriteFile(applicationContext, plantId)
            spriteMaker.saveSpritePng(sprite, file)
            db.withTransaction {
                if (override != null) {
                    // Re-read inside the transaction so a concurrent tick's state changes aren't overwritten.
                    db.ownedPlantDao().get(plantId)?.let { current ->
                        if (current.styleId != override.name) db.ownedPlantDao().update(current.copy(styleId = override.name))
                    }
                }
                garden.setSprite(plantId, file.absolutePath)
            }
            Result.success()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.w(TAG, "sprite for plant $plantId failed (attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS)", e)
            if (shouldRetry(runAttemptCount)) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "SpriteWorker"
        const val KEY_PLANT_ID = "plantId"
        const val KEY_STYLE_ID = "styleId"

        /** Total runs, including the first. */
        const val MAX_ATTEMPTS = 3
        private const val BACKOFF_SECONDS = 15L

        /** Photos are decoded at most about this big; SpriteMaker downsizes further anyway. */
        private const val MAX_DECODE_EDGE = 2048

        fun workName(plantId: Long): String = "sprite_$plantId"

        /** `runAttemptCount` is 0 on the first run. */
        fun shouldRetry(runAttemptCount: Int): Boolean = runAttemptCount + 1 < MAX_ATTEMPTS

        /** StyleId by enum name (case-insensitive); unknown → WATERCOLOUR. */
        fun styleIdFromName(name: String?): StyleId =
            StyleId.entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: StyleId.WATERCOLOUR

        /** Largest power-of-two sample size that keeps the long edge ≥ [maxEdge] (1 if already smaller). */
        fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
            var sample = 1
            while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
            return sample
        }

        fun spriteFile(context: Context, plantId: Long): File =
            File(File(context.filesDir, "sprites"), "sprite_$plantId.png")

        /** (Re)makes one plant's sprite. Replaces any pending job for the same plant. */
        fun enqueue(context: Context, plantId: Long, styleId: StyleId? = null) {
            val data = if (styleId == null) workDataOf(KEY_PLANT_ID to plantId)
            else workDataOf(KEY_PLANT_ID to plantId, KEY_STYLE_ID to styleId.name)
            val request = OneTimeWorkRequestBuilder<SpriteWorker>()
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(plantId), ExistingWorkPolicy.REPLACE, request)
        }

        /** Re-renders every listed plant, e.g. after the paint style changed in Settings. */
        fun requeueAll(context: Context, plantIds: Collection<Long>, styleId: StyleId? = null) {
            plantIds.forEach { enqueue(context, it, styleId) }
        }

        private fun decodePhoto(path: String): Bitmap? {
            val file = File(path)
            if (!file.isFile) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_DECODE_EDGE)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeFile(path, opts)
        }
    }
}
