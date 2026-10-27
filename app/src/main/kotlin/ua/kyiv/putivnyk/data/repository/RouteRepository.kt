package ua.kyiv.putivnyk.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.kyiv.putivnyk.data.local.dao.RouteDao
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode
import ua.kyiv.putivnyk.data.model.toDomain
import ua.kyiv.putivnyk.data.model.toEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteRepository @Inject constructor(
    private val routeDao: RouteDao,
    private val gson: Gson
) {

    fun getAllRoutes(): Flow<List<Route>> =
        routeDao.getAllRoutes().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getFavoriteRoutes(): Flow<List<Route>> =
        routeDao.getFavoriteRoutes().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getRouteById(id: Long): Route? =
        routeDao.getRouteById(id)?.toDomain()

    suspend fun saveRoute(route: Route): Long {
        require(route.name.isNotBlank()) { "Route name must not be blank" }
        require(route.startPoint.latitude in -90.0..90.0 && route.startPoint.longitude in -180.0..180.0) {
            "Invalid start coordinates"
        }
        require(route.endPoint.latitude in -90.0..90.0 && route.endPoint.longitude in -180.0..180.0) {
            "Invalid end coordinates"
        }
        return routeDao.insertRoute(route.toEntity())
    }

    suspend fun updateRoute(route: Route) {
        require(route.startPoint.latitude in -90.0..90.0 && route.startPoint.longitude in -180.0..180.0) {
            "Invalid start coordinates"
        }
        routeDao.updateRoute(
            route.copy(updatedAt = System.currentTimeMillis()).toEntity()
        )
    }

    suspend fun deleteRoute(route: Route) =
        routeDao.deleteRoute(route.toEntity())

    suspend fun deleteAllRoutes() =
        routeDao.deleteAllRoutes()

    suspend fun getAllRoutesSnapshot(): List<Route> =
        getAllRoutes().first()

    suspend fun toggleFavorite(routeId: Long) {
        val route = getRouteById(routeId) ?: return
        updateRoute(route.copy(isFavorite = !route.isFavorite))
    }

    suspend fun exportRoutesJson(): String {
        val routes = routeDao.observeAll().first().map { it.toDomain() }
        return gson.toJson(routes)
    }

    suspend fun importRoutesJson(json: String): Int {
        val listType = object : TypeToken<List<ImportedRoute?>>() {}.type
        val imported = runCatching {
            gson.fromJson<List<ImportedRoute?>>(json, listType).orEmpty()
        }.getOrDefault(emptyList())

        // Files from older versions or other tools may miss fields, so every value is checked.
        val sanitized = imported.mapNotNull { it?.toRouteOrNull() }
        if (sanitized.isEmpty()) return 0

        val now = System.currentTimeMillis()
        sanitized.forEach { route ->
            saveRoute(route.copy(id = 0, createdAt = now, updatedAt = now))
        }
        return sanitized.size
    }

    /** Shape of exported routes with every field optional, as Gson may leave any of them null. */
    private data class ImportedRoute(
        val name: String? = null,
        val description: String? = null,
        val startPoint: ImportedPoint? = null,
        val endPoint: ImportedPoint? = null,
        val waypoints: List<ImportedPoint?>? = null,
        val distance: Double? = null,
        val estimatedDuration: Int? = null,
        val transportMode: String? = null,
        val isFavorite: Boolean? = null
    ) {
        fun toRouteOrNull(): Route? {
            val routeName = name?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val start = startPoint?.toRoutePointOrNull() ?: return null
            val end = endPoint?.toRoutePointOrNull() ?: return null
            return Route(
                name = routeName,
                description = description,
                startPoint = start,
                endPoint = end,
                waypoints = waypoints.orEmpty().mapNotNull { it?.toRoutePointOrNull() },
                distance = distance?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
                estimatedDuration = estimatedDuration?.coerceAtLeast(0) ?: 0,
                transportMode = TransportMode.entries.firstOrNull {
                    it.name.equals(transportMode, ignoreCase = true) || it.key.equals(transportMode, ignoreCase = true)
                } ?: TransportMode.WALKING,
                isFavorite = isFavorite ?: false
            )
        }
    }

    private data class ImportedPoint(
        val latitude: Double? = null,
        val longitude: Double? = null,
        val name: String? = null
    ) {
        fun toRoutePointOrNull(): RoutePoint? {
            val lat = latitude?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
            val lon = longitude?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
            return RoutePoint(latitude = lat, longitude = lon, name = name?.takeIf { it.isNotBlank() })
        }
    }
}
