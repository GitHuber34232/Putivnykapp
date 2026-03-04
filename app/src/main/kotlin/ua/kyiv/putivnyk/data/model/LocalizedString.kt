package ua.kyiv.putivnyk.data.model

import ua.kyiv.putivnyk.data.local.entity.LocalizedStringEntity

data class LocalizedString(
    val key: String,
    val locale: String,
    val value: String,
    val source: String = "remote",
    val updatedAt: Long = System.currentTimeMillis()
)

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
