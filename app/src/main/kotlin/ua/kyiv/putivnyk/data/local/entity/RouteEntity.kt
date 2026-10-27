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
    val transportMode: String = "walking",
    val startName: String? = null,
    val endName: String? = null,
    /** Names of [waypoints] pairs, in order; "" when a point has no name. */
    val waypointNames: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
