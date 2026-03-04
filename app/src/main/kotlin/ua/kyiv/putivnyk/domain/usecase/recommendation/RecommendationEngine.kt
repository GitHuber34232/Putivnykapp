package ua.kyiv.putivnyk.domain.usecase.recommendation

import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationEngine @Inject constructor() {

    data class ScoredPlace(
        val place: Place,
        val score: Double
    )

    fun recommend(
        allPlaces: List<Place>,
        favorites: List<Place>,
        excludeIds: Set<Long> = emptySet(),
        userLat: Double? = null,
        userLon: Double? = null,
        limit: Int = 50
    ): List<Place> {
        val profile = buildUserProfile(favorites, allPlaces.filter { it.isVisited })
        return allPlaces
            .filter {
                it.category != PlaceCategory.TOILET &&
                    it.id !in excludeIds &&
                    it.latitude in -90.0..90.0 &&
                    it.longitude in -180.0..180.0
            }
            .map { place ->
                ScoredPlace(
                    place = place,
                    score = computeScore(place, profile, userLat, userLon)
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.place }
    }

    fun findSimilar(
        target: Place,
        allPlaces: List<Place>,
        limit: Int = 10
    ): List<Place> {
        return allPlaces
            .filter { it.id != target.id && it.category != PlaceCategory.TOILET }
            .map { candidate ->
                ScoredPlace(
                    place = candidate,
                    score = computeSimilarity(target, candidate)
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.place }
    }

    private data class UserProfile(
        val categoryWeights: Map<PlaceCategory, Double>,
        val tagWeights: Map<String, Double>,
        val avgLat: Double?,
        val avgLon: Double?,
        val avgRating: Double,
        val visitedIds: Set<Long>,
        val favoriteIds: Set<Long>
    )

    private fun buildUserProfile(
        favorites: List<Place>,
        visited: List<Place>
    ): UserProfile {
        val interacted = (favorites + visited).distinctBy { it.id }

        val categoryCount = mutableMapOf<PlaceCategory, Int>()
        val tagCount = mutableMapOf<String, Int>()
        interacted.forEach { place ->
            categoryCount[place.category] = (categoryCount[place.category] ?: 0) + 1
            place.tags.forEach { tag ->
                if (!tag.startsWith("fav:")) {
                    tagCount[tag] = (tagCount[tag] ?: 0) + 1
                }
            }
        }

        val maxCatCount = categoryCount.values.maxOrNull()?.toDouble() ?: 1.0
        val categoryWeights = categoryCount.mapValues { (_, count) -> count / maxCatCount }

        val maxTagCount = tagCount.values.maxOrNull()?.toDouble() ?: 1.0
        val tagWeights = tagCount.mapValues { (_, count) -> count / maxTagCount }

        val lats = interacted.map { it.latitude }
        val lons = interacted.map { it.longitude }
        val avgLat = if (lats.isNotEmpty()) lats.average() else null
        val avgLon = if (lons.isNotEmpty()) lons.average() else null

        val ratings = interacted.mapNotNull { it.rating }
        val avgRating = if (ratings.isNotEmpty()) ratings.average() else 3.5

        return UserProfile(
            categoryWeights = categoryWeights,
            tagWeights = tagWeights,
            avgLat = avgLat,
            avgLon = avgLon,
            avgRating = avgRating,
            visitedIds = visited.map { it.id }.toSet(),
            favoriteIds = favorites.map { it.id }.toSet()
        )
    }

    private fun computeScore(
        place: Place,
        profile: UserProfile,
        userLat: Double?,
        userLon: Double?
    ): Double {
        var score = 0.0

        score += (place.rating ?: 0.0) * 12.0

        score += place.popularity.coerceAtMost(500) * 0.3

        val categoryAffinity = profile.categoryWeights[place.category] ?: 0.0
        score += categoryAffinity * 150

        val tagMatch = place.tags.count { profile.tagWeights.containsKey(it) }
        score += tagMatch * 40

        if (place.id in profile.favoriteIds) score += 200
        if (place.id in profile.visitedIds) score -= 80

        if (userLat != null && userLon != null) {
            val distanceKm = NativeGeoEngine.distanceMeters(
                userLat, userLon, place.latitude, place.longitude
            ) / 1000.0
            score += (30.0 / (1.0 + distanceKm)).coerceAtMost(30.0)
        } else if (profile.avgLat != null && profile.avgLon != null) {
            val distanceKm = NativeGeoEngine.distanceMeters(
                profile.avgLat, profile.avgLon, place.latitude, place.longitude
            ) / 1000.0
            score += (15.0 / (1.0 + distanceKm)).coerceAtMost(15.0)
        }

        val ratingDiff = kotlin.math.abs((place.rating ?: 3.5) - profile.avgRating)
        score -= ratingDiff * 5

        return score
    }

    private fun computeSimilarity(a: Place, b: Place): Double {
        var sim = 0.0

        if (a.category == b.category) sim += 100.0

        val commonTags = a.tags.intersect(b.tags.toSet()).size
        sim += commonTags * 30.0

        val ratingDiff = kotlin.math.abs((a.rating ?: 0.0) - (b.rating ?: 0.0))
        sim -= ratingDiff * 10.0

        val distKm = NativeGeoEngine.distanceMeters(
            a.latitude, a.longitude, b.latitude, b.longitude
        ) / 1000.0
        sim += (20.0 / (1.0 + distKm)).coerceAtMost(20.0)

        val popDiff = kotlin.math.abs(a.popularity - b.popularity)
        sim -= popDiff * 0.05

        return sim
    }
}
