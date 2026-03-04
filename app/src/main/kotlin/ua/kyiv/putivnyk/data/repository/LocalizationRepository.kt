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
class LocalizationRepository @Inject constructor(
    private val localizationDao: LocalizationDao
) {
    fun observeByLocale(locale: String): Flow<Map<String, String>> =
        localizationDao.observeByLocale(locale).map { list ->
            list.associate { it.key to it.value }
        }

    suspend fun getByLocale(locale: String): List<LocalizedString> =
        localizationDao.getByLocale(locale).map { it.toDomain() }

    suspend fun upsertAll(locale: String, values: Map<String, String>, source: String = "remote") {
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

    suspend fun getValue(key: String, locale: String): String? {
        return localizationDao.getByKeyAndLocale(key = key, locale = locale)?.value
    }

    suspend fun upsertValue(
        key: String,
        locale: String,
        value: String,
        source: String = "mlkit"
    ) {
        upsertAll(locale = locale, values = mapOf(key to value), source = source)
    }

    suspend fun clearLocale(locale: String) {
        localizationDao.clearLocale(locale)
    }

    suspend fun clearAll() {
        localizationDao.clearAll()
    }
}
