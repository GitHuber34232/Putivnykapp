package ua.kyiv.putivnyk.i18n

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetUiTranslations @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gson: Gson
) {
    fun load(language: String): Map<String, String> {
        val safeLanguage = language.lowercase()
        val assetPath = "i18n/ui_${safeLanguage}.json"
        val json = runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyMap()

        val type = object : TypeToken<Map<String, String>>() {}.type
        return runCatching { gson.fromJson<Map<String, String>>(json, type) }.getOrDefault(emptyMap())
    }
}
