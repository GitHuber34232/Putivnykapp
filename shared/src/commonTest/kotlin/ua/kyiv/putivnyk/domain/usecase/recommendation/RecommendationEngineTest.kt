package ua.kyiv.putivnyk.domain.usecase.recommendation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.RiverBank

class RecommendationEngineTest {

    private val engine = RecommendationEngine()

    @Test
    fun recommend_prioritizes_places_from_favorite_categories() {
        val museum = place(id = 1L, category = PlaceCategory.MUSEUM, popularity = 100)
        val park = place(id = 2L, category = PlaceCategory.PARK, popularity = 100)

        val results = engine.recommend(
            allPlaces = listOf(museum, park),
            favorites = listOf(place(id = 10L, category = PlaceCategory.MUSEUM))
        )

        assertEquals(museum.id, results.first().id)
    }

    @Test
    fun findSimilar_returns_non_empty_for_related_places() {
        val target = place(id = 1L, category = PlaceCategory.MUSEUM, tags = listOf("art"))
        val similar = place(id = 2L, category = PlaceCategory.MUSEUM, tags = listOf("art"), popularity = 120)
        val other = place(id = 3L, category = PlaceCategory.PARK)

        val results = engine.findSimilar(target, listOf(target, similar, other))

        assertTrue(results.isNotEmpty())
        assertEquals(similar.id, results.first().id)
    }

    private fun place(
        id: Long,
        category: PlaceCategory,
        tags: List<String> = emptyList(),
        popularity: Int = 0,
    ): Place = Place(
        id = id,
        name = "Place $id",
        latitude = 50.45 + (id * 0.001),
        longitude = 30.52 + (id * 0.001),
        category = category,
        tags = tags,
        popularity = popularity,
        riverBank = RiverBank.RIGHT,
    )
}