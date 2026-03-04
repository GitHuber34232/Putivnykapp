package ua.kyiv.putivnyk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.PlaceSortMode
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import ua.kyiv.putivnyk.domain.usecase.recommendation.RecommendationEngine
import javax.inject.Inject

@HiltViewModel
@OptIn(FlowPreview::class)
class PlacesViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val recommendationEngine: RecommendationEngine
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCategory = MutableStateFlow<PlaceCategory?>(null)
    val selectedCategory: StateFlow<PlaceCategory?> = _selectedCategory

    private val _showOnlyFavorites = MutableStateFlow(false)
    val showOnlyFavorites: StateFlow<Boolean> = _showOnlyFavorites

    private val _sortMode = MutableStateFlow(PlaceSortMode.POPULARITY)
    val sortMode: StateFlow<PlaceSortMode> = _sortMode

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded

    private val debouncedSearchQuery = _searchQuery
        .debounce(150)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    private val filteredPlaces = combine(
        placeRepository.getAllPlaces(),
        debouncedSearchQuery,
        _selectedCategory,
        _showOnlyFavorites
    ) { allPlaces, query, category, onlyFavorites ->
        var filtered = allPlaces
        val normalizedQuery = query.trim()
        val isToiletExplicitlyRequested = normalizedQuery.contains("туалет", ignoreCase = true) ||
            normalizedQuery.contains("toilet", ignoreCase = true) ||
            normalizedQuery.contains("wc", ignoreCase = true)

        if (!isToiletExplicitlyRequested) {
            filtered = filtered.filter { it.category != PlaceCategory.TOILET }
        }
        if (onlyFavorites) {
            filtered = filtered.filter { it.isFavorite }
        }
        if (category != null) {
            filtered = filtered.filter { it.category == category }
        }
        if (normalizedQuery.isNotBlank()) {
            filtered = filtered.filter { place ->
                place.name.contains(normalizedQuery, ignoreCase = true) ||
                place.nameEn?.contains(normalizedQuery, ignoreCase = true) == true ||
                place.description?.contains(normalizedQuery, ignoreCase = true) == true ||
                place.category.displayName.contains(normalizedQuery, ignoreCase = true) ||
                place.tags.any { it.contains(normalizedQuery, ignoreCase = true) }
            }
        }

        filtered
    }

    val places: StateFlow<List<Place>> = combine(
        filteredPlaces,
        _sortMode,
        _userLocation,
        placeRepository.getFavoritePlaces()
    ) { filtered, sortMode, userLocation, favorites ->

        when (sortMode) {
            PlaceSortMode.POPULARITY -> filtered.sortedByDescending { it.popularity }
            PlaceSortMode.RATING -> filtered.sortedByDescending { it.rating ?: 0.0 }
            PlaceSortMode.DISTANCE -> {
                val currentLocation = userLocation ?: (50.4501 to 30.5234)
                filtered.sortedBy { place ->
                    NativeGeoEngine.distanceMeters(
                        lat1 = currentLocation.first,
                        lon1 = currentLocation.second,
                        lat2 = place.latitude,
                        lon2 = place.longitude
                    )
                }
            }
            PlaceSortMode.RECOMMENDED -> {
                val loc = userLocation
                recommendationEngine.recommend(
                    allPlaces = filtered,
                    favorites = favorites,
                    userLat = loc?.first,
                    userLon = loc?.second,
                    limit = filtered.size
                )
            }
        }.also { _isLoaded.value = true }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val favoritePlaces: StateFlow<List<Place>> = placeRepository.getFavoritePlaces()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: PlaceCategory?) {
        _selectedCategory.value = category
    }

    fun toggleShowOnlyFavorites() {
        _showOnlyFavorites.value = !_showOnlyFavorites.value
    }

    fun setSortMode(mode: PlaceSortMode) {
        _sortMode.value = mode
    }

    fun updateUserLocation(latitude: Double, longitude: Double) {
        _userLocation.value = latitude to longitude
    }

    fun toggleFavorite(placeId: Long) {
        viewModelScope.launch {
            placeRepository.toggleFavorite(placeId)
        }
    }

    fun toggleVisited(placeId: Long) {
        viewModelScope.launch {
            placeRepository.toggleVisited(placeId)
        }
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedCategory.value = null
        _showOnlyFavorites.value = false
        _sortMode.value = PlaceSortMode.POPULARITY
    }
}
