package ua.kyiv.putivnyk.data.remote.events

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry

class KtorEventsRepositoryTest {

    @Test
    fun getEvents_maps_backend_payload_to_domain() = runTest {
        val repository = KtorEventsRepository(
            client = mockClient(
                """
                [
                  {
                    "id": "evt-1",
                    "title": "Jazz Evening",
                    "description": "Live music",
                    "category": "concert",
                    "starts_at": "2026-04-01T19:00:00Z",
                    "ends_at": "2026-04-01T21:00:00Z",
                    "location_name": "Kyiv Hall",
                    "latitude": 50.45,
                    "longitude": 30.52,
                    "price_from": 200,
                    "price_to": 350,
                    "cover_url": "https://example.com/cover.jpg"
                  }
                ]
                """.trimIndent()
            ),
            baseUrl = "https://api.example.com/",
            telemetry = RecordingTelemetry()
        )

        val items = repository.getEvents("en")

        assertEquals(1, items.size)
        assertEquals("evt-1", items.first().id)
        assertEquals("Концерт", items.first().category)
        assertEquals("200-350 грн", items.first().priceLabel)
    }

    @Test
    fun getEvents_returns_empty_list_for_http_error() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = "[]",
                        status = HttpStatusCode.BadGateway,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val repository = KtorEventsRepository(client, "https://api.example.com/", RecordingTelemetry())

        val items = repository.getEvents("uk")

        assertTrue(items.isEmpty())
    }

    private fun mockClient(body: String): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler {
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private class RecordingTelemetry : AppTelemetry {
        override fun trackEvent(name: String, attributes: Map<String, String>) = Unit
        override fun trackWarning(name: String, throwable: Throwable?, attributes: Map<String, String>) = Unit
        override fun trackError(name: String, throwable: Throwable?, attributes: Map<String, String>) = Unit
    }
}