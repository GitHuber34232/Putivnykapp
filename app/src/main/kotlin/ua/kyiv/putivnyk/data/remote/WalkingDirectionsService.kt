package ua.kyiv.putivnyk.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.kyiv.putivnyk.data.local.OfflineRoutingService
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode
import ua.kyiv.putivnyk.di.OsrmClient
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalkingDirectionsService @Inject constructor(
    @param:OsrmClient private val client: OkHttpClient,
    private val gson: Gson,
    private val offlineRoutingService: OfflineRoutingService,
) {

    data class WalkingRouteResult(
        val geometry: List<RoutePoint>,
        val distanceMeters: Double? = null,
        val durationSeconds: Double? = null,
    )

    companion object {
        private const val TAG = "WalkingDirections"
        private const val OSRM_BASE = "https://router.project-osrm.org/route/v1"
        private const val MAX_WAYPOINTS_PER_REQUEST = 25
        private const val BACKTRACK_TOLERANCE_METERS = 25.0
        private const val MIN_MIRROR_LENGTH = 3
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
    }

    suspend fun fetchWalkingRoute(
        waypoints: List<RoutePoint>,
        transportMode: TransportMode = TransportMode.WALKING
    ): List<RoutePoint> {
        return fetchWalkingRouteDetails(waypoints, transportMode).geometry
    }

    suspend fun fetchWalkingRouteDetails(
        waypoints: List<RoutePoint>,
        transportMode: TransportMode = TransportMode.WALKING
    ): WalkingRouteResult {
        val profile = when (transportMode) {
            TransportMode.WALKING -> "foot"
            TransportMode.DRIVING -> "driving"
        }
        Log.d(TAG, "fetchWalkingRoute called with ${waypoints.size} waypoints, profile=$profile")
        if (waypoints.size < 2) return WalkingRouteResult(geometry = waypoints)

        return withContext(Dispatchers.IO) {
            try {

                if (waypoints.size > MAX_WAYPOINTS_PER_REQUEST) {
                    return@withContext fetchInBatches(waypoints, profile)
                }
                val raw = fetchSingleRoute(waypoints, profile)
                if (raw != null) {
                    Log.d(TAG, "OSRM returned ${raw.geometry.size} points (was ${waypoints.size} waypoints)")
                    val collapsed = collapseBacktracks(raw.geometry)
                    if (collapsed.size < raw.geometry.size) {
                        Log.d(TAG, "Collapsed backtracks: ${raw.geometry.size} → ${collapsed.size} points")
                    }
                    raw.copy(geometry = collapsed)
                } else {
                    Log.w(TAG, "OSRM returned null, trying offline GraphHopper")
                    offlineRoutingService.route(waypoints)
                        ?: straightLineFallback(waypoints)
                }
            } catch (e: Exception) {
                Log.w(TAG, "OSRM failed (${e.message}), trying offline GraphHopper")
                try {
                    offlineRoutingService.route(waypoints)
                        ?: straightLineFallback(waypoints)
                } catch (offlineError: Exception) {
                    Log.e(TAG, "Offline routing also failed", offlineError)
                    straightLineFallback(waypoints)
                }
            }
        }
    }

    private fun straightLineFallback(waypoints: List<RoutePoint>): WalkingRouteResult {
        Log.w(TAG, "Using straight-line fallback for ${waypoints.size} waypoints")
        return WalkingRouteResult(
            geometry = waypoints,
            distanceMeters = NativeGeoDistance.estimatePolylineDistance(waypoints)
        )
    }

    private suspend fun fetchSingleRoute(
        waypoints: List<RoutePoint>,
        profile: String = "foot"
    ): WalkingRouteResult? {

        val coords = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        val url = "$OSRM_BASE/$profile/$coords?overview=full&geometries=geojson"
        Log.d(TAG, "Requesting OSRM: $url")

        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                val result = executeOsrmRequest(url)
                if (result != null) return result
                Log.w(TAG, "OSRM attempt $attempt/$MAX_RETRIES returned null")
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "OSRM attempt $attempt/$MAX_RETRIES failed: ${e.message}")
            }
            if (attempt < MAX_RETRIES) {
                val backoff = INITIAL_BACKOFF_MS * (1L shl (attempt - 1))
                Log.d(TAG, "Retrying in ${backoff}ms...")
                delay(backoff)
            }
        }
        Log.e(TAG, "All $MAX_RETRIES OSRM attempts failed", lastException)
        return null
    }

    private fun executeOsrmRequest(url: String): WalkingRouteResult? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Putivnyk/1.0")
            .get()
            .build()

        val response = client.newCall(request).execute()
        Log.d(TAG, "OSRM response code: ${response.code}")
        if (!response.isSuccessful) {
            Log.w(TAG, "OSRM returned ${response.code}")
            response.close()
            return null
        }

        val body = response.body?.string()
        response.close()
        if (body == null) {
            Log.w(TAG, "OSRM response body is null")
            return null
        }
        Log.d(TAG, "OSRM response body length: ${body.length}")

        return parseOsrmResponse(body)
    }

    private fun parseOsrmResponse(json: String): WalkingRouteResult? {
        return try {
            val root = gson.fromJson(json, JsonObject::class.java)
            val code = root.get("code")?.asString
            if (code != "Ok") {
                Log.w(TAG, "OSRM code: $code, full response: ${json.take(500)}")
                return null
            }
            val routes = root.getAsJsonArray("routes")
            if (routes == null || routes.size() == 0) {
                Log.w(TAG, "OSRM no routes in response")
                return null
            }

            val routeObject = routes[0].asJsonObject
            val geometry = routeObject
                .getAsJsonObject("geometry")
            val coordinates = geometry.getAsJsonArray("coordinates")
            Log.d(TAG, "OSRM geometry has ${coordinates.size()} coordinate pairs")

            val points = mutableListOf<RoutePoint>()
            for (coord in coordinates) {
                val arr = coord.asJsonArray
                val lon = arr[0].asDouble
                val lat = arr[1].asDouble
                points.add(RoutePoint(latitude = lat, longitude = lon))
            }
            val distanceMeters = routeObject.get("distance")?.asDouble
            val durationSeconds = routeObject.get("duration")?.asDouble

            Log.d(TAG, "Parsed ${points.size} route points from OSRM")
            if (points.size < 2) null else WalkingRouteResult(
                geometry = points,
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OSRM response: ${json.take(300)}", e)
            null
        }
    }

    private suspend fun fetchInBatches(
        waypoints: List<RoutePoint>,
        profile: String = "foot"
    ): WalkingRouteResult {
        val batchSize = MAX_WAYPOINTS_PER_REQUEST
        val result = mutableListOf<RoutePoint>()
        var totalDistance = 0.0
        var totalDuration = 0.0

        var start = 0
        while (start < waypoints.size - 1) {
            val end = (start + batchSize).coerceAtMost(waypoints.size)
            val batch = waypoints.subList(start, end)
            val batchResult = fetchSingleRoute(batch, profile)

            if (batchResult != null && batchResult.geometry.isNotEmpty()) {

                if (result.isNotEmpty()) {
                    result.addAll(batchResult.geometry.drop(1))
                } else {
                    result.addAll(batchResult.geometry)
                }
                totalDistance += batchResult.distanceMeters ?: NativeGeoDistance.estimatePolylineDistance(batchResult.geometry)
                totalDuration += batchResult.durationSeconds ?: 0.0
            } else {

                if (result.isNotEmpty()) {
                    result.addAll(batch.drop(1))
                } else {
                    result.addAll(batch)
                }
                totalDistance += NativeGeoDistance.estimatePolylineDistance(batch)
            }

            start = end - 1
        }

        return WalkingRouteResult(
            geometry = collapseBacktracks(result),
            distanceMeters = totalDistance.takeIf { it > 0.0 },
            durationSeconds = totalDuration.takeIf { it > 0.0 }
        )
    }

    internal fun collapseBacktracks(points: List<RoutePoint>): List<RoutePoint> {
        if (points.size < MIN_MIRROR_LENGTH * 2 + 2) return points

        val result = mutableListOf<RoutePoint>()
        var i = 0
        while (i < points.size) {
            result.add(points[i])

            if (i >= MIN_MIRROR_LENGTH && i + MIN_MIRROR_LENGTH < points.size) {
                var ml = 0
                while (i + ml + 1 < points.size && i - ml - 1 >= 0) {
                    val ahead = points[i + ml + 1]
                    val behind = points[i - ml - 1]
                    if (haversineMeters(ahead.latitude, ahead.longitude,
                            behind.latitude, behind.longitude) < BACKTRACK_TOLERANCE_METERS
                    ) {
                        ml++
                    } else {
                        break
                    }
                }
                if (ml >= MIN_MIRROR_LENGTH) {
                    Log.d(TAG, "Collapsed odd backtrack of $ml pts at index $i")
                    i = i + ml + 1
                    continue
                }
            }

            if (i >= MIN_MIRROR_LENGTH && i + MIN_MIRROR_LENGTH + 1 < points.size) {
                val cur = points[i]
                val nxt = points[i + 1]
                if (haversineMeters(cur.latitude, cur.longitude,
                        nxt.latitude, nxt.longitude) < BACKTRACK_TOLERANCE_METERS
                ) {
                    var ml = 0
                    while (i + ml + 2 < points.size && i - ml - 1 >= 0) {
                        val ahead = points[i + ml + 2]
                        val behind = points[i - ml - 1]
                        if (haversineMeters(ahead.latitude, ahead.longitude,
                                behind.latitude, behind.longitude) < BACKTRACK_TOLERANCE_METERS
                        ) {
                            ml++
                        } else {
                            break
                        }
                    }
                    if (ml >= MIN_MIRROR_LENGTH) {
                        Log.d(TAG, "Collapsed even backtrack of $ml pts at index $i")

                        i = i + ml + 2
                        continue
                    }
                }
            }

            i++
        }
        return result
    }

    private fun haversineMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6_371_000.0
        val dLat = (lat2 - lat1) * (PI / 180.0)
        val dLon = (lon2 - lon1) * (PI / 180.0)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
                sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * r * asin(sqrt(a))
    }

    private object NativeGeoDistance {
        fun estimatePolylineDistance(points: List<RoutePoint>): Double {
            if (points.size < 2) return 0.0
            return points.zipWithNext().sumOf { (from, to) ->
                val dLat = from.latitude - to.latitude
                val dLon = from.longitude - to.longitude
                if (dLat == 0.0 && dLon == 0.0) 0.0 else haversine(
                    from.latitude,
                    from.longitude,
                    to.latitude,
                    to.longitude
                )
            }
        }

        private fun haversine(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Double {
            val r = 6_371_000.0
            val dLat = (lat2 - lat1) * (PI / 180.0)
            val dLon = (lon2 - lon1) * (PI / 180.0)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
                sin(dLon / 2) * sin(dLon / 2)
            return 2.0 * r * asin(sqrt(a))
        }
    }
}
