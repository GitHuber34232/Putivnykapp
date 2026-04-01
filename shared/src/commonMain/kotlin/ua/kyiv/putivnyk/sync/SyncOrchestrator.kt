package ua.kyiv.putivnyk.sync

data class SyncRunResult(
    val shouldRetry: Boolean,
    val message: String? = null
)

interface SyncOrchestrator {
    suspend fun syncEvents(): SyncRunResult
    suspend fun syncOfflineCache(): SyncRunResult
    suspend fun syncTranslationModels(runAttemptCount: Int): SyncRunResult
}