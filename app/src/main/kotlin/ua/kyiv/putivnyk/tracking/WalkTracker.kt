package ua.kyiv.putivnyk.tracking

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.kyiv.putivnyk.data.repository.AchievementsRepository
import ua.kyiv.putivnyk.data.repository.StatsRepository
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Adds up walked distance from location fixes. GPS jitter, inaccurate fixes and movement
 * faster than walking (bus, metro, car) are ignored; totals are written in batches.
 */
@Singleton
class WalkTracker @Inject constructor(
    private val statsRepository: StatsRepository,
    private val achievementsRepository: AchievementsRepository
) {
    private val mutex = Mutex()
    private var anchorLat = Double.NaN
    private var anchorLon = Double.NaN
    private var anchorTime = 0L
    private var lastFixTime = 0L
    private var pendingMeters = 0.0
    private var pendingMillis = 0L
    private var pendingStartedAt = 0L
    private var lastFlushAt = 0L

    /** [timeMillis] must be the fix time: the same fix arriving twice (map + service) is ignored. */
    suspend fun onLocation(latitude: Double, longitude: Double, accuracyMeters: Float, timeMillis: Long, driving: Boolean) {
        val shouldFlush = mutex.withLock {
            if (timeMillis <= lastFixTime) return
            lastFixTime = timeMillis
            if (accuracyMeters > MAX_ACCURACY_METERS) return
            if (anchorTime == 0L || timeMillis - anchorTime > MAX_GAP_MILLIS) {
                resetAnchor(latitude, longitude, timeMillis)
                return
            }
            val meters = NativeGeoEngine.distanceMeters(anchorLat, anchorLon, latitude, longitude)
            // Below the noise floor: keep the anchor so slow walking still adds up.
            if (meters < max(MIN_STEP_METERS, accuracyMeters * 0.5)) return
            val seconds = (timeMillis - anchorTime) / 1000.0
            val speed = meters / seconds.coerceAtLeast(0.5)
            if (driving || speed > MAX_WALKING_SPEED_MS) {
                resetAnchor(latitude, longitude, timeMillis)
                return
            }
            if (pendingStartedAt == 0L) pendingStartedAt = anchorTime
            pendingMeters += meters
            pendingMillis += timeMillis - anchorTime
            resetAnchor(latitude, longitude, timeMillis)
            pendingMeters >= FLUSH_METERS || timeMillis - lastFlushAt >= FLUSH_INTERVAL_MILLIS
        }
        if (shouldFlush) flush()
    }

    suspend fun flush() {
        val batch = mutex.withLock {
            if (pendingMeters <= 0.0) return
            val result = Triple(pendingMeters, pendingMillis, pendingStartedAt)
            pendingMeters = 0.0
            pendingMillis = 0L
            pendingStartedAt = 0L
            lastFlushAt = lastFixTime
            result
        }
        statsRepository.addWalk(
            meters = batch.first,
            millis = batch.second,
            startedAt = batch.third.takeIf { it > 0L } ?: System.currentTimeMillis(),
            endedAt = lastFixTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        runCatching { achievementsRepository.evaluate() }
    }

    private fun resetAnchor(latitude: Double, longitude: Double, timeMillis: Long) {
        anchorLat = latitude
        anchorLon = longitude
        anchorTime = timeMillis
    }

    companion object {
        private const val MAX_ACCURACY_METERS = 35f
        private const val MIN_STEP_METERS = 6.0
        private const val MAX_WALKING_SPEED_MS = 3.2
        private const val MAX_GAP_MILLIS = 120_000L
        private const val FLUSH_METERS = 150.0
        private const val FLUSH_INTERVAL_MILLIS = 120_000L
    }
}
