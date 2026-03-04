package ua.kyiv.putivnyk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.local.entity.SyncStateEntity

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state ORDER BY entityName ASC")
    fun observeAll(): Flow<List<SyncStateEntity>>

    @Query("SELECT * FROM sync_state WHERE entityName = :entityName LIMIT 1")
    suspend fun getByEntity(entityName: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    @Query("DELETE FROM sync_state")
    suspend fun clearAll()
}
