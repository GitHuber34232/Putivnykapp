package ua.kyiv.putivnyk.data.local

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineTileCacheManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OfflineTileCache"
        private const val REGION_NAME = "kyiv-tiles"
        private const val MIN_ZOOM = 10.0
        private const val MAX_ZOOM = 16.0
        private const val PIXEL_RATIO = 1.0f
    }

    private val kyivBounds = LatLngBounds.Builder()
        .include(LatLng(50.25, 30.15))
        .include(LatLng(50.65, 30.90))
        .build()

    fun ensureCached(styleUri: String) {
        try {
            MapLibre.getInstance(context)
        } catch (e: Exception) {
            Log.w(TAG, "MapLibre not initialized, skipping tile cache", e)
            return
        }

        val offlineManager = OfflineManager.getInstance(context)
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val existing = offlineRegions?.firstOrNull { region ->
                    String(region.metadata) == REGION_NAME
                }
                if (existing != null) {
                    existing.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            if (status == null) return
                            if (status.isComplete) {
                                Log.d(TAG, "Kyiv tiles already cached (${status.completedResourceCount} resources)")
                            } else {
                                Log.d(TAG, "Resuming download: ${status.completedResourceCount}/${status.requiredResourceCount}")
                                startDownload(existing)
                            }
                        }

                        override fun onError(error: String?) {
                            Log.w(TAG, "Status check error: $error, re-downloading")
                            startDownload(existing)
                        }
                    })
                } else {
                    createAndDownload(offlineManager, styleUri)
                }
            }

            override fun onError(error: String) {
                Log.e(TAG, "Failed to list offline regions: $error")
            }
        })
    }

    private fun createAndDownload(offlineManager: OfflineManager, styleUri: String) {
        val definition = OfflineTilePyramidRegionDefinition(
            styleUri,
            kyivBounds,
            MIN_ZOOM,
            MAX_ZOOM,
            PIXEL_RATIO
        )
        offlineManager.createOfflineRegion(
            definition,
            REGION_NAME.toByteArray(),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    Log.d(TAG, "Created offline region, starting download")
                    startDownload(offlineRegion)
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Failed to create offline region: $error")
                }
            }
        )
    }

    private fun startDownload(region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                if (status.isComplete) {
                    Log.d(TAG, "Kyiv tiles download complete: ${status.completedResourceCount} resources")
                    region.setObserver(null)
                }
            }

            override fun onError(error: OfflineRegionError) {
                Log.w(TAG, "Download error: ${error.reason} – ${error.message}")
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                Log.w(TAG, "Tile count limit exceeded: $limit")
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }
}
