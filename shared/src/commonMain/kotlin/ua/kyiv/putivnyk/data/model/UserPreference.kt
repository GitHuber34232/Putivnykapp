package ua.kyiv.putivnyk.data.model

import kotlinx.serialization.Serializable
import ua.kyiv.putivnyk.platform.currentTimeMillis

@Serializable
data class UserPreference(
    val key: String,
    val value: String,
    val updatedAt: Long = currentTimeMillis()
)