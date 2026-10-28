package ua.kyiv.putivnyk.navigation

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.kyiv.putivnyk.data.model.MapPreferenceKeys
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode
import ua.kyiv.putivnyk.data.remote.WalkingDirectionsService
import ua.kyiv.putivnyk.data.repository.FeatureSettings
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.di.AppCoroutineScope
import ua.kyiv.putivnyk.domain.usecase.routing.RouteNavigationMetrics
import ua.kyiv.putivnyk.i18n.AppText
import ua.kyiv.putivnyk.notifications.AppNotifications
import ua.kyiv.putivnyk.tracking.VisitTracker
import ua.kyiv.putivnyk.tracking.WalkTracker
import javax.inject.Inject

/**
 * Keeps navigation going with the screen off: tracks location, updates route progress while
 * the map is not open, feeds the walk/visit trackers and shows the Live Update notification.
 * Declared as location + mediaPlayback so stop narration can play from the background.
 */
@AndroidEntryPoint
class NavigationService : Service() {

    @Inject lateinit var store: NavigationStateStore
    @Inject lateinit var routeRepository: RouteRepository
    @Inject lateinit var userPreferenceRepository: UserPreferenceRepository
    @Inject lateinit var walkingDirectionsService: WalkingDirectionsService
    @Inject lateinit var visitTracker: VisitTracker
    @Inject lateinit var walkTracker: WalkTracker
    @Inject lateinit var notifications: NavigationNotifications
    @Inject lateinit var appNotifications: AppNotifications
    @Inject lateinit var featureSettings: FeatureSettings
    @Inject lateinit var appText: AppText
    @Inject lateinit var appScope: AppCoroutineScope

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var locationManager: LocationManager? = null
    private var receivingUpdates = false
    private var route: Route? = null
    private var waypoints: List<RoutePoint> = emptyList()
    private var geometry: List<RoutePoint> = emptyList()
    private var currentIndex = 0
    private var texts: NavigationNotifications.Texts? = null
    private var nextName: String? = null
    private var nextNameSource: String? = null
    private var lastNotificationAt = 0L
    private var lastNotificationKey = ""
    private var loadJob: Job? = null
    private var snapshotJob: Job? = null
    private var activeRouteJob: Job? = null
    private var narrationJob: Job? = null

