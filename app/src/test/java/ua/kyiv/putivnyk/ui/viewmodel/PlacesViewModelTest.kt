package ua.kyiv.putivnyk.ui.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.PlaceSortMode
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.domain.usecase.recommendation.RecommendationEngine
import ua.kyiv.putivnyk.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class PlacesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val placeRepository: PlaceRepository = mock()
    private val recommendationEngine: RecommendationEngine = mock()

    private val samplePlaces = listOf(
        Place(
            id = 1L,
            name = "Софійський собор",
            nameEn = "Saint Sophia Cathedral",
            latitude = 50.4527,
            longitude = 30.5143,
            category = PlaceCategory.CATHEDRAL,
            tags = listOf("UNESCO", "собор"),
            popularity = 95,
            rating = 4.8
        ),
        Place(
            id = 2L,
            name = "Мариїнський парк",
            nameEn = "Mariinsky Park",
            latitude = 50.4466,
            longitude = 30.5382,
            category = PlaceCategory.PARK,
            tags = listOf("парк", "прогулянка"),
            popularity = 85,
            rating = 4.5,
            isFavorite = true
        ),
        Place(
            id = 3L,
            name = "Громадський туалет",
            latitude = 50.4500,
            longitude = 30.5200,
            category = PlaceCategory.TOILET,
            popularity = 10,
            rating = 2.0
        )
    )

    private fun createViewModel(places: List<Place> = samplePlaces): PlacesViewModel {
        whenever(placeRepository.getAllPlaces()).thenReturn(flowOf(places))
        whenever(placeRepository.getFavoritePlaces()).thenReturn(
            flowOf(places.filter { it.isFavorite })
        )
        return PlacesViewModel(placeRepository, recommendationEngine)
    }

    @Test
    fun isLoaded_becomes_true_after_places_collected() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.isLoaded.first())
    }

    @Test
    fun places_excludes_toilets_by_default() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val places = viewModel.places.first()
        assertTrue(places.none { it.category == PlaceCategory.TOILET })
        assertEquals(2, places.size)
    }

    @Test
    fun places_sorted_by_popularity_by_default() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val places = viewModel.places.first()
        assertEquals("Софійський собор", places[0].name)
        assertEquals("Мариїнський парк", places[1].name)
    }

    @Test
    fun setSortMode_rating_sorts_by_rating() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setSortMode(PlaceSortMode.RATING)
        advanceUntilIdle()
        val places = viewModel.places.first()
        assertEquals("Софійський собор", places[0].name)
        assertEquals("Мариїнський парк", places[1].name)
    }

    @Test
    fun selectCategory_filters_by_category() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectCategory(PlaceCategory.PARK)
        advanceUntilIdle()
        val places = viewModel.places.first()
        assertEquals(1, places.size)
        assertEquals("Мариїнський парк", places[0].name)
    }

    @Test
    fun toggleShowOnlyFavorites_filters_non_favorites() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.toggleShowOnlyFavorites()
        advanceUntilIdle()
        val places = viewModel.places.first()
        assertEquals(1, places.size)
        assertTrue(places[0].isFavorite)
    }

    @Test
    fun updateSearchQuery_filters_by_name() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateSearchQuery("Софій")
        advanceUntilIdle()
        val places = viewModel.places.first()
        assertEquals(1, places.size)
        assertEquals("Софійський собор", places[0].name)
    }

    @Test
    fun searchQuery_toilet_includes_toilet_category() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateSearchQuery("туалет")
        advanceUntilIdle()
        val places = viewModel.places.first()
        assertTrue(places.any { it.category == PlaceCategory.TOILET })
    }

    @Test
    fun favoritePlaces_returns_only_favorites() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val favorites = viewModel.favoritePlaces.first()
        assertEquals(1, favorites.size)
        assertTrue(favorites.all { it.isFavorite })
    }

    @Test
    fun toggleFavorite_calls_repository() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.toggleFavorite(1L)
        advanceUntilIdle()
        verify(placeRepository).toggleFavorite(1L)
    }

    @Test
    fun toggleVisited_calls_repository() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.toggleVisited(2L)
        advanceUntilIdle()
        verify(placeRepository).toggleVisited(2L)
    }

    @Test
    fun selectCategory_null_resets_filter() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectCategory(PlaceCategory.PARK)
        advanceUntilIdle()
        assertEquals(1, viewModel.places.first().size)
        viewModel.selectCategory(null)
        advanceUntilIdle()
        assertEquals(2, viewModel.places.first().size)
    }

    @Test
    fun showOnlyFavorites_initial_state_is_false() = runTest {
        val viewModel = createViewModel()
        assertFalse(viewModel.showOnlyFavorites.first())
    }
}
