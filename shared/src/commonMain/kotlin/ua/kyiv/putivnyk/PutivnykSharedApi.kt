package ua.kyiv.putivnyk

import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.domain.usecase.recommendation.RecommendationEngine
import ua.kyiv.putivnyk.domain.usecase.routing.RouteMetricsCalculator
import ua.kyiv.putivnyk.domain.usecase.routing.RouteOptimizer
import ua.kyiv.putivnyk.domain.usecase.routing.SmartRouteBuilder
import ua.kyiv.putivnyk.i18n.LanguageInfo
import ua.kyiv.putivnyk.i18n.SupportedLanguages

class PutivnykSharedApi(
    private val routeOptimizer: RouteOptimizer = RouteOptimizer(),
    private val recommendationEngine: RecommendationEngine = RecommendationEngine(),
    private val smartRouteBuilder: SmartRouteBuilder = SmartRouteBuilder(routeOptimizer)
) {
    fun supportedLanguages(): List<LanguageInfo> = SupportedLanguages.majorIso639_1

    fun recommendPlaces(
        allPlaces: List<Place>,
        favorites: List<Place>,
        excludeIds: Set<Long> = emptySet(),
        userLat: Double? = null,
        userLon: Double? = null,
        limit: Int = 50
    ): List<Place> = recommendationEngine.recommend(
        allPlaces = allPlaces,
        favorites = favorites,
        excludeIds = excludeIds,
        userLat = userLat,
        userLon = userLon,
        limit = limit
    )

    fun buildRoute(
        request: SmartRouteBuilder.RouteRequest,
        allPlaces: List<Place>,
        favorites: List<Place>
    ): Route? = smartRouteBuilder.buildRoute(request, allPlaces, favorites)

    fun optimizeRoute(route: Route): Route = routeOptimizer.optimizeWaypoints(route)

    fun buildOptimalRoute(
        name: String,
        places: List<RoutePoint>,
        description: String? = null
    ): Route? = routeOptimizer.buildOptimalRoute(name, places, description)

    fun validateRoute(route: Route): List<String> = RouteMetricsCalculator.validateRoute(route)

    fun similarPlaces(target: Place, allPlaces: List<Place>, limit: Int = 10): List<Place> =
        recommendationEngine.findSimilar(target, allPlaces, limit)
}