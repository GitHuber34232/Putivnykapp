package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import ua.kyiv.putivnyk.data.model.LocalizedString
import ua.kyiv.putivnyk.data.model.MapBookmark
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.SyncState
import ua.kyiv.putivnyk.data.model.SyncStatus
import ua.kyiv.putivnyk.data.model.UserPreference
import ua.kyiv.putivnyk.platform.currentTimeMillis
import ua.kyiv.putivnyk.platform.io.FileSystemProvider
import ua.kyiv.putivnyk.storage.JsonFileStore

private const val IOS_DATA_DIR = "ios_repository_store"

class IosPlaceRepository(fileSystemProvider: FileSystemProvider) : PlaceRepository {
    private val store = JsonFileStore(
        fileSystemProvider = fileSystemProvider,
        path = "$IOS_DATA_DIR/places.json",
        serializer = ListSerializer(Place.serializer()),
        defaultValue = { emptyList() }
    )

    override fun getAllPlaces(): Flow<List<Place>> = store.observe()

    override fun getFavoritePlaces(): Flow<List<Place>> = store.observe().map { places -> places.filter { it.isFavorite } }

    override fun getPlacesByCategory(category: PlaceCategory): Flow<List<Place>> =
        store.observe().map { places -> places.filter { it.category == category } }

    override suspend fun getPlaceById(id: Long): Place? = store.snapshot().firstOrNull { it.id == id }

    override suspend fun savePlace(place: Place): Long {
        val assignedId = nextPlaceId()
        store.update { current -> current + place.copy(id = assignedId) }
        return assignedId
    }

    override suspend fun savePlaces(places: List<Place>) {
        store.update { current ->
            var nextId = current.maxOfOrNull { it.id } ?: 0L
            current + places.map { place ->
                val assigned = if (place.id > 0) place.id else ++nextId
                place.copy(id = assigned)
            }
        }
    }

    override suspend fun updatePlace(place: Place) {
        store.update { current -> current.map { if (it.id == place.id) place.copy(updatedAt = currentTimeMillis()) else it } }
    }

    override suspend fun deletePlace(place: Place) {
        store.update { current -> current.filterNot { it.id == place.id } }
    }

    override suspend fun deleteAllPlaces() {
        store.set(emptyList())
    }

    override suspend fun getPlacesCount(): Int = store.snapshot().size

    override suspend fun toggleFavorite(placeId: Long) {
        store.update { current ->
            current.map { place ->
                if (place.id == placeId) place.copy(isFavorite = !place.isFavorite, updatedAt = currentTimeMillis()) else place
            }
        }
    }

    override suspend fun toggleVisited(placeId: Long) {
        store.update { current ->
            current.map { place ->
                if (place.id == placeId) place.copy(isVisited = !place.isVisited, updatedAt = currentTimeMillis()) else place
            }
        }
    }

    override suspend fun getAllPlacesSnapshot(): List<Place> = store.snapshot()

    override suspend fun searchPlaces(query: String): List<Place> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return store.snapshot()
        return store.snapshot().filter { place ->
            place.name.lowercase().contains(normalized) ||
                (place.nameEn?.lowercase()?.contains(normalized) == true) ||
                (place.description?.lowercase()?.contains(normalized) == true) ||
                place.tags.any { it.lowercase().contains(normalized) }
        }
    }

    private fun nextPlaceId(): Long = (store.snapshot().maxOfOrNull { it.id } ?: 0L) + 1
}

class IosRouteRepository(fileSystemProvider: FileSystemProvider) : RouteRepository {
    private val store = JsonFileStore(
        fileSystemProvider = fileSystemProvider,
        path = "$IOS_DATA_DIR/routes.json",
        serializer = ListSerializer(Route.serializer()),
        defaultValue = { emptyList() }
    )

    override fun getAllRoutes(): Flow<List<Route>> = store.observe()

    override fun getFavoriteRoutes(): Flow<List<Route>> = store.observe().map { routes -> routes.filter { it.isFavorite } }

    override suspend fun getRouteById(id: Long): Route? = store.snapshot().firstOrNull { it.id == id }

    override suspend fun saveRoute(route: Route): Long {
        val assignedId = (store.snapshot().maxOfOrNull { it.id } ?: 0L) + 1
        store.update { current -> current + route.copy(id = assignedId) }
        return assignedId
    }

    override suspend fun updateRoute(route: Route) {
        store.update { current -> current.map { if (it.id == route.id) route.copy(updatedAt = currentTimeMillis()) else it } }
    }

    override suspend fun deleteRoute(route: Route) {
        store.update { current -> current.filterNot { it.id == route.id } }
    }

    override suspend fun deleteAllRoutes() {
        store.set(emptyList())
    }

    override suspend fun getAllRoutesSnapshot(): List<Route> = store.snapshot()

    override suspend fun toggleFavorite(routeId: Long) {
        store.update { current ->
            current.map { route ->
                if (route.id == routeId) route.copy(isFavorite = !route.isFavorite, updatedAt = currentTimeMillis()) else route
            }
        }
    }

    override suspend fun exportRoutesJson(): String = kotlinx.serialization.json.Json.encodeToString(
        ListSerializer(Route.serializer()),
        store.snapshot()
    )

    override suspend fun importRoutesJson(json: String): Int {
        val imported = runCatching {
            kotlinx.serialization.json.Json.decodeFromString(ListSerializer(Route.serializer()), json)
        }.getOrDefault(emptyList())
        if (imported.isEmpty()) return 0

        var importedCount = 0
        store.update { current ->
            var nextId = current.maxOfOrNull { it.id } ?: 0L
            val appended = imported.mapNotNull { route ->
                if (route.name.isBlank()) return@mapNotNull null
                importedCount++
                route.copy(id = ++nextId, createdAt = currentTimeMillis(), updatedAt = currentTimeMillis())
            }
            current + appended
        }
        return importedCount
    }
}

