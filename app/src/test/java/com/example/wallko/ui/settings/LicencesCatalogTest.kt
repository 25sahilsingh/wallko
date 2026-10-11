package com.example.wallko.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LicencesCatalogTest {
    /** Gradle runs unit tests with the module (app/) as working directory. */
    private fun asset(path: String): File =
        listOf(File("src/main/assets", path), File("app/src/main/assets", path)).firstOrNull { it.exists() }
            ?: File("src/main/assets", path)

    @Test fun everyLicenceFileExistsAndIsNotEmpty() {
        LicencesCatalog.entries.forEach { e ->
            val f = asset(LicencesCatalog.assetPath(e))
            assertTrue("missing ${f.path}", f.isFile)
            assertTrue("empty ${f.path}", f.readText().isNotBlank())
        }
    }

    @Test fun requiredCreditsPresent() {
        val names = LicencesCatalog.entries.joinToString("|") { it.name + " " + it.detail + " " + it.licence }
        listOf("Nunito", "AIY", "Magenta", "OpenStreetMap", "OpenFreeMap", "MapLibre", "AndroidX", "Kotlin", "Hilt", "Coil", "Lottie", "ML Kit", "LiteRT")
            .forEach { assertTrue("no credit for $it", names.contains(it)) }
    }

    @Test fun reflowJoinsHardWrappedLines() {
        val raw = "TITLE\n\n" +
            "This is a long hard-wrapped line that goes past fifty characters\n" +
            "and continues here.\n" +
            "\n\n" +
            "1. Short item\n" +
            "2. Next item\n" +
            "--------------------------------------------------------------\n" +
            "HEADER AFTER DASHES"
        val expected = "TITLE\n\n" +
            "This is a long hard-wrapped line that goes past fifty characters and continues here.\n\n" +
            "1. Short item\n2. Next item\n" +
            "--------------------------------------------------------------\n" +
            "HEADER AFTER DASHES"
        assertEquals(expected, LicenceText.reflow(raw))
    }
}
