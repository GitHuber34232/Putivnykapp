package ua.kyiv.putivnyk.navigation

import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import ua.kyiv.putivnyk.domain.usecase.routing.RouteNavigationMetrics

/**
 * Route progress from a single location fix. Used by the background navigation service;
 * the reach radii match the in-app map so both agree on which stop was reached.
 */
object NavigationProgressCalculator {
    const val WALKING_REACH_RADIUS_METERS = 80.0
    const val DRIVING_REACH_RADIUS_METERS = 150.0
    private const val ARRIVED_REMAINING_METERS = 8.0

    data class Progress(
        /** Index of the last reached stop in [waypoints]; the next target is currentIndex + 1. */
        val currentIndex: Int,
        val distanceToNextMeters: Double,
        val remainingMeters: Double,
        val totalMeters: Double,
        val fraction: Float,
        val remainingGeometry: List<RoutePoint>,
        val completed: Boolean
    )

    fun compute(
        waypoints: List<RoutePoint>,
        geometry: List<RoutePoint>,
        currentIndex: Int,
        latitude: Double,
        longitude: Double,
        transportMode: TransportMode
    ): Progress {
        require(waypoints.size >= 2) { "A route needs at least two points" }
        val reach = if (transportMode == TransportMode.DRIVING) DRIVING_REACH_RADIUS_METERS else WALKING_REACH_RADIUS_METERS
        var index = currentIndex.coerceIn(0, waypoints.lastIndex)
        for (candidate in (index + 1)..waypoints.lastIndex) {
            val point = waypoints[candidate]
            if (NativeGeoEngine.distanceMeters(latitude, longitude, point.latitude, point.longitude) <= reach) {
                index = candidate
            }
        }

        val user = RoutePoint(latitude, longitude)
        val projection = if (geometry.size >= 2) RouteNavigationMetrics.projectOnPolyline(geometry, latitude, longitude) else null
        val hasProjection = projection != null && projection.totalDistanceMeters > 0.0
        val totalMeters = if (hasProjection) projection.totalDistanceMeters else NativeGeoEngine.polylineDistanceMeters(waypoints)
        val remainingStops = waypoints.drop(index + 1)
        val remainingMeters = if (hasProjection) {
            (projection.totalDistanceMeters - projection.distanceFromStartMeters).coerceAtLeast(0.0)
        } else {
            NativeGeoEngine.polylineDistanceMeters(listOf(user) + remainingStops)
        }
        val completed = index >= waypoints.lastIndex ||
            (index == waypoints.lastIndex - 1 && remainingMeters <= ARRIVED_REMAINING_METERS)
        val finalIndex = if (completed) waypoints.lastIndex else index
        val next = waypoints.getOrNull(finalIndex + 1)
        val remainingGeometry = when {
            completed -> emptyList()
            hasProjection -> RouteNavigationMetrics.remainingGeometry(geometry, projection)
            else -> listOf(user) + remainingStops
        }
        return Progress(
            currentIndex = finalIndex,
            distanceToNextMeters = next?.let {
                NativeGeoEngine.distanceMeters(latitude, longitude, it.latitude, it.longitude)
            } ?: 0.0,
            remainingMeters = if (completed) 0.0 else remainingMeters,
            totalMeters = totalMeters,
            fraction = when {
                completed -> 1f
                totalMeters > 0.0 -> (1.0 - remainingMeters / totalMeters).toFloat().coerceIn(0f, 1f)
                else -> 0f
            },
            remainingGeometry = remainingGeometry,
            completed = completed
        )
    }
}
