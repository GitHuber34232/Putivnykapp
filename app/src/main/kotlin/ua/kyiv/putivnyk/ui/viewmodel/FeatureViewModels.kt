package ua.kyiv.putivnyk.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ua.kyiv.putivnyk.audio.AudioGuideEngine
import ua.kyiv.putivnyk.data.model.MapPreferenceKeys
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.repository.AchievementsRepository
import ua.kyiv.putivnyk.data.repository.FeatureSettings
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.StatsRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import ua.kyiv.putivnyk.domain.stats.AchievementDefinition
import ua.kyiv.putivnyk.domain.stats.AchievementEngine
import ua.kyiv.putivnyk.domain.stats.AchievementProgress
import ua.kyiv.putivnyk.domain.stats.StatsSnapshot
import ua.kyiv.putivnyk.domain.stats.StatsTime
import ua.kyiv.putivnyk.i18n.AppText
import ua.kyiv.putivnyk.map.offline.GeoBounds
import ua.kyiv.putivnyk.map.offline.OfflineArea
import ua.kyiv.putivnyk.map.offline.OfflineAreas
import ua.kyiv.putivnyk.map.offline.OfflineRegionsRepository
import ua.kyiv.putivnyk.navigation.NavigationController
import ua.kyiv.putivnyk.share.RouteShareCodec
import ua.kyiv.putivnyk.share.RouteShareManager
import ua.kyiv.putivnyk.tracking.GeofenceRegistrar
import ua.kyiv.putivnyk.tracking.VisitTracker
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    statsRepository: StatsRepository,
    private val achievementsRepository: AchievementsRepository
) : ViewModel() {

    val snapshot: StateFlow<StatsSnapshot?> = statsRepository.observeSnapshot()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val achievements: StateFlow<List<AchievementProgress>> = snapshot
        .map { it?.let(AchievementEngine::progress).orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { runCatching { achievementsRepository.evaluate() } }
    }
}

@HiltViewModel
class OfflineMapsViewModel @Inject constructor(
    private val repository: OfflineRegionsRepository,
    private val userPreferenceRepository: UserPreferenceRepository
) : ViewModel() {

    val regions: StateFlow<List<OfflineRegionsRepository.DownloadedRegion>> = repository.regions
    val failure: StateFlow<String?> = repository.failure
    val areas: List<OfflineArea> = OfflineAreas.kyiv
    val maxZoom: Int get() = repository.maxZoom

    private val _currentView = MutableStateFlow<GeoBounds?>(null)

    /** What the map showed last, offered as "download this area". */
    val currentView: StateFlow<GeoBounds?> = _currentView.asStateFlow()

    init {
        repository.refresh()
        viewModelScope.launch {
            val lat = userPreferenceRepository.getString("map.center.lat", "").toDoubleOrNull()
            val lon = userPreferenceRepository.getString("map.center.lon", "").toDoubleOrNull()
            val zoom = userPreferenceRepository.getString("map.zoom", "").toIntOrNull() ?: 15
            if (lat != null && lon != null) _currentView.value = GeoBounds.around(lat, lon, zoom.coerceAtLeast(13))
        }
    }

    fun estimateTiles(bounds: GeoBounds): Long = repository.estimateTiles(bounds)

    fun estimateBytes(bounds: GeoBounds): Long = repository.estimateBytes(bounds)

    fun download(area: OfflineArea, title: String) = repository.download(area.id, title, area.bounds)

    fun downloadCurrentView(title: String) {
        val bounds = _currentView.value ?: return
        repository.download("${OfflineAreas.CURRENT_VIEW_ID}_${System.currentTimeMillis()}", title, bounds)
    }

    fun pause(regionId: Long) = repository.pause(regionId)

    fun resume(regionId: Long) = repository.resume(regionId)

    fun delete(regionId: Long) = repository.delete(regionId)

    fun clearFailure() = repository.clearFailure()
}

/** Shared by the mini player, place details and routes (activity scope). */
@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    private val engine: AudioGuideEngine,
    private val placeRepository: PlaceRepository
) : ViewModel() {

    val state: StateFlow<AudioGuideEngine.State> = engine.state

    fun playPlace(placeId: Long) = engine.playPlaces(listOf(placeId))

    fun playPlaces(placeIds: List<Long>) = engine.playPlaces(placeIds)

    /** Plays the stories of the places along a route's stops (matched by position). */
    fun playRoute(points: List<RoutePoint>, onNothingToPlay: () -> Unit) {
        viewModelScope.launch {
            val places = placeRepository.getAllPlacesSnapshot()
            val ids = points.mapNotNull { point ->
                places
                    .map { it to NativeGeoEngine.distanceMeters(point.latitude, point.longitude, it.latitude, it.longitude) }
                    .filter { (_, meters) -> meters <= ROUTE_PLACE_MATCH_METERS }
                    .minByOrNull { (_, meters) -> meters }
                    ?.first
                    ?.id
            }.distinct()
            if (ids.isEmpty()) onNothingToPlay() else engine.playPlaces(ids)
        }
    }

    fun toggle() = engine.togglePlayPause()

    fun next() = engine.next()

    fun previous() = engine.previous()

    fun stop() = engine.stop()

    private companion object {
        const val ROUTE_PLACE_MATCH_METERS = 60.0
    }
}

