package com.example.wallko.ml

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.wallko.core.config.GameRules
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.core.prefs.SettingsStore
import com.example.wallko.data.plants.PlantRepository
import com.example.wallko.ml.classifier.AiyPlantClassifier
import com.example.wallko.ml.classifier.BioClipNamer
import com.example.wallko.ml.classifier.HybridPlantClassifier
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The full scan classifier (AIY gate -> BioCLIP namer, as bound in MlModule) on a real phone, over the
 * photos in `<external files>/bioclip/photos/` (skipped if the BioCLIP files or photos are missing).
 *
 * Built the way Hilt builds it, except the species catalogue lives in an in-memory Room DB and a
 * throwaway DataStore, so the user's garden data is never touched. Each photo is decoded to a full-size
 * Bitmap and passed straight to classify(), like ScanViewModel does with the captured frame.
 *
 * Results are logged under tag "HybridScanDeviceTest".
 */
@RunWith(AndroidJUnit4::class)
class HybridScanDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun gateBlocksRoomsAndNamesPlants() = runBlocking {
        val dir = File(context.getExternalFilesDir(null), "bioclip")
        assumeTrue("Push the BioCLIP files to $dir first", File(dir, "bioclip_img_int8.onnx").isFile)
        val photos = File(dir, "photos").listFiles { f -> f.extension.lowercase() == "jpg" }.orEmpty().sortedBy { it.name }
        assumeTrue("No photos in $dir/photos", photos.isNotEmpty())

        val db = Room.inMemoryDatabaseBuilder(context, WallkoDatabase::class.java).build()
        val prefsFile = File(context.cacheDir, "hybrid_scan_test/settings.preferences_pb").apply { parentFile?.mkdirs(); delete() }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { prefsFile })
            val plants = PlantRepository(db.plantSpeciesDao(), settings, context)
            val aiy = AiyPlantClassifier(context)
            val classifier = HybridPlantClassifier(aiy, BioClipNamer(context), plants)

            val labels = aiy.labels()
            val bg = aiy.backgroundIndex()
            log("rules: MAX_BACKGROUND_SCORE=${GameRules.Ml.MAX_BACKGROUND_SCORE} MIN_GUESS_SCORE=${GameRules.Ml.MIN_GUESS_SCORE}")

            // Warm-up (loads AIY, BioCLIP and the row mapping) so per-photo times are steady-state.
            val w0 = System.nanoTime()
            classifier.classify(BitmapFactory.decodeFile(photos.first().absolutePath))
            log("warm-up ${ms(w0)} ms")

            for (photo in photos) {
                val bitmap = BitmapFactory.decodeFile(photo.absolutePath)
                val scores = aiy.scores(bitmap)
                val bgScore = scores.getOrNull(bg) ?: 0f
                val topAiy = scores.indices.filter { it != bg }.maxByOrNull { scores[it] }
                val topAiyText = topAiy?.let { "${labels[it].common ?: labels[it].latin} %.2f".format(scores[it]) } ?: "-"

                val t0 = System.nanoTime()
                val result = classifier.classify(bitmap)
                val took = ms(t0)
                val verdict = when {
                    result.top3.isEmpty() -> "NO PLANT"
                    else -> (if (result.confident) "CONFIDENT: " else "GUESSES: ") +
                        result.top3.joinToString(", ") { "${it.common ?: it.latin} %.2f".format(it.score) }
                }
                log("${photo.name} ${bitmap.width}x${bitmap.height} | AIY bg %.2f, top species $topAiyText | $verdict | $took ms".format(bgScore))
            }
        } finally {
            db.close()
            scope.cancel()
            prefsFile.parentFile?.deleteRecursively()
        }
    }

    private fun ms(start: Long) = (System.nanoTime() - start) / 1_000_000

    private fun log(msg: String) {
        Log.i(TAG, msg)
        println("$TAG: $msg")
    }

    private companion object {
        const val TAG = "HybridScanDeviceTest"
    }
}
