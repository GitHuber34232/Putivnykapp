package ua.kyiv.putivnyk.data.cache

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineCacheManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val placeRepository: PlaceRepository,
    private val routeRepository: RouteRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val gson: Gson,
    private val telemetry: AppTelemetry
) {
    private val mutex = Mutex()
    private val cacheDir get() = File(context.filesDir, "offline_cache").also { it.mkdirs() }
    private val placesFile get() = File(cacheDir, "places.json")
    private val routesFile get() = File(cacheDir, "routes.json")
    private val prefsFile get() = File(cacheDir, "preferences.json")
    private val metaFile get() = File(cacheDir, "cache_meta.json")

    suspend fun exportFullSnapshot() = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val allPlaces = placeRepository.getAllPlacesSnapshot()
                val allRoutes = routeRepository.getAllRoutesSnapshot()

                placesFile.writeText(gson.toJson(allPlaces))
                routesFile.writeText(gson.toJson(allRoutes))

                val prefs = userPreferenceRepository.getAllAsMap()
                prefsFile.writeText(gson.toJson(prefs))

                val meta = CacheMeta(
                    exportedAt = System.currentTimeMillis(),
                    placesCount = allPlaces.size,
                    routesCount = allRoutes.size,
                    prefsCount = prefs.size
                )
                metaFile.writeText(gson.toJson(meta))

                telemetry.trackEvent(
                    "offline_cache_exported",
                    mapOf(
                        "places" to allPlaces.size.toString(),
                        "routes" to allRoutes.size.toString()
                    )
                )
                meta
            }.getOrElse {
                telemetry.trackError("offline_cache_export_failed", it)
                null
            }
        }
    }

    suspend fun importSnapshot(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                if (!placesFile.exists()) return@withContext false

                val placesType = object : TypeToken<List<Place>>() {}.type
                val places: List<Place> = gson.fromJson(placesFile.readText(), placesType)
                if (places.isNotEmpty()) {
                    placeRepository.savePlaces(places)
                }

                if (routesFile.exists()) {
                    val routesType = object : TypeToken<List<Route>>() {}.type
                    val routes: List<Route> = gson.fromJson(routesFile.readText(), routesType)
                    routes.forEach { routeRepository.saveRoute(it) }
                }

                if (prefsFile.exists()) {
                    val prefsType = object : TypeToken<Map<String, String>>() {}.type
                    val prefs: Map<String, String> = gson.fromJson(prefsFile.readText(), prefsType)
                    prefs.forEach { (key, value) ->
                        userPreferenceRepository.upsert(key, value)
                    }
                }

                telemetry.trackEvent("offline_cache_imported", mapOf("places" to places.size.toString()))
                true
            }.getOrElse {
                telemetry.trackError("offline_cache_import_failed", it)
                false
            }
        }
    }

    suspend fun getCacheMeta(): CacheMeta? = withContext(Dispatchers.IO) {
        runCatching {
            if (!metaFile.exists()) return@withContext null
            gson.fromJson(metaFile.readText(), CacheMeta::class.java)
        }.getOrNull()
    }

    suspend fun hasCachedData(): Boolean = withContext(Dispatchers.IO) {
        placesFile.exists() && placesFile.length() > 2
    }

    suspend fun clearCache() = mutex.withLock {
        withContext(Dispatchers.IO) {
            cacheDir.listFiles()?.forEach { it.delete() }
            telemetry.trackEvent("offline_cache_cleared")
        }
    }

    data class CacheMeta(
        val exportedAt: Long,
        val placesCount: Int,
        val routesCount: Int,
        val prefsCount: Int
    )
}