class IosUserPreferenceRepository(fileSystemProvider: FileSystemProvider) : UserPreferenceRepository {
    private val store = JsonFileStore(
        fileSystemProvider = fileSystemProvider,
        path = "$IOS_DATA_DIR/preferences.json",
        serializer = ListSerializer(UserPreference.serializer()),
        defaultValue = { emptyList() }
    )

    override fun observeAll(): Flow<List<UserPreference>> = store.observe()

    override fun observeAsMap(): Flow<Map<String, String>> = store.observe().map { prefs -> prefs.associate { it.key to it.value } }

    override suspend fun getByKey(key: String): UserPreference? = store.snapshot().firstOrNull { it.key == key }

    override suspend fun getString(key: String, defaultValue: String): String = getByKey(key)?.value ?: defaultValue

    override suspend fun upsert(key: String, value: String) {
        val updated = UserPreference(key = key, value = value, updatedAt = currentTimeMillis())
        store.update { current -> current.filterNot { it.key == key } + updated }
    }

    override suspend fun deleteByKey(key: String) {
        store.update { current -> current.filterNot { it.key == key } }
    }

    override suspend fun getAllAsMap(): Map<String, String> = store.snapshot().associate { it.key to it.value }

    override suspend fun clearAll() {
        store.set(emptyList())
    }
}

class IosLocalizationRepository(fileSystemProvider: FileSystemProvider) : LocalizationRepository {
    private val store = JsonFileStore(
        fileSystemProvider = fileSystemProvider,
        path = "$IOS_DATA_DIR/localization.json",
        serializer = ListSerializer(LocalizedString.serializer()),
        defaultValue = { emptyList() }
    )

    override fun observeByLocale(locale: String): Flow<Map<String, String>> =
        store.observe().map { entries ->
            entries.filter { it.locale == locale }.associate { it.key to it.value }
        }

    override suspend fun getByLocale(locale: String): List<LocalizedString> = store.snapshot().filter { it.locale == locale }

    override suspend fun upsertAll(locale: String, values: Map<String, String>, source: String) {
        store.update { current ->
            val filtered = current.filterNot { it.locale == locale && it.key in values.keys }
            filtered + values.map { (key, value) ->
                LocalizedString(key = key, locale = locale, value = value, source = source, updatedAt = currentTimeMillis())
            }
        }
    }

    override suspend fun getValue(key: String, locale: String): String? =
        store.snapshot().firstOrNull { it.key == key && it.locale == locale }?.value

    override suspend fun upsertValue(key: String, locale: String, value: String, source: String) {
        upsertAll(locale, mapOf(key to value), source)
    }

    override suspend fun clearLocale(locale: String) {
        store.update { current -> current.filterNot { it.locale == locale } }
    }

    override suspend fun clearAll() {
        store.set(emptyList())
    }
}

class IosSyncStateRepository(fileSystemProvider: FileSystemProvider) : SyncStateRepository {
    private val store = JsonFileStore(
        fileSystemProvider = fileSystemProvider,
        path = "$IOS_DATA_DIR/sync-state.json",
        serializer = ListSerializer(SyncState.serializer()),
        defaultValue = { emptyList() }
    )

    override fun observeAll(): Flow<List<SyncState>> = store.observe()

    override suspend fun getByEntity(entityName: String): SyncState? = store.snapshot().firstOrNull { it.entityName == entityName }

    override suspend fun setRunning(entityName: String) {
        upsert(entityName, SyncStatus.RUNNING, null, 0L)
    }

    override suspend fun setSuccess(entityName: String, syncedAt: Long) {
        upsert(entityName, SyncStatus.SUCCESS, null, syncedAt)
    }

    override suspend fun setError(entityName: String, message: String?) {
        upsert(entityName, SyncStatus.ERROR, message, 0L)
    }

    override suspend fun clearAll() {
        store.set(emptyList())
    }

    private suspend fun upsert(entityName: String, status: SyncStatus, error: String?, lastSyncAt: Long) {
        val updated = SyncState(
            entityName = entityName,
            status = status,
            lastError = error,
            lastSyncAt = lastSyncAt,
            updatedAt = currentTimeMillis()
        )
        store.update { current -> current.filterNot { it.entityName == entityName } + updated }
    }
}

class IosMapBookmarkRepository(fileSystemProvider: FileSystemProvider) : MapBookmarkRepository {
    private val store = JsonFileStore(
        fileSystemProvider = fileSystemProvider,
        path = "$IOS_DATA_DIR/bookmarks.json",
        serializer = ListSerializer(MapBookmark.serializer()),
        defaultValue = { emptyList() }
    )

    override fun observeAll(): Flow<List<MapBookmark>> = store.observe()

    override suspend fun getById(id: Long): MapBookmark? = store.snapshot().firstOrNull { it.id == id }

    override suspend fun save(bookmark: MapBookmark): Long {
        val assignedId = (store.snapshot().maxOfOrNull { it.id } ?: 0L) + 1
        store.update { current -> current + bookmark.copy(id = assignedId) }
        return assignedId
    }

    override suspend fun update(bookmark: MapBookmark) {
        store.update { current -> current.map { if (it.id == bookmark.id) bookmark.copy(updatedAt = currentTimeMillis()) else it } }
    }

    override suspend fun delete(bookmark: MapBookmark) {
        store.update { current -> current.filterNot { it.id == bookmark.id } }
    }

    override suspend fun deleteAll() {
        store.set(emptyList())
    }
}