package ua.kyiv.putivnyk.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode

class NavigationProgressCalculatorTest {

    // Three stops about 333 m apart, due north.
    private val a = RoutePoint(50.4500, 30.5200, "A")
    private val b = RoutePoint(50.4530, 30.5200, "B")
    private val c = RoutePoint(50.4560, 30.5200, "C")
    private val waypoints = listOf(a, b, c)

    private fun compute(currentIndex: Int, lat: Double, lon: Double, geometry: List<RoutePoint> = emptyList()) =
        NavigationProgressCalculator.compute(waypoints, geometry, currentIndex, lat, lon, TransportMode.WALKING)

    @Test
    fun atTheStartNothingIsReachedYet() {
        val progress = compute(0, a.latitude, a.longitude)
        assertEquals(0, progress.currentIndex)
        assertEquals(0f, progress.fraction, 0.01f)
        assertEquals(333.0, progress.distanceToNextMeters, 5.0)
        assertEquals(667.0, progress.remainingMeters, 8.0)
        assertFalse(progress.completed)
    }

    @Test
    fun nearAStopMovesToTheNextLeg() {
        val progress = compute(0, b.latitude - 0.0002, b.longitude)
        assertEquals(1, progress.currentIndex)
        assertEquals(c, waypoints[progress.currentIndex + 1])
        assertTrue(progress.fraction in 0.4f..0.55f)
    }

    @Test
    fun reachingTheDestinationCompletesTheRouteEvenAfterSkippingStops() {
        val progress = compute(0, c.latitude, c.longitude)
        assertEquals(waypoints.lastIndex, progress.currentIndex)
        assertTrue(progress.completed)
        assertEquals(1f, progress.fraction, 0f)
        assertEquals(0.0, progress.remainingMeters, 0.0)
        assertTrue(progress.remainingGeometry.isEmpty())
    }

    @Test
    fun walkingGeometryIsUsedWhenAvailable() {
        val midway = RoutePoint((a.latitude + b.latitude) / 2, a.longitude)
        val progress = compute(0, midway.latitude, midway.longitude, geometry = waypoints)
        assertEquals(0.25f, progress.fraction, 0.02f)
        assertEquals(500.0, progress.remainingMeters, 10.0)
        assertTrue(progress.remainingGeometry.size >= 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aRouteNeedsTwoPoints() {
        NavigationProgressCalculator.compute(listOf(a), emptyList(), 0, a.latitude, a.longitude, TransportMode.WALKING)
    }
}
