package ua.kyiv.putivnyk.data.model

import ua.kyiv.putivnyk.data.local.entity.UserPreferenceEntity

data class UserPreference(
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

fun UserPreference.toEntity(): UserPreferenceEntity = UserPreferenceEntity(
    key = key,
    value = value,
    updatedAt = updatedAt
)

fun UserPreferenceEntity.toDomain(): UserPreference = UserPreference(
    key = key,
    value = value,
    updatedAt = updatedAt
)
