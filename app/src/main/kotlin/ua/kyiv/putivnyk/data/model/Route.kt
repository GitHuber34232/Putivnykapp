package ua.kyiv.putivnyk.data.model

import ua.kyiv.putivnyk.data.local.entity.RouteEntity

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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val name: String? = null
)

fun Route.toEntity(): RouteEntity {
    val waypointsList = mutableListOf<Double>()
    waypoints.forEach {
        waypointsList.add(it.latitude)
        waypointsList.add(it.longitude)
    }

    return RouteEntity(
        id = id,
        name = name,
        description = description,
        startLat = startPoint.latitude,
        startLon = startPoint.longitude,
        endLat = endPoint.latitude,
        endLon = endPoint.longitude,
        waypoints = waypointsList,
        distance = distance,
        estimatedDuration = estimatedDuration,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun RouteEntity.toDomain(): Route {
    val waypointsList = mutableListOf<RoutePoint>()
    var i = 0
    while (i < waypoints.size - 1) {
        waypointsList.add(
            RoutePoint(
                latitude = waypoints[i],
                longitude = waypoints[i + 1]
            )
        )
        i += 2
    }

    return Route(
        id = id,
        name = name,
        description = description,
        startPoint = RoutePoint(startLat, startLon),
        endPoint = RoutePoint(endLat, endLon),
        waypoints = waypointsList,
        distance = distance,
        estimatedDuration = estimatedDuration,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
