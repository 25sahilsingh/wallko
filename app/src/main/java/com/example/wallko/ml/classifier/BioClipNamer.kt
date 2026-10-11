package com.example.wallko.ml.classifier

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.wallko.core.config.GameRules
import com.example.wallko.ml.image.ImageMath
import com.example.wallko.ml.image.argbPixels
import com.example.wallko.ml.image.crop
import com.example.wallko.ml.image.ensureArgb8888
import com.example.wallko.ml.image.singleThreadDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * BioCLIP image encoder (ONNX Runtime, int8) + precomputed text table: names a plant photo from a
 * custom species list (AIY's labels + houseplants). See docs/ml-research.md.
 *
 * The model is too big for the APK, so it is read from `<external files>/bioclip/` ([DIR]). Missing
 * files mean "not available" (checked again on the next call); a file that fails to load disables
 * BioCLIP until the app restarts. Loading and inference run on one dedicated thread.
 */
@Singleton
class BioClipNamer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dispatcher = singleThreadDispatcher("wallko-bioclip")

    /** Only touched on [dispatcher]. */
    private var engine: Engine? = null
    private var failed = false

    /** Row labels (`latin|common`), or null if BioCLIP is not available. Loads the model on first use. */
    suspend fun labels(): List<SpeciesLabel>? = withContext(dispatcher) { engine()?.labels }

    /** Probability per text-table row for [bitmap] (upright, any size), or null if BioCLIP is not available. */
    suspend fun rowProbabilities(bitmap: Bitmap): FloatArray? = withContext(dispatcher) { engine()?.infer(bitmap) }

    private fun engine(): Engine? {
        engine?.let { return it }
        if (failed) return null
        val dir = File(context.getExternalFilesDir(null) ?: return null, DIR)
        val files = listOf(MODEL_FILE, TABLE_FILE, LABELS_FILE).map { File(dir, it) }
        if (!files.all { it.isFile }) return null
        return try {
            Engine.load(files[0], files[1], files[2]).also { engine = it }
        } catch (e: Throwable) {
            // OrtException, a bad table, or a missing native lib: stay on AIY-only.
            Log.w(TAG, "BioCLIP failed to load; AIY only", e)
            failed = true
            null
        }
    }

    private class Engine(
        val env: OrtEnvironment,
        val session: OrtSession,
        val table: FloatArray,
        val labels: List<SpeciesLabel>,
    ) {
        private val size = GameRules.Ml.BIOCLIP_INPUT_SIZE
        private val input = FloatArray(3 * size * size)
        private val shape = longArrayOf(1, 3, size.toLong(), size.toLong())

        fun infer(bitmap: Bitmap): FloatArray {
            BioClipMath.normalize(preprocess(bitmap).argbPixels(), size, input)
            val embedding = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { t ->
                session.run(mapOf(INPUT_NAME to t)).use { r ->
                    @Suppress("UNCHECKED_CAST")
                    (r[0].value as Array<FloatArray>)[0]
                }
            }
            check(embedding.size * labels.size == table.size) { "Embedding ${embedding.size} doesn't match the table" }
            return BioClipMath.rowProbabilities(embedding, table)
        }

        /** CLIP: short side → [size] (filtered), centre crop [size]². Halves first so big photos don't alias. */
        private fun preprocess(bitmap: Bitmap): Bitmap {
            var src = bitmap.ensureArgb8888()
            while (minOf(src.width, src.height) >= 2 * size) {
                src = Bitmap.createScaledBitmap(src, src.width / 2, src.height / 2, true)
            }
            val (w, h) = BioClipMath.shortSideTo(src.width, src.height, size)
            val scaled = Bitmap.createScaledBitmap(src, w, h, true)
            return scaled.crop(ImageMath.centreCropRect(w, h))
        }

        companion object {
            fun load(model: File, tableFile: File, labelsFile: File): Engine {
                val labels = ClassifierMath.splitLabelLines(labelsFile.readText(Charsets.UTF_8)).map(BioClipMath::parseLabelLine)
                val table = readFloats(tableFile)
                check(labels.isNotEmpty() && table.size % labels.size == 0) {
                    "Table of ${table.size} floats doesn't fit ${labels.size} labels"
                }
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(GameRules.Ml.BIOCLIP_THREADS) }
                val session = env.createSession(model.absolutePath, opts)
                Log.i(TAG, "Loaded ${labels.size} rows × ${table.size / labels.size}")
                return Engine(env, session, table, labels)
            }

            /** Little-endian float32. */
            private fun readFloats(f: File): FloatArray {
                val buf = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                return FloatArray(buf.remaining()).also { buf.get(it) }
            }
        }
    }

    companion object {
        private const val TAG = "BioClipNamer"
        const val DIR = "bioclip"
        const val MODEL_FILE = "bioclip_img_int8.onnx"
        const val TABLE_FILE = "text_table_f32.bin"
        const val LABELS_FILE = "text_labels.txt"
        private const val INPUT_NAME = "pixels"
    }
}
