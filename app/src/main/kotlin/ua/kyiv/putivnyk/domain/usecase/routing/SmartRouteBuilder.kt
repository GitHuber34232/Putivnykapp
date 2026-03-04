package ua.kyiv.putivnyk.domain.usecase.routing

import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartRouteBuilder @Inject constructor(
    private val routeOptimizer: RouteOptimizer
) {

    data class RouteRequest(
        val name: String = "Мій маршрут",
        val description: String? = null,
        val selectedPlaces: List<Place> = emptyList(),
        val categories: Set<PlaceCategory> = emptySet(),
        val startPlace: Place? = null,
        val endPlace: Place? = null,
        val maxPlaces: Int = 10,
        val maxDistanceKm: Double = 20.0,
        val preferFavorites: Boolean = true,
        val preferHighRated: Boolean = true,
        val userLat: Double? = null,
        val userLon: Double? = null
    )

    fun buildRoute(
        request: RouteRequest,
        allPlaces: List<Place>,
        favorites: List<Place>
    ): Route? {
        val candidates = selectCandidates(request, allPlaces, favorites)
        if (candidates.size < 2) return null

        val start = request.startPlace?.let { toRoutePoint(it) }
            ?: toRoutePoint(candidates.first())
        val end = request.endPlace?.let { toRoutePoint(it) }
            ?: toRoutePoint(candidates.last())

        val intermediates = candidates
            .filter {
                val sp = toRoutePoint(it)
                sp != start && sp != end
            }
            .map { toRoutePoint(it) }

        val route = Route(
            name = request.name.ifBlank { "Мій маршрут" },
            description = request.description ?: buildDescription(candidates),
            startPoint = start,
            endPoint = end,
            waypoints = intermediates,
            distance = 0.0,
            estimatedDuration = 0
        )

        return routeOptimizer.optimizeWaypoints(route)
    }

    fun suggestPlacesForRoute(
        allPlaces: List<Place>,
        favorites: List<Place>,
        categories: Set<PlaceCategory>,
        userLat: Double?,
        userLon: Double?,
        maxPlaces: Int = 10
    ): List<Place> {
        return selectCandidates(
            RouteRequest(
                categories = categories,
                maxPlaces = maxPlaces,
                userLat = userLat,
                userLon = userLon
            ),
            allPlaces,
            favorites
        )
    }

    private fun selectCandidates(
        request: RouteRequest,
        allPlaces: List<Place>,
        favorites: List<Place>
    ): List<Place> {
        if (request.selectedPlaces.size >= 2) {
            return request.selectedPlaces
                .filter { RouteMetricsCalculator.isValidCoordinate(it.latitude, it.longitude) }
                .take(request.maxPlaces)
        }

        val explicitPlaces = request.selectedPlaces.toMutableList()
        val explicitIds = explicitPlaces.map { it.id }.toSet()
        val favoriteIds = favorites.map { it.id }.toSet()

        val pool = allPlaces.filter { place ->
            place.id !in explicitIds &&
                place.category != PlaceCategory.TOILET &&
                RouteMetricsCalculator.isValidCoordinate(place.latitude, place.longitude) &&
                (request.categories.isEmpty() || place.category in request.categories)
        }

        val scored = pool.map { place ->
            var score = 0.0

            if (request.preferHighRated) {
                score += (place.rating ?: 0.0) * 15.0
            }
            score += place.popularity.coerceAtMost(300) * 0.2

            if (request.preferFavorites && place.id in favoriteIds) {
                score += 100.0
            }

            if (request.userLat != null && request.userLon != null) {
                val dist = NativeGeoEngine.distanceMeters(
                    request.userLat, request.userLon,
                    place.latitude, place.longitude
                ) / 1000.0
                if (dist <= request.maxDistanceKm) {
                    score += (20.0 / (1.0 + dist)).coerceAtMost(20.0)
                } else {
                    score -= 50.0
                }
            }

            if (place.isVisited) score -= 20.0

            place to score
        }

        val needed = request.maxPlaces - explicitPlaces.size
        val selected = scored
            .sortedByDescending { it.second }
            .take(needed.coerceAtLeast(0))
            .map { it.first }

        return explicitPlaces + selected
    }

    private fun buildDescription(places: List<Place>): String {
        val categories = places.map { it.category }.distinct()
        val categoryNames = categories.take(3).joinToString(", ") { it.displayName }
        return "Маршрут через $categoryNames (${places.size} місць)"
    }

    private fun toRoutePoint(place: Place): RoutePoint = RoutePoint(
        latitude = place.latitude,
        longitude = place.longitude,
        name = place.name
    )
}
