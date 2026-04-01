package ua.kyiv.putivnyk.data.model

import ua.kyiv.putivnyk.data.local.entity.RouteEntity

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
