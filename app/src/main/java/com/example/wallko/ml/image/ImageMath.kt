package com.example.wallko.ml.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Half-open pixel rectangle [left, right) × [top, bottom). Pure Kotlin so it works in JVM unit tests. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0
}

/** Pure image maths on plain ints / IntArray ARGB pixels. No Android types. */
object ImageMath {

    /** Largest centred square inside a [width]×[height] image. */
    fun centreCropRect(width: Int, height: Int): PixelRect {
        require(width > 0 && height > 0) { "Empty image $width×$height" }
        val side = min(width, height)
        val left = (width - side) / 2
        val top = (height - side) / 2
        return PixelRect(left, top, left + side, top + side)
    }

    /**
     * Grows [rect] by `max(width, height) * fraction` (at least [minPadPx]) on every side,
     * clamped to the [imageWidth]×[imageHeight] image.
     */
    fun padRect(rect: PixelRect, fraction: Float, imageWidth: Int, imageHeight: Int, minPadPx: Int = 0): PixelRect {
        val pad = max((max(rect.width, rect.height) * fraction).roundToInt(), minPadPx)
        return PixelRect(
            (rect.left - pad).coerceAtLeast(0),
            (rect.top - pad).coerceAtLeast(0),
            (rect.right + pad).coerceAtMost(imageWidth),
            (rect.bottom + pad).coerceAtMost(imageHeight),
        )
    }

    /** Where a [srcWidth]×[srcHeight] image lands when scaled to fit, centred, inside a [target]² square. */
    fun letterboxRect(srcWidth: Int, srcHeight: Int, target: Int): PixelRect {
        require(srcWidth > 0 && srcHeight > 0 && target > 0)
        val scale = min(target.toFloat() / srcWidth, target.toFloat() / srcHeight)
        val w = (srcWidth * scale).roundToInt().coerceIn(1, target)
        val h = (srcHeight * scale).roundToInt().coerceIn(1, target)
        val left = (target - w) / 2
        val top = (target - h) / 2
        return PixelRect(left, top, left + w, top + h)
    }

    /** Size scaled down (never up) so the long edge is at most [maxEdge]. */
    fun fitWithin(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        val long = max(width, height)
        if (long <= maxEdge) return width to height
        val s = maxEdge.toFloat() / long
        return (width * s).roundToInt().coerceAtLeast(1) to (height * s).roundToInt().coerceAtLeast(1)
    }

    /** Tight bounds of pixels whose alpha is > [threshold], or null if none. */
    fun alphaTrimBounds(pixels: IntArray, width: Int, height: Int, threshold: Int = 0): PixelRect? {
        require(pixels.size >= width * height)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((pixels[row + x] ushr 24) > threshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < 0) null else PixelRect(minX, minY, maxX + 1, maxY + 1)
    }

    /** Tight bounds of mask values ≥ [threshold], or null if none. */
    fun maskBounds(mask: FloatArray, width: Int, height: Int, threshold: Float): PixelRect? {
        require(mask.size >= width * height)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (mask[row + x] >= threshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < 0) null else PixelRect(minX, minY, maxX + 1, maxY + 1)
    }

    /** Fraction of mask values ≥ [threshold]. */
    fun maskCoverage(mask: FloatArray, count: Int, threshold: Float): Float {
        if (count <= 0) return 0f
        var n = 0
        for (i in 0 until count) if (mask[i] >= threshold) n++
        return n.toFloat() / count
    }

    /** Replaces the alpha of each pixel with `mask * 255` (in place). */
    fun applyMask(pixels: IntArray, mask: FloatArray, count: Int = pixels.size) {
        for (i in 0 until count) {
            val a = (mask[i].coerceIn(0f, 1f) * 255f).roundToInt()
            pixels[i] = (a shl 24) or (pixels[i] and 0x00FFFFFF)
        }
    }

    /** Extracts the alpha channel (0..255) of ARGB pixels. */
    fun alphaOf(pixels: IntArray): IntArray = IntArray(pixels.size) { pixels[it] ushr 24 }

    /** Puts [alpha] (0..255) onto the RGB of [rgbPixels], producing ARGB pixels. */
    fun withAlpha(rgbPixels: IntArray, alpha: IntArray): IntArray =
        IntArray(rgbPixels.size) { (alpha[it].coerceIn(0, 255) shl 24) or (rgbPixels[it] and 0x00FFFFFF) }

    /**
     * Softens an alpha mask with a separable box blur of the given [radius] (edges clamp),
     * which feathers hard edges by about [radius] px. Radius 0 returns a copy.
     */
    fun featherAlpha(alpha: IntArray, width: Int, height: Int, radius: Int): IntArray {
        if (radius <= 0) return alpha.copyOf()
        val tmp = IntArray(width * height)
        val out = IntArray(width * height)
        val window = 2 * radius + 1
        // Horizontal pass.
        for (y in 0 until height) {
            val row = y * width
            var sum = 0
            for (k in -radius..radius) sum += alpha[row + k.coerceIn(0, width - 1)]
            for (x in 0 until width) {
                tmp[row + x] = (sum + window / 2) / window
                val outIdx = (x - radius).coerceIn(0, width - 1)
                val inIdx = (x + radius + 1).coerceIn(0, width - 1)
                sum += alpha[row + inIdx] - alpha[row + outIdx]
            }
        }
        // Vertical pass.
        for (x in 0 until width) {
            var sum = 0
            for (k in -radius..radius) sum += tmp[k.coerceIn(0, height - 1) * width + x]
            for (y in 0 until height) {
                out[y * width + x] = (sum + window / 2) / window
                val outIdx = (y - radius).coerceIn(0, height - 1)
                val inIdx = (y + radius + 1).coerceIn(0, height - 1)
                sum += tmp[inIdx * width + x] - tmp[outIdx * width + x]
            }
        }
        return out
    }

    /**
     * Alpha (0..1) of a soft oval inscribed in a [width]×[height] image at pixel ([x], [y]):
     * 1 inside `1 - feather` of the radius, smoothly falling to 0 at the oval edge.
     */
    fun ovalAlpha(x: Int, y: Int, width: Int, height: Int, feather: Float): Float {
        val rx = width / 2f
        val ry = height / 2f
        val dx = (x + 0.5f - rx) / rx
        val dy = (y + 0.5f - ry) / ry
        val d = sqrt(dx * dx + dy * dy)
        val inner = (1f - feather).coerceIn(0f, 1f)
        return when {
            d <= inner -> 1f
            d >= 1f -> 0f
            else -> {
                val t = (1f - d) / (1f - inner) // 0 at edge, 1 at inner
                t * t * (3f - 2f * t)
            }
        }
    }

    /** Multiplies existing alpha by [ovalAlpha] (in place). */
    fun applyOvalVignette(pixels: IntArray, width: Int, height: Int, feather: Float) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val a = ((pixels[i] ushr 24) * ovalAlpha(x, y, width, height, feather)).roundToInt()
                pixels[i] = (a shl 24) or (pixels[i] and 0x00FFFFFF)
            }
        }
    }
}
