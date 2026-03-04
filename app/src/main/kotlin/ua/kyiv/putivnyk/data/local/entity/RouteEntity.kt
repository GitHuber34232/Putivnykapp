package ua.kyiv.putivnyk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String?,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val waypoints: List<Double> = emptyList(),
    val distance: Double,
    val estimatedDuration: Int,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
