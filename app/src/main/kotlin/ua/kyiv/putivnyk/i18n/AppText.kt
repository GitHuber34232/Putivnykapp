package ua.kyiv.putivnyk.i18n

import ua.kyiv.putivnyk.data.repository.LocalizationRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI strings for places without Compose (notifications, widgets, the audio guide), resolved
 * the same way as on screen: Ukrainian source, cached ML Kit translations, English assets.
 */
@Singleton
class AppText @Inject constructor(
    private val userPreferenceRepository: UserPreferenceRepository,
    private val localizationRepository: LocalizationRepository,
    private val assetUiTranslations: AssetUiTranslations
) {
    @Volatile
    private var english: Map<String, String>? = null

    suspend fun language(): String {
        val mode = userPreferenceRepository.getString("ui.lang.mode", "auto")
        val manual = userPreferenceRepository.getString("ui.lang.manual", "uk").ifBlank { "uk" }
        val raw = if (mode == "manual") manual else Locale.getDefault().language.ifBlank { "uk" }
        val language = raw.lowercase()
        return if (SupportedLanguages.contains(language)) language else "uk"
    }

    suspend fun get(key: String, fallbackUk: String): String {
        val language = language()
        if (language == "uk") return fallbackUk
        localizationRepository.getValue(key, language)?.takeIf { it.isNotBlank() }?.let { return it }
        val englishTexts = english ?: runCatching { assetUiTranslations.load("en") }.getOrDefault(emptyMap()).also { english = it }
        return englishTexts[key] ?: fallbackUk
    }

    /** Place names and descriptions: the cached on-device translation, or the original. */
    suspend fun dynamic(text: String): String {
        val source = text.trim()
        if (source.isEmpty()) return text
        val language = language()
        if (language == "uk") return text
        return localizationRepository.getValue(dynamicTranslationKey(source), language)?.takeIf { it.isNotBlank() } ?: text
    }
}
