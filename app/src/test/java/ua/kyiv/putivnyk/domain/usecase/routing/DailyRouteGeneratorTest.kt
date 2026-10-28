package ua.kyiv.putivnyk.domain.usecase.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import java.time.LocalDate

class DailyRouteGeneratorTest {

    private val categories = listOf(
        PlaceCategory.MUSEUM, PlaceCategory.CATHEDRAL, PlaceCategory.PARK,
        PlaceCategory.SQUARE, PlaceCategory.THEATER, PlaceCategory.ARCHITECTURE_MONUMENT
    )

    // A 6 x 6 grid of places about 300 m apart around the city centre.
    private val places = (0 until 36).map { i ->
        Place(
            id = i + 1L,
            name = "Місце $i",
            latitude = 50.44 + (i / 6) * 0.0027,
            longitude = 30.50 + (i % 6) * 0.0042,
            category = categories[i % categories.size],
            popularity = 100 - i,
            rating = 4.0 + (i % 5) / 10.0
        )
    }

    @Test
    fun sameDateGivesTheSameRoute() {
        val date = LocalDate.of(2026, 10, 27)
        val first = DailyRouteGenerator.generate(places, date)
        val second = DailyRouteGenerator.generate(places.shuffled(), date)
        assertNotNull(first)
        assertEquals(first!!.places.map { it.id }, second!!.places.map { it.id })
    }

    @Test
    fun routeIsAShortWalkWithoutExcludedPlaces() {
        val withToilets = places + Place(id = 999L, name = "WC", latitude = 50.441, longitude = 30.501, category = PlaceCategory.TOILET, popularity = 1000)
        val route = DailyRouteGenerator.generate(withToilets, LocalDate.of(2026, 1, 15))!!
        assertTrue(route.places.size in 3..4)
        assertTrue(route.places.none { it.category == PlaceCategory.TOILET })
        assertEquals(route.places.size, route.places.map { it.id }.toSet().size)
        route.places.zipWithNext().forEach { (from, to) ->
            assertTrue(NativeGeoEngine.distanceMeters(from.latitude, from.longitude, to.latitude, to.longitude) <= 1_400.0)
        }
        assertTrue(route.walkingMinutes > 0)
    }

    @Test
    fun tooFewPlacesGiveNoRoute() {
        assertNull(DailyRouteGenerator.generate(places.take(2), LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun storedRouteKeepsItsOrderAndDistance() {
        val stops = places.take(3)
        val route = DailyRouteGenerator.fromPlaces(LocalDate.of(2026, 10, 27), stops)
        assertEquals(stops, route.places)
        assertTrue(route.distanceMeters > 0.0)
    }
}
