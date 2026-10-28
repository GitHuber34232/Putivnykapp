package ua.kyiv.putivnyk.domain.usecase.routing

import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import java.time.LocalDate
import kotlin.random.Random

/**
 * "Route of the day": a short walk through 3-5 well-rated places close to each other.
 * The choice is deterministic for a date, so the widget and the home screen agree all day,
 * and places the user has not visited yet are preferred.
 */
object DailyRouteGenerator {
    /** Walking distance is longer than the straight line between stops. */
    private const val WALKING_DETOUR_FACTOR = 1.3
    private const val SEED_POOL = 30
    private const val ATTEMPTS = 6

    private val excludedCategories = setOf(PlaceCategory.TOILET, PlaceCategory.RESTAURANT, PlaceCategory.DISTRICT)

    data class DailyRoute(
        val date: LocalDate,
        val places: List<Place>,
        val distanceMeters: Double,
        val walkingMinutes: Int
    )

    fun generate(
        places: List<Place>,
        date: LocalDate,
        maxStops: Int = 4,
        maxLegMeters: Double = 1_400.0
    ): DailyRoute? {
        val candidates = places.filter { it.category !in excludedCategories }.distinctBy { it.id }
        if (candidates.size < 3) return null
        val seeds = candidates
            .sortedWith(compareByDescending<Place> { it.popularity }.thenByDescending { it.rating ?: 0.0 })
            .take(SEED_POOL)
        val random = Random(date.toEpochDay() * 31 + candidates.size)

        var best: List<Place> = emptyList()
        repeat(ATTEMPTS) {
            val start = seeds[random.nextInt(seeds.size)]
            val chain = buildChain(start, candidates, maxStops, maxLegMeters)
            if (chain.size > best.size) best = chain
            if (best.size >= maxStops) return@repeat
        }
        if (best.size < 3) return null
        return fromPlaces(date, best)
    }

    /** A daily route through [places] in this order (used for the route already chosen today). */
    fun fromPlaces(date: LocalDate, places: List<Place>): DailyRoute {
        val straight = places.zipWithNext().sumOf { (a, b) ->
            NativeGeoEngine.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        val walking = straight * WALKING_DETOUR_FACTOR
        return DailyRoute(
            date = date,
            places = places,
            distanceMeters = walking,
            walkingMinutes = RouteNavigationMetrics.estimateDurationMinutes(walking)
        )
    }

    private fun buildChain(start: Place, candidates: List<Place>, maxStops: Int, maxLegMeters: Double): List<Place> {
        val chain = mutableListOf(start)
        while (chain.size < maxStops) {
            val last = chain.last()
            val next = candidates
                .asSequence()
                .filter { candidate -> chain.none { it.id == candidate.id } }
                .map { candidate ->
                    candidate to NativeGeoEngine.distanceMeters(last.latitude, last.longitude, candidate.latitude, candidate.longitude)
                }
                .filter { (_, meters) -> meters in 120.0..maxLegMeters }
                .maxByOrNull { (candidate, meters) -> score(candidate, meters, chain) }
                ?.first
                ?: break
            chain += next
        }
        return chain
    }

    private fun score(candidate: Place, legMeters: Double, chain: List<Place>): Double {
        val popularity = candidate.popularity / 100.0
        val rating = (candidate.rating ?: 3.5) / 5.0
        val unvisited = if (candidate.isVisited) 0.0 else 0.35
        // Different kinds of places make a more interesting walk.
        val variety = if (chain.any { it.category == candidate.category }) 0.0 else 0.2
        return popularity + rating + unvisited + variety - legMeters / 1_000.0
    }
}
