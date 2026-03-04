package ua.kyiv.putivnyk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.domain.usecase.recommendation.RecommendationEngine
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val recommendationEngine: RecommendationEngine
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 5
        private const val PINNED_IDS_KEY = "home.pinned_place_ids"
    }

    private val _selectedPlaceId = MutableStateFlow<Long?>(null)
    val selectedPlaceId: StateFlow<Long?> = _selectedPlaceId

    private val _favoriteCategory = MutableStateFlow("General")
    val favoriteCategory: StateFlow<String> = _favoriteCategory

    private val _showFavoriteDialog = MutableStateFlow(false)
    val showFavoriteDialog: StateFlow<Boolean> = _showFavoriteDialog

    private val _showAddPinnedDialog = MutableStateFlow(false)
    val showAddPinnedDialog: StateFlow<Boolean> = _showAddPinnedDialog

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded

    private val _visibleRecoCount = MutableStateFlow(PAGE_SIZE)
    val visibleRecoCount: StateFlow<Int> = _visibleRecoCount

    private val _hasMoreRecommendations = MutableStateFlow(false)
    val hasMoreRecommendations: StateFlow<Boolean> = _hasMoreRecommendations

    private val _pinnedPlaceIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _snackbarEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    private val allPlaces = placeRepository.getAllPlaces()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            val saved = userPreferenceRepository.getString(PINNED_IDS_KEY, "")
            _pinnedPlaceIds.value = saved.split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .toSet()
        }
    }

    val pinnedPlaces: StateFlow<List<Place>> = combine(
        allPlaces,
        _pinnedPlaceIds
    ) { places, ids ->
        if (ids.isEmpty()) emptyList()
        else places.filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val fullRecommendations = combine(
        allPlaces,
        placeRepository.getFavoritePlaces(),
        _pinnedPlaceIds
    ) { places, favorites, pinnedIds ->
        recommendationEngine.recommend(
            allPlaces = places,
            favorites = favorites,
            excludeIds = pinnedIds
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recommendedPlaces: StateFlow<List<Place>> = combine(
        fullRecommendations,
        _visibleRecoCount
    ) { all, count ->
        _hasMoreRecommendations.value = all.size > count
        _isLoaded.value = true
        all.take(count)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availablePlacesForPinning: StateFlow<List<Place>> = combine(
        allPlaces,
        _pinnedPlaceIds
    ) { places, pinned ->
        places.filter { it.category != PlaceCategory.TOILET && it.id !in pinned }
            .sortedByDescending { it.popularity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadMoreRecommendations() {
        _visibleRecoCount.value += PAGE_SIZE
    }

    fun requestOpenMapForPlace(place: Place) {
        viewModelScope.launch {
            userPreferenceRepository.upsert("map.focus.placeId", place.id.toString())
            userPreferenceRepository.deleteByKey("map.focus.lat")
            userPreferenceRepository.deleteByKey("map.focus.lon")
            userPreferenceRepository.deleteByKey("map.focus.zoom")
        }
    }

    fun openFavoriteDialog(initialPlaceId: Long?) {
        _selectedPlaceId.value = initialPlaceId
        _showFavoriteDialog.value = true
    }

    fun dismissFavoriteDialog() {
        _showFavoriteDialog.value = false
    }

    fun selectFavoritePlace(placeId: Long) {
        _selectedPlaceId.value = placeId
    }

    fun setFavoriteCategory(value: String) {
        _favoriteCategory.value = value
    }

    fun saveFavoriteWithCategory() {
        val placeId = _selectedPlaceId.value ?: return
        val categoryTag = "fav:${_favoriteCategory.value.trim().ifBlank { "General" }}"

        viewModelScope.launch {
            val place = placeRepository.getPlaceById(placeId) ?: return@launch
            val updatedTags = (place.tags + categoryTag).distinct()
            placeRepository.updatePlace(place.copy(isFavorite = true, tags = updatedTags))
            _showFavoriteDialog.value = false
            _snackbarEvent.tryEmit("added_to_favorites")
        }
    }

    fun openAddPinnedDialog() {
        _showAddPinnedDialog.value = true
    }

    fun dismissAddPinnedDialog() {
        _showAddPinnedDialog.value = false
    }

    fun pinPlace(placeId: Long) {
        viewModelScope.launch {
            val updated = _pinnedPlaceIds.value + placeId
            _pinnedPlaceIds.value = updated
            persistPinnedIds(updated)
            _showAddPinnedDialog.value = false
            _snackbarEvent.tryEmit("place_pinned")
        }
    }

    fun unpinPlace(placeId: Long) {
        viewModelScope.launch {
            val updated = _pinnedPlaceIds.value - placeId
            _pinnedPlaceIds.value = updated
            persistPinnedIds(updated)
            _snackbarEvent.tryEmit("place_unpinned")
        }
    }

    private suspend fun persistPinnedIds(ids: Set<Long>) {
        userPreferenceRepository.upsert(PINNED_IDS_KEY, ids.joinToString(","))
    }
}
