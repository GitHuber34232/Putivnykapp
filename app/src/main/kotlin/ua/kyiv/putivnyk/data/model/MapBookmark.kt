package ua.kyiv.putivnyk.data.model

import ua.kyiv.putivnyk.data.local.entity.MapBookmarkEntity

data class MapBookmark(
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val latitude: Double,
    val longitude: Double,
    val zoomLevel: Int = 14,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun MapBookmark.toEntity(): MapBookmarkEntity = MapBookmarkEntity(
    id = id,
    title = title,
    note = note,
    latitude = latitude,
    longitude = longitude,
    zoomLevel = zoomLevel,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun MapBookmarkEntity.toDomain(): MapBookmark = MapBookmark(
    id = id,
    title = title,
    note = note,
    latitude = latitude,
    longitude = longitude,
    zoomLevel = zoomLevel,
    createdAt = createdAt,
    updatedAt = updatedAt
)
