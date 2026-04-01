package ua.kyiv.putivnyk.domain.geo

import ua.kyiv.putivnyk.data.model.RoutePoint

actual object NativeGeoEngine {
    private val nativeAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary("putivnyk_native")
            true
        }.getOrDefault(false)
    }

    actual fun isNativeAvailable(): Boolean = nativeAvailable

    actual fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return if (nativeAvailable) {
            nativeDistanceMeters(lat1, lon1, lat2, lon2)
        } else {
            GeoMath.fallbackDistanceMeters(lat1, lon1, lat2, lon2)
        }
    }

    actual fun polylineDistanceMeters(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0
        if (nativeAvailable) {
            return nativePolylineDistanceMeters(points.toFlatArray())
        }
        return GeoMath.polylineDistanceMeters(points)
    }

    actual fun nearestPointIndex(points: List<RoutePoint>, lat: Double, lon: Double): Int {
        if (points.isEmpty()) return -1
        if (nativeAvailable) {
            return nativeNearestPointIndex(points.toFlatArray(), lat, lon)
        }
        return GeoMath.nearestPointIndex(points, lat, lon)
    }

    actual fun simplifyPolyline(points: List<RoutePoint>, toleranceMeters: Double): List<RoutePoint> {
        if (points.size < 3 || toleranceMeters <= 0.0) return points
        if (!nativeAvailable) return points
        return nativeSimplifyPolyline(points.toFlatArray(), toleranceMeters).toRoutePoints()
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

    private external fun nativeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double
    private external fun nativePolylineDistanceMeters(points: DoubleArray): Double
    private external fun nativeNearestPointIndex(points: DoubleArray, lat: Double, lon: Double): Int
    private external fun nativeSimplifyPolyline(points: DoubleArray, toleranceMeters: Double): DoubleArray
}