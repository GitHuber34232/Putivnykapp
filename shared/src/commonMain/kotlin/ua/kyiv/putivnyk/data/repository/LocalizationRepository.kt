package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.model.LocalizedString

interface LocalizationRepository {
    fun observeByLocale(locale: String): Flow<Map<String, String>>
    suspend fun getByLocale(locale: String): List<LocalizedString>
    suspend fun upsertAll(locale: String, values: Map<String, String>, source: String = "remote")
    suspend fun getValue(key: String, locale: String): String?
    suspend fun upsertValue(key: String, locale: String, value: String, source: String = "mlkit")
    suspend fun clearLocale(locale: String)
    suspend fun clearAll()
}