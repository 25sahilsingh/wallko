package com.example.wallko.ml.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URL

class BioClipFilesTest {

    @get:Rule val tmp = TemporaryFolder()

    /** sha256("abc"), the FIPS 180-2 test vector. */
    private val abcHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private val abc = BioClipFiles.Asset("abc.txt", 3L, abcHash)

    private fun write(dir: File, name: String, text: String) = File(dir, name).apply { writeText(text) }

    private fun fill(dir: File) = BioClipFiles.ALL.forEach { write(dir, it.name, "x") }

    @Test fun assetsAreUniqueAndUnderTheRelease() {
        assertEquals(3, BioClipFiles.ALL.map { it.name }.toSet().size)
        BioClipFiles.ALL.forEach {
            assertTrue(it.url.startsWith("https://github.com/25sahilsingh/wallko/releases/download/bioclip-v1/"))
            assertTrue(it.url.endsWith("/" + it.name))
            assertTrue(it.size > 0)
            assertTrue(Regex("[0-9a-f]{64}").matches(it.sha256))
        }
    }

    @Test fun hashesAFile() {
        assertEquals(abcHash, BioClipFiles.sha256Hex(write(tmp.root, "abc.txt", "abc")))
        assertEquals("00ff10", BioClipFiles.toHex(byteArrayOf(0, -1, 16)))
    }

    @Test fun matchesNeedsSizeAndHash() {
        assertTrue(BioClipFiles.matches(abc, 3L, abcHash))
        assertTrue(BioClipFiles.matches(abc, 3L, abcHash.uppercase()))
        assertFalse(BioClipFiles.matches(abc, 4L, abcHash))
        assertFalse(BioClipFiles.matches(abc, 3L, abcHash.replace('b', 'c')))
    }

    @Test fun validFile() {
        assertTrue(BioClipFiles.isValid(write(tmp.root, "abc.txt", "abc"), abc))
    }

    @Test fun missingWrongSizeOrWrongContentIsInvalid() {
        assertFalse(BioClipFiles.isValid(File(tmp.root, "abc.txt"), abc))
        assertFalse(BioClipFiles.isValid(write(tmp.root, "abc.txt", "abcd"), abc))
        assertFalse(BioClipFiles.isValid(write(tmp.root, "abc.txt", "abd"), abc))
        assertFalse(BioClipFiles.isValid(tmp.newFolder("dir"), abc.copy(name = "dir")))
    }

    @Test fun sizedChecksEveryFile() {
        val dir = tmp.newFolder()
        assertFalse(BioClipFiles.allSized(dir))
        fill(dir)
        assertFalse(BioClipFiles.allSized(dir))
        assertTrue(BioClipFiles.allPresent(dir))
        assertTrue(BioClipFiles.sizeMatches(File(dir, BioClipFiles.LABELS.name).apply { writeBytes(ByteArray(75_828)) }, BioClipFiles.LABELS))
    }

    @Test fun downloadedDirWinsOverAdbPushedDir() {
        val downloaded = tmp.newFolder("files")
        val pushed = tmp.newFolder("external")
        fill(downloaded)
        fill(pushed)
        assertEquals(downloaded, BioClipFiles.firstComplete(listOf(downloaded, pushed)))
    }

    @Test fun halfFinishedDownloadFallsBackToAdbPushedDir() {
        val downloaded = tmp.newFolder("files")
        val pushed = tmp.newFolder("external")
        write(downloaded, BioClipFiles.MODEL.name, "x")
        write(downloaded, BioClipFiles.TABLE.name + BioClipDownloadWorker.PART_SUFFIX, "x")
        fill(pushed)
        assertEquals(pushed, BioClipFiles.firstComplete(listOf(downloaded, pushed)))
    }

    @Test fun nothingCompleteOrNoExternalStorage() {
        val downloaded = tmp.newFolder("files")
        assertNull(BioClipFiles.firstComplete(listOf(downloaded, null)))
        fill(downloaded)
        assertEquals(downloaded, BioClipFiles.firstComplete(listOf(downloaded, null)))
        assertNull(BioClipFiles.firstComplete(listOf(null, File(tmp.root, "missing"))))
    }

    @Test fun followsHttpsRedirectsAcrossHosts() {
        val from = URL(BioClipFiles.MODEL.url)
        assertEquals(
            URL("https://release-assets.githubusercontent.com/x?sig=1"),
            BioClipFiles.redirectTarget(from, 302, "https://release-assets.githubusercontent.com/x?sig=1"),
        )
        assertEquals(URL("https://github.com/other"), BioClipFiles.redirectTarget(from, 301, "/other"))
        assertNull(BioClipFiles.redirectTarget(from, 200, null))
        assertNull(BioClipFiles.redirectTarget(from, 404, "https://example.com/"))
    }

    @Test fun refusesHttpDowngradeAndMissingLocation() {
        val from = URL(BioClipFiles.MODEL.url)
        assertThrows(IllegalArgumentException::class.java) { BioClipFiles.redirectTarget(from, 302, "http://example.com/x") }
        assertThrows(IllegalArgumentException::class.java) { BioClipFiles.redirectTarget(from, 307, null) }
    }

    @Test fun retriesAtMostFiveRuns() {
        assertTrue(BioClipDownloadWorker.shouldRetry(3))
        assertFalse(BioClipDownloadWorker.shouldRetry(4))
    }
}
