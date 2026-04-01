package ua.kyiv.putivnyk.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry
import ua.kyiv.putivnyk.data.telemetry.LogcatTelemetry
import ua.kyiv.putivnyk.BuildConfig
import ua.kyiv.putivnyk.domain.usecase.recommendation.RecommendationEngine
import ua.kyiv.putivnyk.domain.usecase.routing.RouteOptimizer
import ua.kyiv.putivnyk.domain.usecase.routing.SmartRouteBuilder
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val allowedBaseUrl = BuildConfig.EVENTS_BASE_URL.trim().toHttpUrlOrNull()
            ?: error("Invalid EVENTS_BASE_URL")
        require(allowedBaseUrl.isHttps) { "EVENTS_BASE_URL must use HTTPS" }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val requestUrl = request.url
                if (!requestUrl.isHttps) {
                    throw IOException("Blocked non-HTTPS request")
                }
                if (requestUrl.host != allowedBaseUrl.host || requestUrl.port != allowedBaseUrl.port) {
                    throw IOException("Blocked request to unexpected host")
                }
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @OsrmClient
    fun provideOsrmClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (!request.url.isHttps) {
                    throw IOException("Blocked non-HTTPS request")
                }
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideWorkConfiguration(workerFactory: HiltWorkerFactory): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    }

    @Provides
    @Singleton
    fun provideTelemetry(): AppTelemetry = LogcatTelemetry()

    @Provides
    @Singleton
    fun provideRouteOptimizer(): RouteOptimizer = RouteOptimizer()

    @Provides
    @Singleton
    fun provideSmartRouteBuilder(routeOptimizer: RouteOptimizer): SmartRouteBuilder =
        SmartRouteBuilder(routeOptimizer)

    @Provides
    @Singleton
    fun provideRecommendationEngine(): RecommendationEngine = RecommendationEngine()
}
