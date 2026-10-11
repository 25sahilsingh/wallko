package com.example.wallko.ui.scan

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Pure helpers for the scan flow (no Android types; JVM-tested). */
object ScanMath {

    /** Longest edge of the photo we keep and classify. The sprite is 384 px and the classifier 224 px. */
    const val MAX_PHOTO_EDGE = 1600
    const val JPEG_QUALITY = 90
    const val PHOTOS_DIR = "photos"

    /** Wraps any rotation to one of 0, 90, 180, 270. */
    fun normalizeRotation(degrees: Int): Int = (((degrees % 360) + 360) % 360 + 45) / 90 % 4 * 90

    /** Size after rotating by [rotationDegrees] (width/height swap at 90 and 270). */
    fun rotatedSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (normalizeRotation(rotationDegrees) % 180 == 0) width to height else height to width

    /** Scales (w, h) down so the longest edge is at most [maxEdge], keeping aspect ratio. Never upscales. */
    fun fitWithin(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxEdge > 0)
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longest
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneOffset.UTC)

    /** `photos/<timestamp>.jpg` file name, sortable, UTC so it never collides across zone changes. */
    fun photoFileName(epochMillis: Long): String = FILE_STAMP.format(Instant.ofEpochMilli(epochMillis)) + ".jpg"

    /** Title for a guess row: common name when known, else the Latin name. */
    fun guessTitle(common: String?, latin: String): String = common?.trim()?.takeIf { it.isNotEmpty() } ?: latin

    /** Latin subtitle, hidden when it would just repeat the title. */
    fun guessSubtitle(common: String?, latin: String): String? = if (guessTitle(common, latin) == latin) null else latin

    /**
     * Initially selected guess: the top one when confident, nothing otherwise (the player must pick).
     * Returns an index into the guess list, or null.
     */
    fun initialSelection(guessCount: Int, confident: Boolean): Int? = if (confident && guessCount > 0) 0 else null

    /**
     * A short place name from Geocoder address parts: the most local non-blank of
     * neighbourhood → town → district → region → country. Street-level parts are skipped on purpose.
     */
    fun shortPlaceName(
        subLocality: String?,
        locality: String?,
        subAdminArea: String?,
        adminArea: String?,
        countryName: String?,
    ): String? = listOf(subLocality, locality, subAdminArea, adminArea, countryName)
        .firstNotNullOfOrNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
}
