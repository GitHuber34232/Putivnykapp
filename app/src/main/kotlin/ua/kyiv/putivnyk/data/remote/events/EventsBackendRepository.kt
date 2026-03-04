package ua.kyiv.putivnyk.data.remote.events

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
    private val telemetry: AppTelemetry
) {

    private val supportedBackendLanguages = setOf("uk", "en")

    suspend fun getEvents(language: String = "uk"): List<EventItem> = withContext(Dispatchers.IO) {
        val normalizedLanguage = normalizeBackendLanguage(language)
        val response = runCatching {
            api.getEvents(language = normalizedLanguage)
        }.onFailure {
            telemetry.trackWarning("events_fetch_failed", it, mapOf("language" to normalizedLanguage))
        }.getOrElse {
            return@withContext emptyList()
        }

        if (!response.isSuccessful) {
            val exception = HttpException(response)
            telemetry.trackError(
                "events_http_error",
                exception,
                mapOf("language" to normalizedLanguage, "code" to response.code().toString())
            )
            return@withContext emptyList()
        }

        val body = response.body().orEmpty()
        telemetry.trackEvent("events_fetch_completed", mapOf("count" to body.size.toString(), "language" to normalizedLanguage))
        body.map { it.toDomain() }
    }

    private fun normalizeBackendLanguage(language: String): String {
        val normalized = language.lowercase(Locale.ROOT)
        return if (normalized in supportedBackendLanguages) normalized else "uk"
    }
}
