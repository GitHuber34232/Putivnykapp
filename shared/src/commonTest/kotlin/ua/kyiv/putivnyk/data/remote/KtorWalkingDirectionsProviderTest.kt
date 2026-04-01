package ua.kyiv.putivnyk.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ua.kyiv.putivnyk.data.model.RoutePoint
import kotlin.test.assertNotNull

class KtorWalkingDirectionsProviderTest {

    private val provider = KtorWalkingDirectionsProvider(HttpClient(MockEngine))

    @Test
    fun collapseBacktracks_removes_mirrored_segment() {
        val points = listOf(
            RoutePoint(50.0, 30.0),
            RoutePoint(50.0001, 30.0001),
            RoutePoint(50.0002, 30.0002),
            RoutePoint(50.0003, 30.0003),
            RoutePoint(50.0002, 30.0002),
            RoutePoint(50.0001, 30.0001),
            RoutePoint(50.0, 30.0),
            RoutePoint(50.0010, 30.0010)
        )

        val collapsed = provider.collapseBacktracks(points)

        assertTrue(collapsed.size < points.size)
        assertEquals(RoutePoint(50.0010, 30.0010), collapsed.last())
    }

    @Test
    fun collapseBacktracks_keeps_short_non_mirrored_path() {
        val points = listOf(
            RoutePoint(50.0, 30.0),
            RoutePoint(50.0002, 30.0002),
            RoutePoint(50.0004, 30.0005),
            RoutePoint(50.0008, 30.0009)
        )

        val collapsed = provider.collapseBacktracks(points)

        assertEquals(points, collapsed)
    }

    @Test
    fun trimInitialLoop_removes_loop_returning_to_start() {
        val points = listOf(
            RoutePoint(50.0, 30.0),
            RoutePoint(50.0008, 30.0008),
            RoutePoint(50.0012, 30.0012),
            RoutePoint(50.0005, 30.0005),
            RoutePoint(50.0001, 30.0001),
            RoutePoint(50.0000, 30.0000),
            RoutePoint(50.0015, 30.0018),
            RoutePoint(50.0020, 30.0021)
        )

        val trimmed = provider.trimInitialLoop(points)

        assertTrue(trimmed.size < points.size)
        assertEquals(points.last(), trimmed.last())
        assertNotNull(trimmed.first())
    }
}