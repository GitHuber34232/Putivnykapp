package ua.kyiv.putivnyk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.data.model.MapPreferenceKeys
import ua.kyiv.putivnyk.data.model.SyncStatus
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.remote.events.EventsBackendRepository
import ua.kyiv.putivnyk.data.remote.events.model.EventItem
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.repository.SyncStateRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.domain.usecase.routing.RouteMetricsCalculator
import ua.kyiv.putivnyk.sync.EventsSyncWorker
import ua.kyiv.putivnyk.sync.SyncScheduler
import ua.kyiv.putivnyk.ui.utils.EventTimeFormatter
import java.util.Locale
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

    private val syncEntity = EventsSyncWorker.SYNC_ENTITY
    private val staleThresholdMs = 24 * 60 * 60 * 1000L

    // True from the start: init loads right away, so the screen never flashes the empty state.
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _sortMode = MutableStateFlow(EventSortMode.DATE)
    val sortMode: StateFlow<EventSortMode> = _sortMode.asStateFlow()

    private val _events = MutableStateFlow<List<EventItem>>(emptyList())
    private val _selectedEvent = MutableStateFlow<EventItem?>(null)
    val selectedEvent: StateFlow<EventItem?> = _selectedEvent.asStateFlow()

    private val _snackbarEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    private var refreshJob: Job? = null

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
            current != null && current.lastSyncAt != 0L &&
                System.currentTimeMillis() - current.lastSyncAt > staleThresholdMs
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
        val normalizedQuery = query.trim()
        if (normalizedQuery.isNotBlank()) {
            filtered = filtered.filter { item ->
                item.title.contains(normalizedQuery, ignoreCase = true) ||
                    item.description.contains(normalizedQuery, ignoreCase = true) ||
                    item.category.contains(normalizedQuery, ignoreCase = true) ||
                    item.locationName.contains(normalizedQuery, ignoreCase = true)
            }
        }

        if (!selectedCategory.isNullOrBlank()) {
            filtered = filtered.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }

        when (sortMode) {
            EventSortMode.DATE -> sortUpcomingFirst(filtered)
            EventSortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
            EventSortMode.CATEGORY -> filtered.sortedBy { it.category.lowercase() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            val cached = eventsRepository.cachedEvents(resolvePreferredLanguage())
            if (cached != null && _events.value.isEmpty()) {
                _events.value = cached.items
            }
            refresh()
        }
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
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _isLoading.value = true
            syncStateRepository.setRunning(syncEntity)
            try {
                val list = eventsRepository.refreshEvents(resolvePreferredLanguage())
                _events.value = list
                _loadFailed.value = false
                syncStateRepository.setSuccess(syncEntity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep showing the cached list; the screen offers a retry.
                _loadFailed.value = true
                syncStateRepository.setError(syncEntity, e.message)
            } finally {
                _isLoading.value = false
            }
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
        refresh()
    }

    fun selectEvent(event: EventItem?) {
        _selectedEvent.value = event
    }

    fun openSelectedEventOnMap(onReady: () -> Unit) {
        val event = _selectedEvent.value ?: return
        val lat = event.latitude ?: return
        val lon = event.longitude ?: return

        viewModelScope.launch {
            userPreferenceRepository.deleteByKey(MapPreferenceKeys.FOCUS_PLACE_ID)
            userPreferenceRepository.upsert(MapPreferenceKeys.FOCUS_ZOOM, "16")
            userPreferenceRepository.upsert(MapPreferenceKeys.FOCUS_LON, lon.toString())
            userPreferenceRepository.upsert(MapPreferenceKeys.FOCUS_LAT, lat.toString())
            onReady()
        }
    }

    fun addSelectedEventToActiveRoute() {
        val event = _selectedEvent.value ?: return
        val lat = event.latitude ?: return
        val lon = event.longitude ?: return

        viewModelScope.launch {
            val activeRouteId = userPreferenceRepository
                .getString(MapPreferenceKeys.ACTIVE_ROUTE_ID, "")
                .toLongOrNull()
            val active = activeRouteId?.let { routeRepository.getRouteById(it) }
            if (active == null) {
                _snackbarEvent.tryEmit("no_active_route")
                return@launch
            }
            val updated = RouteMetricsCalculator.withAppendedWaypoint(
                route = active,
                waypoint = RoutePoint(
                    latitude = lat,
                    longitude = lon,
                    name = event.title
                )
            )
            if (updated == active) {
                _snackbarEvent.tryEmit("already_in_route")
                return@launch
            }
            routeRepository.updateRoute(updated)
            _snackbarEvent.tryEmit("added_to_route")
        }
    }

    private fun sortUpcomingFirst(items: List<EventItem>): List<EventItem> {
        val now = System.currentTimeMillis()
        val withTime = items.map { item ->
            val start = EventTimeFormatter.parseToEpochMillis(item.startsAt)
            val end = EventTimeFormatter.parseToEpochMillis(item.endsAt) ?: start
            Triple(item, start, end)
        }
        val (finished, current) = withTime.partition { (_, _, end) -> end != null && end < now }
        val upcoming = current.sortedWith(compareBy(nullsLast()) { (_, start, _) -> start })
        val past = finished.sortedByDescending { (_, start, _) -> start }
        return (upcoming + past).map { it.first }
    }
}
