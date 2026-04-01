package ua.kyiv.putivnyk.data.remote.events

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import ua.kyiv.putivnyk.data.model.EventItem
import ua.kyiv.putivnyk.data.remote.events.model.EventDto
import ua.kyiv.putivnyk.data.remote.events.model.toDomain
import ua.kyiv.putivnyk.data.repository.EventLanguageSupport
import ua.kyiv.putivnyk.data.repository.EventsRepository
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry

class KtorEventsRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    private val telemetry: AppTelemetry
) : EventsRepository {

    override suspend fun getEvents(language: String): List<EventItem> {
        val normalizedLanguage = EventLanguageSupport.normalizeBackendLanguage(language)
        val response = runCatching {
            client.get(baseUrl.trimEnd('/') + "/events") {
                parameter("city", "kyiv")
                parameter("lang", normalizedLanguage)
            }
        }.onFailure {
            telemetry.trackWarning("events_fetch_failed", it, mapOf("language" to normalizedLanguage))
        }.getOrElse {
            return emptyList()
        }

        if (!response.status.isSuccess()) {
            telemetry.trackError(
                "events_http_error",
                attributes = mapOf(
                    "language" to normalizedLanguage,
                    "code" to response.status.value.toString()
                )
            )
            return emptyList()
        }

        val body = runCatching { response.body<List<EventDto>>() }
            .onFailure {
                telemetry.trackError("events_response_decode_failed", it, mapOf("language" to normalizedLanguage))
            }
            .getOrElse { return emptyList() }

        telemetry.trackEvent(
            "events_fetch_completed",
            mapOf("count" to body.size.toString(), "language" to normalizedLanguage)
        )
        return body.map { it.toDomain() }
    }
}