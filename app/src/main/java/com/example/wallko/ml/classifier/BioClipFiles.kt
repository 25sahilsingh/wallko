package com.example.wallko.ml.classifier

import java.io.File
import java.net.URL
import java.security.MessageDigest

/**
 * The BioCLIP files [BioClipNamer] needs: where they're hosted, how big they are, their SHA-256 and
 * where on the phone they're looked for. [BioClipDownloadWorker] fetches them into `<files>/bioclip/`
 * on first run. See docs/ml-pipeline.md.
 */
object BioClipFiles {

    /** A GitHub release; each asset is at `BASE_URL + name` (redirects to GitHub's asset host). */
    const val BASE_URL = "https://github.com/25sahilsingh/wallko/releases/download/bioclip-v1/"
    const val DIR = "bioclip"

    data class Asset(val name: String, val size: Long, val sha256: String) {
        val url: String get() = BASE_URL + name
    }

    val MODEL = Asset(
        "bioclip_img_int8.onnx", 87_355_555L,
        "fed5c79eecc8a529e0b3f35fb5da170286ea592e6d9029c368433ca916269998",
    )
    val TABLE = Asset(
        "text_table_f32.bin", 4_427_776L,
        "8f7d8b7ffe8c750b0e500d4be64412caf8ebca6c9ad0ba3a0701439593054b77",
    )
    val LABELS = Asset(
        "text_labels.txt", 75_828L,
        "b76941108b8eb4a6cd748d68ab10e2123da11f6a16adf718986f6ef7ad85aaec",
    )

    /** In the order [BioClipNamer] loads them: model, table, labels. */
    val ALL = listOf(MODEL, TABLE, LABELS)

    const val BUFFER_SIZE = 64 * 1024

    /** All files exist in [dir] (sizes not checked, so adb-pushed dev builds of the files still load). */
    fun allPresent(dir: File): Boolean = ALL.all { File(dir, it.name).isFile }

    /** All files exist in [dir] with the right size: cheap enough for app start (no hashing). */
    fun allSized(dir: File): Boolean = ALL.all { sizeMatches(File(dir, it.name), it) }

    /**
     * The first of [candidates] that holds every file, or null. [BioClipNamer] passes the downloaded
     * dir (`<files>/bioclip/`) first, then the adb-push dir (`<external files>/bioclip/`, may be null).
     * A half-finished download only has some files, so it falls through to the next candidate.
     */
    fun firstComplete(candidates: List<File?>): File? = candidates.firstOrNull { it != null && allPresent(it) }

    fun sizeMatches(file: File, asset: Asset): Boolean = file.isFile && file.length() == asset.size

    /** [file] exists with [asset]'s size and SHA-256. Reads the whole file when the size matches. */
    fun isValid(file: File, asset: Asset): Boolean =
        sizeMatches(file, asset) && sha256Hex(file).equals(asset.sha256, ignoreCase = true)

    /** True if a download of [size] bytes hashing to [sha256Hex] is [asset]. */
    fun matches(asset: Asset, size: Long, sha256Hex: String): Boolean =
        size == asset.size && sha256Hex.equals(asset.sha256, ignoreCase = true)

    /**
     * Where an HTTP [code] response to [from] with header [location] sends us: the next URL for a 3xx
     * redirect, null for anything else. Only https targets are followed (throws otherwise), so a
     * redirect can't downgrade the download to plain http.
     */
    fun redirectTarget(from: URL, code: Int, location: String?): URL? {
        if (code !in REDIRECT_CODES) return null
        requireNotNull(location) { "HTTP $code from $from without a Location" }
        val next = URL(from, location)
        require(next.protocol == "https") { "Refusing redirect to ${next.protocol}: $next" }
        return next
    }

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return toHex(digest.digest())
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
