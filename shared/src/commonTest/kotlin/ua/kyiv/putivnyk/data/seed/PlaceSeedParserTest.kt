package ua.kyiv.putivnyk.data.seed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.RiverBank

class PlaceSeedParserTest {

    @Test
    fun parsePlaces_maps_valid_items_and_skips_invalid_ones() {
        val rawJson = """
            [
              {
                "id": 1,
                "name": "   Mariinsky Park   ",
                "name_en": "Mariinsky Park",
                "latitude": 50.45,
                "longitude": 30.55,
                "category": "park",
                "tags": ["park", "unknown"],
                "rating": 4.5
              },
              {
                "id": 2,
                "name": "",
                "latitude": 999.0,
                "longitude": 30.5,
                "category": "museum"
              }
            ]
        """.trimIndent()

        val places = PlaceSeedParser.parsePlaces(rawJson)

        assertEquals(1, places.size)
        assertEquals("Mariinsky Park", places.first().name)
        assertEquals("Mariinsky Park", places.first().nameEn)
        assertEquals(PlaceCategory.PARK, places.first().category)
        assertEquals(listOf("PARK"), places.first().tags)
        assertEquals(90, places.first().popularity)
        assertEquals(RiverBank.RIGHT, places.first().riverBank)
    }

    @Test
    fun parsePlaces_prefers_unified_tag_over_legacy_category() {
        val rawJson = """
            [
              {
                "id": 5,
                "name": "Some place",
                "latitude": 50.45,
                "longitude": 30.40,
                "category": "other",
                "tags": ["museum"]
              }
            ]
        """.trimIndent()

        val places = PlaceSeedParser.parsePlaces(rawJson)

        assertTrue(places.isNotEmpty())
        assertEquals(PlaceCategory.MUSEUM, places.first().category)
        assertEquals(RiverBank.LEFT, places.first().riverBank)
    }
}