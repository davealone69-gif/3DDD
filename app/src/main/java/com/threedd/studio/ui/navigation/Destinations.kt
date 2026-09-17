package com.threedd.studio.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String) {
    data object Studio : Destination("studio")
    data object Library : Destination("library")
    data object Material : Destination("material")
    data object Motion : Destination("motion")
    data object Lighting : Destination("lighting")
    data object Export : Destination("export")
    data object Scan : Destination("scan")
    data object Settings : Destination("settings")
    data object AgeGate : Destination("agegate")
    data object About : Destination("about")
}

data class BottomTab(val destination: Destination, val label: String, val icon: ImageVector)

val bottomTabs = listOf(
    BottomTab(Destination.Studio, "Studio", Icons.Filled.AutoAwesome),
    BottomTab(Destination.Library, "Library", Icons.Filled.Category),
    BottomTab(Destination.Material, "Material", Icons.Filled.Tune),
    BottomTab(Destination.Motion, "Motion", Icons.Filled.Movie),
    BottomTab(Destination.Export, "Export", Icons.Filled.FileDownload)
)
