package ua.kyiv.putivnyk.i18n

import platform.Foundation.NSBundle
import ua.kyiv.putivnyk.platform.io.BundleTextResourceLoader
import ua.kyiv.putivnyk.platform.io.TextResourceLoader

class BundleUiTranslationsProvider(
    bundle: NSBundle = NSBundle.mainBundle,
    private val resourceLoader: TextResourceLoader = BundleTextResourceLoader(bundle)
) : UiTranslationsProvider {

    override fun load(language: String): Map<String, String> {
        val safeLanguage = language.lowercase()
        val rawJson = resourceLoader.loadText("i18n/ui_${safeLanguage}.json")
            ?: resourceLoader.loadText("ui_${safeLanguage}.json")
            ?: return emptyMap()
        return UiTranslationsJsonCodec.decode(rawJson)
    }
}