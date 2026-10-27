package ua.kyiv.putivnyk.data.model

/**
 * Keys in the user_preferences table that screens use to hand work over to the map.
 * Screens write a "pending" key and navigate; MapViewModel observes the keys and consumes them.
 */
object MapPreferenceKeys {
    const val ACTIVE_ROUTE_ID = "map.activeRouteId"
    const val PENDING_ACTIVE_ROUTE_ID = "map.pending.activeRouteId"
    const val PENDING_ROUTE_TO_PLACE_ID = "map.pending.routeToPlaceId"
    const val FOCUS_PLACE_ID = "map.focus.placeId"
    const val FOCUS_LAT = "map.focus.lat"
    const val FOCUS_LON = "map.focus.lon"
    const val FOCUS_ZOOM = "map.focus.zoom"

    val PENDING_KEYS: Set<String> = setOf(
        PENDING_ACTIVE_ROUTE_ID,
        PENDING_ROUTE_TO_PLACE_ID,
        FOCUS_PLACE_ID,
        FOCUS_LAT,
        FOCUS_LON
    )
}
