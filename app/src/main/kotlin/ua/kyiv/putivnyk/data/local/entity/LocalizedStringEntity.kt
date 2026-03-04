package ua.kyiv.putivnyk.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "localized_strings",
    primaryKeys = ["key", "locale"]
)
data class LocalizedStringEntity(
    val key: String,
    val locale: String,
    val value: String,
    val source: String = "remote",
    val updatedAt: Long = System.currentTimeMillis()
)
