package ua.kyiv.putivnyk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One visit of a place; [source] is "auto", "geofence", "manual" or "import". */
@Entity(
    tableName = "visits",
    indices = [Index("placeId"), Index("visitedAt")]
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val placeId: Long,
    val visitedAt: Long,
    val source: String
)

/** Walking totals for one day ("yyyy-MM-dd", Kyiv time). */
@Entity(tableName = "walk_days")
data class WalkDayEntity(
    @PrimaryKey
    val day: String,
    val distanceMeters: Double,
    val activeMillis: Long,
    val routesCompleted: Int,
    val firstActivityAt: Long,
    val lastActivityAt: Long
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String,
    val unlockedAt: Long
)

/** Simple event counters, e.g. how many landmarks were recognised in AR. */
@Entity(tableName = "counters")
data class CounterEntity(
    @PrimaryKey
    val name: String,
    val value: Long
)
