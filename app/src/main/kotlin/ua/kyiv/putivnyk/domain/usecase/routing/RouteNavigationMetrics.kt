package ua.kyiv.putivnyk.domain.usecase.routing

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sqrt
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine

object RouteNavigationMetrics {
    const val DEFAULT_WALKING_SPEED_METERS_PER_MINUTE = 76.0
    const val DEFAULT_DRIVING_SPEED_METERS_PER_MINUTE = 500.0

    data class PolylineProjection(
        val segmentIndex: Int,
        val snappedPoint: RoutePoint,
        val distanceToRouteMeters: Double,
        val distanceFromStartMeters: Double,
        val totalDistanceMeters: Double
    )

    fun estimateDurationMinutes(
        distanceMeters: Double,
        walkingSpeedMetersPerMinute: Double = DEFAULT_WALKING_SPEED_METERS_PER_MINUTE
    ): Int {
        if (distanceMeters <= 0.0) return 0
        return ceil(distanceMeters / walkingSpeedMetersPerMinute).toInt().coerceAtLeast(1)
    }

    fun estimateDurationMinutes(
        distanceMeters: Double,
        transportMode: TransportMode
    ): Int {
        val speed = when (transportMode) {
            TransportMode.WALKING -> DEFAULT_WALKING_SPEED_METERS_PER_MINUTE
            TransportMode.DRIVING -> DEFAULT_DRIVING_SPEED_METERS_PER_MINUTE
        }
        return estimateDurationMinutes(distanceMeters, speed)
    }

    fun projectOnPolyline(
        polyline: List<RoutePoint>,
        latitude: Double,
        longitude: Double
    ): PolylineProjection? {
        if (polyline.isEmpty()) return null
        if (polyline.size == 1) {
            val onlyPoint = polyline.first()
            return PolylineProjection(
                segmentIndex = 0,
                snappedPoint = onlyPoint,
                distanceToRouteMeters = NativeGeoEngine.distanceMeters(
                    latitude,
                    longitude,
                    onlyPoint.latitude,
                    onlyPoint.longitude
                ),
                distanceFromStartMeters = 0.0,
                totalDistanceMeters = 0.0
            )
        }

        var traversedMeters = 0.0
        var bestProjection: PolylineProjection? = null

        polyline.zipWithNext().forEachIndexed { index, (start, end) ->
            val segmentLength = NativeGeoEngine.distanceMeters(
                start.latitude,
                start.longitude,
                end.latitude,
                end.longitude
            )

            val candidate = projectOnSegment(
                start = start,
                end = end,
                latitude = latitude,
                longitude = longitude,
                segmentLengthMeters = segmentLength,
                traversedBeforeSegmentMeters = traversedMeters,
                segmentIndex = index,
                totalDistanceMeters = 0.0
            )

            if (bestProjection == null || candidate.distanceToRouteMeters < bestProjection.distanceToRouteMeters) {
                bestProjection = candidate
            }

            traversedMeters += segmentLength
        }

        val totalDistance = traversedMeters
        return bestProjection?.copy(totalDistanceMeters = totalDistance)
    }

    fun remainingGeometry(
        polyline: List<RoutePoint>,
        projection: PolylineProjection,
        duplicateToleranceMeters: Double = 4.0
    ): List<RoutePoint> {
        if (polyline.isEmpty()) return emptyList()
        if (polyline.size == 1) return polyline

        val nextIndex = (projection.segmentIndex + 1).coerceAtMost(polyline.lastIndex)
        val nextPoint = polyline[nextIndex]

        return buildList {
            add(projection.snappedPoint)
            if (NativeGeoEngine.distanceMeters(
                    projection.snappedPoint.latitude,
                    projection.snappedPoint.longitude,
                    nextPoint.latitude,
                    nextPoint.longitude
                ) > duplicateToleranceMeters
            ) {
                add(nextPoint)
            }
            addAll(polyline.drop(nextIndex + 1))
        }
    }

    private fun projectOnSegment(
        start: RoutePoint,
        end: RoutePoint,
        latitude: Double,
        longitude: Double,
        segmentLengthMeters: Double,
        traversedBeforeSegmentMeters: Double,
        segmentIndex: Int,
        totalDistanceMeters: Double
    ): PolylineProjection {
        if (segmentLengthMeters <= 0.0) {
            return PolylineProjection(
                segmentIndex = segmentIndex,
                snappedPoint = start,
                distanceToRouteMeters = NativeGeoEngine.distanceMeters(
                    latitude,
                    longitude,
                    start.latitude,
                    start.longitude
                ),
                distanceFromStartMeters = traversedBeforeSegmentMeters,
                totalDistanceMeters = totalDistanceMeters
            )
        }

        val referenceLatitudeRadians = Math.toRadians((start.latitude + end.latitude + latitude) / 3.0)
        val startX = longitudeToMeters(start.longitude, referenceLatitudeRadians)
        val startY = latitudeToMeters(start.latitude)
        val endX = longitudeToMeters(end.longitude, referenceLatitudeRadians)
        val endY = latitudeToMeters(end.latitude)
        val userX = longitudeToMeters(longitude, referenceLatitudeRadians)
        val userY = latitudeToMeters(latitude)

        val dx = endX - startX
        val dy = endY - startY
        val lengthSquared = dx * dx + dy * dy
        val rawT = if (lengthSquared == 0.0) {
            0.0
        } else {
            ((userX - startX) * dx + (userY - startY) * dy) / lengthSquared
        }
        val t = rawT.coerceIn(0.0, 1.0)

        val snappedX = startX + dx * t
        val snappedY = startY + dy * t
        val snappedLat = metersToLatitude(snappedY)
        val snappedLon = metersToLongitude(snappedX, referenceLatitudeRadians)
        val snappedPoint = RoutePoint(latitude = snappedLat, longitude = snappedLon)

        val distanceToRoute = sqrt((userX - snappedX) * (userX - snappedX) + (userY - snappedY) * (userY - snappedY))
        val distanceFromStart = traversedBeforeSegmentMeters + segmentLengthMeters * t

        return PolylineProjection(
            segmentIndex = segmentIndex,
            snappedPoint = snappedPoint,
            distanceToRouteMeters = distanceToRoute,
            distanceFromStartMeters = distanceFromStart,
            totalDistanceMeters = totalDistanceMeters
        )
    }

    private fun latitudeToMeters(latitude: Double): Double = EARTH_RADIUS_METERS * Math.toRadians(latitude)

    private fun longitudeToMeters(longitude: Double, referenceLatitudeRadians: Double): Double =
        EARTH_RADIUS_METERS * Math.toRadians(longitude) * cos(referenceLatitudeRadians)

    private fun metersToLatitude(yMeters: Double): Double = Math.toDegrees(yMeters / EARTH_RADIUS_METERS)

    private fun metersToLongitude(xMeters: Double, referenceLatitudeRadians: Double): Double {
        val cosLat = cos(referenceLatitudeRadians).coerceAtLeast(MIN_COS_LATITUDE)
        return Math.toDegrees(xMeters / (EARTH_RADIUS_METERS * cosLat))
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MIN_COS_LATITUDE = 0.01
}