package ua.kyiv.putivnyk.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.i18n.OnDeviceTranslationService
import ua.kyiv.putivnyk.i18n.SupportedLanguages
import java.util.Locale

/**
 * Prefetches the translation model for the language the app is shown in, so the first
 * translated screen does not wait for a download. Runs only on unmetered networks.
 */
@HiltWorker
class TranslationModelSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val translationService: OnDeviceTranslationService,
    private val userPreferenceRepository: UserPreferenceRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val mode = userPreferenceRepository.getString("ui.lang.mode", "auto")
            val manual = userPreferenceRepository.getString("ui.lang.manual", "uk").ifBlank { "uk" }
            val preferred = if (mode == "manual") manual else Locale.getDefault().language.ifBlank { "uk" }
            val language = preferred.lowercase().takeIf { SupportedLanguages.contains(it) } ?: "uk"

            // Ukrainian content needs no model; every other language needs the uk -> target pair.
            if (language == "uk" || !translationService.isSupportedByMlKit(language)) {
                Log.d(TAG, "No translation model needed for '$language'")
                return Result.success()
            }

            val ok = translationService.downloadModel(
                sourceLanguageIso = "uk",
                targetLanguageIso = language
            )
            Log.d(TAG, "Translation model uk->$language ready: $ok")

            if (!ok && runAttemptCount < 3) Result.retry() else Result.success()
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
