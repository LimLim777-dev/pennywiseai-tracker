package com.pennywiseai.tracker.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : BottomNavItem(
        route = "home",
        title = "Home",
        icon = Icons.Default.Home
    )

    data object Analytics : BottomNavItem(
        route = "analytics",
        title = "Analytics",
        icon = Icons.Default.Analytics
    )

    data object Investment : BottomNavItem(
        route = "investment",
        title = "Invest",
        icon = Icons.AutoMirrored.Filled.TrendingUp
    )

    data object Settings : BottomNavItem(
        route = "settings",
        title = "Settings",
        icon = Icons.Default.Settings
    )
}