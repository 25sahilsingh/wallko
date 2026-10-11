package com.example.wallko.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Map
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.wallko.R
import kotlinx.serialization.Serializable

@Serializable data object GardenRoute
@Serializable data object ScanRoute
@Serializable data object WalkRoute
@Serializable data object MapRoute
@Serializable data object CollectionRoute
@Serializable data object SettingsRoute

enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val label: Int,
    val icon: ImageVector,
) {
    Garden(GardenRoute, R.string.tab_garden, Icons.Rounded.LocalFlorist),
    Scan(ScanRoute, R.string.tab_scan, Icons.Rounded.CameraAlt),
    Walk(WalkRoute, R.string.tab_walk, Icons.AutoMirrored.Rounded.DirectionsWalk),
    Map(MapRoute, R.string.tab_map, Icons.Rounded.Map),
}
