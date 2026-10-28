package ua.kyiv.putivnyk.map.offline

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import ua.kyiv.putivnyk.BuildConfig
import ua.kyiv.putivnyk.data.repository.AchievementsRepository
import ua.kyiv.putivnyk.data.repository.StatsRepository
import ua.kyiv.putivnyk.domain.stats.StatsCounters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Map areas downloaded on request with MapLibre's offline database. Downloaded tiles are used
 * by the online map automatically when there is no connection.
 *
 * The default style uses openstreetmap.org tiles; their usage policy forbids bulk downloads at
 * zoom 17+, so downloads stop at [OSM_MAX_ZOOM] and areas are limited in size. A custom style
 * (Gradle property mapLibreStyleUri) from a provider that allows offline use may go deeper.
 */
@Singleton
class OfflineRegionsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val statsRepository: StatsRepository,
    private val achievementsRepository: AchievementsRepository
) {
    data class DownloadedRegion(
        val regionId: Long,
        val areaId: String,
        val title: String,
        val bounds: GeoBounds,
        val createdAt: Long,
        val completedTiles: Long,
        val requiredTiles: Long,
        val completedBytes: Long,
        val isComplete: Boolean,
        val isDownloading: Boolean,
        val error: String? = null
    ) {
        val fraction: Float
            get() = if (requiredTiles <= 0L) 0f else (completedTiles.toFloat() / requiredTiles).coerceIn(0f, 1f)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager: OfflineManager by lazy {
        MapLibre.getInstance(context)
        OfflineManager.getInstance(context).also { it.setOfflineMapboxTileCountLimit(MAX_TILES_PER_REGION) }
    }
    private val handles = mutableMapOf<Long, OfflineRegion>()

    private val _regions = MutableStateFlow<List<DownloadedRegion>>(emptyList())
    val regions: StateFlow<List<DownloadedRegion>> = _regions.asStateFlow()

    private val _failure = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = _failure.asStateFlow()

    val styleUrl: String get() = BuildConfig.MAPLIBRE_STYLE_URI

    /** The bundled style draws openstreetmap.org tiles, which may only be fetched up to z16. */
    val maxZoom: Int
        get() = if (styleUrl.startsWith("asset://") || styleUrl.contains("openstreetmap.org")) OSM_MAX_ZOOM else CUSTOM_MAX_ZOOM

    fun estimateTiles(bounds: GeoBounds): Long = TileMath.tileCount(bounds, MIN_ZOOM, maxZoom)

    fun estimateBytes(bounds: GeoBounds): Long = TileMath.estimatedBytes(bounds, MIN_ZOOM, maxZoom)

    fun isCovered(latitude: Double, longitude: Double): Boolean =
        _regions.value.any { it.isComplete && it.bounds.contains(latitude, longitude) }

    fun refresh() {
        scope.launch {
            runCatching {
                manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                    override fun onList(offlineRegions: Array<OfflineRegion>?) {
                        offlineRegions.orEmpty().forEach { region ->
                            handles[region.id] = region
                            val entry = region.toEntry() ?: return@forEach
                            upsert(entry)
                            region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                                override fun onStatus(status: OfflineRegionStatus?) {
                                    if (status != null) applyStatus(region.id, status)
                                }

                                override fun onError(error: String?) = Unit
                            })
                        }
                        val known = offlineRegions.orEmpty().map { it.id }.toSet()
                        _regions.update { list -> list.filter { it.regionId in known } }
                    }

                    override fun onError(error: String) {
                        _failure.value = error
                    }
                })
            }.onFailure { Log.w(TAG, "Offline regions unavailable", it) }
        }
    }

    fun download(areaId: String, title: String, bounds: GeoBounds) {
        scope.launch {
            val existing = _regions.value.firstOrNull { it.areaId == areaId && areaId != OfflineAreas.CURRENT_VIEW_ID }
            if (existing != null) {
                resume(existing.regionId)
                return@launch
            }
            val definition = OfflineTilePyramidRegionDefinition(
                styleUrl,
                LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west),
                MIN_ZOOM.toDouble(),
                maxZoom.toDouble(),
                context.resources.displayMetrics.density
            )
            val createdAt = System.currentTimeMillis()
            val metadata = JSONObject()
                .put("areaId", areaId)
                .put("title", title)
                .put("createdAt", createdAt)
                .put("north", bounds.north)
                .put("east", bounds.east)
                .put("south", bounds.south)
                .put("west", bounds.west)
                .toString()
                .toByteArray(Charsets.UTF_8)
            runCatching {
                manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        handles[offlineRegion.id] = offlineRegion
                        upsert(
                            DownloadedRegion(
                                regionId = offlineRegion.id,
                                areaId = areaId,
                                title = title,
                                bounds = bounds,
                                createdAt = createdAt,
                                completedTiles = 0L,
                                requiredTiles = estimateTiles(bounds),
                                completedBytes = 0L,
                                isComplete = false,
                                isDownloading = true
                            )
                        )
                        startDownload(offlineRegion)
                    }

                    override fun onError(error: String) {
                        _failure.value = error
                    }
                })
            }.onFailure { _failure.value = it.message }
        }
    }

    fun pause(regionId: Long) {
        handles[regionId]?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        _regions.update { list -> list.map { if (it.regionId == regionId) it.copy(isDownloading = false) else it } }
    }

    fun resume(regionId: Long) {
        val region = handles[regionId] ?: return
        _regions.update { list -> list.map { if (it.regionId == regionId) it.copy(isDownloading = true, error = null) else it } }
        startDownload(region)
    }

    fun delete(regionId: Long) {
        val region = handles[regionId] ?: return
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                handles.remove(regionId)
                _regions.update { list -> list.filterNot { it.regionId == regionId } }
            }

            override fun onError(error: String) {
                _failure.value = error
            }
        })
    }

    fun clearFailure() {
        _failure.value = null
    }

    private fun startDownload(region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                applyStatus(region.id, status)
                if (status.isComplete) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    scope.launch {
                        runCatching {
                            statsRepository.increment(StatsCounters.OFFLINE_REGIONS)
                            achievementsRepository.evaluate()
                        }
                    }
                }
            }

            override fun onError(error: OfflineRegionError) {
                // Connection errors are retried by MapLibre; the reason is shown meanwhile.
                _regions.update { list ->
                    list.map { if (it.regionId == region.id) it.copy(error = error.reason) else it }
                }
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                _regions.update { list ->
                    list.map { if (it.regionId == region.id) it.copy(isDownloading = false, error = "limit") else it }
                }
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    private fun applyStatus(regionId: Long, status: OfflineRegionStatus) {
        _regions.update { list ->
            list.map { region ->
                if (region.regionId != regionId) return@map region
                region.copy(
                    completedTiles = status.completedResourceCount,
                    requiredTiles = status.requiredResourceCount.coerceAtLeast(1L),
                    completedBytes = status.completedResourceSize,
                    isComplete = status.isComplete,
                    isDownloading = !status.isComplete && status.downloadState == OfflineRegion.STATE_ACTIVE
                )
            }
        }
    }

    private fun upsert(entry: DownloadedRegion) {
        _regions.update { list ->
            if (list.any { it.regionId == entry.regionId }) list.map { if (it.regionId == entry.regionId) entry else it }
            else list + entry
        }
    }

    private fun OfflineRegion.toEntry(): DownloadedRegion? = runCatching {
        val json = JSONObject(String(metadata, Charsets.UTF_8))
        DownloadedRegion(
            regionId = id,
            areaId = json.optString("areaId"),
            title = json.optString("title"),
            bounds = GeoBounds(
                north = json.getDouble("north"),
                east = json.getDouble("east"),
                south = json.getDouble("south"),
                west = json.getDouble("west")
            ),
            createdAt = json.optLong("createdAt"),
            completedTiles = 0L,
            requiredTiles = 1L,
            completedBytes = 0L,
            isComplete = false,
            isDownloading = false
        )
    }.getOrNull()

    companion object {
        private const val TAG = "OfflineRegions"
        const val MIN_ZOOM = 11
        const val OSM_MAX_ZOOM = 16
        const val CUSTOM_MAX_ZOOM = 18
        const val MAX_TILES_PER_REGION = 6_000L
    }
}
