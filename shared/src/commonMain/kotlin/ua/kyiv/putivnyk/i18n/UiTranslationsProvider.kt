package ua.kyiv.putivnyk.i18n

interface UiTranslationsProvider {
    fun load(language: String): Map<String, String>
}