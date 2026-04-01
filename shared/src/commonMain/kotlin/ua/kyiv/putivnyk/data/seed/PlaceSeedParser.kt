package ua.kyiv.putivnyk.data.seed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.RiverBank

object PlaceSeedParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parsePlaces(rawJson: String): List<Place> =
        runCatching { json.decodeFromString<List<PlaceSeedDto>>(rawJson) }
            .getOrDefault(emptyList())
            .mapNotNull { it.toDomainOrNull() }
}

@Serializable
data class PlaceSeedDto(
    @SerialName("id")
    val id: Long = 0,
    @SerialName("name")
    val name: String,
    @SerialName("name_en")
    val nameEn: String? = null,
    @SerialName("latitude")
    val latitude: Double,
    @SerialName("longitude")
    val longitude: Double,
    @SerialName("category")
    val category: String,
    @SerialName("tags")
    val tags: List<String>? = null,
    @SerialName("isLeftBank")
    val isLeftBankCamel: Boolean? = null,
    @SerialName("is_left_bank")
    val isLeftBankSnake: Boolean? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("rating")
    val rating: Double? = null,
    @SerialName("popularity")
    val popularity: Int? = null,
    @SerialName("river_bank")
    val riverBank: String? = null,
    @SerialName("visitDuration")
    val visitDuration: Int? = null,
    @SerialName("imageUrls")
    val imageUrls: List<String>? = null,
) {
    fun toDomainOrNull(): Place? {
        val normalizedName = name.trim()
        val hasValidLatitude = latitude in -90.0..90.0 && latitude.isFinite()
        val hasValidLongitude = longitude in -180.0..180.0 && longitude.isFinite()
        if (normalizedName.isBlank() || !hasValidLatitude || !hasValidLongitude) {
            return null
        }

        val normalizedTags = tags.orEmpty()
            .map { it.trim().uppercase() }
            .filter { it in SUPPORTED_TAGS }
            .distinct()

        val resolvedCategory = when {
            normalizedTags.isNotEmpty() -> categoryFromUnifiedTag(normalizedTags.first())
            else -> PlaceCategory.fromString(category)
        }

        val leftBankFlag = isLeftBankCamel ?: isLeftBankSnake
        val resolvedRiverBank = when (leftBankFlag) {
            true -> RiverBank.LEFT
            false -> RiverBank.RIGHT
            null -> riverBank?.let { RiverBank.fromString(it) } ?: RiverBank.fromCoordinates(longitude)
        }

        return Place(
            id = id,
            name = normalizedName,
            nameEn = nameEn?.trim()?.takeIf { it.isNotBlank() },
            latitude = latitude,
            longitude = longitude,
            category = resolvedCategory,
            tags = normalizedTags,
            description = description,
            rating = rating,
            popularity = popularity ?: ((rating ?: 0.0) * 20).toInt(),
            riverBank = resolvedRiverBank,
            visitDuration = visitDuration,
            imageUrls = imageUrls.orEmpty(),
            isFavorite = false,
            isVisited = false,
        )
    }

    private fun categoryFromUnifiedTag(tag: String): PlaceCategory = when (tag) {
        "PARK" -> PlaceCategory.PARK
        "MUSEUM" -> PlaceCategory.MUSEUM
        "THEATER" -> PlaceCategory.THEATER
        "RESTAURANT" -> PlaceCategory.RESTAURANT
        "RELIGION" -> PlaceCategory.CATHEDRAL
        "MONUMENT" -> PlaceCategory.ARCHITECTURE_MONUMENT
        else -> PlaceCategory.OTHER
    }

    private companion object {
        val SUPPORTED_TAGS = setOf(
            "PARK",
            "MUSEUM",
            "THEATER",
            "RESTAURANT",
            "RELIGION",
            "MONUMENT",
        )
    }
}