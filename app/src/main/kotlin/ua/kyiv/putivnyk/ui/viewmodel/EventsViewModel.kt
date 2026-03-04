package ua.kyiv.putivnyk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.data.model.SyncStatus
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.remote.events.EventsBackendRepository
import ua.kyiv.putivnyk.data.remote.events.model.EventItem
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.repository.SyncStateRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.domain.usecase.routing.RouteMetricsCalculator
import java.util.Locale
import ua.kyiv.putivnyk.sync.EventsSyncWorker
import ua.kyiv.putivnyk.sync.SyncScheduler
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventsRepository: EventsBackendRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val routeRepository: RouteRepository,
    private val syncStateRepository: SyncStateRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val supportedEventLanguages = setOf("uk", "en")

    enum class EventSortMode {
        DATE,
        TITLE,
        CATEGORY
    }

    private val syncEntity = "events_backend"
    private val staleThresholdMs = 24 * 60 * 60 * 1000L

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _sortMode = MutableStateFlow(EventSortMode.DATE)
    val sortMode: StateFlow<EventSortMode> = _sortMode.asStateFlow()

    private val _events = MutableStateFlow<List<EventItem>>(emptyList())
    private val _selectedEvent = MutableStateFlow<EventItem?>(null)
    val selectedEvent: StateFlow<EventItem?> = _selectedEvent.asStateFlow()

    val availableCategories: StateFlow<List<String>> = _events
        .map { list ->
            list.map { it.category.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val syncStatusLabel: StateFlow<String> = syncStateRepository.observeAll()
        .map { states ->
            val current = states.firstOrNull { it.entityName == syncEntity }
            when (current?.status ?: SyncStatus.IDLE) {
                SyncStatus.IDLE -> "events.sync_idle"
                SyncStatus.RUNNING -> "events.sync_running"
                SyncStatus.SUCCESS -> "events.sync_success"
                SyncStatus.ERROR -> "events.sync_error"
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "events.sync_idle"
        )

    val freshnessLabel: StateFlow<String> = syncStateRepository.observeAll()
        .map { states ->
            val current = states.firstOrNull { it.entityName == syncEntity }
            when {
                current == null || current.lastSyncAt == 0L -> "events.freshness_unknown"
                System.currentTimeMillis() - current.lastSyncAt > staleThresholdMs -> "events.freshness_stale"
                else -> "events.freshness_ok"
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "events.freshness_unknown"
        )

    val isDataStale: StateFlow<Boolean> = syncStateRepository.observeAll()
        .map { states ->
            val current = states.firstOrNull { it.entityName == syncEntity }
            current == null || current.lastSyncAt == 0L || (System.currentTimeMillis() - current.lastSyncAt > staleThresholdMs)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val events: StateFlow<List<EventItem>> = combine(
        _events,
        _searchQuery,
        _selectedCategory,
        _sortMode
    ) { list, query, selectedCategory, sortMode ->
        var filtered = list
        if (query.isNotBlank()) {
            filtered = filtered.filter { item ->
            item.title.contains(query, ignoreCase = true) ||
                item.description.contains(query, ignoreCase = true) ||
                item.category.contains(query, ignoreCase = true) ||
                item.locationName.contains(query, ignoreCase = true)
            }
        }

        if (!selectedCategory.isNullOrBlank()) {
            filtered = filtered.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }

        when (sortMode) {
            EventSortMode.DATE -> filtered.sortedByDescending { it.startsAt }
            EventSortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
            EventSortMode.CATEGORY -> filtered.sortedBy { it.category.lowercase() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        refresh()
    }

    fun updateSearchQuery(value: String) {
        _searchQuery.value = value
    }

    fun setCategory(category: String?) {
        _selectedCategory.value = category?.takeIf { it.isNotBlank() }
    }

    fun setSortMode(mode: EventSortMode) {
        _sortMode.value = mode
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            syncStateRepository.setRunning(syncEntity)
            val preferredLanguage = resolvePreferredLanguage()
            val result = runCatching { eventsRepository.getEvents(preferredLanguage) }
            result.onSuccess { list ->
                _events.value = list
                syncStateRepository.setSuccess(syncEntity)
            }.onFailure { throwable ->
                syncStateRepository.setError(syncEntity, throwable.message)
            }
            _isLoading.value = false
        }
    }

    private suspend fun resolvePreferredLanguage(): String {
        val mode = userPreferenceRepository.getString("ui.lang.mode", "auto")
        val manual = userPreferenceRepository.getString("ui.lang.manual", "uk").ifBlank { "uk" }
        val preferred = if (mode == "manual") {
            manual.lowercase()
        } else {
            Locale.getDefault().language.ifBlank { "uk" }.lowercase()
        }
        return if (preferred in supportedEventLanguages) preferred else "uk"
    }

    fun retrySyncInBackground() {
        SyncScheduler.scheduleEventsRetry(workManager)
    }

    fun selectEvent(event: EventItem?) {
        _selectedEvent.value = event
    }

    fun openSelectedEventOnMap() {
        val event = _selectedEvent.value ?: return
        val lat = event.latitude ?: return
        val lon = event.longitude ?: return

        viewModelScope.launch {
            userPreferenceRepository.upsert("map.focus.lat", lat.toString())
            userPreferenceRepository.upsert("map.focus.lon", lon.toString())
            userPreferenceRepository.upsert("map.focus.zoom", "15")
            userPreferenceRepository.deleteByKey("map.focus.placeId")
        }
    }

    fun addSelectedEventToActiveRoute() {
        val event = _selectedEvent.value ?: return
        val lat = event.latitude ?: return
        val lon = event.longitude ?: return

        viewModelScope.launch {
            val routes = routeRepository.getAllRoutes().first()
            val active = routes.firstOrNull() ?: return@launch
            routeRepository.updateRoute(
                RouteMetricsCalculator.withAppendedWaypoint(
                    route = active,
                    waypoint = RoutePoint(
                        latitude = lat,
                        longitude = lon,
                        name = event.title
                    )
                )
            )
        }
    }
}
