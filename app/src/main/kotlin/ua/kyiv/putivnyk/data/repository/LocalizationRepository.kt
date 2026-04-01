package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.kyiv.putivnyk.data.local.dao.LocalizationDao
import ua.kyiv.putivnyk.data.model.LocalizedString
import ua.kyiv.putivnyk.data.model.toDomain
import ua.kyiv.putivnyk.data.model.toEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLocalizationRepository @Inject constructor(
    private val localizationDao: LocalizationDao
) : LocalizationRepository {
    override
    fun observeByLocale(locale: String): Flow<Map<String, String>> =
        localizationDao.observeByLocale(locale).map { list ->
            list.associate { it.key to it.value }
        }

    override
    suspend fun getByLocale(locale: String): List<LocalizedString> =
        localizationDao.getByLocale(locale).map { it.toDomain() }

    override
    suspend fun upsertAll(locale: String, values: Map<String, String>, source: String) {
        val now = System.currentTimeMillis()
        val payload = values.map { (key, value) ->
            LocalizedString(
                key = key,
                locale = locale,
                value = value,
                source = source,
                updatedAt = now
            ).toEntity()
        }
        localizationDao.upsertAll(payload)
    }

    override
    suspend fun getValue(key: String, locale: String): String? {
        return localizationDao.getByKeyAndLocale(key = key, locale = locale)?.value
    }

    override
    suspend fun upsertValue(
        key: String,
        locale: String,
        value: String,
        source: String
    ) {
        upsertAll(locale = locale, values = mapOf(key to value), source = source)
    }

    override
    suspend fun clearLocale(locale: String) {
        localizationDao.clearLocale(locale)
    }

    override
    suspend fun clearAll() {
        localizationDao.clearAll()
    }
}
