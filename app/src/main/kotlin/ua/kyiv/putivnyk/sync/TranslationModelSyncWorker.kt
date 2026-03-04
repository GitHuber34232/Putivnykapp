package ua.kyiv.putivnyk.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ua.kyiv.putivnyk.i18n.OnDeviceTranslationService
import ua.kyiv.putivnyk.i18n.SupportedLanguages

@HiltWorker
class TranslationModelSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val translationService: OnDeviceTranslationService
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
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
                Log.d(TAG, "No translation model pairs to download")
                return Result.success()
            }

            var successCount = 0
            var failCount = 0

            for ((source, target) in pairs) {
                val ok = translationService.downloadModel(
                    sourceLanguageIso = source,
                    targetLanguageIso = target
                )
                if (ok) successCount++ else failCount++
            }

            Log.d(TAG, "Translation models sync done: $successCount OK, $failCount failed out of ${pairs.size}")

            if (failCount > 0 && runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation model sync failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        const val TAG = "TranslationModelSync"
        const val WORK_NAME_PERIODIC = "translation_model_sync_periodic"
        const val WORK_NAME_IMMEDIATE = "translation_model_sync_immediate"
    }
}
