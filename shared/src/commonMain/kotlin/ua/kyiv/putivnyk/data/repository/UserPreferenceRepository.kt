package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.model.UserPreference

interface UserPreferenceRepository {
    fun observeAll(): Flow<List<UserPreference>>
    fun observeAsMap(): Flow<Map<String, String>>
    suspend fun getByKey(key: String): UserPreference?
    suspend fun getString(key: String, defaultValue: String = ""): String
    suspend fun upsert(key: String, value: String)
    suspend fun deleteByKey(key: String)
    suspend fun getAllAsMap(): Map<String, String>
    suspend fun clearAll()
}