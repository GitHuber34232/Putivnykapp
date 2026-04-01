package ua.kyiv.putivnyk.data.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry
import ua.kyiv.putivnyk.platform.io.FileSystemProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineCacheManager @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val routeRepository: RouteRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val telemetry: AppTelemetry,
    private val fileSystemProvider: FileSystemProvider
) {
    private val mutex = Mutex()
    private val cacheDir = "offline_cache"
    private val placesFile = "$cacheDir/places.json"
    private val routesFile = "$cacheDir/routes.json"
    private val prefsFile = "$cacheDir/preferences.json"
    private val metaFile = "$cacheDir/cache_meta.json"

    suspend fun exportFullSnapshot() = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                fileSystemProvider.ensureDirectory(cacheDir)
                val allPlaces = placeRepository.getAllPlacesSnapshot()
                val allRoutes = routeRepository.getAllRoutesSnapshot()

                fileSystemProvider.writeText(placesFile, OfflineCacheSnapshotCodec.encodePlaces(allPlaces))
                fileSystemProvider.writeText(routesFile, OfflineCacheSnapshotCodec.encodeRoutes(allRoutes))

                val prefs = userPreferenceRepository.getAllAsMap()
                fileSystemProvider.writeText(prefsFile, OfflineCacheSnapshotCodec.encodePreferences(prefs))

                val meta = CacheMeta(
                    exportedAt = System.currentTimeMillis(),
                    placesCount = allPlaces.size,
                    routesCount = allRoutes.size,
                    prefsCount = prefs.size
                )
                fileSystemProvider.writeText(metaFile, OfflineCacheSnapshotCodec.encodeMeta(meta))

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
                if (!fileSystemProvider.exists(placesFile)) return@withContext false

                val places = OfflineCacheSnapshotCodec.decodePlaces(fileSystemProvider.readText(placesFile).orEmpty())
                if (places.isNotEmpty()) {
                    placeRepository.savePlaces(places)
                }

                if (fileSystemProvider.exists(routesFile)) {
                    val routes = OfflineCacheSnapshotCodec.decodeRoutes(fileSystemProvider.readText(routesFile).orEmpty())
                    routes.forEach { routeRepository.saveRoute(it) }
                }

                if (fileSystemProvider.exists(prefsFile)) {
                    val prefs = OfflineCacheSnapshotCodec.decodePreferences(fileSystemProvider.readText(prefsFile).orEmpty())
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
            if (!fileSystemProvider.exists(metaFile)) return@withContext null
            OfflineCacheSnapshotCodec.decodeMeta(fileSystemProvider.readText(metaFile).orEmpty())
        }.getOrNull()
    }

    suspend fun hasCachedData(): Boolean = withContext(Dispatchers.IO) {
        fileSystemProvider.exists(placesFile) && fileSystemProvider.size(placesFile) > 2
    }

    suspend fun clearCache() = mutex.withLock {
        withContext(Dispatchers.IO) {
            fileSystemProvider.list(cacheDir).forEach { fileSystemProvider.delete(it) }
            telemetry.trackEvent("offline_cache_cleared")
        }
    }
}
