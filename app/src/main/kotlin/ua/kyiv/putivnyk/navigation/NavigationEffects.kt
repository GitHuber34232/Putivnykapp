package ua.kyiv.putivnyk.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.audio.AudioGuideEngine
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.repository.FeatureSettings
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import ua.kyiv.putivnyk.i18n.AppText
import ua.kyiv.putivnyk.notifications.AppNotifications
import ua.kyiv.putivnyk.tracking.RouteCompletionRecorder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reacts to navigation progress no matter who computed it (map or background service):
 * tells the story of each reached place and records a finished route once.
 */
@Singleton
class NavigationEffects @Inject constructor(
    private val store: NavigationStateStore,
    private val featureSettings: FeatureSettings,
    private val placeRepository: PlaceRepository,
    private val audioGuide: AudioGuideEngine,
    private val completionRecorder: RouteCompletionRecorder,
    private val appNotifications: AppNotifications,
    private val appText: AppText
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            var routeId = -1L
            var lastIndex = -1
            var completedHandled = false
            store.snapshot.collect { snapshot ->
                if (snapshot == null) {
                    routeId = -1L
                    lastIndex = -1
                    completedHandled = false
                    return@collect
                }
                // A new route, or the same route restarted from the beginning.
                if (snapshot.routeId != routeId || snapshot.currentIndex < lastIndex) {
                    routeId = snapshot.routeId
                    lastIndex = snapshot.currentIndex
                    completedHandled = snapshot.completed
                    return@collect
                }
                if (snapshot.currentIndex > lastIndex) {
                    for (index in (lastIndex + 1)..snapshot.currentIndex) onStopReached(snapshot, index)
                    lastIndex = snapshot.currentIndex
                }
                if (snapshot.completed && !completedHandled) {
                    completedHandled = true
                    val recorded = completionRecorder.record(snapshot.routeId)
                    if (recorded && !store.mapTracking.value) {
                        appNotifications.showRouteCompleted(
                            title = "🎉 ${appText.get("routes.completed", "Маршрут завершено!")}",
                            text = appText.dynamic(snapshot.routeName)
                        )
                    }
                }
            }
        }
    }

    private suspend fun onStopReached(snapshot: NavigationSnapshot, index: Int) {
        // Index 0 is the start, usually the user's own position.
        if (index == 0 || !featureSettings.values.value.audioNarration) return
        val point = snapshot.waypoints.getOrNull(index) ?: return
        val place = findPlace(point) ?: return
        audioGuide.narrate(place.id)
    }

    private suspend fun findPlace(point: RoutePoint): Place? =
        placeRepository.getAllPlacesSnapshot()
            .asSequence()
            .filter { it.category != PlaceCategory.TOILET }
            .map { it to NativeGeoEngine.distanceMeters(point.latitude, point.longitude, it.latitude, it.longitude) }
            .filter { (_, meters) -> meters <= PLACE_MATCH_METERS }
            .minByOrNull { (_, meters) -> meters }
            ?.first

    private companion object {
        const val PLACE_MATCH_METERS = 60.0
    }
}
