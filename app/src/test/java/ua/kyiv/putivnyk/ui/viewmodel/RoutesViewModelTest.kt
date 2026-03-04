package ua.kyiv.putivnyk.ui.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.data.remote.WalkingDirectionsService
import ua.kyiv.putivnyk.domain.usecase.routing.RouteOptimizer
import ua.kyiv.putivnyk.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class RoutesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val routeRepository: RouteRepository = mock()
    private val routeOptimizer: RouteOptimizer = mock()
    private val userPreferenceRepository: UserPreferenceRepository = mock()
    private val placeRepository: PlaceRepository = mock()
    private val walkingDirectionsService: WalkingDirectionsService = mock()

    private val sampleRoute = Route(
        id = 1L,
        name = "Test Route",
        description = "A test route",
        startPoint = RoutePoint(50.45, 30.52),
        endPoint = RoutePoint(50.46, 30.53),
        waypoints = emptyList(),
        distance = 1500.0,
        estimatedDuration = 20,
        isFavorite = false
    )

    private fun createViewModel(): RoutesViewModel {
        whenever(routeRepository.getAllRoutes()).thenReturn(flowOf(listOf(sampleRoute)))
        whenever(routeRepository.getFavoriteRoutes()).thenReturn(flowOf(emptyList()))
        return RoutesViewModel(routeRepository, routeOptimizer, userPreferenceRepository, placeRepository, walkingDirectionsService)
    }

    @Test
    fun isLoaded_becomes_true_after_routes_collected() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.isLoaded.value)
    }

    @Test
    fun routes_returns_all_routes_initially() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.routes.value.size)
        assertEquals("Test Route", viewModel.routes.value.first().name)
    }

    @Test
    fun searchQuery_filters_routes_by_name() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateSearchQuery("nonexistent")
        advanceUntilIdle()

        assertTrue(viewModel.routes.value.isEmpty())
    }

    @Test
    fun searchQuery_matches_route_name() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateSearchQuery("Test")
        advanceUntilIdle()

        assertEquals(1, viewModel.routes.value.size)
    }

    @Test
    fun toggleShowOnlyFavorites_filters_non_favorites() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleShowOnlyFavorites()
        advanceUntilIdle()

        assertTrue(viewModel.routes.value.isEmpty())
    }

    @Test
    fun requestDeleteRoute_sets_routeToDelete() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.routeToDelete.value)
        viewModel.requestDeleteRoute(sampleRoute)
        assertNotNull(viewModel.routeToDelete.value)
        assertEquals(sampleRoute.id, viewModel.routeToDelete.value?.id)
    }

    @Test
    fun cancelDeleteRoute_clears_routeToDelete() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestDeleteRoute(sampleRoute)
        assertNotNull(viewModel.routeToDelete.value)

        viewModel.cancelDeleteRoute()
        assertNull(viewModel.routeToDelete.value)
    }

    @Test
    fun confirmDeleteRoute_deletes_and_emits_event() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestDeleteRoute(sampleRoute)
        viewModel.confirmDeleteRoute()
        advanceUntilIdle()

        assertNull(viewModel.routeToDelete.value)
        verify(routeRepository).deleteRoute(sampleRoute)
    }

    @Test
    fun confirmDeleteRoute_without_request_does_nothing() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.confirmDeleteRoute()
        advanceUntilIdle()

        verify(routeRepository, never()).deleteRoute(any())
    }
}
