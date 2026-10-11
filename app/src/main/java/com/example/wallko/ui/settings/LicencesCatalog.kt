package com.example.wallko.ui.settings

/** One credited component on the licences screen. */
data class LicenceEntry(
    val name: String,
    val detail: String,
    val copyright: String,
    val licence: String,
    /** File under assets/licences/ holding the full text shown when the row is expanded. */
    val assetFile: String,
)

/** Everything Wallko ships or shows that needs crediting. Pure data so a JVM test can check the files exist. */
object LicencesCatalog {
    const val ASSET_DIR = "licences"
    private const val APACHE = "apache_2.0.txt"
    private const val APACHE_NAME = "Apache License 2.0"

    val entries: List<LicenceEntry> = listOf(
        LicenceEntry(
            name = "Nunito",
            detail = "The rounded font used everywhere in the app.",
            copyright = "Copyright 2014 The Nunito Project Authors",
            licence = "SIL Open Font License 1.1",
            assetFile = "nunito_OFL.txt",
        ),
        LicenceEntry(
            name = "Google AIY Vision plants classifier",
            detail = "On-device model that recognises 2,101 plants from a photo.",
            copyright = "Copyright Google LLC",
            licence = APACHE_NAME,
            assetFile = APACHE,
        ),
        LicenceEntry(
            name = "Magenta arbitrary image stylization",
            detail = "On-device style transfer that paints your plant sprites.",
            copyright = "Copyright Google LLC (The Magenta Authors)",
            licence = APACHE_NAME,
            assetFile = APACHE,
        ),
        LicenceEntry(
            name = "OpenStreetMap & OpenFreeMap",
            detail = "Map data © OpenStreetMap contributors. Tiles by OpenFreeMap, schema © OpenMapTiles.",
            copyright = "© OpenStreetMap contributors",
            licence = "Open Database License (ODbL) 1.0",
            assetFile = "openstreetmap_ODbL.txt",
        ),
        LicenceEntry(
            name = "MapLibre Native",
            detail = "Draws the map.",
            copyright = "Copyright (c) 2021 MapLibre contributors; (c) 2014-2020 Mapbox",
            licence = "BSD 2-Clause License",
            assetFile = "maplibre_BSD-2-Clause.txt",
        ),
        LicenceEntry(
            name = "AndroidX & Jetpack Compose",
            detail = "Compose, Material 3, Navigation, Room, DataStore, WorkManager, CameraX, Health Connect, Lifecycle, Activity.",
            copyright = "Copyright The Android Open Source Project",
            licence = APACHE_NAME,
            assetFile = APACHE,
        ),
        LicenceEntry(
            name = "Kotlin, kotlinx.coroutines & kotlinx.serialization",
            detail = "The language and its core libraries.",
            copyright = "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors",
            licence = APACHE_NAME,
            assetFile = APACHE,
        ),
        LicenceEntry(
            name = "Dagger & Hilt",
            detail = "Dependency injection.",
            copyright = "Copyright The Dagger Authors",
            licence = APACHE_NAME,
            assetFile = APACHE,
        ),
        LicenceEntry(
            name = "Coil",
            detail = "Image loading.",
            copyright = "Copyright Coil Contributors",
            licence = APACHE_NAME,
            assetFile = APACHE,
        ),
        LicenceEntry(
            name = "Lottie",
            detail = "Celebration animations.",
            copyright = "Copyright Airbnb, Inc.",
            licence = APACHE_NAME,
            assetFile = APACHE,
        ),
        LicenceEntry(
            name = "LiteRT (TensorFlow Lite)",
            detail = "Runs the plant and style models on your phone.",
            copyright = "Copyright Google LLC / The TensorFlow Authors",
            licence = APACHE_NAME,
            assetFile = APACHE,
        ),
        LicenceEntry(
            name = "ML Kit & Google Play services",
            detail = "Subject segmentation (cut-outs) and location.",
            copyright = "Copyright Google LLC",
            licence = "Google APIs / ML Kit Terms of Service",
            assetFile = "google_play_services_notice.txt",
        ),
    )

    fun assetPath(entry: LicenceEntry): String = "$ASSET_DIR/${entry.assetFile}"
}

/** Turns hard-wrapped (~80 column) licence files into paragraphs that wrap nicely on a phone. */
object LicenceText {
    /** Lines at least this long are treated as hard-wrapped and joined with the next line. */
    private const val WRAPPED_LINE = 50

    fun reflow(raw: String): String {
        val out = StringBuilder()
        var previous: String? = null
        for (line in raw.replace("\r\n", "\n").lines()) {
            val trimmed = line.trim()
            val prev = previous
            when {
                trimmed.isEmpty() -> {
                    if (prev != null && prev.isNotEmpty()) out.append("\n\n")
                }
                prev != null && prev.length >= WRAPPED_LINE && prev.hasLetters() && trimmed.hasLetters() ->
                    out.append(' ').append(trimmed)
                else -> {
                    if (prev != null && prev.isNotEmpty()) out.append('\n')
                    out.append(trimmed)
                }
            }
            previous = trimmed
        }
        return out.toString().trim()
    }

    private fun String.hasLetters(): Boolean = any { it.isLetter() }
}
