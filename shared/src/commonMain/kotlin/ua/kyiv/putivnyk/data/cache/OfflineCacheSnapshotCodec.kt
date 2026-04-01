package ua.kyiv.putivnyk.data.cache

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.Route

@Serializable
data class CacheMeta(
    val exportedAt: Long,
    val placesCount: Int,
    val routesCount: Int,
    val prefsCount: Int,
)

object OfflineCacheSnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun encodePlaces(places: List<Place>): String = json.encodeToString(ListSerializer(Place.serializer()), places)

    fun decodePlaces(rawJson: String): List<Place> =
        runCatching { json.decodeFromString(ListSerializer(Place.serializer()), rawJson) }.getOrDefault(emptyList())

    fun encodeRoutes(routes: List<Route>): String = json.encodeToString(ListSerializer(Route.serializer()), routes)

    fun decodeRoutes(rawJson: String): List<Route> =
        runCatching { json.decodeFromString(ListSerializer(Route.serializer()), rawJson) }.getOrDefault(emptyList())

    fun encodePreferences(preferences: Map<String, String>): String =
        json.encodeToString(MapSerializer(String.serializer(), String.serializer()), preferences)

    fun decodePreferences(rawJson: String): Map<String, String> =
        runCatching {
            json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), rawJson)
        }.getOrDefault(emptyMap())

    fun encodeMeta(meta: CacheMeta): String = json.encodeToString(CacheMeta.serializer(), meta)

    fun decodeMeta(rawJson: String): CacheMeta? =
        runCatching { json.decodeFromString(CacheMeta.serializer(), rawJson) }.getOrNull()
}