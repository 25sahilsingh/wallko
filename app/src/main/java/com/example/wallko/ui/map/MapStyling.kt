package com.example.wallko.ui.map

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.wallko.ui.theme.WallkoColors
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** OpenFreeMap Positron: light, few colours, easy to pastel-ise. Free, no key. */
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

internal const val FOG_SOURCE = "wallko-fog-src"
internal const val EXPLORED_SOURCE = "wallko-explored-src"
internal const val PATH_SOURCE = "wallko-path-src"
internal const val DRIP_SOURCE = "wallko-drip-src"
private const val EXPLORED_LAYER = "wallko-explored"
private const val FOG_LAYER = "wallko-fog"
private const val PATH_LAYER = "wallko-path"
private const val DRIP_LAYER = "wallko-drip"
private const val DRIP_IMAGE = "wallko-drip-img"

private const val TAG = "WallkoMap"

private object Pastel {
    val cream = WallkoColors.Cream.toArgb()
    val water = Color(0xFFC6EBFF).toArgb() // Sky, lightened
    val green = Color(0xFFD5F3C4).toArgb() // Grass, lightened
    val greenDeep = Color(0xFFC3EBAE).toArgb()
    val residential = WallkoColors.CreamDeep.toArgb()
    val building = Color(0xFFFBE6DC).toArgb() // Peach, very light
    val roadMinor = android.graphics.Color.WHITE
    val roadMajor = Color(0xFFFFD9CF).toArgb() // Peach, light
    val casing = Color(0xFFF3E3C8).toArgb()
    val rail = WallkoColors.Wilt.toArgb()
    val boundary = WallkoColors.Lavender.toArgb()
    val ink = WallkoColors.Ink.toArgb()
    val inkSoft = Color(0xFF6E6A80).toArgb()
}

/**
 * Recolours the base style's layers to the Sunny Meadow palette. Defensive: layers are matched by type and by
 * id / source-layer keywords, unknown layers are left alone, and any failing layer is skipped.
 */
internal fun applyPastelStyle(style: Style) {
    val layers = runCatching { style.layers }.getOrElse { return }
    for (layer in layers) {
        runCatching { restyle(layer) }.onFailure { Log.w(TAG, "restyle ${layer.id} failed", it) }
    }
}

private fun restyle(layer: Layer) {
    val id = layer.id.lowercase()
    when (layer) {
        is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(Pastel.cream))
        is FillLayer -> fillColorFor("$id ${layer.sourceLayer.orEmpty().lowercase()}")?.let {
            layer.setProperties(PropertyFactory.fillColor(it), PropertyFactory.fillOutlineColor(it))
        }
        is LineLayer -> lineColorFor("$id ${layer.sourceLayer.orEmpty().lowercase()}")?.let {
            layer.setProperties(PropertyFactory.lineColor(it))
        }
        is SymbolLayer -> {
            val color = if ("road" in id || "highway" in id || "water" in id) Pastel.inkSoft else Pastel.ink
            layer.setProperties(
                PropertyFactory.textColor(color),
                PropertyFactory.textHaloColor(Pastel.cream),
                PropertyFactory.textHaloWidth(1.5f),
            )
        }
        else -> Unit
    }
}

private fun fillColorFor(key: String): Int? = when {
    "water" in key || "ocean" in key || "ice" in key || "glacier" in key -> Pastel.water
    "wood" in key || "forest" in key -> Pastel.greenDeep
    "park" in key || "grass" in key || "landcover" in key || "garden" in key ||
        "cemetery" in key || "pitch" in key || "golf" in key -> Pastel.green
    "building" in key -> Pastel.building
    "residential" in key || "landuse" in key || "aeroway" in key -> Pastel.residential
    else -> null
}

private fun lineColorFor(key: String): Int? = when {
    "water" in key || "river" in key -> Pastel.water
    "rail" in key || "transit" in key -> Pastel.rail
    "boundary" in key || "admin" in key -> Pastel.boundary
    "casing" in key -> Pastel.casing
    "motorway" in key || "trunk" in key || "major" in key || "primary" in key -> Pastel.roadMajor
    "road" in key || "highway" in key || "street" in key || "path" in key || "minor" in key ||
        "bridge" in key || "tunnel" in key || "transportation" in key -> Pastel.roadMinor
    else -> null
}

