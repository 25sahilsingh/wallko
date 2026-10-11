package com.example.wallko.ml.stylize

import android.graphics.Bitmap

/** Bundled style images (assets/styles, 384×384, Sunny Meadow palette). */
enum class StyleId(val assetPath: String) {
    WATERCOLOUR("styles/watercolour.jpg"),
    FLAT_PASTEL("styles/flat_pastel.jpg"),
    CRAYON("styles/crayon.jpg"),
}

/** Arbitrary style transfer. */
interface Stylizer {
    /** Returns an opaque bitmap at the model's content size (384×384); [content] is centre-cropped/scaled to fit. */
    suspend fun stylize(content: Bitmap, styleId: StyleId): Bitmap
}
