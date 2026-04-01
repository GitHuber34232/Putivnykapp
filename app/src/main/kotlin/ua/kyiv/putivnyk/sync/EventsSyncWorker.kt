package ua.kyiv.putivnyk.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EventsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncOrchestrator: SyncOrchestrator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val result = syncOrchestrator.syncEvents()
        return if (result.shouldRetry) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME_PERIODIC = "events_sync_periodic"
        const val WORK_NAME_RETRY = "events_sync_retry"
        const val SYNC_ENTITY = "events_backend"
    }
}
