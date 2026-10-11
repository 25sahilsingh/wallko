package com.example.wallko.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.wallko.ui.collection.CollectionScreen
import com.example.wallko.ui.garden.GardenScreen
import com.example.wallko.ui.map.MapScreen
import com.example.wallko.ui.scan.ScanScreen
import com.example.wallko.ui.settings.SettingsScreen
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.walk.WalkScreen

@Composable
fun WallkoNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    openScanFirst: Boolean = false,
) {
    // Straight after onboarding, the first thing to do is find a plant.
    LaunchedEffect(openScanFirst) { if (openScanFirst) navController.navigateToTab(TopLevelDestination.Scan) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            WallkoBottomBar(currentDestination) { navController.navigateToTab(it) }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = GardenRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<GardenRoute> {
                GardenScreen(
                    onFindPlant = { navController.navigateToTab(TopLevelDestination.Scan) },
                    onOpenCollection = { navController.navigate(CollectionRoute) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                )
            }
            composable<CollectionRoute> { CollectionScreen(onBack = { navController.popBackStack() }) }
            composable<SettingsRoute> { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable<ScanRoute> { ScanScreen() }
            composable<WalkRoute> { WalkScreen() }
            composable<MapRoute> { MapScreen() }
        }
    }
}

@Composable
private fun WallkoBottomBar(
    currentDestination: NavDestination?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.label), style = MaterialTheme.typography.labelLarge) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = WallkoColors.Ink,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = WallkoColors.Sunflower,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** Switches tabs, keeping each tab's back stack and state. */
private fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
