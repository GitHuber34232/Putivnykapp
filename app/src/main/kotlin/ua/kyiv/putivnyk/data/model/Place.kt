package ua.kyiv.putivnyk.data.model

import ua.kyiv.putivnyk.data.local.entity.PlaceEntity

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
