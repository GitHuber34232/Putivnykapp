package ua.kyiv.putivnyk.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.kyiv.putivnyk.data.local.dao.RouteDao
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.toDomain
import ua.kyiv.putivnyk.data.model.toEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRouteRepository @Inject constructor(
    private val routeDao: RouteDao,
    private val gson: Gson
) : RouteRepository {

    override
    fun getAllRoutes(): Flow<List<Route>> =
        routeDao.getAllRoutes().map { entities ->
            entities.map { it.toDomain() }
        }

    override
    fun getFavoriteRoutes(): Flow<List<Route>> =
        routeDao.getFavoriteRoutes().map { entities ->
            entities.map { it.toDomain() }
        }

    override
    suspend fun getRouteById(id: Long): Route? =
        routeDao.getRouteById(id)?.toDomain()

    override
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

    override
    suspend fun updateRoute(route: Route) {
        require(route.startPoint.latitude in -90.0..90.0 && route.startPoint.longitude in -180.0..180.0) {
            "Invalid start coordinates"
        }
        routeDao.updateRoute(
            route.copy(updatedAt = System.currentTimeMillis()).toEntity()
        )
    }

    override
    suspend fun deleteRoute(route: Route) =
        routeDao.deleteRoute(route.toEntity())

    override
    suspend fun deleteAllRoutes() =
        routeDao.deleteAllRoutes()

    override
    suspend fun getAllRoutesSnapshot(): List<Route> =
        getAllRoutes().first()

    override
    suspend fun toggleFavorite(routeId: Long) {
        val route = getRouteById(routeId) ?: return
        updateRoute(route.copy(isFavorite = !route.isFavorite))
    }

    override
    suspend fun exportRoutesJson(): String {
        val routes = routeDao.observeAll().first().map { it.toDomain() }
        return gson.toJson(routes)
    }

    override
    suspend fun importRoutesJson(json: String): Int {
        val listType = object : TypeToken<List<Route>>() {}.type
        val imported = runCatching {
            gson.fromJson<List<Route>>(json, listType).orEmpty()
        }.getOrDefault(emptyList())

        if (imported.isEmpty()) return 0

        val sanitized = imported.filter { route ->
            route.name.isNotBlank() &&
                route.startPoint.latitude in -90.0..90.0 &&
                route.startPoint.longitude in -180.0..180.0 &&
                route.endPoint.latitude in -90.0..90.0 &&
                route.endPoint.longitude in -180.0..180.0
        }

        if (sanitized.isEmpty()) return 0

        sanitized.forEach { route ->
            saveRoute(
                route.copy(
                    id = 0,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return sanitized.size
    }
}
