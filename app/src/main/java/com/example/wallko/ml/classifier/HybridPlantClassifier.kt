package com.example.wallko.ml.classifier

import android.graphics.Bitmap
import android.util.Log
import com.example.wallko.data.plants.PlantRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AIY is the gate ("is this a plant?"); BioCLIP, when its files are on the phone, names the plant.
 * Without BioCLIP (or if it fails) this is exactly the AIY classifier. See [BioClipMath.hybridClassification].
 */
@Singleton
class HybridPlantClassifier @Inject constructor(
    private val aiy: AiyPlantClassifier,
    private val bioClip: BioClipNamer,
    private val plants: PlantRepository,
) : PlantClassifier {

    private val mappingMutex = Mutex()

    /** BioCLIP row → species index, built once; an empty array means the table can't be used. */
    private var rowSpecies: IntArray? = null

    override suspend fun classify(bitmap: Bitmap): Classification {
        val labels = aiy.labels()
        val background = aiy.backgroundIndex()
        return BioClipMath.hybridClassification(aiy.scores(bitmap), labels, background) {
            bioClipScores(bitmap, labels, background)
        }
    }

    private suspend fun bioClipScores(bitmap: Bitmap, aiyLabels: List<SpeciesLabel>, aiyBackground: Int): BioClipScores? =
        try {
            val labels = bioClip.labels()
            val rows = labels?.let { rowSpecies(it, aiyLabels, aiyBackground) }
            if (labels == null || rows == null || rows.isEmpty()) null
            else bioClip.rowProbabilities(bitmap)?.let { BioClipScores(it, rows, labels) }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.w(TAG, "BioCLIP failed; using AIY", e)
            null
        }

    private suspend fun rowSpecies(labels: List<SpeciesLabel>, aiyLabels: List<SpeciesLabel>, aiyBackground: Int): IntArray =
        mappingMutex.withLock {
            rowSpecies ?: run {
                if (!BioClipMath.aiyRowsMatch(labels, aiyLabels, aiyBackground)) {
                    Log.w(TAG, "BioCLIP table doesn't start with AIY's labels; using AIY")
                    IntArray(0)
                } else {
                    val rows = BioClipMath.rowSpecies(labels, aiyLabels.size, aiyBackground, plants.speciesIndexByLatin())
                    Log.i(TAG, "${rows.count { it >= 0 }} of ${rows.size} BioCLIP rows map to species")
                    rows
                }
            }.also { rowSpecies = it }
        }

    private companion object {
        const val TAG = "HybridPlantClassifier"
    }
}
