package com.example.wallko.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.wallko.ml.classifier.AiyPlantClassifier
import com.example.wallko.ml.stylize.MagentaStylizer
import com.example.wallko.ml.stylize.StyleId
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Loads the real bundled models. */
@RunWith(AndroidJUnit4::class)
class MlModelsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun solid(w: Int, h: Int, color: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun aiyHas2101SpeciesAfterBackgroundDrop() = runBlocking {
        val classifier = AiyPlantClassifier(context)
        val labels = classifier.labels()
        assertEquals(2102, labels.size)
        val bg = classifier.backgroundIndex()
        assertEquals(2101, bg)
        assertEquals("__background__", labels[bg].latin)
        assertEquals("Betula lenta", labels[0].latin)
        assertEquals("Sweet birch", labels[0].common)

        val scores = classifier.speciesScores(solid(640, 480, Color.GREEN))
        assertEquals(2101, scores.size)
        assertTrue(scores.none { it.first == bg })
        assertTrue(scores.all { it.second in 0f..1f })
    }

    @Test
    fun aiyClassifiesSolidColourWithoutCrashing() = runBlocking {
        val result = AiyPlantClassifier(context).classify(solid(300, 500, Color.rgb(90, 160, 60)))
        // A flat colour rarely clears MIN_GUESS_SCORE, so anywhere from 0 to 3 guesses is fine.
        assertTrue(result.top3.size <= 3)
        result.top3.forEach { assertNotEquals(2101, it.outputIndex) }
        val s = result.top3.map { it.score }
        assertEquals(s.sortedDescending(), s)
    }

    @Test
    fun stylizerReturns384AndCachesVector() = runBlocking {
        File(context.filesDir, "style_vectors").deleteRecursively()
        val stylizer = MagentaStylizer(context)
        val out = stylizer.stylize(solid(500, 300, Color.rgb(126, 217, 87)), StyleId.WATERCOLOUR)
        assertEquals(384, out.width)
        assertEquals(384, out.height)
        assertEquals(100, stylizer.styleVector(StyleId.WATERCOLOUR).size)
        assertTrue(File(context.filesDir, "style_vectors").listFiles().orEmpty().any { it.name.startsWith("watercolour") })

        // A fresh instance reads the vector back from disk and still works.
        val again = MagentaStylizer(context).stylize(solid(384, 384, Color.WHITE), StyleId.WATERCOLOUR)
        assertEquals(384, again.width)
    }
}
