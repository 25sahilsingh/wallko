package com.example.wallko.ml.classifier

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.wallko.core.config.GameRules
import com.example.wallko.ml.image.ImageMath
import com.example.wallko.ml.image.argbPixels
import com.example.wallko.ml.image.crop
import com.example.wallko.ml.image.ensureArgb8888
import com.example.wallko.ml.image.singleThreadDispatcher
import com.google.ai.edge.litert.support.common.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/**
 * AIY Vision plants V1 (`[1,224,224,3] uint8 → [1,2102] uint8`). Labels come from the plain-text
 * assets extracted from the model's metadata; the background row is found by name.
 *
 * The interpreter is created lazily and every call is confined to one dedicated thread
 * (LiteRT's Interpreter is not thread-safe).
 */
@Singleton
class AiyPlantClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlantClassifier {

    private val dispatcher = singleThreadDispatcher("wallko-classifier")

    /** Only touched on [dispatcher]. */
    private var engine: Engine? = null

    override suspend fun classify(bitmap: Bitmap): Classification = withContext(dispatcher) {
        val e = engine()
        ClassifierMath.buildClassification(e.infer(bitmap), e.labels, e.backgroundIndex)
    }

    /** Every output score, background row included, indexed by model output index. */
    suspend fun scores(bitmap: Bitmap): FloatArray = withContext(dispatcher) { engine().infer(bitmap) }

    /** Every species score (background dropped), indexed by model output index. Used by tests/debug. */
    suspend fun speciesScores(bitmap: Bitmap): List<Pair<Int, Float>> = withContext(dispatcher) {
        val e = engine()
        ClassifierMath.withoutBackground(e.infer(bitmap), e.backgroundIndex)
    }

    /** All label rows, including the background row, indexed by model output index. */
    suspend fun labels(): List<SpeciesLabel> = withContext(dispatcher) { engine().labels }

    /** Index of the background row in the model output (2101 for AIY), or -1 if not found. */
    suspend fun backgroundIndex(): Int = withContext(dispatcher) { engine().backgroundIndex }

    private fun engine(): Engine = engine ?: Engine.load(context).also { engine = it }

    private class Engine(
        val interpreter: Interpreter,
        val labels: List<SpeciesLabel>,
        val backgroundIndex: Int,
        val inputSize: Int,
        val outputCount: Int,
        val outScale: Float,
        val outZeroPoint: Int,
    ) {
        private val input: ByteBuffer =
            ByteBuffer.allocateDirect(inputSize * inputSize * 3).order(ByteOrder.nativeOrder())
        private val output: ByteBuffer = ByteBuffer.allocateDirect(outputCount).order(ByteOrder.nativeOrder())
        private val rgb = ByteArray(inputSize * inputSize * 3)
        private val raw = ByteArray(outputCount)

        fun infer(bitmap: Bitmap): FloatArray {
            val src = bitmap.ensureArgb8888()
            val square = src.crop(ImageMath.centreCropRect(src.width, src.height))
            val scaled = Bitmap.createScaledBitmap(square, inputSize, inputSize, true)
            ClassifierMath.packRgb(scaled.argbPixels(), rgb)
            input.clear()
            input.put(rgb)
            input.rewind()
            output.clear()
            interpreter.run(input, output)
            output.rewind()
            output.get(raw)
            return ClassifierMath.dequantize(raw, outScale, outZeroPoint)
        }

        companion object {
            fun load(context: Context): Engine {
                val model = FileUtil.loadMappedFile(context, MODEL_PATH)
                val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(NUM_THREADS))

                val inTensor = interpreter.getInputTensor(0)
                val inShape = inTensor.shape()
                check(inTensor.dataType() == DataType.UINT8 && inShape.size == 4 && inShape[3] == 3) {
                    "Unexpected AIY input ${inTensor.dataType()} ${inShape.contentToString()}"
                }
                val inputSize = inShape[1]
                if (inputSize != GameRules.Ml.CLASSIFIER_INPUT_SIZE) {
                    Log.w(TAG, "Model input $inputSize ≠ GameRules ${GameRules.Ml.CLASSIFIER_INPUT_SIZE}; using model's")
                }

                val outTensor = interpreter.getOutputTensor(0)
                check(outTensor.dataType() == DataType.UINT8) { "Unexpected AIY output ${outTensor.dataType()}" }
                val outputCount = outTensor.numElements()
                val q = outTensor.quantizationParams()

                val latin = readLines(context, LABELS_PATH)
                val english = runCatching { readLines(context, LABELS_EN_PATH) }
                    .onFailure { Log.w(TAG, "No English labels", it) }
                    .getOrNull()
                val labels = ClassifierMath.mergeLabels(latin, english)
                if (labels.size != outputCount) {
                    Log.w(TAG, "Label count ${labels.size} ≠ model outputs $outputCount")
                }
                val background = ClassifierMath.findBackgroundIndex(latin)
                if (background < 0) Log.w(TAG, "No background row found in labels; nothing dropped")

                return Engine(interpreter, labels, background, inputSize, outputCount, q.scale, q.zeroPoint)
            }

            private fun readLines(context: Context, path: String): List<String> =
                context.assets.open(path).bufferedReader(Charsets.UTF_8).use { ClassifierMath.splitLabelLines(it.readText()) }
        }
    }

    companion object {
        private const val TAG = "AiyPlantClassifier"
        const val MODEL_PATH = "models/aiy_plants_v1.tflite"
        const val LABELS_PATH = "models/aiy_plants_labels.txt"
        const val LABELS_EN_PATH = "models/aiy_plants_labels_en.txt"
        private const val NUM_THREADS = 2
    }
}
