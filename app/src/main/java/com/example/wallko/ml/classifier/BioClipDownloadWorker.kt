package com.example.wallko.ml.classifier

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Downloads the BioCLIP files ([BioClipFiles]) into `<files>/bioclip/` so scans can name plants with
 * BioCLIP. Until they're all there, [BioClipNamer] finds nothing and scans use AIY only.
 *
 * Each file goes to `<name>.part`, is checked for size and SHA-256, then renamed into place, so the
 * namer never sees a partial file. Files already there and valid are skipped. Network errors and bad
 * downloads retry with exponential backoff, at most [MAX_ATTEMPTS] runs; the next app start tries again.
 */
@HiltWorker
class BioClipDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dir = BioClipNamer.downloadDir(applicationContext)
        try {
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Can't create $dir")
            for (asset in BioClipFiles.ALL) {
                val target = File(dir, asset.name)
                if (BioClipFiles.isValid(target, asset)) continue
                download(asset, dir, target)
                Log.i(TAG, "downloaded ${asset.name} (${asset.size} bytes)")
            }
            Log.i(TAG, "BioCLIP files ready")
            Result.success()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.w(TAG, "BioCLIP download failed (attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS)", e)
            if (shouldRetry(runAttemptCount)) Result.retry() else Result.failure()
        }
    }

    /** Streams [asset] to a temp file while hashing it, verifies, then atomically renames it to [target]. */
    private suspend fun download(asset: BioClipFiles.Asset, dir: File, target: File) {
        val part = File(dir, asset.name + PART_SUFFIX)
        val conn = open(URL(asset.url))
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code for ${conn.url}")

            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(BioClipFiles.BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        size += n
                        if (size > asset.size) throw IOException("${asset.name} is bigger than ${asset.size} bytes")
                        digest.update(buf, 0, n)
                        output.write(buf, 0, n)
                    }
                }
            }
            val hash = BioClipFiles.toHex(digest.digest())
            if (!BioClipFiles.matches(asset, size, hash)) {
                throw IOException("${asset.name}: got $size bytes, sha256 $hash; expected ${asset.size}, ${asset.sha256}")
            }
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            conn.disconnect()
            part.delete()
        }
    }

    /**
     * Connects to [start], following up to [MAX_REDIRECTS] https redirects by hand: GitHub sends release
     * assets to another host, and following them here doesn't depend on HttpURLConnection's own rules.
     */
    private fun open(start: URL): HttpURLConnection {
        var url = start
        repeat(MAX_REDIRECTS + 1) {
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            val next = try {
                BioClipFiles.redirectTarget(url, conn.responseCode, conn.getHeaderField("Location"))
            } catch (e: Exception) {
                conn.disconnect()
                throw e
            }
            if (next == null) return conn
            conn.disconnect()
            url = next
        }
        throw IOException("Too many redirects from $start")
    }

    companion object {
        private const val TAG = "BioClipDownload"
        const val WORK_NAME = "bioclip_download"
        const val PART_SUFFIX = ".part"

        /** Total runs, including the first. */
        const val MAX_ATTEMPTS = 5
        private const val BACKOFF_SECONDS = 60L
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5

        /** `runAttemptCount` is 0 on the first run. */
        fun shouldRetry(runAttemptCount: Int): Boolean = runAttemptCount + 1 < MAX_ATTEMPTS

        /**
         * Enqueues the download unless [BioClipNamer] can already find every file (downloaded, or adb-pushed
         * for development). Called on every app start; KEEP leaves a pending or running download alone.
         * Unmetered network only, not on low battery.
         */
        fun enqueueIfNeeded(context: Context) {
            if (BioClipFiles.firstComplete(BioClipNamer.candidateDirs(context)) != null) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<BioClipDownloadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
