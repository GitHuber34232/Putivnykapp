package ua.kyiv.putivnyk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.local.entity.UserPreferenceEntity

@Dao
interface UserPreferenceDao {
    @Query("SELECT * FROM user_preferences ORDER BY key ASC")
    fun observeAll(): Flow<List<UserPreferenceEntity>>

    @Query("SELECT * FROM user_preferences WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): UserPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: UserPreferenceEntity)

    @Query("DELETE FROM user_preferences WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM user_preferences")
    suspend fun clearAll()
}
