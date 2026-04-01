package ua.kyiv.putivnyk.i18n

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object UiTranslationsJsonCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val serializer = MapSerializer(String.serializer(), String.serializer())

    fun decode(rawJson: String): Map<String, String> =
        runCatching { json.decodeFromString(serializer, rawJson) }.getOrDefault(emptyMap())
}