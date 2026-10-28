package ua.kyiv.putivnyk.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {

    @Test
    fun builtLinksParseBack() {
        assertEquals(DeepLink.Place(42L), DeepLinks.parse(DeepLinks.place(42L)))
        assertEquals(DeepLink.Map, DeepLinks.parse(DeepLinks.map()))
        assertEquals(DeepLink.Stats, DeepLinks.parse(DeepLinks.stats()))
        assertEquals(DeepLink.OfflineMaps, DeepLinks.parse(DeepLinks.offlineMaps()))
        assertEquals(DeepLink.DailyRoute(listOf(3L, 9L, 4L)), DeepLinks.parse(DeepLinks.dailyRoute(listOf(3L, 9L, 4L))))
    }

    @Test
    fun sharedRouteLinkCarriesTheCode() {
        assertEquals(DeepLink.SharedRoute("abc_DEF-1"), DeepLinks.parse("putivnyk://route?d=abc_DEF-1"))
    }

    @Test
    fun malformedOrForeignLinksAreIgnored() {
        assertNull(DeepLinks.parse(null))
        assertNull(DeepLinks.parse("https://example.org/place/1"))
        assertNull(DeepLinks.parse("putivnyk://place/not-a-number"))
        assertNull(DeepLinks.parse("putivnyk://place/-5"))
        assertNull(DeepLinks.parse("putivnyk://daily-route?ids=5"))
        assertNull(DeepLinks.parse("putivnyk://route?d="))
        assertNull(DeepLinks.parse("putivnyk://unknown"))
    }
}
