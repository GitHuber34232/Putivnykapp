package ua.kyiv.putivnyk.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf

class UiTextProvider(private val values: Map<String, String>) {
    fun tr(key: String, fallback: String): String = values[key] ?: fallback
}

data class UiRuntimeTranslator(
    val effectiveLanguage: String,
    val translateDynamic: suspend (String) -> String
)

val LocalUiText = staticCompositionLocalOf { UiTextProvider(emptyMap()) }
val LocalUiRuntimeTranslator = staticCompositionLocalOf<UiRuntimeTranslator?> { null }

@Composable
fun tr(key: String, fallback: String): String = LocalUiText.current.tr(key, fallback)

@Composable
fun trDynamic(text: String): String {
    val runtimeTranslator = LocalUiRuntimeTranslator.current ?: return text
    val language = runtimeTranslator.effectiveLanguage
    val translated by produceState(initialValue = text, key1 = text, key2 = language) {
        value = if (language == "uk") {
            text
        } else {
            runtimeTranslator.translateDynamic(text)
        }
    }
    return translated
}
