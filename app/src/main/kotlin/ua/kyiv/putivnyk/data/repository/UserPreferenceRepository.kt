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
class RoomUserPreferenceRepository @Inject constructor(
    private val userPreferenceDao: UserPreferenceDao
) : UserPreferenceRepository {
    override
    fun observeAll(): Flow<List<UserPreference>> =
        userPreferenceDao.observeAll().map { list -> list.map { it.toDomain() } }

    override
    fun observeAsMap(): Flow<Map<String, String>> =
        observeAll().map { list -> list.associate { it.key to it.value } }

    override
    suspend fun getByKey(key: String): UserPreference? =
        userPreferenceDao.getByKey(key)?.toDomain()

    override
    suspend fun getString(key: String, defaultValue: String): String =
        getByKey(key)?.value ?: defaultValue

    override
    suspend fun upsert(key: String, value: String) {
        userPreferenceDao.upsert(
            UserPreference(
                key = key,
                value = value,
                updatedAt = System.currentTimeMillis()
            ).toEntity()
        )
    }

    override
    suspend fun deleteByKey(key: String) {
        userPreferenceDao.deleteByKey(key)
    }

    override
    suspend fun getAllAsMap(): Map<String, String> =
        observeAll().first().associate { it.key to it.value }

    override
    suspend fun clearAll() {
        userPreferenceDao.clearAll()
    }
}
