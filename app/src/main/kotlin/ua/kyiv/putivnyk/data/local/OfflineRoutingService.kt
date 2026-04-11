package ua.kyiv.putivnyk.data.local

import android.content.Context
import android.util.Log
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode
import ua.kyiv.putivnyk.data.remote.WalkingDirectionsService.WalkingRouteResult
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineRoutingService @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OfflineRouting"
        private const val GHZ_ASSET = "routing/kyiv-gh.ghz"
        private const val GRAPH_DIR_NAME = "graphhopper-kyiv"

        internal fun profileFor(transportMode: TransportMode): String = when (transportMode) {
            TransportMode.WALKING -> "foot"
            TransportMode.DRIVING -> "car"
        }
    }

    private val mutex = Mutex()
    private var hopper: GraphHopper? = null
    private var initFailed = false

    suspend fun route(
        waypoints: List<RoutePoint>,
        transportMode: TransportMode = TransportMode.WALKING
    ): WalkingRouteResult? {
        if (waypoints.size < 2) return null
        val gh = getHopper() ?: return null
        val profile = profileFor(transportMode)

        return withContext(Dispatchers.Default) {
            try {
                val allPoints = mutableListOf<RoutePoint>()
                var totalDistance = 0.0
                var totalDuration = 0.0

                for (i in 0 until waypoints.size - 1) {
                    val from = waypoints[i]
                    val to = waypoints[i + 1]

                    val req = GHRequest(
                        from.latitude, from.longitude,
                        to.latitude, to.longitude
                    ).apply {
                        this.profile = profile
                    }

                    val rsp = gh.route(req)
                    if (rsp.hasErrors()) {
                        Log.w(TAG, "GraphHopper segment $i error: ${rsp.errors.first().message}")
                        if (allPoints.isEmpty()) allPoints.add(from)
                        allPoints.add(to)
                        continue
                    }

                    val best = rsp.best
                    val points = best.points
                    val segmentPoints = points.map { ghPoint ->
                        RoutePoint(
                            latitude = ghPoint.lat,
                            longitude = ghPoint.lon
                        )
                    }

                    if (allPoints.isEmpty()) {
                        allPoints.addAll(segmentPoints)
                    } else if (segmentPoints.isNotEmpty()) {
                        allPoints.addAll(segmentPoints.drop(1))
                    }

                    totalDistance += best.distance
                    totalDuration += best.time / 1000.0
                }

                Log.d(TAG, "Offline route ($profile): ${allPoints.size} points, ${totalDistance.toInt()} m")
                WalkingRouteResult(
                    geometry = allPoints,
                    distanceMeters = totalDistance,
                    durationSeconds = totalDuration
                )
            } catch (e: Exception) {
                Log.e(TAG, "Offline routing failed", e)
                null
            }
        }
    }

    fun isAvailable(): Boolean = hopper != null && !initFailed

    suspend fun tryInit(): Boolean {
        return getHopper() != null
    }

    private suspend fun getHopper(): GraphHopper? {
        if (initFailed) return null
        hopper?.let { return it }

        return mutex.withLock {
            hopper?.let { return it }
            if (initFailed) return null

            withContext(Dispatchers.IO) {
                try {
                    val graphDir = File(context.filesDir, GRAPH_DIR_NAME)
                    if (!graphDir.exists()) {
                        Log.d(TAG, "Extracting GHZ from assets...")
                        extractGhz(graphDir)
                    }

                    Log.d(TAG, "Initializing GraphHopper from ${graphDir.absolutePath}")
                    val gh = GraphHopper().apply {
                        setGraphHopperLocation(graphDir.absolutePath)
                        setAllowWrites(false)
                    }
                    gh.load()
                    hopper = gh
                    Log.d(TAG, "GraphHopper initialized successfully")
                    gh
                } catch (e: Exception) {
                    Log.e(TAG, "GraphHopper initialization failed", e)
                    initFailed = true
                    null
                }
            }
        }
    }

    private fun extractGhz(targetDir: File) {
        targetDir.mkdirs()
        context.assets.open(GHZ_ASSET).use { raw ->
            ZipInputStream(raw).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(targetDir, entry.name)
                    if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        throw SecurityException("Zip entry outside target dir: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out ->
                            zis.copyTo(out)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        Log.d(TAG, "GHZ extracted to ${targetDir.absolutePath}")
    }
}
