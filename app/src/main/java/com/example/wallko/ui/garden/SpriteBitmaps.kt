package com.example.wallko.ui.garden

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded sprite PNGs, keyed by path + file version so a re-render (style change) is picked up. */
internal object SpriteBitmaps {
    private const val MAX_BYTES = 16 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun cached(path: String, version: Long): ImageBitmap? = cache.get(key(path, version))

    suspend fun load(path: String, version: Long): ImageBitmap? = cached(path, version) ?: withContext(Dispatchers.IO) {
        BitmapFactory.decodeFile(path)?.asImageBitmap()?.also { cache.put(key(path, version), it) }
    }

    private fun key(path: String, version: Long) = "$path@$version"
}

/** The plant's sprite, or null while loading / when there is none (PottedPlant then draws the sprout). */
@Composable
fun rememberSpriteBitmap(path: String?, version: Long): State<ImageBitmap?> =
    produceState(initialValue = path?.let { SpriteBitmaps.cached(it, version) }, path, version) {
        value = path?.let { SpriteBitmaps.load(it, version) }
    }
