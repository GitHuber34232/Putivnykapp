package ua.kyiv.putivnyk.data.remote.events

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.kyiv.putivnyk.data.remote.events.model.EventDto
import ua.kyiv.putivnyk.data.remote.events.model.toDomain

class EventDtoMappingTest {

    @Test
    fun events_without_id_or_title_are_dropped_instead_of_crashing() {
        val json = """
            [{"id":"a","title":" Аїда ","category":"theater","price_from":200,"price_to":500},
             {"title":"No id"},
             {"id":"c"}]
        """.trimIndent()
        val dtos = Gson().fromJson(json, Array<EventDto>::class.java).toList()

        val items = dtos.mapNotNull { runCatching { it.toDomain() }.getOrNull() }

        assertEquals(1, items.size)
        assertEquals("Аїда", items.single().title)
        assertEquals("Театр", items.single().category)
        assertEquals("200-500 грн", items.single().priceLabel)
    }
}
