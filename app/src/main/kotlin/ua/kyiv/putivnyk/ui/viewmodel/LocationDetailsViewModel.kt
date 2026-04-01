package ua.kyiv.putivnyk.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.repository.LocalizationRepository
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.repository.SyncStateRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.domain.usecase.routing.RouteMetricsCalculator
import ua.kyiv.putivnyk.i18n.SupportedLanguages
import ua.kyiv.putivnyk.i18n.TranslationService

@HiltViewModel
class LocationDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val placeRepository: PlaceRepository,
    private val localizationRepository: LocalizationRepository,
    private val translationService: TranslationService,
    private val routeRepository: RouteRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val syncStateRepository: SyncStateRepository
) : ViewModel() {

    private val placeId: Long = savedStateHandle.get<String>("placeId")?.toLongOrNull() ?: 0L

    private val _place = MutableStateFlow<Place?>(null)
    val place: StateFlow<Place?> = _place.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _translatedDescription = MutableStateFlow<String?>(null)
    val translatedDescription: StateFlow<String?> = _translatedDescription.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("uk")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _snackbarEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    val availableLanguages = SupportedLanguages.majorIso639_1

    init {
        viewModelScope.launch {
            val mode = userPreferenceRepository.getString("ui.lang.mode", "auto")
            val rawLang = if (mode == "manual") {
                userPreferenceRepository.getString("ui.lang.manual", "uk")
            } else {
                Locale.getDefault().language.ifBlank { "uk" }
            }.lowercase()

            val language = if (SupportedLanguages.contains(rawLang)) rawLang else "uk"
            _selectedLanguage.value = language
            _place.value = placeRepository.getPlaceById(placeId)
            _isLoaded.value = true
            translateDescriptionIfNeeded()
        }
    }

    fun setLanguage(isoCode: String) {
        if (!SupportedLanguages.contains(isoCode)) return
        _selectedLanguage.value = isoCode
        translateDescriptionIfNeeded()
    }

    fun toggleFavorite() {
        val current = _place.value ?: return
        viewModelScope.launch {
            placeRepository.toggleFavorite(current.id)
            _place.value = placeRepository.getPlaceById(current.id)
            val msg = if (_place.value?.isFavorite == true) "added_to_favorites" else "removed_from_favorites"
            _snackbarEvent.tryEmit(msg)
        }
    }

    fun toggleVisited() {
        val current = _place.value ?: return
        viewModelScope.launch {
            placeRepository.toggleVisited(current.id)
            _place.value = placeRepository.getPlaceById(current.id)
            val msg = if (_place.value?.isVisited == true) "marked_visited" else "unmarked_visited"
            _snackbarEvent.tryEmit(msg)
        }
    }

    fun translateDescriptionIfNeeded() {
        val currentPlace = _place.value ?: return
        val sourceText = currentPlace.description?.takeIf { it.isNotBlank() } ?: return
        val targetLang = _selectedLanguage.value

        if (targetLang == "uk") {
            _translatedDescription.value = sourceText
            return
        }

        viewModelScope.launch {
            val cacheKey = "place_${currentPlace.id}_description"
            val syncEntity = "translation_$cacheKey"
            val cached = localizationRepository.getValue(cacheKey, targetLang)
            if (!cached.isNullOrBlank()) {
                _translatedDescription.value = cached
                return@launch
            }

            _isTranslating.value = true
            syncStateRepository.setRunning(syncEntity)
            val translatedResult = runCatching {
                translationService.translateText(
                    text = sourceText,
                    sourceLanguageIso = "uk",
                    targetLanguageIso = targetLang
                )
            }

            val translated = translatedResult.getOrDefault(sourceText)
            translatedResult.onSuccess {
                syncStateRepository.setSuccess(syncEntity)
            }.onFailure { throwable ->
                syncStateRepository.setError(syncEntity, throwable.message)
            }

            _translatedDescription.value = translated
            localizationRepository.upsertValue(
                key = cacheKey,
                locale = targetLang,
                value = translated,
                source = "mlkit"
            )
            _isTranslating.value = false
        }
    }

    fun openOnMap() {
        val current = _place.value ?: return
        viewModelScope.launch {
            userPreferenceRepository.upsert("map.focus.placeId", current.id.toString())
        }
    }

    fun addToActiveRoute() {
        val currentPlace = _place.value ?: return
        viewModelScope.launch {
            val syncEntity = "route_add_place_${currentPlace.id}"
            syncStateRepository.setRunning(syncEntity)
            val routes = routeRepository.getAllRoutes().first()
            val active = routes.firstOrNull() ?: return@launch

            val updated = RouteMetricsCalculator.withAppendedWaypoint(
                route = active,
                waypoint = RoutePoint(
                    latitude = currentPlace.latitude,
                    longitude = currentPlace.longitude,
                    name = currentPlace.name
                )
            )
            runCatching {
                routeRepository.updateRoute(updated)
            }.onSuccess {
                syncStateRepository.setSuccess(syncEntity)
                _snackbarEvent.tryEmit("added_to_route")
            }.onFailure { throwable ->
                syncStateRepository.setError(syncEntity, throwable.message)
                _snackbarEvent.tryEmit("route_add_failed")
            }
        }
    }
}
