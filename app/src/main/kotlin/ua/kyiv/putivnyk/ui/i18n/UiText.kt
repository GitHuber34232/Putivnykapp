package ua.kyiv.putivnyk.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf

class UiTextProvider(private val values: Map<String, String>) {
    fun tr(key: String, fallback: String): String = values[key] ?: fallback

    fun find(key: String): String? = values[key]
}

data class UiRuntimeTranslator(
    val effectiveLanguage: String,
    val translateDynamic: suspend (String) -> String,
    /** Already translated text, if any, so lists don't flash the source text while scrolling. */
    val peekDynamic: (String) -> String? = { null }
)

val LocalUiText = staticCompositionLocalOf { UiTextProvider(emptyMap()) }
val LocalUiRuntimeTranslator = staticCompositionLocalOf<UiRuntimeTranslator?> { null }

/**
 * UI string by key. Keys without a stored translation fall back to translating the
 * Ukrainian [fallback] at runtime, so no screen stays in Ukrainian for other languages.
 */
@Composable
fun tr(key: String, fallback: String): String =
    LocalUiText.current.find(key) ?: trDynamic(fallback)

@Composable
fun trDynamic(text: String): String {
    val runtimeTranslator = LocalUiRuntimeTranslator.current ?: return text
    val language = runtimeTranslator.effectiveLanguage
    if (language == "uk" || text.isBlank()) return text
    val translated by produceState(
        initialValue = runtimeTranslator.peekDynamic(text) ?: text,
        key1 = text,
        key2 = language
    ) {
        value = runtimeTranslator.translateDynamic(text)
    }
    return translated
}
