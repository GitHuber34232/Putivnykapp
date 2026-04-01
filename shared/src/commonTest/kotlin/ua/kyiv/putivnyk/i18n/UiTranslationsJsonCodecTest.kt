package ua.kyiv.putivnyk.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiTranslationsJsonCodecTest {

    @Test
    fun decode_returns_map_for_valid_json() {
        val rawJson = """{"home.title":"Putivnyk","events.sync":"Refresh"}"""

        val translations = UiTranslationsJsonCodec.decode(rawJson)

        assertEquals("Putivnyk", translations["home.title"])
        assertEquals("Refresh", translations["events.sync"])
    }

    @Test
    fun decode_returns_empty_map_for_invalid_json() {
        val translations = UiTranslationsJsonCodec.decode("{not valid json}")

        assertTrue(translations.isEmpty())
    }
}