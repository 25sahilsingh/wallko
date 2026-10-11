package com.example.wallko.ml.sprite

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.example.wallko.core.config.GameRules
import com.example.wallko.ml.image.ImageMath
import com.example.wallko.ml.image.argbPixels
import com.example.wallko.ml.image.bitmapOf
import com.example.wallko.ml.image.crop
import com.example.wallko.ml.image.ensureArgb8888
import com.example.wallko.ml.segment.CutOut
import com.example.wallko.ml.segment.SubjectSegmenter
import com.example.wallko.ml.segment.fallbackOvalVignette
import com.example.wallko.ml.stylize.StyleId
import com.example.wallko.ml.stylize.Stylizer
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Photo → potted-plant sprite (ml-pipeline.md § Make the potted sprite):
 * cut-out (or oval fallback) → crop to box + padding → letterbox to 384 on Cream → stylise →
 * re-apply the resized mask with a ~2 px feather → trim transparent borders.
 * If stylisation fails, the (trimmed) cut-out itself is returned.
 */
@Singleton
class SpriteMaker @Inject constructor(
    private val segmenter: SubjectSegmenter,
    private val stylizer: Stylizer,
) {

    suspend fun make(photo: Bitmap, styleId: StyleId): Bitmap {
        val size = GameRules.Ml.SPRITE_SIZE
        val working = withContext(Dispatchers.Default) { downscale(photo.ensureArgb8888()) }

        val cut: CutOut = try {
            segmenter.cutOut(working)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.w(TAG, "Segmenter threw; using oval fallback", e)
            null
        } ?: withContext(Dispatchers.Default) { fallbackOvalVignette(working) }

        val (content, mask) = withContext(Dispatchers.Default) { letterbox(cut, size) }

        val stylised = try {
            stylizer.stylize(content, styleId)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.w(TAG, "Stylize failed; returning the cut-out", e)
            null
        }

        return withContext(Dispatchers.Default) {
            val maskPixels = mask.argbPixels()
            val rgb = if (stylised == null) {
                // The cut-out itself. Use the Cream-composited RGB so feathered edges don't pick up black.
                content.argbPixels()
            } else {
                val s = if (stylised.width == size && stylised.height == size) stylised.ensureArgb8888()
                else Bitmap.createScaledBitmap(stylised.ensureArgb8888(), size, size, true)
                s.argbPixels()
            }
            val alpha = ImageMath.featherAlpha(ImageMath.alphaOf(maskPixels), size, size, FEATHER_PX)
            val pixels = ImageMath.withAlpha(rgb, alpha)
            val bounds = ImageMath.alphaTrimBounds(pixels, size, size, TRIM_ALPHA_THRESHOLD)
            val full = bitmapOf(pixels, size, size)
            if (bounds == null || (bounds.width == size && bounds.height == size)) full else full.crop(bounds)
        }
    }

    /** Writes [bitmap] as PNG to [file] atomically (temp file + rename). */
    suspend fun saveSpritePng(bitmap: Bitmap, file: File) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        val ok = tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!ok) {
            tmp.delete()
            throw IOException("PNG encode failed for $file")
        }
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("Couldn't move sprite into $file")
        }
    }

    /**
     * Crops the cut-out to its padded box and letterboxes it into a [size]² square.
     * Returns (opaque content on Cream for the stylizer, transparent-background cut-out whose alpha is the mask).
     */
    private fun letterbox(cut: CutOut, size: Int): Pair<Bitmap, Bitmap> {
        val fg = cut.foreground
        val box = if (cut.isFallback) cut.bounds
        else ImageMath.padRect(cut.bounds, PAD_FRACTION, fg.width, fg.height, MIN_PAD_PX)
        val dst = ImageMath.letterboxRect(box.width, box.height, size)
        val srcRect = Rect(box.left, box.top, box.right, box.bottom)
        val dstRect = Rect(dst.left, dst.top, dst.right, dst.bottom)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        val content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(content).apply {
            drawColor(CREAM)
            drawBitmap(fg, srcRect, dstRect, paint)
        }
        val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(mask).drawBitmap(fg, srcRect, dstRect, paint)
        return content to mask
    }

    private fun downscale(src: Bitmap): Bitmap {
        val (w, h) = ImageMath.fitWithin(src.width, src.height, MAX_WORKING_EDGE)
        return if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)
    }

    private companion object {
        const val TAG = "SpriteMaker"
        /** Sunny Meadow Cream #FFF8E7. */
        const val CREAM = 0xFFFFF8E7.toInt()
        const val PAD_FRACTION = 0.06f
        const val MIN_PAD_PX = 4
        const val FEATHER_PX = 2
        const val TRIM_ALPHA_THRESHOLD = 4
        /** Photos are downscaled before segmentation to bound memory; the sprite is only 384 px. */
        const val MAX_WORKING_EDGE = 1024
    }
}
