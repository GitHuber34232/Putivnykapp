package ua.kyiv.putivnyk.data.model

import ua.kyiv.putivnyk.data.local.entity.PlaceEntity

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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

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

enum class PlaceSortMode(val displayName: String) {
    POPULARITY("Популярність"),
    RATING("Рейтинг"),
    DISTANCE("Відстань"),
    RECOMMENDED("Рекомендовані")
}

fun Place.toEntity(): PlaceEntity = PlaceEntity(
    id = id,
    name = name,
    nameEn = nameEn,
    latitude = latitude,
    longitude = longitude,
    category = category.name.lowercase(),
    tags = tags,
    description = description,
    rating = rating,
    popularity = popularity,
    riverBank = riverBank.name.lowercase(),
    visitDuration = visitDuration,
    imageUrls = imageUrls,
    isFavorite = isFavorite,
    isVisited = isVisited,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PlaceEntity.toDomain(): Place = Place(
    id = id,
    name = name,
    nameEn = nameEn,
    latitude = latitude,
    longitude = longitude,
    category = PlaceCategory.fromString(category),
    tags = tags,
    description = description,
    rating = rating,
    popularity = popularity,
    riverBank = RiverBank.fromString(riverBank),
    visitDuration = visitDuration,
    imageUrls = imageUrls,
    isFavorite = isFavorite,
    isVisited = isVisited,
    createdAt = createdAt,
    updatedAt = updatedAt
)
