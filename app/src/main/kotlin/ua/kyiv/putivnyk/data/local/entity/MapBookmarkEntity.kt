package ua.kyiv.putivnyk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "map_bookmarks")
data class MapBookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val latitude: Double,
    val longitude: Double,
    val zoomLevel: Int = 14,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
