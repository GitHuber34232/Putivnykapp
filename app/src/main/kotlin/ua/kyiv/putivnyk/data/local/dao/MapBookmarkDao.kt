package ua.kyiv.putivnyk.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.local.entity.MapBookmarkEntity

@Dao
interface MapBookmarkDao {
    @Query("SELECT * FROM map_bookmarks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MapBookmarkEntity>>

    @Query("SELECT * FROM map_bookmarks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MapBookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: MapBookmarkEntity): Long

    @Update
    suspend fun update(bookmark: MapBookmarkEntity)

    @Delete
    suspend fun delete(bookmark: MapBookmarkEntity)

    @Query("DELETE FROM map_bookmarks")
    suspend fun deleteAll()
}
