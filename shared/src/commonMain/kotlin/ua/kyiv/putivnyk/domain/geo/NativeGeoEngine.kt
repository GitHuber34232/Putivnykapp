package ua.kyiv.putivnyk.domain.geo

import ua.kyiv.putivnyk.data.model.RoutePoint

expect object NativeGeoEngine {
    fun isNativeAvailable(): Boolean
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double
    fun polylineDistanceMeters(points: List<RoutePoint>): Double
    fun nearestPointIndex(points: List<RoutePoint>, lat: Double, lon: Double): Int
    fun simplifyPolyline(points: List<RoutePoint>, toleranceMeters: Double): List<RoutePoint>
}