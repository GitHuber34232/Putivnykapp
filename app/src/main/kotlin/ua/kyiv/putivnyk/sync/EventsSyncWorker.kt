package ua.kyiv.putivnyk.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ua.kyiv.putivnyk.data.remote.events.EventsBackendRepository
import ua.kyiv.putivnyk.data.repository.SyncStateRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import java.util.Locale

@HiltWorker
class EventsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val eventsBackendRepository: EventsBackendRepository,
    private val syncStateRepository: SyncStateRepository,
    private val userPreferenceRepository: UserPreferenceRepository
) : CoroutineWorker(appContext, params) {

    private val supportedEventLanguages = setOf("uk", "en")

    override suspend fun doWork(): Result {
        val entity = SYNC_ENTITY
        return runCatching {
            syncStateRepository.setRunning(entity)
            val mode = userPreferenceRepository.getString("ui.lang.mode", "auto")
            val manual = userPreferenceRepository.getString("ui.lang.manual", "uk").ifBlank { "uk" }
            val preferred = if (mode == "manual") manual else Locale.getDefault().language.ifBlank { "uk" }
            val language = preferred.lowercase().takeIf { it in supportedEventLanguages } ?: "uk"
            // Refreshes the offline cache that the Events screen shows first.
            eventsBackendRepository.refreshEvents(language)
            syncStateRepository.setSuccess(entity)
            Result.success()
        }.getOrElse { throwable ->
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            syncStateRepository.setError(entity, throwable.message)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "events_sync_periodic"
        const val WORK_NAME_RETRY = "events_sync_retry"
        const val SYNC_ENTITY = "events_backend"
        private const val MAX_ATTEMPTS = 3
    }
}
