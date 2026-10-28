package ua.kyiv.putivnyk.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.data.model.MapPreferenceKeys
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.repository.LocalizationRepository
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.repository.StatsRepository
import ua.kyiv.putivnyk.data.repository.SyncStateRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.domain.usecase.routing.RouteMetricsCalculator
import ua.kyiv.putivnyk.i18n.OnDeviceTranslationService
import ua.kyiv.putivnyk.i18n.SupportedLanguages
import ua.kyiv.putivnyk.tracking.VisitTracker
import javax.inject.Inject
import java.util.Locale

@HiltViewModel
class LocationDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val placeRepository: PlaceRepository,
    private val localizationRepository: LocalizationRepository,
    private val translationService: OnDeviceTranslationService,
    private val routeRepository: RouteRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val syncStateRepository: SyncStateRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    // The nav argument is declared as NavType.LongType, so the handle stores a Long.
    private val placeId: Long = when (val raw = savedStateHandle.get<Any>("placeId")) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull() ?: 0L
        else -> 0L
    }

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

    private var translationJob: Job? = null

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
            // Manual marks count in the walk statistics like automatic ones.
            runCatching {
                if (_place.value?.isVisited == true) {
                    statsRepository.recordVisit(current.id, VisitTracker.SOURCE_MANUAL)
                } else {
                    statsRepository.removeVisits(current.id)
                }
            }
            val msg = if (_place.value?.isVisited == true) "marked_visited" else "unmarked_visited"
            _snackbarEvent.tryEmit(msg)
        }
    }

    fun translateDescriptionIfNeeded() {
        val currentPlace = _place.value ?: return
        val sourceText = currentPlace.description?.takeIf { it.isNotBlank() } ?: return
        val targetLang = _selectedLanguage.value

        translationJob?.cancel()
        if (targetLang == "uk") {
            _isTranslating.value = false
            _translatedDescription.value = sourceText
            return
        }

        translationJob = viewModelScope.launch {
            val cacheKey = "place_${currentPlace.id}_description"
            val syncEntity = "translation_$cacheKey"
            val cached = localizationRepository.getValue(cacheKey, targetLang)
            if (!cached.isNullOrBlank() && cached != sourceText) {
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

            val translated = translatedResult.getOrNull()
            if (translated != null && translated.isNotBlank() && translated != sourceText) {
                syncStateRepository.setSuccess(syncEntity)
                // Only real translations are cached, so a failed (offline) attempt is retried next time.
                localizationRepository.upsertValue(
                    key = cacheKey,
                    locale = targetLang,
                    value = translated,
                    source = "mlkit"
                )
                _translatedDescription.value = translated
            } else {
                syncStateRepository.setError(syncEntity, translatedResult.exceptionOrNull()?.message)
                _translatedDescription.value = sourceText
                _snackbarEvent.tryEmit("translation_failed")
            }
            _isTranslating.value = false
        }
    }

    fun openOnMap(onReady: () -> Unit) {
        val current = _place.value ?: return
        viewModelScope.launch {
            userPreferenceRepository.upsert(MapPreferenceKeys.FOCUS_PLACE_ID, current.id.toString())
            onReady()
        }
    }

    fun requestRouteHere(onReady: () -> Unit) {
        val current = _place.value ?: return
        viewModelScope.launch {
            userPreferenceRepository.upsert(MapPreferenceKeys.PENDING_ROUTE_TO_PLACE_ID, current.id.toString())
            onReady()
        }
    }

    fun addToActiveRoute() {
        val currentPlace = _place.value ?: return
        viewModelScope.launch {
            val activeRouteId = userPreferenceRepository
                .getString(MapPreferenceKeys.ACTIVE_ROUTE_ID, "")
                .toLongOrNull()
            val active = activeRouteId?.let { routeRepository.getRouteById(it) }
            if (active == null) {
                _snackbarEvent.tryEmit("no_active_route")
                return@launch
            }

            val syncEntity = "route_add_place_${currentPlace.id}"
            syncStateRepository.setRunning(syncEntity)
            val updated = RouteMetricsCalculator.withAppendedWaypoint(
                route = active,
                waypoint = RoutePoint(
                    latitude = currentPlace.latitude,
                    longitude = currentPlace.longitude,
                    name = currentPlace.name
                )
            )
            if (updated == active) {
                syncStateRepository.setSuccess(syncEntity)
                _snackbarEvent.tryEmit("already_in_route")
                return@launch
            }
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
