package ua.kyiv.putivnyk.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.kyiv.putivnyk.data.model.RoutePoint

class NativeGeoEngineJvmTest {

    @Test
    fun distanceMeters_returns_positive_for_distinct_points() {
        val distance = NativeGeoEngine.distanceMeters(50.4501, 30.5234, 50.4540, 30.5160)
        assertTrue(distance > 100.0)
    }

    @Test
    fun polylineDistanceMeters_returns_zero_for_single_point() {
        val distance = NativeGeoEngine.polylineDistanceMeters(listOf(RoutePoint(50.45, 30.52)))
        assertEquals(0.0, distance, 0.0)
    }

    @Test
    fun nearestPointIndex_returns_minus_one_for_empty_list() {
        val index = NativeGeoEngine.nearestPointIndex(emptyList(), 50.0, 30.0)
        assertEquals(-1, index)
    }

    @Test
    fun simplifyPolyline_when_native_not_available_returns_same_list() {
        val points = listOf(
            RoutePoint(50.4500, 30.5200),
            RoutePoint(50.4510, 30.5210),
            RoutePoint(50.4520, 30.5220)
        )
        val simplified = NativeGeoEngine.simplifyPolyline(points, toleranceMeters = 10.0)

        if (!NativeGeoEngine.isNativeAvailable()) {
            assertEquals(points, simplified)
        } else {
            assertTrue(simplified.isNotEmpty())
        }
    }
}
