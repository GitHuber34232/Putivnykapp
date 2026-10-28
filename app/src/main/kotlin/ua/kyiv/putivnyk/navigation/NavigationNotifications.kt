package ua.kyiv.putivnyk.navigation

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import ua.kyiv.putivnyk.R
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import ua.kyiv.putivnyk.notifications.AppNotifications
import ua.kyiv.putivnyk.ui.utils.RouteUiFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Ongoing navigation notification. On Android 16+ it is a Live Update: a promoted
 * notification with a segmented progress bar (one segment per leg) that stays visible on the
 * lock screen and as a status-bar chip; older versions get a classic progress notification.
 */
@Singleton
class NavigationNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appNotifications: AppNotifications
) {
    data class Texts(
        val navigating: String,
        val arrived: String,
        val stop: String,
        val left: String,
        val calculating: String
    )

    fun build(snapshot: NavigationSnapshot?, nextName: String?, texts: Texts): Notification {
        appNotifications.ensureChannels()
        val builder = Notification.Builder(context, AppNotifications.CHANNEL_NAVIGATION)
            .setSmallIcon(R.drawable.ic_stat_navigation)
            .setColor(AppNotifications.BRAND_COLOR)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(appNotifications.openLink(DeepLinks.map(), AppNotifications.REQUEST_MAP))
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_action_stop),
                    texts.stop,
                    stopIntent()
                ).build()
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        if (snapshot == null) {
            return builder
                .setContentTitle(texts.navigating)
                .setContentText(texts.calculating)
                .setProgress(0, 0, true)
                .build()
        }

        val title = when {
            snapshot.completed -> "🎉 ${texts.arrived}"
            !nextName.isNullOrBlank() -> "→ $nextName"
            else -> snapshot.routeName
        }
        val text = if (snapshot.completed) {
            snapshot.routeName
        } else {
            "${RouteUiFormatter.formatDistance(snapshot.distanceToNextMeters)} · ${texts.left} " +
                "${RouteUiFormatter.formatDistance(snapshot.remainingMeters)} · " +
                RouteUiFormatter.formatDuration(snapshot.remainingMinutes)
        }
        builder.setContentTitle(title).setContentText(text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            applyLiveUpdate(builder, snapshot)
        } else {
            builder.setProgress(PROGRESS_MAX, (snapshot.progress * PROGRESS_MAX).roundToInt(), false)
        }
        return builder.build()
    }

    private fun applyLiveUpdate(builder: Notification.Builder, snapshot: NavigationSnapshot) {
        // Segment lengths follow the straight-line legs between stops; at least 1 unit each.
        val legs = snapshot.waypoints.zipWithNext { a, b ->
            max(1, NativeGeoEngine.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude).roundToInt())
        }
        val total = legs.sum().coerceAtLeast(1)
        val segments = legs.map { length ->
            Notification.ProgressStyle.Segment(length).setColor(AppNotifications.BRAND_COLOR)
        }
        var cumulative = 0
        val points = legs.dropLast(1).map { length ->
            cumulative += length
            Notification.ProgressStyle.Point(cumulative).setColor(STOP_COLOR)
        }
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(segments)
            .setProgressPoints(points)
            .setProgress((snapshot.progress * total).roundToInt().coerceIn(0, total))
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_nav_tracker))
            .setProgressEndIcon(Icon.createWithResource(context, R.drawable.ic_nav_flag))
        builder.setStyle(style)
            .setShortCriticalText(
                if (snapshot.completed) "✓" else RouteUiFormatter.formatDistance(snapshot.distanceToNextMeters)
            )
            .setRequestPromotedOngoing(true)
    }

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        context,
        AppNotifications.REQUEST_STOP_NAVIGATION,
        Intent(context, NavigationService::class.java).setAction(NavigationService.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private companion object {
        const val PROGRESS_MAX = 1000
        /** Golden accent for the stops on the progress bar. */
        const val STOP_COLOR = 0xFFF2B705.toInt()
    }
}
