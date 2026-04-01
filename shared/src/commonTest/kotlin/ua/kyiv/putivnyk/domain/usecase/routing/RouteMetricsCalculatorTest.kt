package ua.kyiv.putivnyk.domain.usecase.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import kotlin.math.ceil

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
        assertTrue(updated.estimatedDuration >= 5)
        assertTrue(updated.updatedAt >= 100L)
    }

    @Test
    fun withAppendedWaypoint_adds_waypoint() {
        val waypoint = RoutePoint(50.4510, 30.5220)
        val updated = RouteMetricsCalculator.withAppendedWaypoint(baseRoute(), waypoint)

        assertEquals(1, updated.waypoints.size)
        assertEquals(waypoint.latitude, updated.waypoints.first().latitude, 0.0)
        assertEquals(waypoint.longitude, updated.waypoints.first().longitude, 0.0)
        assertTrue(updated.distance > 0.0)
    }

    @Test
    fun withMetrics_uses_exact_duration_seconds_without_cap() {
        val updated = RouteMetricsCalculator.withMetrics(
            route = baseRoute(),
            distanceMeters = 8_000.0,
            durationSeconds = 6_900.0
        )

        assertEquals(8_000.0, updated.distance, 0.0)
        assertEquals(ceil(6_900.0 / 60.0).toInt(), updated.estimatedDuration)
    }
}