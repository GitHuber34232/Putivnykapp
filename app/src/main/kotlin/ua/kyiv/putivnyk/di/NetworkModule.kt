package ua.kyiv.putivnyk.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import ua.kyiv.putivnyk.BuildConfig
import ua.kyiv.putivnyk.data.remote.KtorWalkingDirectionsProvider
import ua.kyiv.putivnyk.data.remote.WalkingDirectionsProvider
import ua.kyiv.putivnyk.data.remote.events.KtorEventsRepository
import ua.kyiv.putivnyk.data.repository.EventsRepository
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideEventsHttpClient(okHttpClient: OkHttpClient): HttpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    @Provides
    @Singleton
    @OsrmClient
    fun provideOsrmHttpClient(@OsrmClient okHttpClient: OkHttpClient): HttpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    @Provides
    @Singleton
    fun provideEventsRepository(
        client: HttpClient,
        telemetry: AppTelemetry
    ): EventsRepository = KtorEventsRepository(
        client = client,
        baseUrl = BuildConfig.EVENTS_BASE_URL,
        telemetry = telemetry
    )

    @Provides
    @Singleton
    fun provideWalkingDirectionsProvider(@OsrmClient client: HttpClient): WalkingDirectionsProvider =
        KtorWalkingDirectionsProvider(client)
}
