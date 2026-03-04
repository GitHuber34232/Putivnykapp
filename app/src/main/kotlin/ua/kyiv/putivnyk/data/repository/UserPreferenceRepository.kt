package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ua.kyiv.putivnyk.data.local.dao.UserPreferenceDao
import ua.kyiv.putivnyk.data.model.UserPreference
import ua.kyiv.putivnyk.data.model.toDomain
import ua.kyiv.putivnyk.data.model.toEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferenceRepository @Inject constructor(
    private val userPreferenceDao: UserPreferenceDao
) {
    fun observeAll(): Flow<List<UserPreference>> =
        userPreferenceDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeAsMap(): Flow<Map<String, String>> =
        observeAll().map { list -> list.associate { it.key to it.value } }

    suspend fun getByKey(key: String): UserPreference? =
        userPreferenceDao.getByKey(key)?.toDomain()

    suspend fun getString(key: String, defaultValue: String = ""): String =
        getByKey(key)?.value ?: defaultValue

    suspend fun upsert(key: String, value: String) {
        userPreferenceDao.upsert(
            UserPreference(
                key = key,
                value = value,
                updatedAt = System.currentTimeMillis()
            ).toEntity()
        )
    }

    suspend fun deleteByKey(key: String) {
        userPreferenceDao.deleteByKey(key)
    }

    suspend fun getAllAsMap(): Map<String, String> =
        observeAll().first().associate { it.key to it.value }

    suspend fun clearAll() {
        userPreferenceDao.clearAll()
    }
}
