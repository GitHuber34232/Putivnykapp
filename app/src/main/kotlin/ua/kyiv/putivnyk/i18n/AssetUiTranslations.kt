package ua.kyiv.putivnyk.i18n

import ua.kyiv.putivnyk.platform.io.TextResourceLoader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetUiTranslations @Inject constructor(
    private val resourceLoader: TextResourceLoader
) : UiTranslationsProvider {
    override
    fun load(language: String): Map<String, String> {
        val safeLanguage = language.lowercase()
        val assetPath = "i18n/ui_${safeLanguage}.json"
        val json = resourceLoader.loadText(assetPath) ?: return emptyMap()

        return UiTranslationsJsonCodec.decode(json)
    }
}
