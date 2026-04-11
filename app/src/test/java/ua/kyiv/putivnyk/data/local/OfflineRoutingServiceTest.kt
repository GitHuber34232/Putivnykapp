package ua.kyiv.putivnyk.data.local

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.kyiv.putivnyk.data.model.TransportMode

class OfflineRoutingServiceTest {

    @Test
    fun profileFor_returns_foot_for_walking() {
        assertEquals("foot", OfflineRoutingService.profileFor(TransportMode.WALKING))
    }

    @Test
    fun profileFor_returns_car_for_driving() {
        assertEquals("car", OfflineRoutingService.profileFor(TransportMode.DRIVING))
    }
}