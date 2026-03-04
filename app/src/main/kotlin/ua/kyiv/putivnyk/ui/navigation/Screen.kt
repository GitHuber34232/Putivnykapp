package ua.kyiv.putivnyk.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : Screen("home", "Головна", Icons.Filled.Home)
    data object Map : Screen("map", "Карта", Icons.Filled.Map) {
        const val routePattern = "map?routeBuilder={routeBuilder}"
        fun route(routeBuilder: Boolean): String = "map?routeBuilder=$routeBuilder"
    }
    data object Routes : Screen("routes", "Маршрути", Icons.Filled.Route)
    data object Events : Screen("events", "Події", Icons.Filled.Event)
    data object Settings : Screen("settings", "Налаштування", Icons.Filled.Settings)
    data object LocationDetails : Screen("location/{placeId}", "Деталі", Icons.Filled.Info) {
        fun route(placeId: Long): String = "location/$placeId"
    }

    companion object {
        val bottomNavItems = listOf(Home, Map, Routes, Events, Settings)
    }
}
