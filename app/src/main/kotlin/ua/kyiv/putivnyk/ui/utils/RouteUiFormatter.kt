package ua.kyiv.putivnyk.ui.utils

import kotlin.math.roundToInt

object RouteUiFormatter {
    fun formatDistance(distanceMeters: Double): String {
        if (distanceMeters >= 1000.0) {
            return String.format("%.1f км", distanceMeters / 1000.0)
        }

        val roundedMeters = (distanceMeters / 10.0).roundToInt() * 10
        return "$roundedMeters м"
    }

    fun formatDuration(minutes: Int): String {
        if (minutes <= 0) return "0 хв"
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return when {
            hours <= 0 -> "$remainingMinutes хв"
            remainingMinutes == 0 -> "$hours год"
            else -> "$hours год $remainingMinutes хв"
        }
    }
}