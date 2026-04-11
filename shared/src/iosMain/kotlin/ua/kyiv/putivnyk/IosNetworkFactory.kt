package ua.kyiv.putivnyk

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import ua.kyiv.putivnyk.data.remote.KtorWalkingDirectionsProvider
import ua.kyiv.putivnyk.data.remote.events.KtorEventsRepository
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry

class IosNetworkFactory {
    private val httpClient by lazy {
        HttpClient(Darwin) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                    isLenient = true
                })
            }
        }
    }

    private val telemetry: AppTelemetry = object : AppTelemetry {
        override fun trackEvent(name: String, attributes: Map<String, String>) = Unit

        override fun trackWarning(name: String, throwable: Throwable?, attributes: Map<String, String>) = Unit

        override fun trackError(name: String, throwable: Throwable?, attributes: Map<String, String>) = Unit
    }

    fun createWalkingDirectionsProvider(): KtorWalkingDirectionsProvider =
        KtorWalkingDirectionsProvider(client = httpClient)

    fun createEventsRepository(baseUrl: String): KtorEventsRepository =
        KtorEventsRepository(client = httpClient, baseUrl = baseUrl, telemetry = telemetry)
}