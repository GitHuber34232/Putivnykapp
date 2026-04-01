package ua.kyiv.putivnyk.data.model

import kotlinx.serialization.Serializable
import ua.kyiv.putivnyk.platform.currentTimeMillis

@Serializable
data class Route(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val startPoint: RoutePoint,
    val endPoint: RoutePoint,
    val waypoints: List<RoutePoint> = emptyList(),
    val distance: Double,
    val estimatedDuration: Int,
    val isFavorite: Boolean = false,
    val createdAt: Long = currentTimeMillis(),
    val updatedAt: Long = currentTimeMillis()
)

@Serializable
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val name: String? = null
)