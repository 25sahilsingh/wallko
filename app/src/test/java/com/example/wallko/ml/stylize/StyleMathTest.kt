package com.example.wallko.ml.stylize

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StyleMathTest {

    @Test
    fun resolvesInputsByShapeInEitherOrder() {
        val content = intArrayOf(1, 384, 384, 3)
        val style = intArrayOf(1, 1, 1, 100)
        assertEquals(TransferInputs(0, 1), StyleMath.resolveTransferInputs(listOf(content, style)))
        assertEquals(TransferInputs(1, 0), StyleMath.resolveTransferInputs(listOf(style, content)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownShapes() {
        StyleMath.resolveTransferInputs(listOf(intArrayOf(1, 384, 384, 3), intArrayOf(1, 1, 1, 64)))
    }

    @Test
    fun pixelFloatRoundTrip() {
        val px = intArrayOf(0xFF000000.toInt(), 0xFFFF8040.toInt(), 0x00FFFFFF)
        val floats = StyleMath.pixelsToRgbFloats(px)
        assertEquals(1f, floats[3], 0f)
        assertEquals(128 / 255f, floats[4], 1e-6f)
        val back = StyleMath.rgbFloatsToPixels(floats)
        // Output is always opaque.
        assertArrayEquals(intArrayOf(0xFF000000.toInt(), 0xFFFF8040.toInt(), -1), back)
    }

    @Test
    fun floatsAreClamped() {
        assertArrayEquals(intArrayOf(0xFFFF0000.toInt()), StyleMath.rgbFloatsToPixels(floatArrayOf(1.7f, -0.2f, 0f)))
    }

    @Test
    fun vectorEncodingRoundTripsAndValidates() {
        val v = FloatArray(100) { it * 0.25f - 3f }
        assertArrayEquals(v, StyleMath.decodeVector(StyleMath.encodeVector(v)), 0f)
        assertNull(StyleMath.decodeVector(ByteArray(12)))
        val bad = v.copyOf().also { it[7] = Float.NaN }
        assertNull(StyleMath.decodeVector(StyleMath.encodeVector(bad)))
    }

    @Test
    fun blendVectorsMixesStyleAndContent() {
        val style = floatArrayOf(1f, 2f)
        val content = floatArrayOf(0f, 4f)
        assertArrayEquals(floatArrayOf(0.4f, 3.2f), StyleMath.blendVectors(style, content, 0.4f), 1e-6f)
        assertArrayEquals(style, StyleMath.blendVectors(style, content, 1f), 0f)
        // Strength is clamped to 0..1.
        assertArrayEquals(content, StyleMath.blendVectors(style, content, -3f), 0f)
    }
}
