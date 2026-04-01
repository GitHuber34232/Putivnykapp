package ua.kyiv.putivnyk.data.remote

import ua.kyiv.putivnyk.data.model.RoutePoint

interface WalkingDirectionsProvider {
    suspend fun fetchWalkingRoute(waypoints: List<RoutePoint>): List<RoutePoint> =
        fetchWalkingRouteResult(waypoints).geometry

    suspend fun fetchWalkingRouteResult(waypoints: List<RoutePoint>): WalkingRouteResult
}