package ua.kyiv.putivnyk.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.local.entity.RouteEntity

@Dao
interface RouteDao {

    @Query("SELECT * FROM routes ORDER BY updatedAt DESC")
    fun getAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getRouteById(id: Long): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity): Long

    @Update
    suspend fun updateRoute(route: RouteEntity)

    @Delete
    suspend fun deleteRoute(route: RouteEntity)

    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()
}
