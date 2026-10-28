package ua.kyiv.putivnyk.navigation

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ua.kyiv.putivnyk.share.RouteShareCodec

/** In-app destinations reachable from widgets, notifications and shared links. */
sealed interface DeepLink {
    data class Place(val placeId: Long) : DeepLink
    data class SharedRoute(val code: String) : DeepLink
    data class DailyRoute(val placeIds: List<Long>) : DeepLink
    data object Map : DeepLink
    data object Stats : DeepLink
    data object OfflineMaps : DeepLink
}

object DeepLinks {
    private const val PREFIX = "${RouteShareCodec.SCHEME}://"

    fun place(placeId: Long): String = "${PREFIX}place/$placeId"
    fun map(): String = "${PREFIX}map"
    fun stats(): String = "${PREFIX}stats"
    fun offlineMaps(): String = "${PREFIX}offline-maps"
    fun dailyRoute(placeIds: List<Long>): String = "${PREFIX}daily-route?ids=${placeIds.joinToString(",")}"

    /** Parses a putivnyk:// link; anything else or malformed input gives null. */
    fun parse(raw: String?): DeepLink? {
        val value = raw?.trim().orEmpty()
        if (!value.startsWith(PREFIX, ignoreCase = true)) return null
        val rest = value.substring(PREFIX.length)
        val path = rest.substringBefore('?').substringBefore('#').trimEnd('/')
        val query = rest.substringAfter('?', "").substringBefore('#')
        val params = query.split('&').filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        val segments = path.split('/').filter { it.isNotEmpty() }
        return when (segments.firstOrNull()?.lowercase()) {
            "place" -> segments.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 }?.let { DeepLink.Place(it) }
            RouteShareCodec.HOST -> params[RouteShareCodec.QUERY_PARAM]
                ?.takeIf { it.isNotBlank() }
                ?.let { DeepLink.SharedRoute(it) }
            "daily-route" -> params["ids"]
                ?.split(',')
                ?.mapNotNull { it.trim().toLongOrNull() }
                ?.distinct()
                ?.takeIf { it.size >= 2 && it.size <= RouteShareCodec.MAX_POINTS }
                ?.let { DeepLink.DailyRoute(it) }
            "map" -> DeepLink.Map
            "stats" -> DeepLink.Stats
            "offline-maps" -> DeepLink.OfflineMaps
            else -> null
        }
    }
}

/** Hands deep links from the activity to the Compose navigation, which consumes them. */
object DeepLinkRouter {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    fun handle(intent: Intent?): Boolean {
        val link = DeepLinks.parse(intent?.dataString) ?: return false
        _pending.value = link
        return true
    }

    fun consume(link: DeepLink) {
        _pending.compareAndSet(link, null)
    }
}
