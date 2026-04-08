package ua.kyiv.putivnyk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "places",
    indices = [Index("category")]
)
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val nameEn: String?,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val tags: List<String> = emptyList(),
    val description: String?,
    val rating: Double?,
    val popularity: Int = 0,
    val riverBank: String = "unknown",
    val visitDuration: Int?,
    val imageUrls: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isVisited: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
