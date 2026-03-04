package ua.kyiv.putivnyk.domain.geo

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ua.kyiv.putivnyk.data.model.RoutePoint

@RunWith(AndroidJUnit4::class)
class NativeGeoEngineInstrumentedTest {

    @Test
    fun nativeLibrary_is_available_on_device() {
        assertTrue(NativeGeoEngine.isNativeAvailable())
    }

    @Test
    fun native_distanceMeters_is_positive() {
        assumeTrue(NativeGeoEngine.isNativeAvailable())
        val distance = NativeGeoEngine.distanceMeters(50.4501, 30.5234, 50.4540, 30.5160)
        assertTrue(distance > 100.0)
    }

    @Test
    fun native_polylineDistanceMeters_is_consistent() {
        assumeTrue(NativeGeoEngine.isNativeAvailable())
        val points = listOf(
            RoutePoint(50.4501, 30.5234),
            RoutePoint(50.4510, 30.5220),
            RoutePoint(50.4540, 30.5160)
        )
        val total = NativeGeoEngine.polylineDistanceMeters(points)
        assertTrue(total > 0.0)
    }

    @Test
    fun native_nearestPointIndex_returns_expected_point() {
        assumeTrue(NativeGeoEngine.isNativeAvailable())
        val points = listOf(
            RoutePoint(50.4500, 30.5200),
            RoutePoint(50.4600, 30.5300),
            RoutePoint(50.4700, 30.5400)
        )
        val index = NativeGeoEngine.nearestPointIndex(points, 50.4601, 30.5299)
        assertEquals(1, index)
    }

    @Test
    fun native_simplifyPolyline_keeps_endpoints() {
        assumeTrue(NativeGeoEngine.isNativeAvailable())
        val points = listOf(
            RoutePoint(50.4500, 30.5200),
            RoutePoint(50.4505, 30.5205),
            RoutePoint(50.4510, 30.5210),
            RoutePoint(50.4520, 30.5220),
            RoutePoint(50.4530, 30.5230)
        )
        val simplified = NativeGeoEngine.simplifyPolyline(points, toleranceMeters = 20.0)

        assertTrue(simplified.isNotEmpty())
        assertEquals(points.first().latitude, simplified.first().latitude, 0.0)
        assertEquals(points.first().longitude, simplified.first().longitude, 0.0)
        assertEquals(points.last().latitude, simplified.last().latitude, 0.0)
        assertEquals(points.last().longitude, simplified.last().longitude, 0.0)
    }
}
