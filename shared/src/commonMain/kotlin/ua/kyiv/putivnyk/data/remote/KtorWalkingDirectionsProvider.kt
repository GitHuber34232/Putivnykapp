package ua.kyiv.putivnyk.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.domain.geo.GeoMath
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class KtorWalkingDirectionsProvider(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_OSRM_BASE_URL
) : WalkingDirectionsProvider {

    override suspend fun fetchWalkingRoute(waypoints: List<RoutePoint>): List<RoutePoint> =
        fetchWalkingRouteResult(waypoints).geometry

    override suspend fun fetchWalkingRouteResult(waypoints: List<RoutePoint>): WalkingRouteResult {
        if (waypoints.size < 2) return fallbackResult(waypoints)

        return runCatching {
            if (waypoints.size > MAX_WAYPOINTS_PER_REQUEST) {
                fetchInBatches(waypoints)
            } else {
                fetchSingleRoute(waypoints) ?: fallbackResult(waypoints)
            }
        }.getOrElse { fallbackResult(waypoints) }
    }

    private suspend fun fetchSingleRoute(waypoints: List<RoutePoint>): WalkingRouteResult? {
        val coordinates = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }

        repeat(MAX_RETRIES) { attempt ->
            val result = runCatching {
                client.get(baseUrl.trimEnd('/') + "/$coordinates") {
                    parameter("overview", "full")
                    parameter("geometries", "geojson")
                }.body<OsrmRouteResponse>()
            }.getOrNull()

            val route = result?.toWalkingRouteResult()
            if (route != null) {
                return route
            }

            if (attempt + 1 < MAX_RETRIES) {
                delay(INITIAL_BACKOFF_MS * (1L shl attempt))
            }
        }

        return null
    }

    private suspend fun fetchInBatches(waypoints: List<RoutePoint>): WalkingRouteResult {
        val result = mutableListOf<RoutePoint>()
        var totalDistance = 0.0
        var totalDuration = 0.0
        var usedFallback = false
        var start = 0

        while (start < waypoints.size - 1) {
            val end = (start + MAX_WAYPOINTS_PER_REQUEST).coerceAtMost(waypoints.size)
            val batch = waypoints.subList(start, end)
            val batchResult = fetchSingleRoute(batch)

            if (batchResult != null) {
                if (result.isNotEmpty()) {
                    result.addAll(batchResult.geometry.drop(1))
                } else {
                    result.addAll(batchResult.geometry)
                }
                totalDistance += batchResult.distanceMeters ?: 0.0
                totalDuration += batchResult.durationSeconds ?: 0.0
                usedFallback = usedFallback || batchResult.usedFallback
            } else if (result.isNotEmpty()) {
                result.addAll(batch.drop(1))
                totalDistance += GeoMath.polylineDistanceMeters(batch)
                usedFallback = true
            } else {
                result.addAll(batch)
                totalDistance += GeoMath.polylineDistanceMeters(batch)
                usedFallback = true
            }

            start = end - 1
        }

        val geometry = sanitizeGeometry(result)
        return WalkingRouteResult(
            geometry = geometry,
            distanceMeters = max(totalDistance, GeoMath.polylineDistanceMeters(geometry)),
            durationSeconds = totalDuration.takeIf { it > 0.0 },
            usedFallback = usedFallback
        )
    }

    internal fun collapseBacktracks(points: List<RoutePoint>): List<RoutePoint> {
        if (points.size < MIN_MIRROR_LENGTH * 2 + 2) return points

        val result = mutableListOf<RoutePoint>()
        var index = 0
        while (index < points.size) {
            result.add(points[index])

            if (index >= MIN_MIRROR_LENGTH && index + MIN_MIRROR_LENGTH < points.size) {
                var mirrorLength = 0
                while (index + mirrorLength + 1 < points.size && index - mirrorLength - 1 >= 0) {
                    val ahead = points[index + mirrorLength + 1]
                    val behind = points[index - mirrorLength - 1]
                    if (haversineMeters(ahead.latitude, ahead.longitude, behind.latitude, behind.longitude) < BACKTRACK_TOLERANCE_METERS) {
                        mirrorLength++
                    } else {
                        break
                    }
                }
                if (mirrorLength >= MIN_MIRROR_LENGTH) {
                    index += mirrorLength + 1
                    continue
                }
            }

            if (index >= MIN_MIRROR_LENGTH && index + MIN_MIRROR_LENGTH + 1 < points.size) {
                val current = points[index]
                val next = points[index + 1]
                if (haversineMeters(current.latitude, current.longitude, next.latitude, next.longitude) < BACKTRACK_TOLERANCE_METERS) {
                    var mirrorLength = 0
                    while (index + mirrorLength + 2 < points.size && index - mirrorLength - 1 >= 0) {
                        val ahead = points[index + mirrorLength + 2]
                        val behind = points[index - mirrorLength - 1]
                        if (haversineMeters(ahead.latitude, ahead.longitude, behind.latitude, behind.longitude) < BACKTRACK_TOLERANCE_METERS) {
                            mirrorLength++
                        } else {
                            break
                        }
                    }
                    if (mirrorLength >= MIN_MIRROR_LENGTH) {
                        index += mirrorLength + 2
                        continue
                    }
                }
            }

            index++
        }
        return result
    }

    internal fun trimInitialLoop(points: List<RoutePoint>): List<RoutePoint> {
        if (points.size < 10) return points

        val start = points.first()
        var furthestDistance = 0.0
        var reentryIndex = -1
        val scanEnd = minOf(points.lastIndex - 1, max(12, points.size / 3))

        for (index in 1..scanEnd) {
            val point = points[index]
            val distanceFromStart = haversineMeters(
                start.latitude,
                start.longitude,
                point.latitude,
                point.longitude
            )
            furthestDistance = max(furthestDistance, distanceFromStart)

            if (furthestDistance >= INITIAL_LOOP_MIN_RADIUS_METERS &&
                distanceFromStart <= INITIAL_LOOP_REENTRY_METERS
            ) {
                reentryIndex = index
            }
        }

        return if (reentryIndex > 0 && reentryIndex < points.lastIndex - 2) {
            points.drop(reentryIndex)
        } else {
            points
        }
    }

    private fun sanitizeGeometry(points: List<RoutePoint>): List<RoutePoint> {
        val collapsed = collapseBacktracks(points)
        val trimmed = trimInitialLoop(collapsed)
        return trimmed.fold(mutableListOf<RoutePoint>()) { acc, point ->
            val previous = acc.lastOrNull()
            if (previous == null || haversineMeters(
                    previous.latitude,
                    previous.longitude,
                    point.latitude,
                    point.longitude
                ) >= CONSECUTIVE_DUPLICATE_THRESHOLD_METERS
            ) {
                acc += point
            }
            acc
        }
    }

    private fun fallbackResult(waypoints: List<RoutePoint>): WalkingRouteResult {
        val distance = GeoMath.polylineDistanceMeters(waypoints)
        return WalkingRouteResult(
            geometry = waypoints,
            distanceMeters = distance,
            durationSeconds = (distance / FALLBACK_WALKING_SPEED_METERS_PER_SECOND).takeIf { distance > 0.0 },
            usedFallback = true
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6_371_000.0
        val dLat = (lat2 - lat1) * (PI / 180.0)
        val dLon = (lon2 - lon1) * (PI / 180.0)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * radius * asin(sqrt(a))
    }

    private fun OsrmRouteResponse.toWalkingRouteResult(): WalkingRouteResult? {
        if (code != "Ok") return null
        val route = routes.firstOrNull() ?: return null
        val coordinates = route.geometry.coordinates
        val points = coordinates.mapNotNull { coordinate ->
            val longitude = coordinate.getOrNull(0) ?: return@mapNotNull null
            val latitude = coordinate.getOrNull(1) ?: return@mapNotNull null
            RoutePoint(latitude = latitude, longitude = longitude)
        }
        val geometry = sanitizeGeometry(points)
        if (geometry.size < 2) return null
        return WalkingRouteResult(
            geometry = geometry,
            distanceMeters = route.distance,
            durationSeconds = route.duration,
            usedFallback = false
        )
    }

    private companion object {
        const val DEFAULT_OSRM_BASE_URL = "https://router.project-osrm.org/route/v1/foot"
        const val MAX_WAYPOINTS_PER_REQUEST = 25
        const val BACKTRACK_TOLERANCE_METERS = 25.0
        const val CONSECUTIVE_DUPLICATE_THRESHOLD_METERS = 2.5
        const val INITIAL_LOOP_MIN_RADIUS_METERS = 75.0
        const val INITIAL_LOOP_REENTRY_METERS = 20.0
        const val FALLBACK_WALKING_SPEED_METERS_PER_SECOND = 1.25
        const val MIN_MIRROR_LENGTH = 3
        const val MAX_RETRIES = 3
        const val INITIAL_BACKOFF_MS = 1000L
    }
}

@Serializable
private data class OsrmRouteResponse(
    val code: String,
    val routes: List<OsrmRoute> = emptyList()
)

@Serializable
private data class OsrmRoute(
    val geometry: OsrmGeometry,
    val distance: Double = 0.0,
    val duration: Double = 0.0
)

@Serializable
private data class OsrmGeometry(
    val coordinates: List<List<Double>> = emptyList()
)