package ua.kyiv.putivnyk.data.model

import ua.kyiv.putivnyk.data.local.entity.MapBookmarkEntity

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
