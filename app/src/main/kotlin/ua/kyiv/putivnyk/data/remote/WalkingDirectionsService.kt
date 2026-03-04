package ua.kyiv.putivnyk.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.kyiv.putivnyk.data.model.RoutePoint
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
) {

    companion object {
        private const val TAG = "WalkingDirections"
        private const val OSRM_BASE = "https://router.project-osrm.org/route/v1/foot"
        private const val MAX_WAYPOINTS_PER_REQUEST = 25
        private const val BACKTRACK_TOLERANCE_METERS = 25.0
        private const val MIN_MIRROR_LENGTH = 3
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
    }

    suspend fun fetchWalkingRoute(waypoints: List<RoutePoint>): List<RoutePoint> {
        Log.d(TAG, "fetchWalkingRoute called with ${waypoints.size} waypoints")
        if (waypoints.size < 2) return waypoints

        return withContext(Dispatchers.IO) {
            try {

                if (waypoints.size > MAX_WAYPOINTS_PER_REQUEST) {
                    return@withContext fetchInBatches(waypoints)
                }
                val raw = fetchSingleRoute(waypoints)
                if (raw != null) {
                    Log.d(TAG, "OSRM returned ${raw.size} points (was ${waypoints.size} waypoints)")
                    val collapsed = collapseBacktracks(raw)
                    if (collapsed.size < raw.size) {
                        Log.d(TAG, "Collapsed backtracks: ${raw.size} → ${collapsed.size} points")
                    }
                    collapsed
                } else {
                    Log.w(TAG, "fetchSingleRoute returned null, falling back to straight lines")
                    waypoints
                }
            } catch (e: Exception) {
                Log.e(TAG, "Walking directions failed, using straight lines", e)
                waypoints
            }
        }
    }

    private suspend fun fetchSingleRoute(waypoints: List<RoutePoint>): List<RoutePoint>? {

        val coords = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        val url = "$OSRM_BASE/$coords?overview=full&geometries=geojson"
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

    private fun executeOsrmRequest(url: String): List<RoutePoint>? {
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

    private fun parseOsrmResponse(json: String): List<RoutePoint>? {
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

            val geometry = routes[0].asJsonObject
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

            Log.d(TAG, "Parsed ${points.size} route points from OSRM")
            if (points.size < 2) null else points
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OSRM response: ${json.take(300)}", e)
            null
        }
    }

    private suspend fun fetchInBatches(waypoints: List<RoutePoint>): List<RoutePoint> {
        val batchSize = MAX_WAYPOINTS_PER_REQUEST
        val result = mutableListOf<RoutePoint>()

        var start = 0
        while (start < waypoints.size - 1) {
            val end = (start + batchSize).coerceAtMost(waypoints.size)
            val batch = waypoints.subList(start, end)
            val batchResult = fetchSingleRoute(batch)

            if (batchResult != null && batchResult.isNotEmpty()) {

                if (result.isNotEmpty()) {
                    result.addAll(batchResult.drop(1))
                } else {
                    result.addAll(batchResult)
                }
            } else {

                if (result.isNotEmpty()) {
                    result.addAll(batch.drop(1))
                } else {
                    result.addAll(batch)
                }
            }

            start = end - 1
        }

        return collapseBacktracks(result)
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
}
