package ua.kyiv.putivnyk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.local.entity.AchievementEntity
import ua.kyiv.putivnyk.data.local.entity.CounterEntity
import ua.kyiv.putivnyk.data.local.entity.VisitEntity
import ua.kyiv.putivnyk.data.local.entity.WalkDayEntity

@Dao
abstract class StatsDao {

    @Insert
    abstract suspend fun insertVisit(visit: VisitEntity): Long

    @Query("DELETE FROM visits WHERE placeId = :placeId")
    abstract suspend fun deleteVisitsForPlace(placeId: Long)

    @Query("SELECT * FROM visits ORDER BY visitedAt DESC")
    abstract fun observeVisits(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits ORDER BY visitedAt DESC")
    abstract suspend fun getVisits(): List<VisitEntity>

    // SQLite on Android 10 has no UPSERT, so days and counters are created first, then updated.
    @Query(
        "INSERT OR IGNORE INTO walk_days (day, distanceMeters, activeMillis, routesCompleted, firstActivityAt, lastActivityAt) " +
            "VALUES (:day, 0, 0, 0, :at, :at)"
    )
    abstract suspend fun ensureDay(day: String, at: Long)

    @Query(
        "UPDATE walk_days SET distanceMeters = distanceMeters + :meters, activeMillis = activeMillis + :millis, " +
            "firstActivityAt = MIN(firstActivityAt, :startedAt), lastActivityAt = MAX(lastActivityAt, :endedAt) WHERE day = :day"
    )
    abstract suspend fun addToDay(day: String, meters: Double, millis: Long, startedAt: Long, endedAt: Long)

    @Query("UPDATE walk_days SET routesCompleted = routesCompleted + 1, lastActivityAt = MAX(lastActivityAt, :at) WHERE day = :day")
    abstract suspend fun incrementRoutesCompleted(day: String, at: Long)

    @Transaction
    open suspend fun addWalk(day: String, meters: Double, millis: Long, startedAt: Long, endedAt: Long) {
        ensureDay(day, startedAt)
        addToDay(day, meters, millis, startedAt, endedAt)
    }

    @Transaction
    open suspend fun addCompletedRoute(day: String, at: Long) {
        ensureDay(day, at)
        incrementRoutesCompleted(day, at)
    }

    @Query("SELECT * FROM walk_days ORDER BY day DESC")
    abstract fun observeDays(): Flow<List<WalkDayEntity>>

    @Query("SELECT * FROM walk_days ORDER BY day DESC")
    abstract suspend fun getDays(): List<WalkDayEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAchievement(achievement: AchievementEntity): Long

    @Query("SELECT * FROM achievements")
    abstract fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    abstract suspend fun getAchievements(): List<AchievementEntity>

    @Query("INSERT OR IGNORE INTO counters (name, value) VALUES (:name, 0)")
    abstract suspend fun ensureCounter(name: String)

    @Query("UPDATE counters SET value = value + :delta WHERE name = :name")
    abstract suspend fun addToCounter(name: String, delta: Long)

    @Transaction
    open suspend fun incrementCounter(name: String, delta: Long) {
        ensureCounter(name)
        addToCounter(name, delta)
    }

    @Query("SELECT * FROM counters")
    abstract fun observeCounters(): Flow<List<CounterEntity>>

    @Query("SELECT * FROM counters")
    abstract suspend fun getCounters(): List<CounterEntity>
}
