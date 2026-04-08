package ua.kyiv.putivnyk.domain.usecase.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.kyiv.putivnyk.data.model.RoutePoint

class RouteNavigationMetricsTest {

    @Test
    fun estimateDurationMinutes_uses_realistic_eta_without_cap() {
        val eta = RouteNavigationMetrics.estimateDurationMinutes(8_000.0)

        assertTrue(eta > 90)
    }

    @Test
    fun projectOnPolyline_returns_distance_from_start_and_remaining_geometry() {
        val polyline = listOf(
            RoutePoint(50.4500, 30.5200),
            RoutePoint(50.4500, 30.5300),
            RoutePoint(50.4500, 30.5400)
        )

        val projection = RouteNavigationMetrics.projectOnPolyline(
            polyline = polyline,
            latitude = 50.4500,
            longitude = 30.5350
        )

        assertNotNull(projection)
        projection ?: return

        assertEquals(1, projection.segmentIndex)
        assertTrue(projection.distanceFromStartMeters > 1000.0)
        assertTrue(projection.totalDistanceMeters > projection.distanceFromStartMeters)

        val remaining = RouteNavigationMetrics.remainingGeometry(polyline, projection)
        assertTrue(remaining.size >= 2)
        assertEquals(polyline.last().latitude, remaining.last().latitude, 0.0)
        assertEquals(polyline.last().longitude, remaining.last().longitude, 0.0)
    }
}