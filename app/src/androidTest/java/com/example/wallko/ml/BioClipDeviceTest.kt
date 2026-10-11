package com.example.wallko.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.exp
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Speed + accuracy check for the BioCLIP int8 image encoder on a real phone, before it goes into the app.
 * The model is too big to bundle just for a test, so push it first (skipped if missing):
 *
 *   adb push bioclip_img_int8.onnx text_table_f32.bin text_labels.txt photos/ \
 *       /sdcard/Android/data/com.example.wallko/files/bioclip/
 *
 * Results are logged under tag "BioClipDeviceTest".
 */
@RunWith(AndroidJUnit4::class)
class BioClipDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun speedAndTopGuessesOnDevice() {
        val dir = File(context.getExternalFilesDir(null), "bioclip")
        val model = File(dir, "bioclip_img_int8.onnx")
        assumeTrue("Push the BioCLIP files to $dir first", model.exists())

        val labels = File(dir, "text_labels.txt").readLines().filter { it.isNotBlank() }
        val table = readFloats(File(dir, "text_table_f32.bin"))
        val dim = table.size / labels.size
        log("labels=${labels.size} dim=$dim model=${model.length() / 1_000_000} MB cores=${Runtime.getRuntime().availableProcessors()}")

        val env = OrtEnvironment.getEnvironment()
        val t0 = System.nanoTime()
        val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
        env.createSession(model.absolutePath, opts).use { session ->
            log("load ${(System.nanoTime() - t0) / 1_000_000} ms")
            val photos = File(dir, "photos").listFiles { f -> f.extension.lowercase() == "jpg" }.orEmpty().sortedBy { it.name }
            for (photo in photos) {
                val input = preprocess(BitmapFactory.decodeFile(photo.absolutePath))
                var emb = FloatArray(0)
                val times = (0 until RUNS + 1).map {
                    val s = System.nanoTime()
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { t ->
                        session.run(mapOf("pixels" to t)).use { r -> emb = (r[0].value as Array<FloatArray>)[0] }
                    }
                    (System.nanoTime() - s) / 1_000_000
                }
                val probs = softmax(FloatArray(labels.size) { i -> 100f * dot(emb, table, i * dim, dim) })
                val top = probs.indices.sortedByDescending { probs[it] }.take(2)
                    .joinToString(", ") { "${name(labels[it])} ${"%.2f".format(probs[it])}" }
                // First run includes warm-up; report it separately from the steady-state average.
                log("${photo.name}: first ${times[0]} ms, avg ${times.drop(1).average().toInt()} ms | $top")
            }
        }
    }

    /** CLIP preprocessing: shortest side → 224, centre crop, mean/std normalise, NCHW floats. */
    private fun preprocess(src: Bitmap): FloatArray {
        val k = SIZE.toFloat() / minOf(src.width, src.height)
        val scaled = Bitmap.createScaledBitmap(src, maxOf(SIZE, (src.width * k).toInt()), maxOf(SIZE, (src.height * k).toInt()), true)
        val crop = Bitmap.createBitmap(scaled, (scaled.width - SIZE) / 2, (scaled.height - SIZE) / 2, SIZE, SIZE)
        val px = IntArray(SIZE * SIZE).also { crop.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
        val out = FloatArray(3 * SIZE * SIZE)
        val plane = SIZE * SIZE
        for (i in px.indices) {
            val p = px[i]
            out[i] = ((p shr 16 and 0xFF) / 255f - MEAN[0]) / STD[0]
            out[plane + i] = ((p shr 8 and 0xFF) / 255f - MEAN[1]) / STD[1]
            out[2 * plane + i] = ((p and 0xFF) / 255f - MEAN[2]) / STD[2]
        }
        return out
    }

    private fun readFloats(f: File): FloatArray {
        val buf = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buf.remaining()).also { buf.get(it) }
    }

    private fun dot(a: FloatArray, table: FloatArray, off: Int, n: Int): Float {
        var s = 0f
        for (j in 0 until n) s += a[j] * table[off + j]
        return s
    }

    private fun softmax(z: FloatArray): FloatArray {
        val m = z.max()
        val e = FloatArray(z.size) { exp(z[it] - m) }
        val sum = e.sum()
        return FloatArray(e.size) { e[it] / sum }
    }

    private fun name(line: String) = line.substringAfter('|').ifBlank { line.substringBefore('|') }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        println("$TAG: $msg")
    }

    private companion object {
        const val TAG = "BioClipDeviceTest"
        const val SIZE = 224
        const val RUNS = 5
        val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    }
}
