package ua.kyiv.putivnyk.data.remote

import kotlin.math.ceil
import ua.kyiv.putivnyk.data.model.RoutePoint

data class WalkingRouteResult(
    val geometry: List<RoutePoint>,
    val distanceMeters: Double? = null,
    val durationSeconds: Double? = null,
    val usedFallback: Boolean = false
) {
    val durationMinutesCeil: Int?
        get() = durationSeconds?.let { ceil(it / 60.0).toInt() }
}