package com.example.wallko.ui.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Takes one picture in memory and returns it upright (EXIF/sensor rotation applied) and downscaled so the
 * longest edge is at most [maxEdge]. The conversion runs on [executor], off the main thread.
 */
suspend fun ImageCapture.takeUprightBitmap(executor: Executor, maxEdge: Int = ScanMath.MAX_PHOTO_EDGE): Bitmap =
    suspendCancellableCoroutine { cont ->
        takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val result = runCatching { image.use { it.toUprightBitmap(maxEdge) } }
                if (!cont.isActive) return
                result.fold(onSuccess = { cont.resume(it) }, onFailure = { cont.resumeWithException(it) })
            }

            override fun onError(exception: ImageCaptureException) {
                if (cont.isActive) cont.resumeWithException(exception)
            }
        })
    }

/** [ImageProxy.toBitmap] does not apply `rotationDegrees`; this does, and scales down in the same pass. */
fun ImageProxy.toUprightBitmap(maxEdge: Int = ScanMath.MAX_PHOTO_EDGE): Bitmap {
    val raw = toBitmap()
    val rotation = ScanMath.normalizeRotation(imageInfo.rotationDegrees)
    val (rw, rh) = ScanMath.rotatedSize(raw.width, raw.height, rotation)
    val (tw, th) = ScanMath.fitWithin(rw, rh, maxEdge)
    if (rotation == 0 && tw == raw.width && th == raw.height) return raw
    val scale = tw.toFloat() / rw
    val matrix = Matrix().apply {
        postRotate(rotation.toFloat())
        postScale(scale, scale)
    }
    val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    if (out !== raw) raw.recycle()
    return out
}

/** Saves [bitmap] as `filesDir/photos/<timestamp>.jpg` (temp file + rename) and returns the file. */
suspend fun savePhotoJpeg(filesDir: File, bitmap: Bitmap, epochMillis: Long): File = withContext(Dispatchers.IO) {
    val dir = File(filesDir, ScanMath.PHOTOS_DIR).apply { mkdirs() }
    var file = File(dir, ScanMath.photoFileName(epochMillis))
    var bump = 1
    while (file.exists()) file = File(dir, ScanMath.photoFileName(epochMillis + bump++))
    val tmp = File(dir, file.name + ".tmp")
    val ok = tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, ScanMath.JPEG_QUALITY, it) }
    if (!ok || !tmp.renameTo(file)) {
        tmp.delete()
        throw IOException("Couldn't save photo to $file")
    }
    file
}
