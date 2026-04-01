package ua.kyiv.putivnyk.data.model

import kotlinx.serialization.Serializable
import ua.kyiv.putivnyk.platform.currentTimeMillis

@Serializable
data class LocalizedString(
    val key: String,
    val locale: String,
    val value: String,
    val source: String = "remote",
    val updatedAt: Long = currentTimeMillis()
)