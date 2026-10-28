package ua.kyiv.putivnyk.map.offline

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/** Geographic rectangle in degrees. */
data class GeoBounds(
    val north: Double,
    val east: Double,
    val south: Double,
    val west: Double
) {
    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude in south..north && longitude in west..east

    companion object {
        /** Approximate viewport of a phone-sized map around [latitude]/[longitude] at [zoom]. */
        fun around(latitude: Double, longitude: Double, zoom: Int, widthTiles: Double = 2.0, heightTiles: Double = 3.5): GeoBounds {
            val tileDegrees = 360.0 / 2.0.pow(zoom.coerceIn(10, 18))
            val halfLon = tileDegrees * widthTiles / 2.0
            val halfLat = tileDegrees * heightTiles / 2.0 * cos(Math.toRadians(latitude))
            return GeoBounds(
                north = (latitude + halfLat).coerceAtMost(85.0),
                east = (longitude + halfLon).coerceAtMost(180.0),
                south = (latitude - halfLat).coerceAtLeast(-85.0),
                west = (longitude - halfLon).coerceAtLeast(-180.0)
            )
        }
    }
}

/** A map area the user can download for offline use. */
data class OfflineArea(
    val id: String,
    val title: String,
    val bounds: GeoBounds
) {
    val titleKey: String get() = "offline.area.$id"
}

object OfflineAreas {
    /** Kyiv districts and the most visited parts of the city (approximate boxes). */
    val kyiv: List<OfflineArea> = listOf(
        OfflineArea("old_kyiv", "Старий Київ і Хрещатик", GeoBounds(50.462, 30.540, 50.436, 30.495)),
        OfflineArea("podil", "Поділ", GeoBounds(50.476, 30.535, 50.456, 30.498)),
        OfflineArea("pechersk", "Печерськ і Лавра", GeoBounds(50.449, 30.572, 50.418, 30.519)),
        OfflineArea("shevchenkivskyi", "Шевченківський район", GeoBounds(50.486, 30.520, 50.435, 30.400)),
        OfflineArea("holosiivskyi", "Голосіївський район і ВДНГ", GeoBounds(50.420, 30.560, 50.360, 30.460)),
        OfflineArea("solomianskyi", "Солом'янський район", GeoBounds(50.452, 30.490, 50.400, 30.400)),
        OfflineArea("sviatoshynskyi", "Святошинський район", GeoBounds(50.482, 30.430, 50.430, 30.330)),
        OfflineArea("obolonskyi", "Оболонь", GeoBounds(50.545, 30.530, 50.488, 30.470)),
        OfflineArea("dniprovskyi", "Дніпровський район і Гідропарк", GeoBounds(50.482, 30.645, 50.430, 30.555)),
        OfflineArea("desnianskyi", "Деснянський район", GeoBounds(50.545, 30.665, 50.488, 30.580)),
        OfflineArea("darnytskyi", "Дарницький район", GeoBounds(50.452, 30.705, 50.378, 30.590))
    )

    const val CURRENT_VIEW_ID = "current_view"
}

/** Web-Mercator tile arithmetic for download size estimates. */
object TileMath {
    /** Average size of one 256 px raster tile, used for estimates only. */
    const val AVERAGE_TILE_BYTES = 18_000L

    fun tileX(longitude: Double, zoom: Int): Int {
        val n = 1 shl zoom
        return floor((longitude + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun tileY(latitude: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val lat = Math.toRadians(latitude.coerceIn(-85.05112878, 85.05112878))
        val y = (1.0 - ln(tan(lat) + 1.0 / cos(lat)) / PI) / 2.0 * n
        return floor(y).toInt().coerceIn(0, n - 1)
    }

    fun tileCount(bounds: GeoBounds, minZoom: Int, maxZoom: Int): Long {
        var total = 0L
        for (zoom in minZoom..maxZoom) {
            val x1 = tileX(bounds.west, zoom)
            val x2 = tileX(bounds.east, zoom)
            val y1 = tileY(bounds.north, zoom)
            val y2 = tileY(bounds.south, zoom)
            total += (max(x1, x2) - min(x1, x2) + 1).toLong() * (max(y1, y2) - min(y1, y2) + 1).toLong()
        }
        return total
    }

    fun estimatedBytes(bounds: GeoBounds, minZoom: Int, maxZoom: Int): Long =
        tileCount(bounds, minZoom, maxZoom) * AVERAGE_TILE_BYTES
}
