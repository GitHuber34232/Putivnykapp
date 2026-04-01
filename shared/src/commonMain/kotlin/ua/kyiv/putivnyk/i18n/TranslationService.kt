package ua.kyiv.putivnyk.i18n

interface TranslationService {
    suspend fun translateText(text: String, sourceLanguageIso: String, targetLanguageIso: String): String
    fun isSupportedByMlKit(isoCode: String): Boolean
    suspend fun downloadModel(
        sourceLanguageIso: String,
        targetLanguageIso: String,
        timeoutMs: Long = MODEL_DOWNLOAD_TIMEOUT_MS
    ): Boolean

    suspend fun deleteDownloadedModels(): Int = 0

    companion object {
        const val MODEL_DOWNLOAD_TIMEOUT_MS = 90_000L
    }
}