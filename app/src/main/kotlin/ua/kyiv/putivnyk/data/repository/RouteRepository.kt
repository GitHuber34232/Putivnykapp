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
