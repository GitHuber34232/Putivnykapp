package ua.kyiv.putivnyk.i18n

import kotlinx.serialization.Serializable

object SupportedLanguages {
    val majorIso639_1: List<LanguageInfo> = listOf(
        LanguageInfo("uk", "Українська"),
        LanguageInfo("en", "English"),
        LanguageInfo("es", "Español"),
        LanguageInfo("fr", "Français"),
        LanguageInfo("de", "Deutsch"),
        LanguageInfo("it", "Italiano"),
        LanguageInfo("pt", "Português"),
        LanguageInfo("pl", "Polski"),
        LanguageInfo("tr", "Türkçe"),
        LanguageInfo("ar", "العربية"),
        LanguageInfo("hi", "हिन्दी"),
        LanguageInfo("zh", "中文"),
        LanguageInfo("ja", "日本語"),
        LanguageInfo("ko", "한국어"),
        LanguageInfo("nl", "Nederlands")
    )

    fun contains(isoCode: String): Boolean =
        majorIso639_1.any { it.isoCode.equals(isoCode, ignoreCase = true) }
}

@Serializable
data class LanguageInfo(
    val isoCode: String,
    val displayName: String
)