package ua.kyiv.putivnyk.domain.usecase.routing

import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine

object RouteMetricsCalculator {
    const val MAX_WAYPOINTS = 200
    private const val DUPLICATE_THRESHOLD_METERS = 15.0

    fun recompute(route: Route): Route {
        val points = buildList {
            add(route.startPoint)
            addAll(route.waypoints)
            add(route.endPoint)
        }

        val distance = NativeGeoEngine.polylineDistanceMeters(points)

        val duration = RouteNavigationMetrics.estimateDurationMinutes(distance, route.transportMode)

        return route.copy(
            distance = distance,
            estimatedDuration = duration,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun withAppendedWaypoint(route: Route, waypoint: RoutePoint): Route {
        if (!isValidCoordinate(waypoint.latitude, waypoint.longitude)) {
            return route
        }

        if (route.waypoints.size >= MAX_WAYPOINTS) {
            return route
        }

        if (isDuplicate(waypoint, route)) {
            return route
        }

        val appended = route.waypoints + waypoint
        val optimized = if (appended.size > 120) {
            NativeGeoEngine.simplifyPolyline(appended, toleranceMeters = 12.0)
        } else {
            appended
        }
        return recompute(route.copy(waypoints = optimized))
    }

    fun isValidCoordinate(lat: Double, lon: Double): Boolean =
        lat in -90.0..90.0 && lon in -180.0..180.0 &&
            !lat.isNaN() && !lon.isNaN() &&
            !lat.isInfinite() && !lon.isInfinite()

    fun isWithinKyivRegion(lat: Double, lon: Double): Boolean =
        lat in 49.8..51.0 && lon in 29.8..31.3

    fun isDuplicate(point: RoutePoint, route: Route): Boolean {
        val allPoints = buildList {
            add(route.startPoint)
            addAll(route.waypoints)
            add(route.endPoint)
        }
        return allPoints.any { existing ->
            NativeGeoEngine.distanceMeters(
                existing.latitude, existing.longitude,
                point.latitude, point.longitude
            ) < DUPLICATE_THRESHOLD_METERS
        }
    }

    fun validateRoute(route: Route): List<String> {
        val errors = mutableListOf<String>()

        if (!isValidCoordinate(route.startPoint.latitude, route.startPoint.longitude)) {
            errors.add("Невалідні координати початкової точки")
        }
        if (!isValidCoordinate(route.endPoint.latitude, route.endPoint.longitude)) {
            errors.add("Невалідні координати кінцевої точки")
        }

        route.waypoints.forEachIndexed { index, wp ->
            if (!isValidCoordinate(wp.latitude, wp.longitude)) {
                errors.add("Невалідні координати точки #${index + 1}")
            }
        }

        if (route.waypoints.size > MAX_WAYPOINTS) {
            errors.add("Забагато точок (макс. $MAX_WAYPOINTS)")
        }

        if (route.name.isBlank()) {
            errors.add("Назва маршруту порожня")
        }

        val startToEnd = NativeGeoEngine.distanceMeters(
            route.startPoint.latitude, route.startPoint.longitude,
            route.endPoint.latitude, route.endPoint.longitude
        )
        if (startToEnd < 1.0 && route.waypoints.isEmpty()) {
            errors.add("Початкова та кінцева точки занадто близькі")
        }

        return errors
    }
}
