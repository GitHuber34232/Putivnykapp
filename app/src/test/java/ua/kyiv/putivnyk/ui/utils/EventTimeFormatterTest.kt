package ua.kyiv.putivnyk.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class EventTimeFormatterTest {

    @Test
    fun parses_offset_date_time() {
        val millis = EventTimeFormatter.parseToEpochMillis("2026-07-15T19:00:00+03:00")
        assertEquals(1_784_131_200_000L, millis)
    }

    @Test
    fun parses_local_date_time_as_kyiv_time() {
        val local = EventTimeFormatter.parseToEpochMillis("2026-01-10T19:00:00")
        val withOffset = EventTimeFormatter.parseToEpochMillis("2026-01-10T19:00:00+02:00")
        assertEquals(withOffset, local)
    }

    @Test
    fun blank_and_garbage_are_not_dates() {
        assertNull(EventTimeFormatter.parseToEpochMillis(""))
        assertNull(EventTimeFormatter.parseToEpochMillis(null))
        assertNull(EventTimeFormatter.parseToEpochMillis("скоро"))
        assertNull(EventTimeFormatter.format("скоро", Locale.US))
    }

    @Test
    fun formats_in_kyiv_time_with_localized_month() {
        // 16:00 UTC is 19:00 in Kyiv in July (UTC+3).
        val formatted = EventTimeFormatter.format("2026-07-15T16:00:00Z", Locale.forLanguageTag("uk"))
        assertTrue(formatted, formatted!!.contains("липня"))
        assertTrue(formatted, formatted.endsWith("19:00"))
    }

    @Test
    fun same_day_detection_uses_kyiv_dates() {
        assertTrue(EventTimeFormatter.isSameDay("2026-07-15T19:00:00+03:00", "2026-07-15T21:30:00+03:00"))
        // 23:30 and 00:30 Kyiv time fall on different days.
        assertFalse(EventTimeFormatter.isSameDay("2026-07-15T20:30:00Z", "2026-07-15T21:30:00Z"))
    }
}
