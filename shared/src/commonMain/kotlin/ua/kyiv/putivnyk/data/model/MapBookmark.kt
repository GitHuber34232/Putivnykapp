package ua.kyiv.putivnyk.data.model

import kotlinx.serialization.Serializable
import ua.kyiv.putivnyk.platform.currentTimeMillis

@Serializable
data class MapBookmark(
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val latitude: Double,
    val longitude: Double,
    val zoomLevel: Int = 14,
    val createdAt: Long = currentTimeMillis(),
    val updatedAt: Long = currentTimeMillis()
)