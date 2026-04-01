package ua.kyiv.putivnyk.sync

import android.util.Log
import ua.kyiv.putivnyk.data.cache.OfflineCacheManager
import ua.kyiv.putivnyk.data.repository.EventLanguageSupport
import ua.kyiv.putivnyk.data.repository.EventsRepository
import ua.kyiv.putivnyk.data.repository.SyncStateRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry
import ua.kyiv.putivnyk.i18n.SupportedLanguages
import ua.kyiv.putivnyk.i18n.TranslationService
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSyncOrchestrator @Inject constructor(
    private val eventsRepository: EventsRepository,
    private val syncStateRepository: SyncStateRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val offlineCacheManager: OfflineCacheManager,
    private val telemetry: AppTelemetry,
    private val translationService: TranslationService
) : SyncOrchestrator {

    override suspend fun syncEvents(): SyncRunResult {
        val entity = EventsSyncWorker.SYNC_ENTITY
        return runCatching {
            syncStateRepository.setRunning(entity)
            val mode = userPreferenceRepository.getString("ui.lang.mode", "auto")
            val manual = userPreferenceRepository.getString("ui.lang.manual", "uk").ifBlank { "uk" }
            val preferred = if (mode == "manual") manual else Locale.getDefault().language.ifBlank { "uk" }
            val language = EventLanguageSupport.normalizeBackendLanguage(preferred)
            eventsRepository.getEvents(language)
            syncStateRepository.setSuccess(entity)
            SyncRunResult(shouldRetry = false)
        }.getOrElse { throwable ->
            syncStateRepository.setError(entity, throwable.message)
            SyncRunResult(shouldRetry = true, message = throwable.message)
        }
    }

    override suspend fun syncOfflineCache(): SyncRunResult {
        val entity = OfflineCacheSyncWorker.SYNC_ENTITY
        return runCatching {
            syncStateRepository.setRunning(entity)
            val meta = offlineCacheManager.exportFullSnapshot()
            if (meta != null) {
                syncStateRepository.setSuccess(entity)
                telemetry.trackEvent(
                    "offline_cache_sync_success",
                    mapOf("places" to meta.placesCount.toString(), "routes" to meta.routesCount.toString())
                )
                SyncRunResult(shouldRetry = false)
            } else {
                syncStateRepository.setError(entity, "Export returned null")
                SyncRunResult(shouldRetry = true, message = "Export returned null")
            }
        }.getOrElse { throwable ->
            syncStateRepository.setError(entity, throwable.message)
            SyncRunResult(shouldRetry = true, message = throwable.message)
        }
    }

    override suspend fun syncTranslationModels(runAttemptCount: Int): SyncRunResult {
        return try {
            val targets = SupportedLanguages.majorIso639_1
                .map { it.isoCode.lowercase() }
                .filter { runCatching { translationService.isSupportedByMlKit(it) }.getOrDefault(false) }
                .distinct()

            val sources = listOf("uk", "en")
                .filter { runCatching { translationService.isSupportedByMlKit(it) }.getOrDefault(false) }

            val pairs = targets.flatMap { target ->
                sources.filter { it != target }.map { source -> source to target }
            }

            if (pairs.isEmpty()) {
                Log.d(TranslationModelSyncWorker.TAG, "No translation model pairs to download")
                return SyncRunResult(shouldRetry = false)
            }

            var failCount = 0
            for ((source, target) in pairs) {
                val ok = translationService.downloadModel(sourceLanguageIso = source, targetLanguageIso = target)
                if (!ok) failCount++
            }

            Log.d(
                TranslationModelSyncWorker.TAG,
                "Translation models sync done: ${pairs.size - failCount} OK, $failCount failed out of ${pairs.size}"
            )
            SyncRunResult(shouldRetry = failCount > 0 && runAttemptCount < 3)
        } catch (e: Exception) {
            Log.e(TranslationModelSyncWorker.TAG, "Translation model sync failed", e)
            SyncRunResult(shouldRetry = runAttemptCount < 3, message = e.message)
        }
    }
}