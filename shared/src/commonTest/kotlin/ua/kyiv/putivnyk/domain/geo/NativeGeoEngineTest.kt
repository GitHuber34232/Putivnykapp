package ua.kyiv.putivnyk.domain.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ua.kyiv.putivnyk.data.model.RoutePoint

class NativeGeoEngineTest {

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
        assertEquals(-1, NativeGeoEngine.nearestPointIndex(emptyList(), 50.0, 30.0))
    }
}