/**
 * Adds the explored tint, the fog, the walk path and Drip on top of every base layer (labels included, so
 * fogged areas look properly foggy).
 */
internal fun addWallkoLayers(style: Style, dripBitmap: Bitmap) {
    val empty = FeatureCollection.fromFeatures(emptyList<Feature>())
    if (style.getSource(EXPLORED_SOURCE) == null) style.addSource(GeoJsonSource(EXPLORED_SOURCE, empty))
    if (style.getSource(FOG_SOURCE) == null) style.addSource(GeoJsonSource(FOG_SOURCE, empty))
    if (style.getSource(PATH_SOURCE) == null) style.addSource(GeoJsonSource(PATH_SOURCE, empty))
    if (style.getSource(DRIP_SOURCE) == null) style.addSource(GeoJsonSource(DRIP_SOURCE, empty))
    style.addImage(DRIP_IMAGE, dripBitmap)

    if (style.getLayer(EXPLORED_LAYER) == null) {
        style.addLayer(
            FillLayer(EXPLORED_LAYER, EXPLORED_SOURCE).withProperties(
                PropertyFactory.fillColor(WallkoColors.Grass.toArgb()),
                PropertyFactory.fillOpacity(0.25f),
                PropertyFactory.fillAntialias(false), // no hairlines between neighbouring patches
            ),
        )
    }
    if (style.getLayer(FOG_LAYER) == null) {
        style.addLayer(
            FillLayer(FOG_LAYER, FOG_SOURCE).withProperties(
                PropertyFactory.fillColor(Pastel.cream),
                PropertyFactory.fillOpacity(0.7f),
                PropertyFactory.fillAntialias(false),
            ),
        )
    }
    if (style.getLayer(PATH_LAYER) == null) {
        style.addLayer(
            LineLayer(PATH_LAYER, PATH_SOURCE).withProperties(
                PropertyFactory.lineColor(WallkoColors.Soil.toArgb()),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                // Near-zero dashes + round caps = a trail of dots ("footsteps"). Units are line widths.
                PropertyFactory.lineDasharray(arrayOf(0.1f, 2f)),
            ),
        )
    }
    if (style.getLayer(DRIP_LAYER) == null) {
        style.addLayer(
            SymbolLayer(DRIP_LAYER, DRIP_SOURCE).withProperties(
                PropertyFactory.iconImage(DRIP_IMAGE),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    }
}

// ---- Geometry → GeoJSON (safe off the main thread; setGeoJson must then run on main) ----

private fun LngLat.toPoint(): Point = Point.fromLngLat(lng, lat)

internal fun fogCollection(shapes: FogShapes): FeatureCollection {
    val rings = buildFogRings(shapes.explored).map { ring -> ring.map { it.toPoint() } }
    return FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Polygon.fromLngLats(rings))))
}

internal fun exploredCollection(shapes: FogShapes): FeatureCollection =
    FeatureCollection.fromFeatures(
        shapes.explored.map { b ->
            Feature.fromGeometry(Polygon.fromLngLats(listOf(rectRing(b, clockwise = false).map { it.toPoint() })))
        },
    )

internal fun pathCollection(path: List<LngLat>): FeatureCollection =
    if (path.size < 2) {
        FeatureCollection.fromFeatures(emptyList<Feature>())
    } else {
        FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(LineString.fromLngLats(path.map { it.toPoint() }))))
    }

internal fun dripCollection(position: LngLat?): FeatureCollection =
    FeatureCollection.fromFeatures(listOfNotNull(position?.let { Feature.fromGeometry(it.toPoint()) }))

internal fun Style.setGeoJsonSafely(sourceId: String, data: FeatureCollection) {
    runCatching {
        if (isFullyLoaded) getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(data)
    }.onFailure { Log.w(TAG, "setGeoJson($sourceId) failed", it) }
}
