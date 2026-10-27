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
    val transportMode: TransportMode = TransportMode.WALKING,
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
        transportMode = transportMode.key,
        startName = startPoint.name,
        endName = endPoint.name,
        waypointNames = waypoints.map { it.name.orEmpty() },
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun RouteEntity.toDomain(): Route {
    val waypointsList = mutableListOf<RoutePoint>()
    // Rows written before names were stored have no waypointNames; Gson may also hand back null.
    val names: List<String>? = waypointNames
    var i = 0
    while (i < waypoints.size - 1) {
        waypointsList.add(
            RoutePoint(
                latitude = waypoints[i],
                longitude = waypoints[i + 1],
                name = names?.getOrNull(i / 2)?.takeIf { it.isNotBlank() }
            )
        )
        i += 2
    }

    return Route(
        id = id,
        name = name,
        description = description,
        startPoint = RoutePoint(startLat, startLon, startName?.takeIf { it.isNotBlank() }),
        endPoint = RoutePoint(endLat, endLon, endName?.takeIf { it.isNotBlank() }),
        waypoints = waypointsList,
        distance = distance,
        estimatedDuration = estimatedDuration,
        transportMode = TransportMode.fromKey(transportMode),
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
