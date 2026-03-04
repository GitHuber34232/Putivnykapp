package ua.kyiv.putivnyk.domain.geo

import ua.kyiv.putivnyk.data.model.RoutePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object NativeGeoEngine {
    private const val EARTH_RADIUS_METERS = 6371000.0

    private val nativeAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary("putivnyk_native")
            true
        }.getOrDefault(false)
    }

    fun isNativeAvailable(): Boolean = nativeAvailable

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return if (nativeAvailable) {
            nativeDistanceMeters(lat1, lon1, lat2, lon2)
        } else {
            fallbackDistanceMeters(lat1, lon1, lat2, lon2)
        }
    }

    fun polylineDistanceMeters(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0

        if (nativeAvailable) {
            return nativePolylineDistanceMeters(points.toFlatArray())
        }

        return points.zipWithNext().sumOf { (a, b) ->
            fallbackDistanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
    }

    fun nearestPointIndex(points: List<RoutePoint>, lat: Double, lon: Double): Int {
        if (points.isEmpty()) return -1

        if (nativeAvailable) {
            return nativeNearestPointIndex(points.toFlatArray(), lat, lon)
        }

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

    fun simplifyPolyline(points: List<RoutePoint>, toleranceMeters: Double): List<RoutePoint> {
        if (points.size < 3 || toleranceMeters <= 0.0) return points
        if (!nativeAvailable) return points

        val simplified = nativeSimplifyPolyline(points.toFlatArray(), toleranceMeters)
        return simplified.toRoutePoints()
    }

    private fun List<RoutePoint>.toFlatArray(): DoubleArray {
        val out = DoubleArray(size * 2)
        forEachIndexed { index, point ->
            out[index * 2] = point.latitude
            out[index * 2 + 1] = point.longitude
        }
        return out
    }

    private fun DoubleArray.toRoutePoints(): List<RoutePoint> {
        if (isEmpty() || size % 2 != 0) return emptyList()
        val result = ArrayList<RoutePoint>(size / 2)
        var i = 0
        while (i < size - 1) {
            result += RoutePoint(latitude = this[i], longitude = this[i + 1])
            i += 2
        }
        return result
    }

    private fun fallbackDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    private external fun nativeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double
    private external fun nativePolylineDistanceMeters(points: DoubleArray): Double
    private external fun nativeNearestPointIndex(points: DoubleArray, lat: Double, lon: Double): Int
    private external fun nativeSimplifyPolyline(points: DoubleArray, toleranceMeters: Double): DoubleArray
}
