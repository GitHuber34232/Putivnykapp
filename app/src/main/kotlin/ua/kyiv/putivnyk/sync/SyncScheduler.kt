package ua.kyiv.putivnyk.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    fun ensurePeriodicEventsSync(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<EventsSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            EventsSyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun ensurePeriodicOfflineCacheSync(workManager: WorkManager) {
        val request = PeriodicWorkRequestBuilder<OfflineCacheSyncWorker>(4, TimeUnit.HOURS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            OfflineCacheSyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleEventsRetry(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<EventsSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            EventsSyncWorker.WORK_NAME_RETRY,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun ensureTranslationModelSync(workManager: WorkManager) {

        val immediateConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val immediateRequest = OneTimeWorkRequestBuilder<TranslationModelSyncWorker>()
            .setConstraints(immediateConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            TranslationModelSyncWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.KEEP,
            immediateRequest
        )

        val periodicConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<TranslationModelSyncWorker>(
            12, TimeUnit.HOURS
        )
            .setConstraints(periodicConstraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            TranslationModelSyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }
}
