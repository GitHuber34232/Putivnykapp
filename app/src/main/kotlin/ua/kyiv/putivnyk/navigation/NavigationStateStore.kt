package ua.kyiv.putivnyk.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode
import javax.inject.Inject
import javax.inject.Singleton

/** State of the active navigation, shared by the map, the AR camera and the background service. */
data class NavigationSnapshot(
    val routeId: Long,
    val routeName: String,
    val transportMode: TransportMode,
    /** Start, intermediate stops and destination. */
    val waypoints: List<RoutePoint>,
    /** Detailed line of the whole route (walking directions), may be empty while loading. */
    val geometry: List<RoutePoint>,
    /** Line from the user's position to the destination. */
    val remainingGeometry: List<RoutePoint>,
    /** Last reached stop; the user is heading to currentIndex + 1. */
    val currentIndex: Int,
    val distanceToNextMeters: Double,
    val remainingMeters: Double,
    val remainingMinutes: Int,
    val totalMeters: Double,
    val progress: Float,
    val completed: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val nextWaypoint: RoutePoint? get() = waypoints.getOrNull(currentIndex + 1)
}

@Singleton
class NavigationStateStore @Inject constructor() {
    private val _snapshot = MutableStateFlow<NavigationSnapshot?>(null)
    val snapshot: StateFlow<NavigationSnapshot?> = _snapshot.asStateFlow()

    private val _mapTracking = MutableStateFlow(false)

    /** True while the map screen is resumed and computes progress itself. */
    val mapTracking: StateFlow<Boolean> = _mapTracking.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)

    /** True while the background navigation service tracks location (it then feeds the trackers). */
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    fun publish(snapshot: NavigationSnapshot?) {
        _snapshot.value = snapshot
    }

    fun setMapTracking(active: Boolean) {
        _mapTracking.value = active
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }
}
