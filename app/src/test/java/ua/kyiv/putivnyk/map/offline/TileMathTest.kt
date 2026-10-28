package ua.kyiv.putivnyk.map.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileMathTest {

    @Test
    fun maidanTileMatchesTheSlippyMapFormula() {
        // x = floor((lon + 180) / 360 * 2^z), y = floor((1 - ln(tan(lat) + sec(lat)) / pi) / 2 * 2^z)
        assertEquals(38324, TileMath.tileX(30.5234, 16))
        assertEquals(22098, TileMath.tileY(50.4501, 16))
        assertEquals(0, TileMath.tileX(-180.0, 3))
        assertEquals(7, TileMath.tileX(179.999, 3))
    }

    @Test
    fun aSingleTileAreaCountsOneTilePerZoom() {
        val bounds = GeoBounds(north = 50.4501, east = 30.5234, south = 50.4500, west = 30.5233)
        assertEquals(6L, TileMath.tileCount(bounds, 11, 16))
    }

    @Test
    fun everyKyivAreaFitsTheDownloadLimit() {
        OfflineAreas.kyiv.forEach { area ->
            val tiles = TileMath.tileCount(area.bounds, 11, 16)
            assertTrue("${area.id}: $tiles", tiles <= 6_000L)
            assertTrue(area.bounds.north > area.bounds.south && area.bounds.east > area.bounds.west)
        }
    }

    @Test
    fun viewportAroundAPointContainsIt() {
        val bounds = GeoBounds.around(50.4501, 30.5234, 15)
        assertTrue(bounds.contains(50.4501, 30.5234))
        assertTrue(TileMath.tileCount(bounds, 11, 16) < 200)
    }
}
