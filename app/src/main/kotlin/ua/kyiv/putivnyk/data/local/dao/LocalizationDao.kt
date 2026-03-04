package ua.kyiv.putivnyk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.local.entity.LocalizedStringEntity

@Dao
interface LocalizationDao {
    @Query("SELECT * FROM localized_strings WHERE locale = :locale")
    suspend fun getByLocale(locale: String): List<LocalizedStringEntity>

    @Query("SELECT * FROM localized_strings WHERE locale = :locale")
    fun observeByLocale(locale: String): Flow<List<LocalizedStringEntity>>

    @Query("SELECT * FROM localized_strings WHERE key = :key AND locale = :locale LIMIT 1")
    suspend fun getByKeyAndLocale(key: String, locale: String): LocalizedStringEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalizedStringEntity>)

    @Query("DELETE FROM localized_strings WHERE locale = :locale")
    suspend fun clearLocale(locale: String)

    @Query("DELETE FROM localized_strings")
    suspend fun clearAll()
}
