package ua.kyiv.putivnyk.domain.geo

import ua.kyiv.putivnyk.data.model.RoutePoint

actual object NativeGeoEngine {
    actual fun isNativeAvailable(): Boolean = false

    actual fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        GeoMath.fallbackDistanceMeters(lat1, lon1, lat2, lon2)

    actual fun polylineDistanceMeters(points: List<RoutePoint>): Double =
        GeoMath.polylineDistanceMeters(points)

    actual fun nearestPointIndex(points: List<RoutePoint>, lat: Double, lon: Double): Int =
        GeoMath.nearestPointIndex(points, lat, lon)

    actual fun simplifyPolyline(points: List<RoutePoint>, toleranceMeters: Double): List<RoutePoint> = points
}