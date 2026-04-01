package ua.kyiv.putivnyk.domain.geo

import ua.kyiv.putivnyk.data.model.RoutePoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object GeoMath {
    private const val EARTH_RADIUS_METERS = 6371000.0

    fun fallbackDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = degreesToRadians(lat2 - lat1)
        val dLon = degreesToRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(degreesToRadians(lat1)) * cos(degreesToRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    private fun degreesToRadians(value: Double): Double = value * PI / 180.0

    fun polylineDistanceMeters(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0
        return points.zipWithNext().sumOf { (a, b) ->
            fallbackDistanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
    }

    fun nearestPointIndex(points: List<RoutePoint>, lat: Double, lon: Double): Int {
        if (points.isEmpty()) return -1

        var bestIndex = 0
        var bestDistance = Double.MAX_VALUE
        points.forEachIndexed { index, point ->
            val distance = fallbackDistanceMeters(point.latitude, point.longitude, lat, lon)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }
}