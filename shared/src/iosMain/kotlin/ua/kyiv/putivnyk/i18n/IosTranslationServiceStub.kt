package ua.kyiv.putivnyk.i18n

class IosTranslationServiceStub : TranslationService {
    override suspend fun translateText(text: String, sourceLanguageIso: String, targetLanguageIso: String): String = text

    override fun isSupportedByMlKit(isoCode: String): Boolean = false

    override suspend fun downloadModel(
        sourceLanguageIso: String,
        targetLanguageIso: String,
        timeoutMs: Long
    ): Boolean = false
}