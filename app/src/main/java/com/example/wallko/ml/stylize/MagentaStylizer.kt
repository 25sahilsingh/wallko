package com.example.wallko.ml.stylize

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.wallko.core.config.GameRules
import com.example.wallko.ml.image.ImageMath
import com.example.wallko.ml.image.argbPixels
import com.example.wallko.ml.image.bitmapOf
import com.example.wallko.ml.image.crop
import com.example.wallko.ml.image.ensureArgb8888
import com.example.wallko.ml.image.singleThreadDispatcher
import com.google.ai.edge.litert.support.common.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter

/**
 * Magenta arbitrary-image-stylization (int8 weights, float32 I/O).
 * - `style_predict.tflite`: style image [1,256,256,3] (0..1) → style vector [1,1,1,100].
 * - `style_transfer.tflite`: content [1,384,384,3] (0..1) + style vector → [1,384,384,3] (0..1).
 *
 * Style vectors are cached in memory and on disk (`filesDir/style_vectors/<style>.bin`).
 * Both interpreters are lazy and confined to one dedicated thread.
 */
@Singleton
class MagentaStylizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : Stylizer {

    private val dispatcher = singleThreadDispatcher("wallko-stylizer")

    // All below only touched on [dispatcher].
    private var predict: Interpreter? = null
    private var transfer: Transfer? = null
    private val vectors = HashMap<StyleId, FloatArray>()

    override suspend fun stylize(content: Bitmap, styleId: StyleId): Bitmap = withContext(dispatcher) {
        val vector = StyleMath.blendVectors(
            style = styleVectorOnThread(styleId),
            content = predictVector(content.ensureArgb8888()),
            strength = GameRules.Ml.STYLE_STRENGTH,
        )
        transfer().run(content, vector)
    }

    /** Style vector for [styleId] (memory → disk → model). Exposed so callers can warm the cache. */
    suspend fun styleVector(styleId: StyleId): FloatArray = withContext(dispatcher) {
        styleVectorOnThread(styleId).copyOf()
    }

    private fun styleVectorOnThread(styleId: StyleId): FloatArray {
        vectors[styleId]?.let { return it }
        val file = vectorFile(styleId)
        val fromDisk = runCatching { if (file.exists()) StyleMath.decodeVector(file.readBytes()) else null }.getOrNull()
        val vector = fromDisk ?: predictVector(decodeStyle(styleId)).also { v ->
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeBytes(StyleMath.encodeVector(v))
                if (!tmp.renameTo(file)) throw IOException("rename failed")
            }.onFailure { Log.w(TAG, "Couldn't cache style vector for $styleId", it) }
        }
        vectors[styleId] = vector
        return vector
    }

    private fun vectorFile(styleId: StyleId) =
        File(File(context.filesDir, VECTOR_DIR), "${styleId.name.lowercase()}$VECTOR_VERSION.bin")

    private fun decodeStyle(styleId: StyleId): Bitmap =
        context.assets.open(styleId.assetPath).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: throw IOException("Can't decode ${styleId.assetPath}")

    /** Style vector of any image: a bundled style image, or the photo itself for blending. */
    private fun predictVector(image: Bitmap): FloatArray {
        val interpreter = predict ?: Interpreter(
            FileUtil.loadMappedFile(context, PREDICT_MODEL),
            Interpreter.Options().setNumThreads(NUM_THREADS),
        ).also { predict = it }

        val inShape = interpreter.getInputTensor(0).shape()
        check(inShape.size == 4 && inShape[3] == 3) { "Unexpected style_predict input ${inShape.contentToString()}" }
        val h = inShape[1]
        val w = inShape[2]

        val style = scaleCentreCrop(image, w, h)

        val input = floatBuffer(w * h * 3)
        input.asFloatBuffer().put(StyleMath.pixelsToRgbFloats(style.argbPixels()))
        val outCount = interpreter.getOutputTensor(0).numElements()
        val output = floatBuffer(outCount)
        interpreter.run(input, output)
        output.rewind()
        return FloatArray(outCount).also { output.asFloatBuffer().get(it) }
    }

    private fun transfer(): Transfer = transfer ?: Transfer(
        Interpreter(FileUtil.loadMappedFile(context, TRANSFER_MODEL), Interpreter.Options().setNumThreads(NUM_THREADS)),
    ).also { transfer = it }

    private class Transfer(val interpreter: Interpreter) {
        val inputs: TransferInputs =
            StyleMath.resolveTransferInputs(List(interpreter.inputTensorCount) { interpreter.getInputTensor(it).shape() })
        private val contentShape = interpreter.getInputTensor(inputs.contentIndex).shape()
        val height = contentShape[1]
        val width = contentShape[2]
        val styleSize = interpreter.getInputTensor(inputs.styleIndex).numElements()
        private val contentBuf = floatBuffer(width * height * 3)
        private val styleBuf = floatBuffer(styleSize)
        private val outBuf = floatBuffer(interpreter.getOutputTensor(0).numElements())
        private val outShape = interpreter.getOutputTensor(0).shape()

        init {
            check(outShape.size == 4 && outShape[1] == height && outShape[2] == width && outShape[3] == 3) {
                "Unexpected style_transfer output ${outShape.contentToString()}"
            }
            if (width != GameRules.Ml.SPRITE_SIZE || height != GameRules.Ml.SPRITE_SIZE) {
                Log.w(TAG, "Transfer content size ${width}×$height ≠ GameRules ${GameRules.Ml.SPRITE_SIZE}")
            }
        }

        fun run(content: Bitmap, vector: FloatArray): Bitmap {
            require(vector.size == styleSize) { "Style vector ${vector.size} ≠ $styleSize" }
            val scaled = scaleCentreCrop(content.ensureArgb8888(), width, height)
            contentBuf.clear()
            contentBuf.asFloatBuffer().put(StyleMath.pixelsToRgbFloats(scaled.argbPixels()))
            styleBuf.clear()
            styleBuf.asFloatBuffer().put(vector)
            val ins = arrayOfNulls<Any>(interpreter.inputTensorCount)
            ins[inputs.contentIndex] = contentBuf.rewind()
            ins[inputs.styleIndex] = styleBuf.rewind()
            outBuf.clear()
            interpreter.runForMultipleInputsOutputs(ins, mapOf(0 to outBuf))
            outBuf.rewind()
            val floats = FloatArray(width * height * 3)
            outBuf.asFloatBuffer().get(floats)
            return bitmapOf(StyleMath.rgbFloatsToPixels(floats), width, height)
        }
    }

    private companion object {
        const val TAG = "MagentaStylizer"
        const val PREDICT_MODEL = "models/style_predict.tflite"
        const val TRANSFER_MODEL = "models/style_transfer.tflite"
        const val VECTOR_DIR = "style_vectors"
        /** Bump if the style images or predict model change, to invalidate disk caches. */
        const val VECTOR_VERSION = "_v1"
        const val NUM_THREADS = 2

        fun floatBuffer(floats: Int): ByteBuffer = ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder())

        fun scaleCentreCrop(src: Bitmap, w: Int, h: Int): Bitmap {
            if (src.width == w && src.height == h) return src
            val square = if (w == h) src.crop(ImageMath.centreCropRect(src.width, src.height)) else src
            return Bitmap.createScaledBitmap(square, w, h, true)
        }
    }
}
