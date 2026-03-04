package ua.kyiv.putivnyk.data.remote.events.model

import com.google.gson.annotations.SerializedName

data class EventDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("category")
    val category: String? = null,
    @SerializedName("starts_at")
    val startsAt: String? = null,
    @SerializedName("ends_at")
    val endsAt: String? = null,
    @SerializedName("location_name")
    val locationName: String? = null,
    @SerializedName("latitude")
    val latitude: Double? = null,
    @SerializedName("longitude")
    val longitude: Double? = null,
    @SerializedName("ticket_url")
    val ticketUrl: String? = null,
    @SerializedName("price")
    val price: String? = null,
    @SerializedName("price_from")
    val priceFrom: Double? = null,
    @SerializedName("price_to")
    val priceTo: Double? = null,
    @SerializedName("cover_url")
    val coverUrl: String? = null
)

data class EventItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val startsAt: String,
    val endsAt: String,
    val locationName: String,
    val latitude: Double?,
    val longitude: Double?,
    val ticketUrl: String?,
    val priceLabel: String,
    val coverUrl: String?
)

private val categoryTranslations = mapOf(
    "concert" to "Концерт",
    "food" to "Їжа",
    "tour" to "Екскурсія",
    "exhibition" to "Виставка",
    "festival" to "Фестиваль",
    "theater" to "Театр",
    "theatre" to "Театр",
    "cinema" to "Кіно",
    "workshop" to "Майстер-клас",
    "lecture" to "Лекція",
    "sport" to "Спорт",
    "market" to "Ярмарок",
    "party" to "Вечірка",
    "music" to "Музика",
    "art" to "Мистецтво",
    "dance" to "Танці",
    "event" to "Подія",
    "other" to "Інше"
)

private fun translateCategory(raw: String): String =
    categoryTranslations[raw.trim().lowercase()] ?: raw.replaceFirstChar { it.uppercaseChar() }

fun EventDto.toDomain(): EventItem = EventItem(
    id = id,
    title = title,
    description = description.orEmpty(),
    category = translateCategory(category.orEmpty()),
    startsAt = startsAt.orEmpty(),
    endsAt = endsAt.orEmpty(),
    locationName = locationName.orEmpty(),
    latitude = latitude,
    longitude = longitude,
    ticketUrl = ticketUrl,
    priceLabel = when {
        !price.isNullOrBlank() -> price
        priceFrom != null && priceTo != null -> "${priceFrom.toInt()}-${priceTo.toInt()} грн"
        priceFrom != null -> "від ${priceFrom.toInt()} грн"
        else -> "Ціна уточнюється"
    },
    coverUrl = coverUrl
)
