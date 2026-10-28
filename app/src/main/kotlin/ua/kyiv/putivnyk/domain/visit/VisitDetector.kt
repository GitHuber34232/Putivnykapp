package ua.kyiv.putivnyk.domain.visit

import ua.kyiv.putivnyk.data.model.PlaceCategory
import kotlin.math.min

/**
 * Decides when the user has really been at a place: either they came very close to it, or
 * they stayed inside its radius long enough. A short walk past a big park does not count.
 */
class VisitDetector(
    private val dwellMillis: Long = DEFAULT_DWELL_MILLIS,
    private val instantRadiusMeters: Double = INSTANT_RADIUS_METERS,
    private val leaveGraceMillis: Long = LEAVE_GRACE_MILLIS
) {
    data class Candidate(
        val placeId: Long,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double
    )

    private val enteredAt = HashMap<Long, Long>()
    private val lastInsideAt = HashMap<Long, Long>()

    /** Returns the ids of the places that count as visited after this location fix. */
    fun update(
        candidates: List<Candidate>,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        now: Long,
        distance: (Double, Double, Double, Double) -> Double
    ): List<Long> {
        if (accuracyMeters > MAX_ACCURACY_METERS) return emptyList()
        val slack = min(accuracyMeters.toDouble(), 30.0) * 0.5
        val arrived = mutableListOf<Long>()
        candidates.forEach { candidate ->
            val meters = distance(latitude, longitude, candidate.latitude, candidate.longitude)
            if (meters <= candidate.radiusMeters + slack) {
                val start = enteredAt.getOrPut(candidate.placeId) { now }
                lastInsideAt[candidate.placeId] = now
                if (meters <= instantRadiusMeters || now - start >= dwellMillis) {
                    arrived += candidate.placeId
                }
            } else {
                val last = lastInsideAt[candidate.placeId]
                if (last != null && now - last > leaveGraceMillis) {
                    enteredAt.remove(candidate.placeId)
                    lastInsideAt.remove(candidate.placeId)
                }
            }
        }
        arrived.forEach { id ->
            enteredAt.remove(id)
            lastInsideAt.remove(id)
        }
        return arrived
    }

    fun reset() {
        enteredAt.clear()
        lastInsideAt.clear()
    }

    companion object {
        const val DEFAULT_DWELL_MILLIS = 40_000L
        const val INSTANT_RADIUS_METERS = 20.0
        const val LEAVE_GRACE_MILLIS = 120_000L
        const val MAX_ACCURACY_METERS = 60f

        /** How close counts as "at the place"; large areas get a larger radius. */
        fun radiusFor(category: PlaceCategory): Double = when (category) {
            PlaceCategory.PARK -> 110.0
            PlaceCategory.DISTRICT -> 150.0
            PlaceCategory.MONASTERY, PlaceCategory.STADIUM -> 90.0
            PlaceCategory.SQUARE, PlaceCategory.EMBANKMENT -> 70.0
            PlaceCategory.STREET -> 60.0
            else -> 45.0
        }
    }
}
