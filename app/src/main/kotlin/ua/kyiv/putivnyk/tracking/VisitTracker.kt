package ua.kyiv.putivnyk.tracking

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.repository.AchievementsRepository
import ua.kyiv.putivnyk.data.repository.FeatureSettings
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.StatsRepository
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import ua.kyiv.putivnyk.domain.visit.VisitDetector
import ua.kyiv.putivnyk.i18n.AppText
import ua.kyiv.putivnyk.notifications.AppNotifications
import javax.inject.Inject
import javax.inject.Singleton

/** Marks places as visited automatically when the user comes to them. */
@Singleton
class VisitTracker @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val statsRepository: StatsRepository,
    private val achievementsRepository: AchievementsRepository,
    private val featureSettings: FeatureSettings,
    private val notifications: AppNotifications,
    private val appText: AppText
) {
    private val detector = VisitDetector()
    private val mutex = Mutex()
    private var candidates: List<Place> = emptyList()
    private var candidatesAt = 0L
    private var candidatesLat = Double.NaN
    private var candidatesLon = Double.NaN
    private var lastFixTime = 0L

    private val _events = MutableSharedFlow<Place>(extraBufferCapacity = 4)

    /** Places visited just now; shown as a snackbar while the map is open, otherwise notified. */
    val events: SharedFlow<Place> = _events.asSharedFlow()

    suspend fun onLocation(latitude: Double, longitude: Double, accuracyMeters: Float, timeMillis: Long) {
        if (!featureSettings.values.value.autoVisited) return
        val arrived = mutex.withLock {
            if (timeMillis <= lastFixTime) return
            lastFixTime = timeMillis
            refreshCandidatesIfNeeded(latitude, longitude, timeMillis)
            val nearby = candidates.map { place ->
                VisitDetector.Candidate(
                    placeId = place.id,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    radiusMeters = VisitDetector.radiusFor(place.category)
                )
            }
            detector.update(nearby, latitude, longitude, accuracyMeters, timeMillis, NativeGeoEngine::distanceMeters)
        }
        arrived.forEach { placeId -> markVisited(placeId, SOURCE_AUTO) }
    }

    /** Marks [placeId] visited once; returns the place, or null if it already was visited. */
    suspend fun markVisited(placeId: Long, source: String): Place? {
        val place = placeRepository.getPlaceById(placeId) ?: return null
        if (place.isVisited) return null
        val now = System.currentTimeMillis()
        val visited = place.copy(isVisited = true, updatedAt = now)
        placeRepository.updatePlace(visited)
        statsRepository.recordVisit(placeId, source, now)
        mutex.withLock { candidates = candidates.filterNot { it.id == placeId } }
        runCatching { achievementsRepository.evaluate() }
        if (_events.subscriptionCount.value > 0) {
            _events.emit(visited)
        } else {
            notifications.showVisit(
                placeId = placeId,
                title = appText.get("visit.auto_title", "Ви відвідали місце"),
                text = appText.dynamic(visited.name),
                listenLabel = appText.get("audio.listen", "Слухати")
            )
        }
        return visited
    }

    private suspend fun refreshCandidatesIfNeeded(latitude: Double, longitude: Double, now: Long) {
        val moved = candidatesLat.isNaN() ||
            NativeGeoEngine.distanceMeters(candidatesLat, candidatesLon, latitude, longitude) > REFRESH_DISTANCE_METERS
        if (!moved && now - candidatesAt < REFRESH_INTERVAL_MILLIS) return
        candidates = placeRepository.getAllPlacesSnapshot().filter { place ->
            !place.isVisited &&
                place.category != PlaceCategory.TOILET &&
                NativeGeoEngine.distanceMeters(latitude, longitude, place.latitude, place.longitude) <= CANDIDATE_RADIUS_METERS
        }
        candidatesAt = now
        candidatesLat = latitude
        candidatesLon = longitude
    }

    companion object {
        const val SOURCE_AUTO = "auto"
        const val SOURCE_GEOFENCE = "geofence"
        const val SOURCE_MANUAL = "manual"
        private const val CANDIDATE_RADIUS_METERS = 2_000.0
        private const val REFRESH_DISTANCE_METERS = 500.0
        private const val REFRESH_INTERVAL_MILLIS = 180_000L
    }
}
