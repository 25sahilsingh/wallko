package com.example.wallko.ml.image

import android.graphics.Bitmap
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/** Software ARGB_8888 copy if needed (e.g. HARDWARE or RGB_565 bitmaps can't be read pixel-by-pixel). */
internal fun Bitmap.ensureArgb8888(): Bitmap =
    if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)

internal fun Bitmap.crop(rect: PixelRect): Bitmap =
    Bitmap.createBitmap(this, rect.left, rect.top, rect.width, rect.height)

/** Unpremultiplied ARGB pixels. */
internal fun Bitmap.argbPixels(): IntArray {
    val out = IntArray(width * height)
    getPixels(out, 0, width, 0, 0, width, height)
    return out
}

/** New mutable ARGB_8888 bitmap from unpremultiplied ARGB [pixels]. */
internal fun bitmapOf(pixels: IntArray, width: Int, height: Int): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }

/** One dedicated daemon thread; used to confine non-thread-safe LiteRT interpreters. */
internal fun singleThreadDispatcher(name: String): CoroutineDispatcher =
    Executors.newSingleThreadExecutor { r -> Thread(r, name).apply { isDaemon = true } }
        .asCoroutineDispatcher()
