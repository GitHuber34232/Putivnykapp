package ua.kyiv.putivnyk.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ua.kyiv.putivnyk.data.cache.OfflineCacheManager
import ua.kyiv.putivnyk.data.repository.SyncStateRepository
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry

@HiltWorker
class OfflineCacheSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val offlineCacheManager: OfflineCacheManager,
    private val syncStateRepository: SyncStateRepository,
    private val telemetry: AppTelemetry
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            syncStateRepository.setRunning(SYNC_ENTITY)
            val meta = offlineCacheManager.exportFullSnapshot()
            if (meta != null) {
                syncStateRepository.setSuccess(SYNC_ENTITY)
                telemetry.trackEvent(
                    "offline_cache_sync_success",
                    mapOf("places" to meta.placesCount.toString(), "routes" to meta.routesCount.toString())
                )
                Result.success()
            } else {
                syncStateRepository.setError(SYNC_ENTITY, "Export returned null")
                Result.retry()
            }
        }.getOrElse { throwable ->
            syncStateRepository.setError(SYNC_ENTITY, throwable.message)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "offline_cache_sync_periodic"
        const val SYNC_ENTITY = "offline_cache"
    }
}
