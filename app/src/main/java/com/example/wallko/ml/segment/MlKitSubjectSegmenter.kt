package com.example.wallko.ml.segment

import android.graphics.Bitmap
import android.util.Log
import com.example.wallko.ml.image.ImageMath
import com.example.wallko.ml.image.argbPixels
import com.example.wallko.ml.image.bitmapOf
import com.example.wallko.ml.image.ensureArgb8888
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter as MlKitSegmenterClient

/**
 * ML Kit subject segmentation (beta, Play Services). The alpha of the returned cut-out is the soft
 * foreground confidence mask; the ML Kit foreground bitmap is used only if no mask comes back.
 */
@Singleton
class MlKitSubjectSegmenter @Inject constructor() : SubjectSegmenter {

    private val client: MlKitSegmenterClient by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .enableForegroundBitmap()
                .build(),
        )
    }

    override suspend fun cutOut(bitmap: Bitmap): CutOut? {
        val src = withContext(Dispatchers.Default) { bitmap.ensureArgb8888() }
        val result = try {
            client.process(InputImage.fromBitmap(src, 0)).await()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            // Includes "module not yet downloaded" on first use.
            Log.w(TAG, "Subject segmentation failed", e)
            return null
        }
        return withContext(Dispatchers.Default) { toCutOut(src, result) }
    }

    private fun toCutOut(src: Bitmap, result: SubjectSegmentationResult): CutOut? {
        val w = src.width
        val h = src.height
        val count = w * h
        val maskBuffer = result.foregroundConfidenceMask
        if (maskBuffer != null && maskBuffer.capacity() >= count) {
            val mask = FloatArray(count)
            maskBuffer.rewind()
            maskBuffer.get(mask)
            if (ImageMath.maskCoverage(mask, count, MASK_THRESHOLD) < MIN_COVERAGE) return null
            val bounds = ImageMath.maskBounds(mask, w, h, MASK_THRESHOLD) ?: return null
            val pixels = src.argbPixels()
            ImageMath.applyMask(pixels, mask, count)
            return CutOut(bitmapOf(pixels, w, h), bounds)
        }
        val fg = result.foregroundBitmap?.ensureArgb8888() ?: return null
        val pixels = fg.argbPixels()
        val bounds = ImageMath.alphaTrimBounds(pixels, fg.width, fg.height, ALPHA_THRESHOLD) ?: return null
        if (bounds.width * bounds.height < MIN_COVERAGE * fg.width * fg.height) return null
        return CutOut(fg, bounds)
    }

    private companion object {
        const val TAG = "MlKitSubjectSegmenter"
        const val MASK_THRESHOLD = 0.5f
        const val ALPHA_THRESHOLD = 127
        /** Below this fraction of foreground pixels the mask counts as empty. */
        const val MIN_COVERAGE = 0.005f
    }
}
