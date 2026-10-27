package ua.kyiv.putivnyk.data.remote.events

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Locale
import ua.kyiv.putivnyk.data.remote.events.model.EventItem
import ua.kyiv.putivnyk.data.remote.events.model.toDomain
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventsBackendRepository @Inject constructor(
    private val api: EventsApi,
    private val telemetry: AppTelemetry,
    private val cache: EventsCache
) {

    private val supportedBackendLanguages = setOf("uk", "en")

    /**
     * Fetches fresh events and stores them in the offline cache.
     * Throws on network or HTTP errors so callers can keep showing cached data.
     */
    suspend fun refreshEvents(language: String = "uk"): List<EventItem> = withContext(Dispatchers.IO) {
        val normalizedLanguage = normalizeBackendLanguage(language)
        val response = try {
            api.getEvents(language = normalizedLanguage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            telemetry.trackWarning("events_fetch_failed", e, mapOf("language" to normalizedLanguage))
            throw e
        }

        if (!response.isSuccessful) {
            val exception = HttpException(response)
            telemetry.trackError(
                "events_http_error",
                exception,
                mapOf("language" to normalizedLanguage, "code" to response.code().toString())
            )
            throw exception
        }

        val items = response.body().orEmpty()
            // A malformed item must not drop the whole list.
            .mapNotNull { dto -> runCatching { dto.toDomain() }.getOrNull() }
            // LazyColumn keys must be unique.
            .distinctBy { it.id }
        cache.write(normalizedLanguage, items)
        telemetry.trackEvent("events_fetch_completed", mapOf("count" to items.size.toString(), "language" to normalizedLanguage))
        items
    }

    suspend fun cachedEvents(language: String = "uk"): EventsCache.Snapshot? =
        cache.read(normalizeBackendLanguage(language))

    private fun normalizeBackendLanguage(language: String): String {
        val normalized = language.lowercase(Locale.ROOT)
        return if (normalized in supportedBackendLanguages) normalized else "uk"
    }
}