@HiltViewModel
class RouteShareViewModel @Inject constructor(
    private val shareManager: RouteShareManager
) : ViewModel() {

    fun link(route: Route): String = shareManager.link(route)

    fun qrContent(route: Route): String? = shareManager.qrContent(route)

    /** System share sheet with the link and the QR code picture. */
    fun share(activityContext: Context, route: Route, message: String, chooserTitle: String) {
        viewModelScope.launch {
            runCatching { shareManager.share(activityContext, route, message, chooserTitle) }
                .onFailure { Log.w("RouteShare", "Sharing failed", it) }
        }
    }
}

@HiltViewModel
class FeaturesViewModel @Inject constructor(
    private val featureSettings: FeatureSettings,
    private val geofenceRegistrar: GeofenceRegistrar,
    private val navigationController: NavigationController,
    private val userPreferenceRepository: UserPreferenceRepository
) : ViewModel() {

    val values: StateFlow<FeatureSettings.Values> = featureSettings.values

    fun set(key: String, enabled: Boolean) {
        viewModelScope.launch {
            featureSettings.set(key, enabled)
            when (key) {
                FeatureSettings.KEY_BACKGROUND_GEOFENCES, FeatureSettings.KEY_AUTO_VISITED ->
                    runCatching { geofenceRegistrar.refresh() }
                // Takes effect on the route already running, not only on the next one.
                FeatureSettings.KEY_BACKGROUND_NAVIGATION -> {
                    val activeRouteId = userPreferenceRepository.getString(MapPreferenceKeys.ACTIVE_ROUTE_ID, "").toLongOrNull()
                    if (!enabled) {
                        navigationController.stop()
                    } else if (activeRouteId != null) {
                        // The controller reads the setting from the flow, which updates right after the write.
                        withTimeoutOrNull(2_000) { featureSettings.values.first { it.backgroundNavigation } }
                        navigationController.start(activeRouteId)
                    }
                }
            }
        }
    }

    fun hasBackgroundLocation(): Boolean = geofenceRegistrar.hasBackgroundPermission()
}

/** App-wide events and actions: achievements, auto visits, shared and daily routes (activity scope). */
@HiltViewModel
class AppEventsViewModel @Inject constructor(
    private val shareManager: RouteShareManager,
    private val placeRepository: PlaceRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val appText: AppText,
    achievementsRepository: AchievementsRepository,
    visitTracker: VisitTracker
) : ViewModel() {

    val achievementUnlocked: SharedFlow<AchievementDefinition> = achievementsRepository.unlocked
    val placeVisited = visitTracker.events

    private val _pendingImport = MutableStateFlow<RouteShareCodec.SharedRoute?>(null)
    val pendingImport: StateFlow<RouteShareCodec.SharedRoute?> = _pendingImport.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Accepts a link, QR payload or pasted text; false when it holds no valid route. */
    fun offerSharedRoute(text: String): Boolean {
        val route = RouteShareCodec.extractCode(text)?.let(RouteShareCodec::decode)
        if (route == null) {
            _messages.tryEmit(MESSAGE_INVALID_ROUTE)
            return false
        }
        _pendingImport.value = route
        return true
    }

    fun dismissImport() {
        _pendingImport.value = null
    }

    fun confirmImport(startNow: Boolean, onStart: () -> Unit) {
        val route = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            val id = shareManager.import(route)
            if (startNow) {
                userPreferenceRepository.upsert(MapPreferenceKeys.PENDING_ACTIVE_ROUTE_ID, id.toString())
                onStart()
            } else {
                _messages.emit(MESSAGE_IMPORTED)
            }
        }
    }

    /** Saves the route of the day (or reuses it) and starts it on the map. */
    fun startDailyRoute(placeIds: List<Long>, onReady: () -> Unit) {
        viewModelScope.launch {
            val places = placeIds.mapNotNull { placeRepository.getPlaceById(it) }
            if (places.size < 2) return@launch
            val date = StatsTime.today().format(DateTimeFormatter.ofPattern("dd.MM"))
            val name = "${appText.get("daily.title", "Маршрут дня")} · $date"
            val id = shareManager.saveRouteFromPlaces(name, places, description = places.joinToString(" → ") { it.name })
            userPreferenceRepository.upsert(MapPreferenceKeys.PENDING_ACTIVE_ROUTE_ID, id.toString())
            onReady()
        }
    }

    companion object {
        const val MESSAGE_INVALID_ROUTE = "route_invalid"
        const val MESSAGE_IMPORTED = "route_imported"
    }
}
