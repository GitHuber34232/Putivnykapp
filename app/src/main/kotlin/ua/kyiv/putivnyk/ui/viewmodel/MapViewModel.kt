package ua.kyiv.putivnyk.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.data.model.MapBookmark
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.PlaceSortMode
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode
import ua.kyiv.putivnyk.data.repository.MapBookmarkRepository
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.data.remote.WalkingDirectionsService
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import ua.kyiv.putivnyk.domain.usecase.routing.RouteMetricsCalculator
import ua.kyiv.putivnyk.domain.usecase.routing.RouteNavigationMetrics
import ua.kyiv.putivnyk.domain.usecase.routing.RouteOptimizer
import ua.kyiv.putivnyk.domain.usecase.routing.SmartRouteBuilder
import kotlin.math.pow
import kotlin.math.max
import kotlin.math.abs
import javax.inject.Inject

@HiltViewModel
@OptIn(FlowPreview::class)
class MapViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val routeRepository: RouteRepository,
    private val mapBookmarkRepository: MapBookmarkRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val smartRouteBuilder: SmartRouteBuilder,
    private val routeOptimizer: RouteOptimizer,
    private val walkingDirectionsService: WalkingDirectionsService
) : ViewModel() {

    companion object {
        private val MAIDAN_NEZALEZHNOSTI = MapCenter(50.4501, 30.5234)
        private const val DEFAULT_ZOOM = 14
        private const val POI_PROMPT_RADIUS_METERS = 120.0
        private const val WAYPOINT_REACHED_RADIUS_METERS = 80.0
        private const val OFF_ROUTE_THRESHOLD_METERS = 40.0

        private const val OFF_ROUTE_CONSECUTIVE_THRESHOLD = 2

        private const val REROUTE_COOLDOWN_MS = 5_000L

        private const val MIN_SATELLITES_FOR_FIX = 4

        private const val LOW_SAT_CONSECUTIVE_THRESHOLD = 3

        private const val TRANSPORT_SPEED_THRESHOLD_MS = 3.0
        private const val TRANSPORT_REROUTE_COOLDOWN_MS = 3_000L
        private const val TRANSPORT_WAYPOINT_REACH_RADIUS_METERS = 150.0
        private const val SPEED_HISTORY_SIZE = 5
    }

    private val _selectedPlace = MutableStateFlow<Place?>(null)
    val selectedPlace: StateFlow<Place?> = _selectedPlace

    private val _showOnlyFavorites = MutableStateFlow(false)
    val showOnlyFavorites: StateFlow<Boolean> = _showOnlyFavorites

    private val _mapCenter = MutableStateFlow(MAIDAN_NEZALEZHNOSTI)
    val mapCenter: StateFlow<MapCenter> = _mapCenter

    private val _zoomLevel = MutableStateFlow(DEFAULT_ZOOM)
    val zoomLevel: StateFlow<Int> = _zoomLevel

    private val _sortMode = MutableStateFlow(PlaceSortMode.POPULARITY)
    val sortMode: StateFlow<PlaceSortMode> = _sortMode

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCategory = MutableStateFlow<PlaceCategory?>(null)
    val selectedCategory: StateFlow<PlaceCategory?> = _selectedCategory

    private val debouncedSearchQuery = _searchQuery
        .debounce(120)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    private val _userLocation = MutableStateFlow(MapCenter(50.4501, 30.5234))
    val userLocation: StateFlow<MapCenter> = _userLocation

    private val _hasUserLocation = MutableStateFlow(false)
    val hasUserLocation: StateFlow<Boolean> = _hasUserLocation

    private val _userBearing = MutableStateFlow(-1f)
    val userBearing: StateFlow<Float> = _userBearing

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded

    private val _isCreatingRoute = MutableStateFlow(false)
    val isCreatingRoute: StateFlow<Boolean> = _isCreatingRoute

    private val _visibleBoundsRaw = MutableStateFlow(
        computeViewportBounds(center = _mapCenter.value, zoom = _zoomLevel.value)
    )

    private val visibleBoundsDebounced = _visibleBoundsRaw
        .debounce(800)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _visibleBoundsRaw.value
        )

    val visibleBounds: StateFlow<MapViewportBounds> = visibleBoundsDebounced

    private val _activeRoute = MutableStateFlow<Route?>(null)
    val activeRoute: StateFlow<Route?> = _activeRoute

    private val _pendingRouteAddPlace = MutableStateFlow<Place?>(null)
    val pendingRouteAddPlace: StateFlow<Place?> = _pendingRouteAddPlace

    private val _currentWaypointIndex = MutableStateFlow(0)
    val currentWaypointIndex: StateFlow<Int> = _currentWaypointIndex

    private val _remainingDistance = MutableStateFlow(0.0)
    val remainingDistance: StateFlow<Double> = _remainingDistance

    private val _remainingMinutes = MutableStateFlow(0)
    val remainingMinutes: StateFlow<Int> = _remainingMinutes

    private val _routeProgressFraction = MutableStateFlow(0f)
    val routeProgressFraction: StateFlow<Float> = _routeProgressFraction

    private val _distanceToNextWaypoint = MutableStateFlow(0.0)
    val distanceToNextWaypoint: StateFlow<Double> = _distanceToNextWaypoint

    private val _nextWaypointName = MutableStateFlow<String?>(null)
    val nextWaypointName: StateFlow<String?> = _nextWaypointName

    private val _poiPromptPlace = MutableStateFlow<Place?>(null)
    val poiPromptPlace: StateFlow<Place?> = _poiPromptPlace

    private val _promptedPoiIds = mutableSetOf<Long>()

    private var progressUpdateJob: Job? = null

    val bookmarks: StateFlow<List<MapBookmark>> = mapBookmarkRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var persistMapStateJob: Job? = null

    private val filterState = combine(
        _showOnlyFavorites,
        debouncedSearchQuery,
        _selectedCategory
    ) { onlyFavorites, query, category ->
        MapFilterState(
            onlyFavorites = onlyFavorites,
            query = query,
            category = category
        )
    }

    val placesOnMap: StateFlow<List<Place>> = combine(
        placeRepository.getAllPlaces(),
        visibleBoundsDebounced,
        _sortMode,
        _userLocation,
        filterState
    ) { allPlaces, viewport, sortMode, userLocation, filterState ->
        var filtered = allPlaces
            .distinctBy { it.id }
            .filter { place ->
                viewport.contains(place.latitude, place.longitude)
            }

        val normalizedQuery = filterState.query.trim()
        val isToiletExplicitlyRequested = normalizedQuery.contains("туалет", ignoreCase = true) ||
            normalizedQuery.contains("toilet", ignoreCase = true) ||
            normalizedQuery.contains("wc", ignoreCase = true)

        if (!isToiletExplicitlyRequested) {
            filtered = filtered.filter { it.category != PlaceCategory.TOILET }
        }

        if (filterState.category != null) {
            filtered = filtered.filter { it.category == filterState.category }
        }

        if (normalizedQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.name.contains(normalizedQuery, ignoreCase = true) ||
                    it.category.displayName.contains(normalizedQuery, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(normalizedQuery, ignoreCase = true) }
            }
        }

        if (filterState.onlyFavorites) {
            filtered = filtered.filter { it.isFavorite }
        }

        when (sortMode) {
            PlaceSortMode.POPULARITY -> filtered.sortedByDescending { it.popularity }
            PlaceSortMode.RATING -> filtered.sortedByDescending { it.rating ?: 0.0 }
            PlaceSortMode.DISTANCE -> filtered.sortedBy { place ->
                NativeGeoEngine.distanceMeters(
                    lat1 = userLocation.latitude,
                    lon1 = userLocation.longitude,
                    lat2 = place.latitude,
                    lon2 = place.longitude
                )
            }
            PlaceSortMode.RECOMMENDED -> filtered.sortedByDescending { place ->
                var score = place.popularity + (place.rating ?: 0.0) * 10
                if (place.isFavorite) score += 100
                if (place.isVisited) score -= 40
                score
            }
        }.also { _isLoaded.value = true }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeRouteLabel: StateFlow<String?> = _activeRoute
        .map { route ->
            route?.let { "Активний маршрут: ${it.name} (${it.waypoints.size + 2} точок)" }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val routeWaypoints: StateFlow<List<RoutePoint>> = _activeRoute
        .map { route ->
            route?.let {
                buildList {
                    add(it.startPoint)
                    addAll(it.waypoints)
                    add(it.endPoint)
                }
            } ?: emptyList()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _walkingRouteGeometry = MutableStateFlow<List<RoutePoint>>(emptyList())
    private val _fullWalkingRouteGeometry = MutableStateFlow<List<RoutePoint>>(emptyList())
    private val _totalRouteDistanceMeters = MutableStateFlow(0.0)
    private var _totalRouteDurationSeconds: Double? = null

    val routeLinePoints: StateFlow<List<RoutePoint>> = _walkingRouteGeometry

    private var walkingDirectionsJob: Job? = null
    private var rerouteJob: Job? = null
    private var lastRerouteTimestamp: Long = 0L
    private var consecutiveOffRouteCount: Int = 0
    private var lastGpsAccuracy: Float = Float.MAX_VALUE

    private var _gpsSatellitesUsed: Int = 0
    private var _gpsSatellitesTotal: Int = 0
    private val _isUnderground = MutableStateFlow(false)
    val isUnderground: StateFlow<Boolean> = _isUnderground

    private var lowSatelliteCount: Int = 0

    private val _isRerouting = MutableStateFlow(false)
    val isRerouting: StateFlow<Boolean> = _isRerouting

    private var lastLocationTimestamp: Long = 0L
    private var lastLocationLat: Double = Double.NaN
    private var lastLocationLon: Double = Double.NaN
    private val speedHistory = ArrayDeque<Double>(SPEED_HISTORY_SIZE + 1)
    private var routeProgressJob: Job? = null

    private fun estimateCurrentSpeedMs(): Double {
        if (speedHistory.isEmpty()) return 0.0
        return speedHistory.average()
    }

    private fun isLikelyOnTransport(): Boolean = estimateCurrentSpeedMs() > TRANSPORT_SPEED_THRESHOLD_MS

    init {
        restoreMapState()

        viewModelScope.launch {
            val savedRouteId = userPreferenceRepository.getString("map.activeRouteId", "").toLongOrNull()
            if (savedRouteId != null) {
                val route = routeRepository.getRouteById(savedRouteId)
                _activeRoute.value = route
                if (route != null) {
                    _totalRouteDistanceMeters.value = route.distance
                    restoreRouteProgress(savedRouteId)
                    fetchWalkingDirectionsForRoute(route)
                }
            }
        }

        viewModelScope.launch {
            routeRepository.getAllRoutes().collect { routes ->
                val current = _activeRoute.value ?: return@collect
                val updated = routes.find { it.id == current.id }
                if (updated != null && updated != current) {
                    _activeRoute.value = updated
                    _totalRouteDistanceMeters.value = updated.distance

                    val oldWp = buildList {
                        add(current.startPoint); addAll(current.waypoints); add(current.endPoint)
                    }
                    val newWp = buildList {
                        add(updated.startPoint); addAll(updated.waypoints); add(updated.endPoint)
                    }
                    if (oldWp != newWp) {
                        fetchWalkingDirectionsForRoute(updated)
                    }
                } else if (updated == null) {
                    _activeRoute.value = null
                    _fullWalkingRouteGeometry.value = emptyList()
                    _walkingRouteGeometry.value = emptyList()
                    _totalRouteDistanceMeters.value = 0.0
                    _routeProgressFraction.value = 0f
                }
            }
        }

        viewModelScope.launch {
            applyPendingFocusFromPreferences()
        }
    }

    fun syncPendingFocus() {
        viewModelScope.launch {
            applyPendingActiveRoute()
            applyPendingRouteToPlace()
            applyPendingFocusFromPreferences()
        }
    }

    fun createRouteToPlace(destination: Place, transportMode: TransportMode = TransportMode.WALKING) {
        if (!_hasUserLocation.value) return
        val user = _userLocation.value
        _isCreatingRoute.value = true
        viewModelScope.launch {
            try {
                val start = RoutePoint(user.latitude, user.longitude, "Моя позиція")
                val end = RoutePoint(destination.latitude, destination.longitude, destination.name)
                val straightLine = NativeGeoEngine.distanceMeters(
                    start.latitude, start.longitude, end.latitude, end.longitude
                )
                val walkingDistance = straightLine * 1.35
                val estimatedMin = RouteNavigationMetrics.estimateDurationMinutes(walkingDistance, transportMode)

                val route = Route(
                    name = "→ ${destination.name}",
                    startPoint = start,
                    endPoint = end,
                    waypoints = emptyList(),
                    distance = walkingDistance,
                    estimatedDuration = estimatedMin,
                    transportMode = transportMode
                )

                val savedId = routeRepository.saveRoute(route)
                val saved = route.copy(id = savedId)
                _activeRoute.value = saved
                userPreferenceRepository.upsert("map.activeRouteId", savedId.toString())
                fetchWalkingDirectionsForRoute(saved)
            } finally {
                _isCreatingRoute.value = false
            }
        }
    }

    fun activateRoute(routeId: Long) {
        viewModelScope.launch {
            val route = routeRepository.getRouteById(routeId) ?: return@launch
            _activeRoute.value = route
            userPreferenceRepository.upsert("map.activeRouteId", routeId.toString())
            restoreRouteProgress(routeId)
            fetchWalkingDirectionsForRoute(route)
        }
    }

    fun deactivateRoute() {
        val routeId = _activeRoute.value?.id
        _activeRoute.value = null
        walkingDirectionsJob?.cancel()
        rerouteJob?.cancel()
        routeProgressJob?.cancel()
        _isRerouting.value = false
        _fullWalkingRouteGeometry.value = emptyList()
        _walkingRouteGeometry.value = emptyList()
        _totalRouteDistanceMeters.value = 0.0
        _totalRouteDurationSeconds = null
        speedHistory.clear()
        viewModelScope.launch {
            userPreferenceRepository.deleteByKey("map.activeRouteId")
        }
        if (routeId != null) {

            _currentWaypointIndex.value = 0
            _remainingDistance.value = 0.0
            _remainingMinutes.value = 0
            _routeProgressFraction.value = 0f
            _distanceToNextWaypoint.value = 0.0
            _nextWaypointName.value = null
            _poiPromptPlace.value = null
            _promptedPoiIds.clear()
        }
    }

    fun deactivateAndResetProgress() {
        val routeId = _activeRoute.value?.id
        _activeRoute.value = null
        walkingDirectionsJob?.cancel()
        rerouteJob?.cancel()
        routeProgressJob?.cancel()
        _isRerouting.value = false
        _fullWalkingRouteGeometry.value = emptyList()
        _walkingRouteGeometry.value = emptyList()
        _totalRouteDistanceMeters.value = 0.0
        _totalRouteDurationSeconds = null
        speedHistory.clear()
        viewModelScope.launch {
            userPreferenceRepository.deleteByKey("map.activeRouteId")
        }
        if (routeId != null) {
            clearRouteProgress(routeId)
        }
    }

    private suspend fun applyPendingActiveRoute() {
        val pendingId = userPreferenceRepository.getString("map.pending.activeRouteId", "").toLongOrNull()
        if (pendingId != null) {
            userPreferenceRepository.deleteByKey("map.pending.activeRouteId")

            if (_activeRoute.value?.id == pendingId) return
            val route = routeRepository.getRouteById(pendingId)
            if (route != null) {
                _activeRoute.value = route
                userPreferenceRepository.upsert("map.activeRouteId", pendingId.toString())
                restoreRouteProgress(pendingId)
                fetchWalkingDirectionsForRoute(route)
            }
        }
    }

    private suspend fun applyPendingRouteToPlace() {
        val pendingPlaceId = userPreferenceRepository.getString("map.pending.routeToPlaceId", "").toLongOrNull()
        if (pendingPlaceId != null) {
            userPreferenceRepository.deleteByKey("map.pending.routeToPlaceId")
            val place = placeRepository.getPlaceById(pendingPlaceId)
            if (place != null) {
                selectPlace(place)
                createRouteToPlace(place)
            }
        }
    }

    fun requestRouteToPlace(placeId: Long) {
        viewModelScope.launch {
            userPreferenceRepository.upsert("map.pending.routeToPlaceId", placeId.toString())
        }
    }

    fun selectPlace(place: Place?) {
        _selectedPlace.value = place

        place?.let {
            _mapCenter.value = MapCenter(it.latitude, it.longitude)
            _visibleBoundsRaw.value = computeViewportBounds(center = _mapCenter.value, zoom = _zoomLevel.value)
        }
    }

    fun toggleShowOnlyFavorites() {
        _showOnlyFavorites.value = !_showOnlyFavorites.value
    }

    fun updateMapCenter(latitude: Double, longitude: Double) {
        _mapCenter.value = clampCenterToKyiv(MapCenter(latitude, longitude))
        _visibleBoundsRaw.value = computeViewportBounds(center = _mapCenter.value, zoom = _zoomLevel.value)
        schedulePersistMapState()
    }

    fun updateZoomLevel(zoom: Int) {
        _zoomLevel.value = zoom.coerceIn(10, 19)
        _visibleBoundsRaw.value = computeViewportBounds(center = _mapCenter.value, zoom = _zoomLevel.value)
        schedulePersistMapState()
    }

    fun setSortMode(mode: PlaceSortMode) {
        _sortMode.value = mode
        viewModelScope.launch {
            userPreferenceRepository.upsert("map.sort.mode", mode.name)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: PlaceCategory?) {
        _selectedCategory.value = category
    }

    fun updateVisibleBounds(bounds: MapViewportBounds) {
        _visibleBoundsRaw.value = bounds
    }

    fun updateUserLocation(latitude: Double, longitude: Double, bearing: Float = -1f, accuracy: Float = Float.MAX_VALUE) {
        _userLocation.value = MapCenter(latitude, longitude)
        _hasUserLocation.value = true
        if (bearing >= 0f) _userBearing.value = bearing
        lastGpsAccuracy = accuracy

        val now = System.currentTimeMillis()
        if (!lastLocationLat.isNaN() && lastLocationTimestamp > 0L) {
            val dt = (now - lastLocationTimestamp) / 1000.0
            if (dt > 0.1) {
                val dist = NativeGeoEngine.distanceMeters(lastLocationLat, lastLocationLon, latitude, longitude)
                val speed = dist / dt
                if (speed < 50.0) {
                    speedHistory.addLast(speed)
                    while (speedHistory.size > SPEED_HISTORY_SIZE) speedHistory.removeFirst()
                }
            }
        }
        lastLocationLat = latitude
        lastLocationLon = longitude
        lastLocationTimestamp = now

        if (_activeRoute.value != null) {
            _mapCenter.value = clampCenterToKyiv(MapCenter(latitude, longitude))
            if (_zoomLevel.value < 16) _zoomLevel.value = 16
        }

        scheduleRouteProgressUpdate(latitude, longitude)
    }

    fun updateUserBearing(bearing: Float) {
        if (bearing >= 0f) _userBearing.value = bearing
    }

    fun updateGpsSatellites(usedCount: Int, totalCount: Int) {
        _gpsSatellitesUsed = usedCount
        _gpsSatellitesTotal = totalCount

        if (usedCount < MIN_SATELLITES_FOR_FIX) {
            lowSatelliteCount++
            if (lowSatelliteCount >= LOW_SAT_CONSECUTIVE_THRESHOLD && !_isUnderground.value) {
                _isUnderground.value = true
                Log.d("GPS", "Underground detected: $usedCount/$totalCount satellites")
            }
        } else {
            if (_isUnderground.value) {
                Log.d("GPS", "Back on surface: $usedCount/$totalCount satellites")
            }
            lowSatelliteCount = 0
            _isUnderground.value = false
        }
    }

    fun toggleFavorite(placeId: Long) {
        viewModelScope.launch {
            placeRepository.toggleFavorite(placeId)
        }
    }

    fun centerOnKyiv() {
        _mapCenter.value = MAIDAN_NEZALEZHNOSTI
        _zoomLevel.value = DEFAULT_ZOOM
        _visibleBoundsRaw.value = computeViewportBounds(center = _mapCenter.value, zoom = _zoomLevel.value)
        schedulePersistMapState()
    }

    fun centerOnUserLocation() {
        if (!_hasUserLocation.value) {
            centerOnKyiv()
            return
        }
        val user = _userLocation.value
        _mapCenter.value = clampCenterToKyiv(user)
        _zoomLevel.value = 16
        _visibleBoundsRaw.value = computeViewportBounds(center = _mapCenter.value, zoom = _zoomLevel.value)
        schedulePersistMapState()
    }

    fun saveCurrentViewAsBookmark() {
        viewModelScope.launch {
            val center = _mapCenter.value
            val zoom = _zoomLevel.value
            mapBookmarkRepository.save(
                MapBookmark(
                    title = "Київ ${"%.4f".format(center.latitude)}, ${"%.4f".format(center.longitude)}",
                    note = "Автозбереження позиції карти",
                    latitude = center.latitude,
                    longitude = center.longitude,
                    zoomLevel = zoom
                )
            )
        }
    }

    fun applyBookmark(bookmark: MapBookmark) {
        _mapCenter.value = clampCenterToKyiv(MapCenter(bookmark.latitude, bookmark.longitude))
        _zoomLevel.value = bookmark.zoomLevel.coerceIn(10, 19)
        _visibleBoundsRaw.value = computeViewportBounds(center = _mapCenter.value, zoom = _zoomLevel.value)
        schedulePersistMapState()
    }

    fun clearAllBookmarks() {
        viewModelScope.launch {
            mapBookmarkRepository.deleteAll()
        }
    }

    fun removeBookmark(bookmark: MapBookmark) {
        viewModelScope.launch {
            mapBookmarkRepository.delete(bookmark)
        }
    }

    fun requestAddToActiveRoute(place: Place) {
        _pendingRouteAddPlace.value = place
    }

    fun dismissAddToRouteDialog() {
        _pendingRouteAddPlace.value = null
    }

    fun confirmAddToActiveRoute() {
        val route = _activeRoute.value ?: return
        val place = _pendingRouteAddPlace.value ?: return
        if (place.category == PlaceCategory.TOILET) {
            _pendingRouteAddPlace.value = null
            return
        }
        if (!RouteMetricsCalculator.isValidCoordinate(place.latitude, place.longitude)) {
            _pendingRouteAddPlace.value = null
            return
        }
        if (route.waypoints.size >= RouteMetricsCalculator.MAX_WAYPOINTS) {
            _pendingRouteAddPlace.value = null
            return
        }

        val waypoint = RoutePoint(
            latitude = place.latitude,
            longitude = place.longitude,
            name = place.name
        )
        if (RouteMetricsCalculator.isDuplicate(waypoint, route)) {
            _pendingRouteAddPlace.value = null
            return
        }

        viewModelScope.launch {
            val updatedRoute = RouteMetricsCalculator.withAppendedWaypoint(
                route = route,
                waypoint = waypoint
            )
            routeRepository.updateRoute(updatedRoute)
            _pendingRouteAddPlace.value = null
        }
    }

    fun createRouteFromPlaces(
        name: String,
        selectedPlaces: List<Place>,
        transportMode: TransportMode = TransportMode.WALKING
    ) {
        if (selectedPlaces.size < 2) return
        val placesWithoutToilets = selectedPlaces
            .distinctBy { it.id }
            .filter {
                it.category != PlaceCategory.TOILET &&
                    RouteMetricsCalculator.isValidCoordinate(it.latitude, it.longitude)
            }
        if (placesWithoutToilets.size < 2) return

        _isCreatingRoute.value = true
        viewModelScope.launch {
            try {
                val points = placesWithoutToilets.map { RoutePoint(it.latitude, it.longitude, it.name) }
                val route = withContext(Dispatchers.Default) {
                    routeOptimizer.buildOptimalRoute(
                        name = name.trim().ifBlank { "Мій маршрут" },
                        places = points,
                        description = "Маршрут, створений на карті"
                    )
                }?.copy(transportMode = transportMode) ?: return@launch
                routeRepository.saveRoute(RouteMetricsCalculator.recompute(route))
            } finally {
                _isCreatingRoute.value = false
            }
        }
    }

    fun createSmartRoute(
        name: String,
        categories: Set<PlaceCategory> = emptySet(),
        maxPlaces: Int = 8
    ) {
        if (maxPlaces < 2) return
        viewModelScope.launch {
            val allPlaces = placeRepository.getAllPlacesSnapshot()
            val favorites = allPlaces.filter { it.isFavorite }
            val userLoc = if (_hasUserLocation.value) _userLocation.value else null

            val request = SmartRouteBuilder.RouteRequest(
                name = name.ifBlank { "Розумний маршрут" },
                categories = categories,
                maxPlaces = maxPlaces,
                userLat = userLoc?.latitude,
                userLon = userLoc?.longitude
            )

            val route = withContext(Dispatchers.Default) {
                smartRouteBuilder.buildRoute(request, allPlaces, favorites)
            }
            if (route != null) {
                routeRepository.saveRoute(route)
            }
        }
    }

    private fun scheduleRouteProgressUpdate(lat: Double, lon: Double) {
        if (_activeRoute.value == null) return
        routeProgressJob?.cancel()
        routeProgressJob = viewModelScope.launch {
            val route = _activeRoute.value ?: return@launch
            val fullGeometry = _fullWalkingRouteGeometry.value
            val allPoints = buildList {
                add(route.startPoint)
                addAll(route.waypoints)
                add(route.endPoint)
            }
            if (allPoints.size < 2) return@launch

            val geometryProjection = if (fullGeometry.size >= 2) {
                withContext(Dispatchers.Default) {
                    RouteNavigationMetrics.projectOnPolyline(fullGeometry, lat, lon)
                }
            } else null

            val currentIdx = _currentWaypointIndex.value
            val onTransport = isLikelyOnTransport()
            val waypointRadius = if (onTransport) TRANSPORT_WAYPOINT_REACH_RADIUS_METERS else WAYPOINT_REACHED_RADIUS_METERS
            val newIdx = resolveCurrentWaypointIndex(
                userLat = lat,
                userLon = lon,
                allPoints = allPoints,
                currentIdx = currentIdx,
                reachRadius = waypointRadius
            )
            _currentWaypointIndex.value = newIdx

            recalculateRemainingDistance(lat, lon, allPoints, newIdx, geometryProjection)

            persistRouteProgress(route.id, newIdx)

            checkOffRouteAndReroute(lat, lon, route, allPoints, newIdx, geometryProjection)

            checkPoiProximity(lat, lon, allPoints, newIdx)
        }
    }

    private fun resolveCurrentWaypointIndex(
        userLat: Double,
        userLon: Double,
        allPoints: List<RoutePoint>,
        currentIdx: Int,
        reachRadius: Double = WAYPOINT_REACHED_RADIUS_METERS
    ): Int {
        var resolvedIndex = currentIdx.coerceIn(0, allPoints.lastIndex)

        for (index in (resolvedIndex + 1)..allPoints.lastIndex) {
            val distance = NativeGeoEngine.distanceMeters(
                userLat,
                userLon,
                allPoints[index].latitude,
                allPoints[index].longitude
            )
            if (distance <= reachRadius) {
                resolvedIndex = index
            }
        }

        val nearestIdx = NativeGeoEngine.nearestPointIndex(allPoints, userLat, userLon)
        if (nearestIdx >= resolvedIndex) {
            val distToNearest = NativeGeoEngine.distanceMeters(
                userLat,
                userLon,
                allPoints[nearestIdx].latitude,
                allPoints[nearestIdx].longitude
            )
            if (distToNearest <= reachRadius) {
                resolvedIndex = nearestIdx
            }
        }

        return resolvedIndex.coerceAtMost(allPoints.lastIndex)
    }

    private fun persistRouteProgress(routeId: Long, waypointIndex: Int) {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            delay(500)
            userPreferenceRepository.upsert("route.progress.$routeId", waypointIndex.toString())
        }
    }

    private fun restoreRouteProgress(routeId: Long) {
        viewModelScope.launch {
            val savedIdx = userPreferenceRepository.getString("route.progress.$routeId", "0").toIntOrNull() ?: 0
            _currentWaypointIndex.value = savedIdx
            _promptedPoiIds.clear()
        }
    }

    private fun clearRouteProgress(routeId: Long) {
        viewModelScope.launch {
            userPreferenceRepository.deleteByKey("route.progress.$routeId")
        }
        _currentWaypointIndex.value = 0
        _remainingDistance.value = 0.0
        _remainingMinutes.value = 0
        _routeProgressFraction.value = 0f
        _distanceToNextWaypoint.value = 0.0
        _nextWaypointName.value = null
        _poiPromptPlace.value = null
        _promptedPoiIds.clear()
    }

    private val poiCategories = setOf(
        PlaceCategory.RESTAURANT,
        PlaceCategory.MUSEUM,
        PlaceCategory.THEATER
    )

    private fun checkPoiProximity(lat: Double, lon: Double, routePoints: List<RoutePoint>, currentIdx: Int) {
        if (_poiPromptPlace.value != null) return

        viewModelScope.launch {
            val allPlaces = placeRepository.getAllPlacesSnapshot()

            for (i in currentIdx until routePoints.size) {
                val rp = routePoints[i]
                val distToUser = NativeGeoEngine.distanceMeters(lat, lon, rp.latitude, rp.longitude)
                if (distToUser > POI_PROMPT_RADIUS_METERS) continue

                val matchingPlace = allPlaces.firstOrNull { place ->
                    place.category in poiCategories &&
                        place.id !in _promptedPoiIds &&
                        NativeGeoEngine.distanceMeters(
                            place.latitude, place.longitude,
                            rp.latitude, rp.longitude
                        ) < 50.0
                }
                if (matchingPlace != null) {
                    _promptedPoiIds.add(matchingPlace.id)
                    _poiPromptPlace.value = matchingPlace
                    return@launch
                }
            }
        }
    }

    fun dismissPoiPrompt() {
        _poiPromptPlace.value = null
    }

    fun acceptPoiVisit() {

        _poiPromptPlace.value = null
    }

    fun skipWaypoint() {
        val route = _activeRoute.value ?: return
        val allPoints = buildList {
            add(route.startPoint)
            addAll(route.waypoints)
            add(route.endPoint)
        }
        val newIdx = (_currentWaypointIndex.value + 1).coerceAtMost(allPoints.lastIndex)
        _currentWaypointIndex.value = newIdx
        persistRouteProgress(route.id, newIdx)

        val userLoc = _userLocation.value
        recalculateRemainingDistance(
            userLoc.latitude,
            userLoc.longitude,
            allPoints,
            newIdx,
            RouteNavigationMetrics.projectOnPolyline(_fullWalkingRouteGeometry.value, userLoc.latitude, userLoc.longitude)
        )
    }

    private fun recalculateRemainingDistance(
        userLat: Double,
        userLon: Double,
        allPoints: List<RoutePoint>,
        currentIdx: Int,
        geometryProjection: RouteNavigationMetrics.PolylineProjection?
    ) {
        val hasProjection = geometryProjection != null && geometryProjection.totalDistanceMeters > 0.0
        val remainingRouteDistance = if (hasProjection) {
            val projection = geometryProjection
            _totalRouteDistanceMeters.value = projection.totalDistanceMeters
            _walkingRouteGeometry.value = RouteNavigationMetrics.remainingGeometry(
                _fullWalkingRouteGeometry.value,
                projection
            )
            (projection.totalDistanceMeters - projection.distanceFromStartMeters).coerceAtLeast(0.0)
        } else {
            val fallbackGeometry = allPoints.drop(currentIdx.coerceAtMost(allPoints.lastIndex))
            _walkingRouteGeometry.value = fallbackGeometry
            if (_totalRouteDistanceMeters.value <= 0.0) {
                _totalRouteDistanceMeters.value = NativeGeoEngine.polylineDistanceMeters(allPoints)
            }
            NativeGeoEngine.polylineDistanceMeters(fallbackGeometry)
        }

        if (currentIdx >= allPoints.lastIndex || remainingRouteDistance <= 8.0) {

            _remainingDistance.value = 0.0
            _remainingMinutes.value = 0
            _routeProgressFraction.value = 1f
            _distanceToNextWaypoint.value = 0.0
            _nextWaypointName.value = null
            _walkingRouteGeometry.value = emptyList()
            return
        }

        _remainingDistance.value = remainingRouteDistance
        _remainingMinutes.value = estimateRemainingMinutes(remainingRouteDistance)

        val totalDistance = _totalRouteDistanceMeters.value.takeIf { it > 0.0 } ?: remainingRouteDistance
        _routeProgressFraction.value = if (totalDistance <= 0.0) {
            0f
        } else {
            (1.0 - (remainingRouteDistance / totalDistance)).toFloat().coerceIn(0f, 1f)
        }

        val nextIdx = currentIdx + 1
        val distToNext = NativeGeoEngine.distanceMeters(
            userLat, userLon,
            allPoints[nextIdx].latitude, allPoints[nextIdx].longitude
        )
        _distanceToNextWaypoint.value = distToNext
        _nextWaypointName.value = allPoints[nextIdx].name
    }

    private fun estimateRemainingMinutes(remainingDistanceMeters: Double): Int {
        val totalDist = _totalRouteDistanceMeters.value
        val osrmDuration = _totalRouteDurationSeconds
        if (osrmDuration != null && osrmDuration > 0.0 && totalDist > 0.0) {
            val fraction = (remainingDistanceMeters / totalDist).coerceIn(0.0, 1.0)
            val remainingSec = osrmDuration * fraction
            return kotlin.math.ceil(remainingSec / 60.0).toInt().coerceAtLeast(if (remainingDistanceMeters > 10.0) 1 else 0)
        }
        val mode = _activeRoute.value?.transportMode ?: TransportMode.WALKING
        return RouteNavigationMetrics.estimateDurationMinutes(remainingDistanceMeters, mode)
    }

    private fun checkOffRouteAndReroute(
        userLat: Double,
        userLon: Double,
        route: Route,
        allWaypoints: List<RoutePoint>,
        currentIdx: Int,
        geometryProjection: RouteNavigationMetrics.PolylineProjection?
    ) {
        val geometry = _fullWalkingRouteGeometry.value
        if (geometry.size < 2) return
        if (currentIdx >= allWaypoints.lastIndex) return

        if (lastGpsAccuracy > 100f) {
            Log.d("WalkingDirections", "GPS accuracy ${lastGpsAccuracy.toInt()}m too poor, skipping reroute check")
            return
        }

        if (_isUnderground.value) {
            Log.d("WalkingDirections", "Underground detected (${_gpsSatellitesUsed} sats), skipping reroute check")
            return
        }

        val onTransport = isLikelyOnTransport()

        val distToRoute = if (geometryProjection != null) {
            geometryProjection.distanceToRouteMeters
        } else {
            val nearestIdx = NativeGeoEngine.nearestPointIndex(geometry, userLat, userLon)
            val nearestPt = geometry[nearestIdx]
            NativeGeoEngine.distanceMeters(userLat, userLon, nearestPt.latitude, nearestPt.longitude)
        }

        val offRouteThreshold = if (onTransport) OFF_ROUTE_THRESHOLD_METERS * 0.8 else OFF_ROUTE_THRESHOLD_METERS

        if (distToRoute <= offRouteThreshold) {
            consecutiveOffRouteCount = 0
            return
        }

        consecutiveOffRouteCount++
        val requiredConsecutive = if (onTransport) 1 else OFF_ROUTE_CONSECUTIVE_THRESHOLD
        if (consecutiveOffRouteCount < requiredConsecutive) return

        val now = System.currentTimeMillis()
        val cooldown = if (onTransport) TRANSPORT_REROUTE_COOLDOWN_MS else REROUTE_COOLDOWN_MS
        if (now - lastRerouteTimestamp < cooldown) return
        lastRerouteTimestamp = now
        consecutiveOffRouteCount = 0

        Log.d("WalkingDirections", "User is ${distToRoute.toInt()}m off-route (transport=$onTransport, speed=${"%.1f".format(estimateCurrentSpeedMs())} m/s), rerouting...")

        val remainingWaypoints = buildRemainingWaypoints(
            allWaypoints = allWaypoints,
            currentIdx = currentIdx,
            userLat = userLat,
            userLon = userLon,
            geometry = geometry,
            geometryProjection = geometryProjection
        )

        val rerouteTargets = if (remainingWaypoints.isNotEmpty()) {
            remainingWaypoints
        } else {
            listOf(allWaypoints.last())
        }

        val userPoint = RoutePoint(latitude = userLat, longitude = userLon, name = null)
        val newWaypoints = buildList {
            add(userPoint)
            addAll(rerouteTargets)
        }

        walkingDirectionsJob?.cancel()
        rerouteJob?.cancel()
        _isRerouting.value = true
        val previousGeometry = _walkingRouteGeometry.value
        rerouteJob = viewModelScope.launch {
            try {
                val detailed = walkingDirectionsService.fetchWalkingRouteDetails(newWaypoints, route.transportMode)

                if (_activeRoute.value?.id == route.id && detailed.geometry.size >= 2) {
                    applyRouteGeometryResult(
                        route = route,
                        requestedWaypoints = newWaypoints,
                        result = detailed,
                        persistRouteMetrics = false
                    )
                    Log.d("WalkingDirections", "Rerouted: ${detailed.geometry.size} geometry points")
                } else if (_activeRoute.value?.id == route.id) {
                    Log.w("WalkingDirections", "Reroute returned empty geometry, keeping old geometry")
                    _walkingRouteGeometry.value = previousGeometry
                }
            } catch (e: Exception) {
                Log.w("WalkingDirections", "Reroute failed, keeping old geometry", e)
                _walkingRouteGeometry.value = previousGeometry
            } finally {
                _isRerouting.value = false
            }
        }
    }

    private fun buildRemainingWaypoints(
        allWaypoints: List<RoutePoint>,
        currentIdx: Int,
        userLat: Double,
        userLon: Double,
        geometry: List<RoutePoint>,
        geometryProjection: RouteNavigationMetrics.PolylineProjection?
    ): List<RoutePoint> {
        val candidateWaypoints = allWaypoints
            .subList((currentIdx + 1).coerceAtMost(allWaypoints.lastIndex), allWaypoints.size)

        if (candidateWaypoints.isEmpty()) return emptyList()

        val userProjectionSegment = geometryProjection?.segmentIndex ?: -1

        return candidateWaypoints.filter { waypoint ->
            val distFromUser = NativeGeoEngine.distanceMeters(
                userLat, userLon, waypoint.latitude, waypoint.longitude
            )
            if (distFromUser < 50.0) return@filter false

            if (userProjectionSegment >= 0 && geometry.size >= 2) {
                val waypointGeomIdx = NativeGeoEngine.nearestPointIndex(
                    geometry, waypoint.latitude, waypoint.longitude
                )
                waypointGeomIdx > userProjectionSegment
            } else {
                true
            }
        }
    }

    private fun fetchWalkingDirectionsForRoute(route: Route) {
        walkingDirectionsJob?.cancel()
        rerouteJob?.cancel()
        _isRerouting.value = false

        val allWaypoints = buildList {
            add(route.startPoint)
            addAll(route.waypoints)
            add(route.endPoint)
        }

        val currentIdx = _currentWaypointIndex.value
        val waypoints: List<RoutePoint> = if (_hasUserLocation.value && allWaypoints.size >= 2) {
            val user = _userLocation.value
            val legStart = allWaypoints[currentIdx.coerceAtMost(allWaypoints.lastIndex)]
            val distToLegStart = NativeGeoEngine.distanceMeters(
                user.latitude, user.longitude,
                legStart.latitude, legStart.longitude
            )
            if (distToLegStart > OFF_ROUTE_THRESHOLD_METERS) {

                val remaining = allWaypoints.subList(
                    (currentIdx + 1).coerceAtMost(allWaypoints.lastIndex),
                    allWaypoints.size
                )
                buildList {
                    add(RoutePoint(user.latitude, user.longitude, null))
                    addAll(remaining)
                }
            } else if (currentIdx > 0) {

                allWaypoints.subList(currentIdx, allWaypoints.size)
            } else {
                allWaypoints
            }
        } else {
            allWaypoints
        }

        Log.d("WalkingDirections", "fetchWalkingDirectionsForRoute: route=${route.id}, ${waypoints.size} effective waypoints (currentIdx=$currentIdx)")

        _fullWalkingRouteGeometry.value = waypoints
        _walkingRouteGeometry.value = waypoints
        _totalRouteDistanceMeters.value = route.distance.takeIf { it > 0.0 }
            ?: NativeGeoEngine.polylineDistanceMeters(waypoints)

        walkingDirectionsJob = viewModelScope.launch {
            val detailed = walkingDirectionsService.fetchWalkingRouteDetails(waypoints, route.transportMode)
            Log.d("WalkingDirections", "Got ${detailed.geometry.size} detailed points (was ${waypoints.size} waypoints), routeMatch=${_activeRoute.value?.id == route.id}")

            if (_activeRoute.value?.id == route.id && detailed.geometry.size >= 2) {
                applyRouteGeometryResult(
                    route = route,
                    requestedWaypoints = waypoints,
                    result = detailed,
                    persistRouteMetrics = shouldPersistRouteMetrics(route, waypoints)
                )
                Log.d("WalkingDirections", "Updated route geometry with ${detailed.geometry.size} points")
            } else if (_activeRoute.value?.id == route.id) {
                Log.w("WalkingDirections", "OSRM returned fallback (${detailed.geometry.size} pts for ${waypoints.size} waypoints), keeping straight-line")
            }
        }
    }

    private fun shouldPersistRouteMetrics(route: Route, requestedWaypoints: List<RoutePoint>): Boolean {
        if (requestedWaypoints.size < 2) return false
        return sameCoordinate(requestedWaypoints.first(), route.startPoint) &&
            sameCoordinate(requestedWaypoints.last(), route.endPoint) &&
            requestedWaypoints.size == route.waypoints.size + 2
    }

    private suspend fun applyRouteGeometryResult(
        route: Route,
        requestedWaypoints: List<RoutePoint>,
        result: WalkingDirectionsService.WalkingRouteResult,
        persistRouteMetrics: Boolean
    ) {
        val resolvedGeometry = if (result.geometry.size >= 2) result.geometry else requestedWaypoints
        _fullWalkingRouteGeometry.value = resolvedGeometry
        _walkingRouteGeometry.value = resolvedGeometry

        val resolvedDistance = result.distanceMeters
            ?.takeIf { it > 0.0 }
            ?: NativeGeoEngine.polylineDistanceMeters(resolvedGeometry)
        _totalRouteDistanceMeters.value = resolvedDistance

        _totalRouteDurationSeconds = result.durationSeconds?.takeIf { it > 0.0 }

        val resolvedDurationMinutes = result.durationSeconds
            ?.let { seconds -> kotlin.math.ceil(seconds / 60.0).toInt() }
            ?.coerceAtLeast(1)
            ?: RouteNavigationMetrics.estimateDurationMinutes(resolvedDistance, route.transportMode)

        if (persistRouteMetrics) {
            maybePersistRouteMetrics(route, resolvedDistance, resolvedDurationMinutes)
        }

        if (_hasUserLocation.value) {
            val user = _userLocation.value
            val allPoints = buildList {
                add(route.startPoint)
                addAll(route.waypoints)
                add(route.endPoint)
            }
            val projection = RouteNavigationMetrics.projectOnPolyline(
                resolvedGeometry,
                user.latitude,
                user.longitude
            )
            recalculateRemainingDistance(
                user.latitude,
                user.longitude,
                allPoints,
                _currentWaypointIndex.value,
                projection
            )
        }
    }

    private suspend fun maybePersistRouteMetrics(
        route: Route,
        distanceMeters: Double,
        durationMinutes: Int
    ) {
        val currentRoute = _activeRoute.value?.takeIf { it.id == route.id } ?: route
        val distanceChanged = abs(currentRoute.distance - distanceMeters) >= 25.0
        val durationChanged = currentRoute.estimatedDuration != durationMinutes
        if (!distanceChanged && !durationChanged) return

        val updatedRoute = currentRoute.copy(
            distance = distanceMeters,
            estimatedDuration = durationMinutes,
            updatedAt = System.currentTimeMillis()
        )
        _activeRoute.value = updatedRoute
        routeRepository.updateRoute(updatedRoute)
    }

    private fun sameCoordinate(first: RoutePoint, second: RoutePoint, tolerance: Double = 0.00005): Boolean {
        return abs(first.latitude - second.latitude) <= tolerance &&
            abs(first.longitude - second.longitude) <= tolerance
    }

    private fun computeViewportBounds(center: MapCenter, zoom: Int): MapViewportBounds {
        val baseSpan = 0.6
        val zoomFactor = 2.0.pow((zoom - 13).toDouble())
        val latSpan = (baseSpan / zoomFactor).coerceIn(0.01, 1.2)
        val lonSpan = (baseSpan / zoomFactor).coerceIn(0.01, 1.2)

        val kyivBounds = MapViewportBounds(
            minLatitude = 50.213,
            maxLatitude = 50.590,
            minLongitude = 30.239,
            maxLongitude = 30.825
        )

        val raw = MapViewportBounds(
            minLatitude = center.latitude - latSpan / 2,
            maxLatitude = center.latitude + latSpan / 2,
            minLongitude = center.longitude - lonSpan / 2,
            maxLongitude = center.longitude + lonSpan / 2
        )

        return raw.clampTo(kyivBounds)
    }

    private fun clampCenterToKyiv(center: MapCenter): MapCenter {
        return MapCenter(
            latitude = center.latitude.coerceIn(50.213, 50.590),
            longitude = center.longitude.coerceIn(30.239, 30.825)
        )
    }

    private fun schedulePersistMapState() {
        persistMapStateJob?.cancel()
        persistMapStateJob = viewModelScope.launch {
            delay(250)
            val center = _mapCenter.value
            userPreferenceRepository.upsert("map.center.lat", center.latitude.toString())
            userPreferenceRepository.upsert("map.center.lon", center.longitude.toString())
            userPreferenceRepository.upsert("map.zoom", _zoomLevel.value.toString())
        }
    }

    private fun restoreMapState() {
        viewModelScope.launch {
            val savedLat = userPreferenceRepository.getString("map.center.lat", "").toDoubleOrNull()
            val savedLon = userPreferenceRepository.getString("map.center.lon", "").toDoubleOrNull()
            val savedZoom = userPreferenceRepository.getString("map.zoom", "").toIntOrNull()
            _mapCenter.value = if (savedLat != null && savedLon != null) {
                MapCenter(savedLat, savedLon)
            } else {
                MAIDAN_NEZALEZHNOSTI
            }
            _zoomLevel.value = savedZoom ?: DEFAULT_ZOOM
            val sortMode = userPreferenceRepository.getString("map.sort.mode", "").uppercase()
            _sortMode.value = PlaceSortMode.entries.firstOrNull { it.name == sortMode } ?: PlaceSortMode.POPULARITY
            _visibleBoundsRaw.value = computeViewportBounds(center = _mapCenter.value, zoom = _zoomLevel.value)
        }
    }

    private suspend fun applyPendingFocusFromPreferences() {
        val pendingId = userPreferenceRepository.getString("map.focus.placeId", "").toLongOrNull()
        if (pendingId != null) {
            val place = placeRepository.getPlaceById(pendingId)
            if (place != null) {
                selectPlace(place)
                userPreferenceRepository.deleteByKey("map.focus.placeId")
                return
            }
        }

        val lat = userPreferenceRepository.getString("map.focus.lat", "").toDoubleOrNull()
        val lon = userPreferenceRepository.getString("map.focus.lon", "").toDoubleOrNull()
        val zoom = userPreferenceRepository.getString("map.focus.zoom", "").toIntOrNull()
        if (lat != null && lon != null) {
            updateMapCenter(lat, lon)
            if (zoom != null) {
                updateZoomLevel(zoom)
            }
            userPreferenceRepository.deleteByKey("map.focus.lat")
            userPreferenceRepository.deleteByKey("map.focus.lon")
            userPreferenceRepository.deleteByKey("map.focus.zoom")
        }
    }
}

data class MapViewportBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
) {
    fun contains(latitude: Double, longitude: Double): Boolean {
        return latitude in minLatitude..maxLatitude && longitude in minLongitude..maxLongitude
    }

    fun clampTo(bounds: MapViewportBounds): MapViewportBounds {
        return MapViewportBounds(
            minLatitude = minLatitude.coerceAtLeast(bounds.minLatitude),
            maxLatitude = maxLatitude.coerceAtMost(bounds.maxLatitude),
            minLongitude = minLongitude.coerceAtLeast(bounds.minLongitude),
            maxLongitude = maxLongitude.coerceAtMost(bounds.maxLongitude)
        )
    }
}

data class MapCenter(
    val latitude: Double,
    val longitude: Double
)

private data class MapFilterState(
    val onlyFavorites: Boolean,
    val query: String,
    val category: PlaceCategory?
)
