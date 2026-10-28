package ua.kyiv.putivnyk.domain.visit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine

class VisitDetectorTest {

    private val place = VisitDetector.Candidate(placeId = 7L, latitude = 50.4501, longitude = 30.5234, radiusMeters = 45.0)
    private val distance: (Double, Double, Double, Double) -> Double = NativeGeoEngine::distanceMeters

    /** [meters] north of the place. */
    private fun latNorth(meters: Double) = place.latitude + meters / 111_320.0

    @Test
    fun comingRightUpToThePlaceCountsImmediately() {
        val detector = VisitDetector()
        val arrived = detector.update(listOf(place), place.latitude, place.longitude, 8f, 1_000L, distance)
        assertEquals(listOf(7L), arrived)
    }

    @Test
    fun stayingInsideTheRadiusCountsAfterTheDwellTime() {
        val detector = VisitDetector()
        val lat = latNorth(35.0)
        assertTrue(detector.update(listOf(place), lat, place.longitude, 10f, 0L, distance).isEmpty())
        assertTrue(detector.update(listOf(place), lat, place.longitude, 10f, 20_000L, distance).isEmpty())
        assertEquals(listOf(7L), detector.update(listOf(place), lat, place.longitude, 10f, VisitDetector.DEFAULT_DWELL_MILLIS + 1, distance))
    }

    @Test
    fun walkingPastDoesNotCount() {
        val detector = VisitDetector()
        assertTrue(detector.update(listOf(place), latNorth(35.0), place.longitude, 10f, 0L, distance).isEmpty())
        assertTrue(detector.update(listOf(place), latNorth(400.0), place.longitude, 10f, 15_000L, distance).isEmpty())
        // Back long after leaving: the dwell time starts again.
        val later = 15_000L + VisitDetector.LEAVE_GRACE_MILLIS + 10_000L
        assertTrue(detector.update(listOf(place), latNorth(400.0), place.longitude, 10f, later, distance).isEmpty())
        assertTrue(detector.update(listOf(place), latNorth(35.0), place.longitude, 10f, later + 1_000L, distance).isEmpty())
    }

    @Test
    fun inaccurateFixesAreIgnored() {
        val detector = VisitDetector()
        assertTrue(detector.update(listOf(place), place.latitude, place.longitude, 150f, 0L, distance).isEmpty())
    }

    @Test
    fun largePlacesHaveLargerRadius() {
        assertTrue(VisitDetector.radiusFor(PlaceCategory.PARK) > VisitDetector.radiusFor(PlaceCategory.MUSEUM))
    }
}
