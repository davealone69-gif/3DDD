package com.threedd.studio.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.threedd.studio.ui.about.AboutScreen
import com.threedd.studio.ui.repair.DiagnosticsScreen
import com.threedd.studio.ui.agegate.AgeGateScreen
import com.threedd.studio.ui.export.ExportScreen
import com.threedd.studio.ui.library.LibraryScreen
import com.threedd.studio.ui.lighting.LightingScreen
import com.threedd.studio.ui.material.MaterialScreen
import com.threedd.studio.ui.motion.MotionScreen
import com.threedd.studio.ui.navigation.Destination
import com.threedd.studio.ui.navigation.bottomTabs
import com.threedd.studio.ui.scan.ScanScreen
import com.threedd.studio.ui.settings.SettingsScreen
import com.threedd.studio.ui.studio.StudioScreen

@Composable
fun ThreeDoubleDApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                bottomTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.destination.route,
                        onClick = {
                            // Pop back to the start destination so every tab - including
                            // Studio - is always reachable, and never stack duplicates.
                            navController.navigate(tab.destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Studio.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.Studio.route) {
                StudioScreen(
                    onOpenLibrary = { navController.navigate(Destination.Library.route) },
                    onOpenLighting = { navController.navigate(Destination.Lighting.route) },
                    onOpenScan = { navController.navigate(Destination.Scan.route) },
                    onOpenSettings = { navController.navigate(Destination.Settings.route) },
                    onOpenAgeGate = { navController.navigate(Destination.AgeGate.route) },
                    onOpenAbout = { navController.navigate(Destination.About.route) },
                    onOpenDiagnostics = { navController.navigate(Destination.Diagnostics.route) },
                    onOpenExport = { navController.navigate(Destination.Export.route) }
                )
            }
            composable(Destination.Library.route) {
                LibraryScreen(
                    onOpenScan = { navController.navigate(Destination.Scan.route) },
                    onOpenStudio = { navController.navigate(Destination.Studio.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    } }
                )
            }
            composable(Destination.Material.route) { MaterialScreen() }
            composable(Destination.Motion.route) { MotionScreen() }
            composable(Destination.Lighting.route) { LightingScreen() }
            composable(Destination.Export.route) { ExportScreen() }
            composable(Destination.Scan.route) { ScanScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onOpenAgeGate = { navController.navigate(Destination.AgeGate.route) },
                    onOpenDiagnostics = { navController.navigate(Destination.Diagnostics.route) }
                )
            }
            composable(
                route = "${Destination.AgeGate.route}?next={next}",
                arguments = listOf(navArgument("next") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                AgeGateScreen(
                    next = entry.arguments?.getString("next").orEmpty(),
                    onUnlocked = { route ->
                        if (route.isNotBlank()) navController.navigate(route)
                    }
                )
            }
            composable(Destination.Diagnostics.route) { DiagnosticsScreen() }
            composable(Destination.About.route) { AboutScreen() }
        }
    }
}
