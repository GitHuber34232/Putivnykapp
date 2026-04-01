package ua.kyiv.putivnyk.data.remote.events.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ua.kyiv.putivnyk.data.model.EventItem

@Serializable
data class EventDto(
    @SerialName("id")
    val id: String,
    @SerialName("title")
    val title: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("category")
    val category: String? = null,
    @SerialName("starts_at")
    val startsAt: String? = null,
    @SerialName("ends_at")
    val endsAt: String? = null,
    @SerialName("location_name")
    val locationName: String? = null,
    @SerialName("latitude")
    val latitude: Double? = null,
    @SerialName("longitude")
    val longitude: Double? = null,
    @SerialName("ticket_url")
    val ticketUrl: String? = null,
    @SerialName("price")
    val price: String? = null,
    @SerialName("price_from")
    val priceFrom: Double? = null,
    @SerialName("price_to")
    val priceTo: Double? = null,
    @SerialName("cover_url")
    val coverUrl: String? = null
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