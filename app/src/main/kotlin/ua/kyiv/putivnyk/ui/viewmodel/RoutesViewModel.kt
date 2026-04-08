package ua.kyiv.putivnyk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.data.remote.WalkingDirectionsService
import ua.kyiv.putivnyk.domain.usecase.routing.RouteMetricsCalculator
import ua.kyiv.putivnyk.domain.usecase.routing.RouteOptimizer
import kotlinx.coroutines.Job
import javax.inject.Inject

@HiltViewModel
class RoutesViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val routeOptimizer: RouteOptimizer,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val placeRepository: PlaceRepository,
    private val walkingDirectionsService: WalkingDirectionsService
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _showOnlyFavorites = MutableStateFlow(false)
    val showOnlyFavorites: StateFlow<Boolean> = _showOnlyFavorites

    private val _selectedRoute = MutableStateFlow<Route?>(null)
    val selectedRoute: StateFlow<Route?> = _selectedRoute

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded

    private val _routeToDelete = MutableStateFlow<Route?>(null)
    val routeToDelete: StateFlow<Route?> = _routeToDelete

    private val _deleteEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleteEvent: SharedFlow<Unit> = _deleteEvent

    private val _createEvent = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val createEvent: SharedFlow<Boolean> = _createEvent

    private val _isCreatingRoute = MutableStateFlow(false)
    val isCreatingRoute: StateFlow<Boolean> = _isCreatingRoute

    private val _walkingPreview = MutableStateFlow<List<RoutePoint>>(emptyList())
    val walkingPreview: StateFlow<List<RoutePoint>> = _walkingPreview
    private var walkingPreviewJob: Job? = null

    fun updateWalkingPreview(places: List<Place>) {
        walkingPreviewJob?.cancel()
        if (places.size < 2) {
            _walkingPreview.value = places.map { RoutePoint(it.latitude, it.longitude, it.name) }
            return
        }
        val waypoints = places.map { RoutePoint(it.latitude, it.longitude, it.name) }
        _walkingPreview.value = waypoints
        walkingPreviewJob = viewModelScope.launch {
            val detailed = walkingDirectionsService.fetchWalkingRoute(waypoints)
            _walkingPreview.value = detailed
        }
    }

    fun clearWalkingPreview() {
        walkingPreviewJob?.cancel()
        _walkingPreview.value = emptyList()
    }

    private val _activeRouteId = MutableStateFlow<Long?>(null)
    val activeRouteId: StateFlow<Long?> = _activeRouteId

    init {

        viewModelScope.launch {
            val savedId = userPreferenceRepository.getString("map.activeRouteId", "").toLongOrNull()
            _activeRouteId.value = savedId
        }
    }

    private val _availablePlaces = MutableStateFlow<List<Place>>(emptyList())
    val availablePlaces: StateFlow<List<Place>> = _availablePlaces

    fun loadAvailablePlaces() {
        viewModelScope.launch {
            val places = placeRepository.getAllPlacesSnapshot()
                .filter { it.category != PlaceCategory.TOILET }
                .sortedBy { it.name }
            _availablePlaces.value = places
        }
    }

    val routes: StateFlow<List<Route>> = combine(
        routeRepository.getAllRoutes(),
        _showOnlyFavorites,
        _searchQuery
    ) { allRoutes, onlyFavorites, query ->
        var filtered = if (onlyFavorites) {
            allRoutes.filter { it.isFavorite }
        } else {
            allRoutes
        }

        val normalizedQuery = query.trim()
        if (normalizedQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.name.contains(normalizedQuery, ignoreCase = true) ||
                    (it.description?.contains(normalizedQuery, ignoreCase = true) == true)
            }
        }

        _isLoaded.value = true
        filtered
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateSearchQuery(value: String) {
        _searchQuery.value = value
    }

    val favoriteRoutes: StateFlow<List<Route>> = routeRepository.getFavoriteRoutes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleShowOnlyFavorites() {
        _showOnlyFavorites.value = !_showOnlyFavorites.value
    }

    fun selectRoute(route: Route?) {
        _selectedRoute.value = route
    }

    fun toggleFavorite(routeId: Long) {
        viewModelScope.launch {
            routeRepository.toggleFavorite(routeId)
        }
    }

    fun requestDeleteRoute(route: Route) {
        _routeToDelete.value = route
    }

    fun confirmDeleteRoute() {
        val route = _routeToDelete.value ?: return
        _routeToDelete.value = null
        viewModelScope.launch {
            routeRepository.deleteRoute(route)
            _deleteEvent.tryEmit(Unit)
        }
    }

    fun cancelDeleteRoute() {
        _routeToDelete.value = null
    }

    fun saveRoute(route: Route) {
        viewModelScope.launch {
            routeRepository.saveRoute(RouteMetricsCalculator.recompute(route))
        }
    }

    fun renameRoute(routeId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val route = routeRepository.getRouteById(routeId) ?: return@launch
            routeRepository.updateRoute(
                RouteMetricsCalculator.recompute(
                    route.copy(
                    name = newName.trim(),
                    updatedAt = System.currentTimeMillis()
                    )
                )
            )
        }
    }

    fun removeLastWaypoint(routeId: Long) {
        viewModelScope.launch {
            val route = routeRepository.getRouteById(routeId) ?: return@launch
            if (route.waypoints.isEmpty()) return@launch
            routeRepository.updateRoute(
                RouteMetricsCalculator.recompute(route.copy(waypoints = route.waypoints.dropLast(1)))
            )
        }
    }

    fun removeWaypointAt(routeId: Long, index: Int) {
        viewModelScope.launch {
            val route = routeRepository.getRouteById(routeId) ?: return@launch
            if (index !in route.waypoints.indices) return@launch
            val updated = route.waypoints.toMutableList().apply { removeAt(index) }
            routeRepository.updateRoute(
                RouteMetricsCalculator.recompute(route.copy(waypoints = updated))
            )
        }
    }

    fun clearWaypoints(routeId: Long) {
        viewModelScope.launch {
            val route = routeRepository.getRouteById(routeId) ?: return@launch
            routeRepository.updateRoute(
                RouteMetricsCalculator.recompute(route.copy(waypoints = emptyList()))
            )
        }
    }

    fun addWaypoint(routeId: Long, latitude: Double, longitude: Double, name: String?) {
        if (!RouteMetricsCalculator.isValidCoordinate(latitude, longitude)) return
        viewModelScope.launch {
            val route = routeRepository.getRouteById(routeId) ?: return@launch
            if (route.waypoints.size >= RouteMetricsCalculator.MAX_WAYPOINTS) return@launch
            val waypoint = RoutePoint(
                latitude = latitude,
                longitude = longitude,
                name = name?.trim()?.takeIf { it.isNotBlank() }
            )
            if (RouteMetricsCalculator.isDuplicate(waypoint, route)) return@launch
            routeRepository.updateRoute(RouteMetricsCalculator.withAppendedWaypoint(route, waypoint))
        }
    }

    fun optimizeRoute(routeId: Long) {
        viewModelScope.launch {
            val route = routeRepository.getRouteById(routeId) ?: return@launch
            val optimized = withContext(Dispatchers.Default) {
                routeOptimizer.optimizeWaypoints(route)
            }
            routeRepository.updateRoute(optimized)
        }
    }

    fun reverseRoute(routeId: Long) {
        viewModelScope.launch {
            val route = routeRepository.getRouteById(routeId) ?: return@launch
            val reversed = route.copy(
                startPoint = route.endPoint,
                endPoint = route.startPoint,
                waypoints = route.waypoints.reversed(),
                updatedAt = System.currentTimeMillis()
            )
            routeRepository.updateRoute(reversed)
        }
    }

    fun activateRouteOnMap(routeId: Long) {
        viewModelScope.launch {
            userPreferenceRepository.upsert("map.pending.activeRouteId", routeId.toString())
            _activeRouteId.value = routeId
        }
    }

    fun deactivateRoute() {
        viewModelScope.launch {
            userPreferenceRepository.deleteByKey("map.activeRouteId")
            userPreferenceRepository.deleteByKey("map.pending.activeRouteId")
            _activeRouteId.value = null
        }
    }

    fun createRouteFromPlaces(
        name: String,
        selectedPlaces: List<Place>,
        transportMode: TransportMode = TransportMode.WALKING
    ) {
        if (selectedPlaces.size < 2) return
        val placesFiltered = selectedPlaces
            .distinctBy { it.id }
            .filter {
                it.category != PlaceCategory.TOILET &&
                    RouteMetricsCalculator.isValidCoordinate(it.latitude, it.longitude)
            }
        if (placesFiltered.size < 2) return

        _isCreatingRoute.value = true
        viewModelScope.launch {
            try {
                val points = placesFiltered.map { RoutePoint(it.latitude, it.longitude, it.name) }

                val start = points.first()
                val end = points.last()
                val intermediates = if (points.size > 2) points.subList(1, points.size - 1) else emptyList()
                val route = RouteMetricsCalculator.recompute(
                    Route(
                        name = name.trim().ifBlank { "Мій маршрут" },
                        description = "Маршрут з ${placesFiltered.size} точок",
                        startPoint = start,
                        endPoint = end,
                        waypoints = intermediates,
                        distance = 0.0,
                        estimatedDuration = 0,
                        transportMode = transportMode
                    )
                )
                routeRepository.saveRoute(route)
                _createEvent.tryEmit(true)
            } catch (e: Exception) {
                _createEvent.tryEmit(false)
            } finally {
                _isCreatingRoute.value = false
            }
        }
    }
}
