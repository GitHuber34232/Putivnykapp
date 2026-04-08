package ua.kyiv.putivnyk.domain.usecase.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint

class RouteMetricsCalculatorTest {

    private fun baseRoute(): Route = Route(
        id = 1L,
        name = "Test route",
        startPoint = RoutePoint(50.4501, 30.5234),
        endPoint = RoutePoint(50.4540, 30.5160),
        waypoints = emptyList(),
        distance = 0.0,
        estimatedDuration = 0,
        createdAt = 100L,
        updatedAt = 100L
    )

    @Test
    fun recompute_sets_distance_and_min_duration() {
        val updated = RouteMetricsCalculator.recompute(baseRoute())
        assertTrue(updated.distance > 0.0)
        assertTrue(updated.estimatedDuration >= 1)
        assertTrue(updated.updatedAt >= 100L)
    }

    @Test
    fun withAppendedWaypoint_adds_waypoint() {
        val route = baseRoute()
        val waypoint = RoutePoint(50.4510, 30.5220)

        val updated = RouteMetricsCalculator.withAppendedWaypoint(route, waypoint)

        assertEquals(1, updated.waypoints.size)
        assertEquals(waypoint.latitude, updated.waypoints.first().latitude, 0.0)
        assertEquals(waypoint.longitude, updated.waypoints.first().longitude, 0.0)
        assertTrue(updated.distance > 0.0)
    }
}
