package ua.kyiv.putivnyk.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.kyiv.putivnyk.R
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.repository.DailyRouteRepository
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import ua.kyiv.putivnyk.domain.stats.StatsTime
import ua.kyiv.putivnyk.i18n.AppText
import ua.kyiv.putivnyk.navigation.DeepLinks
import ua.kyiv.putivnyk.notifications.AppNotifications
import ua.kyiv.putivnyk.ui.utils.RouteUiFormatter
import ua.kyiv.putivnyk.ui.utils.lastKnownLocation
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Renders the home-screen widgets "Nearby" and "Route of the day" (plain RemoteViews). */
@Singleton
class WidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val placeRepository: PlaceRepository,
    private val dailyRouteRepository: DailyRouteRepository,
    private val appNotifications: AppNotifications,
    private val appText: AppText
) {
    private val mutex = Mutex()

    @Volatile
    private var lastLocation: Pair<Double, Double>? = null
    private var nearbyLat = Double.NaN
    private var nearbyLon = Double.NaN
    private var nearbyUpdatedAt = 0L

    suspend fun updateAll() {
        updateNearby()
        updateDailyRoute()
    }

    /** Fed with the app's location fixes; refreshes "Nearby" after a real move. */
    suspend fun onLocation(latitude: Double, longitude: Double) {
        lastLocation = latitude to longitude
        val now = System.currentTimeMillis()
        if (now - nearbyUpdatedAt < MIN_LOCATION_REFRESH_MILLIS) return
        val moved = nearbyLat.isNaN() ||
            NativeGeoEngine.distanceMeters(nearbyLat, nearbyLon, latitude, longitude) > REFRESH_DISTANCE_METERS
        if (moved || now - nearbyUpdatedAt > MAX_STALE_MILLIS) updateNearby()
    }

    suspend fun updateNearby(): Unit = mutex.withLock {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, NearbyWidgetProvider::class.java))
        if (ids.isEmpty()) return@withLock
        val location = lastLocation ?: context.lastKnownLocation()?.let { it.latitude to it.longitude }
        val (originLat, originLon) = location ?: (KYIV_CENTER_LAT to KYIV_CENTER_LON)
        val nearest = placeRepository.getAllPlacesSnapshot()
            .asSequence()
            .filter { it.category != PlaceCategory.TOILET }
            .map { it to NativeGeoEngine.distanceMeters(originLat, originLon, it.latitude, it.longitude) }
            .sortedBy { it.second }
            .take(MAX_ROWS)
            .toList()

        val views = RemoteViews(context.packageName, R.layout.widget_nearby)
        views.setTextViewText(R.id.widget_title, appText.get("widget.nearby_title", "Поруч зі мною"))
        views.setTextViewText(
            R.id.widget_subtitle,
            if (location == null) {
                appText.get("widget.no_location", "Без геолокації · від Майдану")
            } else {
                "${appText.get("widget.updated", "Оновлено")} ${java.time.LocalTime.now(StatsTime.zone).format(TIME_FORMAT)}"
            }
        )
        views.removeAllViews(R.id.widget_rows)
        nearest.forEachIndexed { index, (place, meters) ->
            val row = RemoteViews(context.packageName, R.layout.widget_place_row)
            row.setTextViewText(R.id.widget_row_icon, place.category.icon)
            row.setTextViewText(R.id.widget_row_title, appText.dynamic(place.name))
            row.setTextViewText(
                R.id.widget_row_subtitle,
                "${categoryLabel(place)} · ${RouteUiFormatter.formatDistance(meters)}"
            )
            row.setOnClickPendingIntent(
                R.id.widget_row_root,
                appNotifications.openLink(DeepLinks.place(place.id), REQUEST_NEARBY_BASE + index)
            )
            views.addView(R.id.widget_rows, row)
        }
        views.setViewVisibility(R.id.widget_empty, if (nearest.isEmpty()) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_empty, appText.get("widget.empty", "Місця ще завантажуються"))
        views.setOnClickPendingIntent(R.id.widget_header, appNotifications.openLink(DeepLinks.map(), REQUEST_NEARBY_HEADER))
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(NearbyWidgetProvider::class.java, REQUEST_NEARBY_REFRESH))
        ids.forEach { id -> manager.updateAppWidget(id, views) }

        nearbyUpdatedAt = System.currentTimeMillis()
        nearbyLat = originLat
        nearbyLon = originLon
    }

    suspend fun updateDailyRoute(): Unit = mutex.withLock {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, DailyRouteWidgetProvider::class.java))
        if (ids.isEmpty()) return@withLock
        val today = StatsTime.today()
        val route = dailyRouteRepository.today()

        val views = RemoteViews(context.packageName, R.layout.widget_daily_route)
        views.setTextViewText(R.id.widget_title, appText.get("daily.title", "Маршрут дня"))
        views.setTextViewText(
            R.id.widget_subtitle,
            today.format(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag(appText.language())))
        )
        views.removeAllViews(R.id.widget_rows)
        route?.places?.forEachIndexed { index, place ->
            val row = RemoteViews(context.packageName, R.layout.widget_route_stop_row)
            row.setTextViewText(R.id.widget_stop_number, (index + 1).toString())
            row.setTextViewText(R.id.widget_stop_title, "${place.category.icon} ${appText.dynamic(place.name)}")
            row.setOnClickPendingIntent(
                R.id.widget_stop_root,
                appNotifications.openLink(DeepLinks.place(place.id), REQUEST_DAILY_BASE + index)
            )
            views.addView(R.id.widget_rows, row)
        }
        if (route == null) {
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, appText.get("widget.empty", "Місця ще завантажуються"))
            views.setViewVisibility(R.id.widget_footer, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_empty, View.GONE)
            views.setViewVisibility(R.id.widget_footer, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_summary,
                "${RouteUiFormatter.formatDistance(route.distanceMeters)} · ${RouteUiFormatter.formatDuration(route.walkingMinutes)}"
            )
            views.setTextViewText(R.id.widget_start, appText.get("daily.start", "Почати"))
            views.setOnClickPendingIntent(
                R.id.widget_start,
                appNotifications.openLink(DeepLinks.dailyRoute(route.places.map { it.id }), REQUEST_DAILY_START)
            )
        }
        views.setOnClickPendingIntent(R.id.widget_header, appNotifications.openLink(DeepLinks.map(), REQUEST_DAILY_HEADER))
        ids.forEach { id -> manager.updateAppWidget(id, views) }
    }

    private suspend fun categoryLabel(place: Place): String =
        appText.get("category.${place.category.name.lowercase()}", place.category.displayName)

    private fun refreshIntent(provider: Class<*>, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, provider).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        const val ACTION_REFRESH = "ua.kyiv.putivnyk.widget.REFRESH"
        private const val MAX_ROWS = 4
        private const val KYIV_CENTER_LAT = 50.4501
        private const val KYIV_CENTER_LON = 30.5234
        private const val REFRESH_DISTANCE_METERS = 300.0
        private const val MIN_LOCATION_REFRESH_MILLIS = 60_000L
        private const val MAX_STALE_MILLIS = 15 * 60_000L
        private const val REQUEST_NEARBY_BASE = 7000
        private const val REQUEST_NEARBY_HEADER = 7100
        private const val REQUEST_NEARBY_REFRESH = 7101
        private const val REQUEST_DAILY_BASE = 7200
        private const val REQUEST_DAILY_HEADER = 7300
        private const val REQUEST_DAILY_START = 7301
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
