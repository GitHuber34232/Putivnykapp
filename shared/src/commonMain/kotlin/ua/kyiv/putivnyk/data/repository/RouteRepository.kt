package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.model.Route

interface RouteRepository {
    fun getAllRoutes(): Flow<List<Route>>
    fun getFavoriteRoutes(): Flow<List<Route>>
    suspend fun getRouteById(id: Long): Route?
    suspend fun saveRoute(route: Route): Long
    suspend fun updateRoute(route: Route)
    suspend fun deleteRoute(route: Route)
    suspend fun deleteAllRoutes()
    suspend fun getAllRoutesSnapshot(): List<Route>
    suspend fun toggleFavorite(routeId: Long)
    suspend fun exportRoutesJson(): String
    suspend fun importRoutesJson(json: String): Int
}