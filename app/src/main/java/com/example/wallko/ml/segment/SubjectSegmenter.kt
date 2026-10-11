package com.example.wallko.ml.segment

import android.graphics.Bitmap
import com.example.wallko.ml.image.ImageMath
import com.example.wallko.ml.image.PixelRect
import com.example.wallko.ml.image.argbPixels
import com.example.wallko.ml.image.bitmapOf
import com.example.wallko.ml.image.crop
import com.example.wallko.ml.image.ensureArgb8888

/** Separates the main subject (the plant) from the background. */
interface SubjectSegmenter {
    /** Returns null on failure or an empty mask; callers then use [fallbackOvalVignette]. */
    suspend fun cutOut(bitmap: Bitmap): CutOut?
}

/**
 * @param foreground ARGB_8888 bitmap whose alpha is the subject mask (0 = background).
 * @param bounds subject bounding box in [foreground] pixel coordinates.
 * @param isFallback true if this came from [fallbackOvalVignette] rather than real segmentation.
 */
class CutOut(
    val foreground: Bitmap,
    val bounds: PixelRect,
    val isFallback: Boolean = false,
)

/** Fallback from risks.md: no mask, centre-crop with a soft oval vignette. */
fun fallbackOvalVignette(bitmap: Bitmap, feather: Float = OVAL_FEATHER): CutOut {
    val src = bitmap.ensureArgb8888()
    val rect = ImageMath.centreCropRect(src.width, src.height)
    val pixels = src.crop(rect).argbPixels()
    ImageMath.applyOvalVignette(pixels, rect.width, rect.height, feather)
    return CutOut(
        foreground = bitmapOf(pixels, rect.width, rect.height),
        bounds = PixelRect(0, 0, rect.width, rect.height),
        isFallback = true,
    )
}

/** Fraction of the oval radius used for the soft edge. */
private const val OVAL_FEATHER = 0.25f
