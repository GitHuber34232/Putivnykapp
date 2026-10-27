package ua.kyiv.putivnyk.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMappingTest {

    @Test
    fun route_toEntity_and_back_preserves_core_fields() {
        val route = Route(
            id = 7,
            name = "City Walk",
            description = "Center",
            startPoint = RoutePoint(50.4501, 30.5234),
            endPoint = RoutePoint(50.4540, 30.5160),
            waypoints = listOf(
                RoutePoint(50.4510, 30.5220),
                RoutePoint(50.4520, 30.5200)
            ),
            distance = 1234.5,
            estimatedDuration = 20,
            isFavorite = true,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val mappedBack = route.toEntity().toDomain()

        assertEquals(route.id, mappedBack.id)
        assertEquals(route.name, mappedBack.name)
        assertEquals(route.description, mappedBack.description)
        assertEquals(route.startPoint.latitude, mappedBack.startPoint.latitude, 0.0)
        assertEquals(route.startPoint.longitude, mappedBack.startPoint.longitude, 0.0)
        assertEquals(route.endPoint.latitude, mappedBack.endPoint.latitude, 0.0)
        assertEquals(route.endPoint.longitude, mappedBack.endPoint.longitude, 0.0)
        assertEquals(route.distance, mappedBack.distance, 0.0)
        assertEquals(route.estimatedDuration, mappedBack.estimatedDuration)
        assertEquals(route.isFavorite, mappedBack.isFavorite)
        assertEquals(route.createdAt, mappedBack.createdAt)
        assertEquals(route.updatedAt, mappedBack.updatedAt)
        assertEquals(route.waypoints.size, mappedBack.waypoints.size)
    }

    @Test
    fun route_toEntity_and_back_preserves_point_names() {
        val route = Route(
            name = "Named",
            startPoint = RoutePoint(50.4501, 30.5234, "Майдан"),
            endPoint = RoutePoint(50.4540, 30.5160, "Софія"),
            waypoints = listOf(
                RoutePoint(50.4510, 30.5220, "Хрещатик"),
                RoutePoint(50.4520, 30.5200, null)
            ),
            distance = 100.0,
            estimatedDuration = 2
        )

        val mappedBack = route.toEntity().toDomain()

        assertEquals("Майдан", mappedBack.startPoint.name)
        assertEquals("Софія", mappedBack.endPoint.name)
        assertEquals(listOf("Хрещатик", null), mappedBack.waypoints.map { it.name })
        assertEquals(route.waypoints, mappedBack.waypoints)
    }

    @Test
    fun toDomain_handles_rows_without_point_names() {
        val route = Route(
            name = "Legacy",
            startPoint = RoutePoint(1.0, 2.0),
            endPoint = RoutePoint(3.0, 4.0),
            waypoints = listOf(RoutePoint(5.0, 6.0)),
            distance = 0.0,
            estimatedDuration = 0
        )

        val legacyEntity = route.toEntity().copy(startName = null, endName = null, waypointNames = emptyList())
        val mapped = legacyEntity.toDomain()

        assertEquals(1, mapped.waypoints.size)
        assertEquals(null, mapped.waypoints.first().name)
        assertEquals(null, mapped.startPoint.name)
    }

    @Test
    fun toDomain_ignores_unpaired_waypoint_tail() {
        val route = Route(
            name = "x",
            startPoint = RoutePoint(0.0, 0.0),
            endPoint = RoutePoint(1.0, 1.0),
            distance = 0.0,
            estimatedDuration = 5
        )

        val entity = route.toEntity().copy(waypoints = listOf(1.0, 2.0, 3.0))
        val mapped = entity.toDomain()

        assertEquals(1, mapped.waypoints.size)
        assertTrue(mapped.waypoints.first().latitude == 1.0 && mapped.waypoints.first().longitude == 2.0)
    }
}
