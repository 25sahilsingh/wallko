package com.example.wallko.game

import com.example.wallko.ml.stylize.StyleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteWorkerMathTest {

    @Test fun styleByName() {
        assertEquals(StyleId.CRAYON, SpriteWorker.styleIdFromName("CRAYON"))
        assertEquals(StyleId.FLAT_PASTEL, SpriteWorker.styleIdFromName("flat_pastel"))
        assertEquals(StyleId.WATERCOLOUR, SpriteWorker.styleIdFromName(null))
        assertEquals(StyleId.WATERCOLOUR, SpriteWorker.styleIdFromName("oil"))
    }

    @Test fun retriesAtMostThreeRuns() {
        assertTrue(SpriteWorker.shouldRetry(0))
        assertTrue(SpriteWorker.shouldRetry(1))
        assertFalse(SpriteWorker.shouldRetry(2))
    }

    @Test fun sampleSize() {
        assertEquals(1, SpriteWorker.sampleSizeFor(1000, 800, 2048))
        assertEquals(1, SpriteWorker.sampleSizeFor(4000, 3000, 2048))
        assertEquals(2, SpriteWorker.sampleSizeFor(4096, 3072, 2048))
        assertEquals(4, SpriteWorker.sampleSizeFor(8192, 6144, 2048))
    }

    @Test fun workName() = assertEquals("sprite_42", SpriteWorker.workName(42))
}
