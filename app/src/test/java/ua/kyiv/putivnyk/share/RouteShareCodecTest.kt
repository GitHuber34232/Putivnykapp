package ua.kyiv.putivnyk.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode

class RouteShareCodecTest {

    private val route = RouteShareCodec.SharedRoute(
        name = "Прогулянка Подолом",
        transportMode = TransportMode.WALKING,
        points = listOf(
            RoutePoint(50.45866, 30.51744, "Андріївська церква"),
            RoutePoint(50.46411, 30.51869, null),
            RoutePoint(50.46559, 30.51547, "Контрактова площа")
        )
    )

    @Test
    fun roundTripKeepsNameModeStopsAndCoordinates() {
        val decoded = RouteShareCodec.decode(RouteShareCodec.encode(route))
        assertNotNull(decoded)
        decoded!!
        assertEquals(route.name, decoded.name)
        assertEquals(TransportMode.WALKING, decoded.transportMode)
        assertEquals(route.points.size, decoded.points.size)
        route.points.zip(decoded.points).forEach { (sent, received) ->
            assertEquals(sent.latitude, received.latitude, 1e-5)
            assertEquals(sent.longitude, received.longitude, 1e-5)
            assertEquals(sent.name, received.name)
        }
    }

    @Test
    fun drivingModeSurvivesTheRoundTrip() {
        val driving = route.copy(transportMode = TransportMode.DRIVING)
        assertEquals(TransportMode.DRIVING, RouteShareCodec.decode(RouteShareCodec.encode(driving))?.transportMode)
    }

    @Test
    fun codeIsUrlSafe() {
        val code = RouteShareCodec.encode(route)
        assertTrue(code, code.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test
    fun codeIsFoundInsideAMessageWithTheLink() {
        val code = RouteShareCodec.encode(route)
        val message = "Дивись, який маршрут: ${RouteShareCodec.toLink(code)} — гарна прогулянка!"
        assertEquals(code, RouteShareCodec.extractCode(message))
        assertEquals(code, RouteShareCodec.extractCode(code))
    }

    @Test
    fun garbageAndTruncatedCodesAreRejected() {
        val code = RouteShareCodec.encode(route)
        assertNull(RouteShareCodec.decode(""))
        assertNull(RouteShareCodec.decode("not-a-route"))
        assertNull(RouteShareCodec.decode(code.dropLast(6)))
        assertNull(RouteShareCodec.extractCode("просто текст без маршруту"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun singlePointCannotBeShared() {
        RouteShareCodec.encode(route.copy(points = route.points.take(1)))
    }

    @Test
    fun longRoutesAreThinnedOutKeepingStartAndEnd() {
        val points = (0 until 202).map { RoutePoint(50.40 + it * 0.0005, 30.50 + it * 0.0003, "Точка $it") }
        val limited = RouteShareManager.limitPoints(points)
        assertEquals(RouteShareCodec.MAX_POINTS, limited.size)
        assertEquals(points.first(), limited.first())
        assertEquals(points.last(), limited.last())
        assertNotNull(RouteShareCodec.decode(RouteShareCodec.encode(route.copy(points = limited))))
    }

    @Test
    fun linkWithoutStopNamesFitsIntoAQrCode() {
        val points = (0 until RouteShareCodec.MAX_POINTS).map { RoutePoint(50.40 + it * 0.0011, 30.45 + it * 0.0007, null) }
        val link = RouteShareCodec.toLink(RouteShareCodec.encode(route.copy(points = points)))
        assertTrue("${link.length}", link.length <= RouteShareManager.QR_MAX_LENGTH)
    }
}
