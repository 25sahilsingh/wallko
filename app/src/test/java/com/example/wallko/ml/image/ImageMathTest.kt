package com.example.wallko.ml.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMathTest {

    @Test
    fun centreCropRect() {
        assertEquals(PixelRect(20, 0, 80, 60), ImageMath.centreCropRect(100, 60))
        assertEquals(PixelRect(0, 20, 60, 80), ImageMath.centreCropRect(60, 100))
        assertEquals(PixelRect(0, 0, 50, 50), ImageMath.centreCropRect(50, 50))
        assertEquals(PixelRect(0, 0, 3, 3), ImageMath.centreCropRect(4, 3)) // odd remainder floors left
    }

    @Test
    fun letterboxRect() {
        assertEquals(PixelRect(0, 96, 384, 288), ImageMath.letterboxRect(200, 100, 384))
        assertEquals(PixelRect(96, 0, 288, 384), ImageMath.letterboxRect(100, 200, 384))
        assertEquals(PixelRect(0, 0, 384, 384), ImageMath.letterboxRect(10, 10, 384))
        val thin = ImageMath.letterboxRect(1000, 1, 384)
        assertEquals(384, thin.width)
        assertEquals(1, thin.height)
    }

    @Test
    fun padRectGrowsAndClamps() {
        assertEquals(PixelRect(40, 40, 160, 110), ImageMath.padRect(PixelRect(50, 50, 150, 100), 0.1f, 1000, 1000))
        assertEquals(PixelRect(0, 0, 120, 60), ImageMath.padRect(PixelRect(5, 5, 115, 55), 0.1f, 120, 60))
        assertEquals(PixelRect(46, 46, 58, 58), ImageMath.padRect(PixelRect(50, 50, 54, 54), 0f, 100, 100, minPadPx = 4))
    }

    @Test
    fun fitWithinOnlyShrinks() {
        assertEquals(800 to 600, ImageMath.fitWithin(800, 600, 1024))
        assertEquals(1024 to 768, ImageMath.fitWithin(4000, 3000, 1024))
        assertEquals(512 to 1024, ImageMath.fitWithin(2000, 4000, 1024))
    }

    @Test
    fun alphaTrimBounds() {
        val w = 6
        val h = 5
        val px = IntArray(w * h) { 0x00FFFFFF }
        px[1 * w + 2] = 0xFF000000.toInt()
        px[3 * w + 4] = 0x10000000
        assertEquals(PixelRect(2, 1, 5, 4), ImageMath.alphaTrimBounds(px, w, h))
        // Threshold hides the faint pixel.
        assertEquals(PixelRect(2, 1, 3, 2), ImageMath.alphaTrimBounds(px, w, h, threshold = 0x20))
        assertNull(ImageMath.alphaTrimBounds(IntArray(w * h), w, h))
    }

    @Test
    fun maskBoundsAndCoverage() {
        val mask = FloatArray(16)
        mask[5] = 0.9f // (1,1)
        mask[10] = 0.6f // (2,2)
        mask[15] = 0.4f // below threshold
        assertEquals(PixelRect(1, 1, 3, 3), ImageMath.maskBounds(mask, 4, 4, 0.5f))
        assertEquals(2f / 16f, ImageMath.maskCoverage(mask, 16, 0.5f), 1e-6f)
        assertNull(ImageMath.maskBounds(FloatArray(16), 4, 4, 0.5f))
    }

    @Test
    fun applyMaskAndWithAlpha() {
        val px = intArrayOf(0xFF112233.toInt(), 0xFF445566.toInt())
        ImageMath.applyMask(px, floatArrayOf(0f, 1f))
        assertEquals(0x00112233, px[0])
        assertEquals(0xFF445566.toInt(), px[1])
        assertEquals(intArrayOf(128, 255).toList(), ImageMath.alphaOf(intArrayOf(0x80000000.toInt(), -1)).toList())
        assertEquals(0x7F445566, ImageMath.withAlpha(intArrayOf(0xFF445566.toInt()), intArrayOf(0x7F))[0])
    }

    @Test
    fun featherKeepsFlatFieldsAndSoftensEdges() {
        val w = 10
        val h = 3
        val flat = IntArray(w * h) { 200 }
        assertTrue(ImageMath.featherAlpha(flat, w, h, 2).all { it == 200 })

        val step = IntArray(w * h) { if (it % w < 5) 0 else 255 }
        val out = ImageMath.featherAlpha(step, w, h, 2)
        assertEquals(0, out[0])
        assertEquals(255, out[w - 1])
        val mid = out[4]
        assertTrue("edge should be partially transparent, was $mid", mid in 1..254)
        // Monotonic across the edge.
        for (x in 1 until w) assertTrue(out[x] >= out[x - 1])
    }

    @Test
    fun ovalAlpha() {
        assertEquals(1f, ImageMath.ovalAlpha(50, 50, 100, 100, 0.25f), 0f)
        assertEquals(0f, ImageMath.ovalAlpha(0, 0, 100, 100, 0.25f), 0f)
        val edge = ImageMath.ovalAlpha(10, 50, 100, 100, 0.25f)
        assertTrue(edge > 0f && edge < 1f)
    }
}
