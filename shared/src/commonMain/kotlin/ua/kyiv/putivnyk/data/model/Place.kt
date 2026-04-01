package ua.kyiv.putivnyk.data.model

import kotlinx.serialization.Serializable
import ua.kyiv.putivnyk.platform.currentTimeMillis

@Serializable
data class Place(
    val id: Long = 0,
    val name: String,
    val nameEn: String? = null,
    val latitude: Double,
    val longitude: Double,
    val category: PlaceCategory,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val rating: Double? = null,
    val popularity: Int = 0,
    val riverBank: RiverBank = RiverBank.UNKNOWN,
    val visitDuration: Int? = null,
    val imageUrls: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isVisited: Boolean = false,
    val createdAt: Long = currentTimeMillis(),
    val updatedAt: Long = currentTimeMillis()
)

@Serializable
enum class PlaceCategory(val displayName: String, val icon: String) {
    PARK("Парк", "🌳"),
    MUSEUM("Музей", "🏛️"),
    THEATER("Театр", "🎭"),
    RESTAURANT("Ресторан", "🍽️"),
    CATHEDRAL("Собор", "⛪"),
    MONASTERY("Монастир", "🕍"),
    ARCHITECTURE_MONUMENT("Пам'ятка архітектури", "🏰"),
    SQUARE("Площа", "🧭"),
    STREET("Вулиця", "🛣️"),
    DISTRICT("Район", "🏙️"),
    STADIUM("Стадіон", "🏟️"),
    EMBANKMENT("Набережна", "🌊"),
    FAMOUS_PLACE("Відомі місця", "📍"),
    TOILET("Туалети", "🚻"),
    OTHER("Інше", "📍");

    companion object {
        fun fromString(value: String): PlaceCategory {
            val normalized = value.trim().lowercase()
            return when (normalized) {
                "park", "парк" -> PARK
                "museum", "музей", "museums", "музеї" -> MUSEUM
                "theater", "theatre", "театр" -> THEATER
                "restaurant", "food", "ресторан", "їжа" -> RESTAURANT
                "cathedral", "собор", "religion", "релігія" -> CATHEDRAL
                "monastery", "монастир" -> MONASTERY
                "architecture_monument", "architecture", "historic_building", "пам'ятка архітектури", "monument", "монумент" -> ARCHITECTURE_MONUMENT
                "square", "площа" -> SQUARE
                "street", "вулиця", "андріївський узвіз" -> STREET
                "district", "район" -> DISTRICT
                "stadium", "стадіон" -> STADIUM
                "embankment", "набережна" -> EMBANKMENT
                "famous_place", "landmark", "historical", "cultural", "religious", "відомі місця", "пам'ятка" -> FAMOUS_PLACE
                "toilet", "wc", "туалет" -> TOILET
                else -> entries.find { it.name.equals(value, ignoreCase = true) } ?: OTHER
            }
        }
    }
}

@Serializable
enum class RiverBank(val displayName: String) {
    LEFT("Лівий берег"),
    RIGHT("Правий берег"),
    BOTH("Обидва береги"),
    UNKNOWN("Невідомо");

    companion object {
        fun fromString(value: String): RiverBank {
            return when (value.trim().lowercase()) {
                "left", "лівий", "left_bank" -> LEFT
                "right", "правий", "right_bank" -> RIGHT
                "both", "обидва" -> BOTH
                else -> UNKNOWN
            }
        }

        fun fromCoordinates(longitude: Double): RiverBank {
            return if (longitude >= 30.58) LEFT else RIGHT
        }
    }
}

@Serializable
enum class PlaceSortMode(val displayName: String) {
    POPULARITY("Популярність"),
    RATING("Рейтинг"),
    DISTANCE("Відстань"),
    RECOMMENDED("Рекомендовані")
}