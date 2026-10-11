package com.example.wallko.ml.stylize

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Which transfer-model input is which, resolved from tensor shapes. */
data class TransferInputs(val contentIndex: Int, val styleIndex: Int)

/** Pure helpers for the Magenta stylizer (no Android). */
object StyleMath {

    /**
     * Content = the 4-D tensor whose last dim is 3 and spatial dims > 1; style = the one with
     * [styleSize] elements (`[1,1,1,100]`). Doesn't trust input order.
     */
    fun resolveTransferInputs(shapes: List<IntArray>, styleSize: Int = STYLE_VECTOR_SIZE): TransferInputs {
        val content = shapes.indexOfFirst { it.size == 4 && it[3] == 3 && it[1] > 1 && it[2] > 1 }
        val style = shapes.indexOfFirst { s -> s.fold(1) { a, d -> a * d } == styleSize && !(s.size == 4 && s[3] == 3) }
        require(content >= 0 && style >= 0 && content != style) {
            "Can't identify transfer inputs from ${shapes.map { it.contentToString() }}"
        }
        return TransferInputs(content, style)
    }

    /** ARGB pixels → RGB floats in 0..1, row-major (HWC). */
    fun pixelsToRgbFloats(pixels: IntArray, out: FloatArray = FloatArray(pixels.size * 3)): FloatArray {
        require(out.size >= pixels.size * 3)
        var o = 0
        for (p in pixels) {
            out[o++] = (p shr 16 and 0xFF) / 255f
            out[o++] = (p shr 8 and 0xFF) / 255f
            out[o++] = (p and 0xFF) / 255f
        }
        return out
    }

    /** RGB floats in 0..1 (clamped) → opaque ARGB pixels. */
    fun rgbFloatsToPixels(floats: FloatArray, out: IntArray = IntArray(floats.size / 3)): IntArray {
        require(floats.size >= out.size * 3)
        var f = 0
        for (i in out.indices) {
            val r = (floats[f++].coerceIn(0f, 1f) * 255f).roundToInt()
            val g = (floats[f++].coerceIn(0f, 1f) * 255f).roundToInt()
            val b = (floats[f++].coerceIn(0f, 1f) * 255f).roundToInt()
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    /**
     * Magenta style blending: `strength * style + (1 - strength) * content`. Mixing in the photo's own
     * style vector keeps the plant's colours and shapes instead of repainting it in the style image's.
     */
    fun blendVectors(style: FloatArray, content: FloatArray, strength: Float): FloatArray {
        require(style.size == content.size) { "Vector sizes differ: ${style.size} vs ${content.size}" }
        val s = strength.coerceIn(0f, 1f)
        return FloatArray(style.size) { s * style[it] + (1f - s) * content[it] }
    }

    /** Little-endian float32 encoding for the on-disk style-vector cache. */
    fun encodeVector(vector: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.asFloatBuffer().put(vector)
        return buf.array()
    }

    /** Decodes [encodeVector] output; null if the size is wrong or any value isn't finite. */
    fun decodeVector(bytes: ByteArray, expectedSize: Int = STYLE_VECTOR_SIZE): FloatArray? {
        if (bytes.size != expectedSize * 4) return null
        val out = FloatArray(expectedSize)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out)
        return out.takeIf { v -> v.all { it.isFinite() } }
    }

    const val STYLE_VECTOR_SIZE = 100
}
