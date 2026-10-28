package ua.kyiv.putivnyk.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode
import ua.kyiv.putivnyk.data.repository.AchievementsRepository
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.repository.StatsRepository
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import ua.kyiv.putivnyk.domain.stats.StatsCounters
import ua.kyiv.putivnyk.domain.usecase.routing.RouteNavigationMetrics
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Sharing routes as links and QR codes, and turning received ones back into saved routes. */
@Singleton
class RouteShareManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val routeRepository: RouteRepository,
    private val statsRepository: StatsRepository,
    private val achievementsRepository: AchievementsRepository
) {
    fun shareCode(route: Route, withNames: Boolean = true): String = RouteShareCodec.encode(
        RouteShareCodec.SharedRoute(
            name = route.name,
            transportMode = route.transportMode,
            points = limitPoints(listOf(route.startPoint) + route.waypoints + route.endPoint)
                .map { if (withNames) it else it.copy(name = null) }
        )
    )

    fun link(route: Route): String = RouteShareCodec.toLink(shareCode(route))

    /**
     * Text of the QR code: the full link while it stays easy to scan, otherwise the link
     * without stop names (stops keep their positions); null if even that is too long.
     */
    fun qrContent(route: Route): String? {
        val full = link(route)
        if (full.length <= QR_COMFORTABLE_LENGTH) return full
        return RouteShareCodec.toLink(shareCode(route, withNames = false)).takeIf { it.length <= QR_MAX_LENGTH }
    }

    /** Opens the system share sheet with the link in the text and the QR code as an image. */
    suspend fun share(activityContext: Context, route: Route, message: String, chooserTitle: String) {
        val link = link(route)
        val qrText = qrContent(route)
        val uri = qrText?.let { text ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val directory = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
                    val file = File(directory, "route_${route.id}.png")
                    FileOutputStream(file).use { out ->
                        renderQr(encodeQr(text), scale = QR_SCALE).compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }.getOrNull()
            }
        }
        val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "$message\n$link")
        if (uri != null) {
            send.setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Android 17 no longer grants EXTRA_STREAM access implicitly: grant it through ClipData.
            send.clipData = ClipData.newRawUri(route.name, uri)
        } else {
            send.setType("text/plain")
        }
        activityContext.startActivity(Intent.createChooser(send, chooserTitle))
        statsRepository.increment(StatsCounters.ROUTES_SHARED)
        runCatching { achievementsRepository.evaluate() }
    }

    /** Saves a received route; returns its new id. */
    suspend fun import(shared: RouteShareCodec.SharedRoute): Long {
        val id = saveRoute(shared.name, shared.points, shared.transportMode, description = null)
        statsRepository.increment(StatsCounters.ROUTES_IMPORTED)
        return id
    }

    /** Saves a walk through [places] in order, reusing an identical route saved earlier. */
    suspend fun saveRouteFromPlaces(name: String, places: List<Place>, description: String? = null): Long {
        val points = places.map { RoutePoint(it.latitude, it.longitude, it.name) }
        return saveRoute(name, points, TransportMode.WALKING, description)
    }

    private suspend fun saveRoute(
        name: String,
        points: List<RoutePoint>,
        transportMode: TransportMode,
        description: String?
    ): Long {
        require(points.size >= 2) { "A route needs at least two points" }
        val existing = routeRepository.getAllRoutesSnapshot().firstOrNull { route ->
            route.name == name && samePoints(listOf(route.startPoint) + route.waypoints + route.endPoint, points)
        }
        if (existing != null) return existing.id
        val straight = NativeGeoEngine.polylineDistanceMeters(points)
        val distance = if (transportMode == TransportMode.WALKING) straight * WALKING_DETOUR_FACTOR else straight * DRIVING_DETOUR_FACTOR
        return routeRepository.saveRoute(
            Route(
                name = name,
                description = description,
                startPoint = points.first(),
                endPoint = points.last(),
                waypoints = points.subList(1, points.lastIndex),
                distance = distance,
                estimatedDuration = RouteNavigationMetrics.estimateDurationMinutes(distance, transportMode),
                transportMode = transportMode
            )
        )
    }

    private fun samePoints(a: List<RoutePoint>, b: List<RoutePoint>): Boolean =
        a.size == b.size && a.zip(b).all { (x, y) ->
            NativeGeoEngine.distanceMeters(x.latitude, x.longitude, y.latitude, y.longitude) < 5.0
        }

    companion object {
        private const val SHARE_DIRECTORY = "shared"
        private const val WALKING_DETOUR_FACTOR = 1.3
        private const val DRIVING_DETOUR_FACTOR = 1.4
        private const val QR_SCALE = 12

        /** About QR version 25: still scans quickly from another phone's screen. */
        const val QR_COMFORTABLE_LENGTH = 1_200

        /** Byte capacity of the largest QR code (version 40) at error correction level M. */
        const val QR_MAX_LENGTH = 2_331

        /** Long routes are thinned out evenly to the codec limit; start and end are kept. */
        fun limitPoints(points: List<RoutePoint>, max: Int = RouteShareCodec.MAX_POINTS): List<RoutePoint> {
            if (points.size <= max) return points
            val step = (points.size - 1).toDouble() / (max - 1)
            return (0 until max).map { i -> points[Math.round(i * step).toInt().coerceAtMost(points.lastIndex)] }
        }

        fun encodeQr(text: String): BitMatrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        )

        fun renderQr(matrix: BitMatrix, scale: Int): Bitmap {
            val width = matrix.width * scale
            val height = matrix.height * scale
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val row = y * width
                for (x in 0 until width) {
                    pixels[row + x] = if (matrix.get(x / scale, y / scale)) Color.BLACK else Color.WHITE
                }
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }
    }
}