    // All callbacks are overridden: on Android 10 they are abstract, not default methods.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            scope.launch { onLocation(location) }
        }

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { stopNavigation() }
            return START_NOT_STICKY
        }
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        store.setServiceRunning(true)
        observeSnapshots()
        val requestedRouteId = intent?.getLongExtra(EXTRA_ROUTE_ID, -1L) ?: -1L
        if (requestedRouteId > 0L && requestedRouteId != route?.id) {
            loadJob?.cancel()
            loadJob = scope.launch { loadRoute(requestedRouteId) }
        }
        startLocationUpdates()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        store.setServiceRunning(false)
        scope.cancel()
        appScope.launch { runCatching { walkTracker.flush() } }
        super.onDestroy()
    }

    private fun goForeground(): Boolean {
        val notification = notifications.build(store.snapshot.value, nextName, texts ?: FALLBACK_TEXTS)
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (featureSettings.values.value.audioNarration) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        }
        return runCatching {
            startForeground(AppNotifications.NAVIGATION_NOTIFICATION_ID, notification, types)
        }.onFailure { Log.w(TAG, "Cannot start navigation in the foreground", it) }.isSuccess
    }

    private suspend fun loadRoute(routeId: Long) {
        val loaded = routeRepository.getRouteById(routeId)
        if (loaded == null) {
            stopNavigation()
            return
        }
        route = loaded
        waypoints = buildList {
            add(loaded.startPoint)
            addAll(loaded.waypoints)
            add(loaded.endPoint)
        }
        currentIndex = userPreferenceRepository.getString("route.progress.$routeId", "0").toIntOrNull()
            ?.coerceIn(0, waypoints.lastIndex) ?: 0
        texts = loadTexts()
        val shared = store.snapshot.value?.takeIf { it.routeId == routeId && it.geometry.size >= 2 }
        geometry = shared?.geometry ?: withContext(Dispatchers.IO) {
            runCatching {
                walkingDirectionsService.fetchWalkingRouteDetails(waypoints, loaded.transportMode).geometry
            }.getOrDefault(emptyList())
        }
        refreshNotification(force = true)
    }

    private suspend fun loadTexts() = NavigationNotifications.Texts(
        navigating = appText.get("nav.notification_title", "Навігація"),
        arrived = appText.get("routes.completed", "Маршрут завершено!"),
        stop = appText.get("map.deactivate_route", "Зупинити"),
        left = appText.get("nav.notification_left", "залишилось"),
        calculating = appText.get("nav.notification_calculating", "Визначаємо місцезнаходження…")
    )

    private fun observeSnapshots() {
        if (narrationJob == null) {
            // Narration switched on in Settings (app in the foreground): add the mediaPlayback type.
            narrationJob = scope.launch {
                featureSettings.values.map { it.audioNarration }.distinctUntilChanged().drop(1).collect { goForeground() }
            }
        }
        if (activeRouteJob == null) {
            // Stopped or deleted in the Routes tab while the map was not open.
            activeRouteJob = scope.launch {
                userPreferenceRepository.observeAsMap()
                    .map { it[MapPreferenceKeys.ACTIVE_ROUTE_ID] }
                    .distinctUntilChanged()
                    .collect { activeId -> if (activeId == null) stopNavigation(clearRoute = false) }
            }
        }
        if (snapshotJob != null) return
        snapshotJob = scope.launch {
            store.snapshot.collect { snapshot ->
                if (snapshot == null) {
                    // The route was stopped in the app.
                    if (route != null && store.mapTracking.value) stopNavigation(clearRoute = false)
                    return@collect
                }
                if (snapshot.routeId != route?.id) {
                    loadJob?.cancel()
                    loadJob = launch { loadRoute(snapshot.routeId) }
                    return@collect
                }
                // While the map is open it computes progress; keep the same stop and line here.
                currentIndex = maxOf(currentIndex, snapshot.currentIndex)
                if (snapshot.geometry.size >= 2) geometry = snapshot.geometry
                refreshNotification(force = false)
                if (snapshot.completed) {
                    delay(COMPLETED_LINGER_MILLIS)
                    stopNavigation(clearRoute = false)
                }
            }
        }
    }

    private suspend fun onLocation(location: Location) {
        val time = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        val accuracy = if (location.hasAccuracy()) location.accuracy else 50f
        val activeRoute = route
        val driving = activeRoute?.transportMode == TransportMode.DRIVING
        walkTracker.onLocation(location.latitude, location.longitude, accuracy, time, driving)
        visitTracker.onLocation(location.latitude, location.longitude, accuracy, time)

        if (activeRoute == null || waypoints.size < 2 || store.mapTracking.value) return
        val progress = NavigationProgressCalculator.compute(
            waypoints = waypoints,
            geometry = geometry,
            currentIndex = currentIndex,
            latitude = location.latitude,
            longitude = location.longitude,
            transportMode = activeRoute.transportMode
        )
        if (progress.currentIndex != currentIndex) {
            currentIndex = progress.currentIndex
            userPreferenceRepository.upsert("route.progress.${activeRoute.id}", currentIndex.toString())
        }
        store.publish(
            NavigationSnapshot(
                routeId = activeRoute.id,
                routeName = activeRoute.name,
                transportMode = activeRoute.transportMode,
                waypoints = waypoints,
                geometry = geometry,
                remainingGeometry = progress.remainingGeometry,
                currentIndex = progress.currentIndex,
                distanceToNextMeters = progress.distanceToNextMeters,
                remainingMeters = progress.remainingMeters,
                remainingMinutes = RouteNavigationMetrics.estimateDurationMinutes(progress.remainingMeters, activeRoute.transportMode),
                totalMeters = progress.totalMeters,
                progress = progress.fraction,
                completed = progress.completed
            )
        )
    }

    private suspend fun refreshNotification(force: Boolean) {
        val snapshot = store.snapshot.value?.takeIf { it.routeId == route?.id }
        val rawNext = snapshot?.nextWaypoint?.name
        if (rawNext != nextNameSource) {
            nextNameSource = rawNext
            nextName = rawNext?.let { appText.dynamic(it) }
        }
        val key = buildString {
            append(snapshot?.currentIndex)
            append('|')
            append(snapshot?.completed)
            append('|')
            append(nextName)
        }
        val now = System.currentTimeMillis()
        if (!force && key == lastNotificationKey && now - lastNotificationAt < NOTIFICATION_INTERVAL_MILLIS) return
        lastNotificationKey = key
        lastNotificationAt = now
        val notification = notifications.build(snapshot, nextName, texts ?: FALLBACK_TEXTS)
        runCatching {
            getSystemService(android.app.NotificationManager::class.java)
                .notify(AppNotifications.NAVIGATION_NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (receivingUpdates) return
        val manager = getSystemService(LocationManager::class.java) ?: return
        locationManager = manager
        listOf(
            LocationManager.GPS_PROVIDER to GPS_INTERVAL_MILLIS,
            LocationManager.NETWORK_PROVIDER to NETWORK_INTERVAL_MILLIS
        ).forEach { (provider, interval) ->
            runCatching {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, interval, MIN_DISTANCE_METERS, listener, Looper.getMainLooper())
                    receivingUpdates = true
                }
            }.onFailure { Log.w(TAG, "No updates from $provider", it) }
        }
    }

    private fun stopLocationUpdates() {
        runCatching { locationManager?.removeUpdates(listener) }
        receivingUpdates = false
    }

    /** Stop pressed in the notification: ends the route like the map's stop button. */
    private suspend fun stopNavigation(clearRoute: Boolean = true) {
        if (clearRoute) {
            userPreferenceRepository.deleteByKey(MapPreferenceKeys.ACTIVE_ROUTE_ID)
            store.publish(null)
        }
        stopLocationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "ua.kyiv.putivnyk.navigation.START"
        const val ACTION_STOP = "ua.kyiv.putivnyk.navigation.STOP"
        const val EXTRA_ROUTE_ID = "routeId"

        private const val TAG = "NavigationService"
        private const val GPS_INTERVAL_MILLIS = 2_000L
        private const val NETWORK_INTERVAL_MILLIS = 5_000L
        private const val MIN_DISTANCE_METERS = 3f
        private const val NOTIFICATION_INTERVAL_MILLIS = 3_000L
        private const val COMPLETED_LINGER_MILLIS = 15_000L

        private val FALLBACK_TEXTS = NavigationNotifications.Texts(
            navigating = "Навігація",
            arrived = "Маршрут завершено!",
            stop = "Зупинити",
            left = "залишилось",
            calculating = "Визначаємо місцезнаходження…"
        )
    }
}
