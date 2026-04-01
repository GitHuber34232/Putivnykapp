package ua.kyiv.putivnyk.data.model

import ua.kyiv.putivnyk.data.local.entity.LocalizedStringEntity

fun LocalizedString.toEntity(): LocalizedStringEntity = LocalizedStringEntity(
    key = key,
    locale = locale,
    value = value,
    source = source,
    updatedAt = updatedAt
)

fun LocalizedStringEntity.toDomain(): LocalizedString = LocalizedString(
    key = key,
    locale = locale,
    value = value,
    source = source,
    updatedAt = updatedAt
)
