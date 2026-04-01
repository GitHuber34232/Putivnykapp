package ua.kyiv.putivnyk.data.model

import kotlinx.serialization.Serializable

@Serializable
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