package ua.kyiv.putivnyk.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ua.kyiv.putivnyk.MainActivity
import ua.kyiv.putivnyk.R
import ua.kyiv.putivnyk.audio.AudioGuideService
import ua.kyiv.putivnyk.i18n.AppText
import ua.kyiv.putivnyk.navigation.DeepLinks
import javax.inject.Inject
import javax.inject.Singleton

/** Notification channels and the one-off notifications (visits, achievements, finished routes). */
@Singleton
class AppNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    private data class ChannelTexts(
        val navigation: String,
        val navigationDescription: String,
        val audio: String,
        val audioDescription: String,
        val visits: String,
        val visitsDescription: String,
        val achievements: String,
        val achievementsDescription: String
    )

    @Volatile
    private var texts = ChannelTexts(
        navigation = context.getString(R.string.channel_navigation),
        navigationDescription = context.getString(R.string.channel_navigation_description),
        audio = context.getString(R.string.channel_audio),
        audioDescription = context.getString(R.string.channel_audio_description),
        visits = context.getString(R.string.channel_visits),
        visitsDescription = context.getString(R.string.channel_visits_description),
        achievements = context.getString(R.string.channel_achievements),
        achievementsDescription = context.getString(R.string.channel_achievements_description)
    )

    /**
     * Channel names are shown in the system settings: they follow the app's own language
     * (15 languages), not only the two resource translations. Existing channels keep their
     * importance; only the names change.
     */
    suspend fun localizeChannels(appText: AppText) {
        texts = ChannelTexts(
            navigation = appText.get("channel.navigation", "Навігація"),
            navigationDescription = appText.get("channel.navigation_description", "Прогрес маршруту на екрані блокування"),
            audio = appText.get("channel.audio", "Аудіогід"),
            audioDescription = appText.get("channel.audio_description", "Керування відтворенням аудіогіду"),
            visits = appText.get("channel.visits", "Відвідані місця"),
            visitsDescription = appText.get("channel.visits_description", "Коли місце автоматично позначено відвіданим"),
            achievements = appText.get("channel.achievements", "Досягнення"),
            achievementsDescription = appText.get("channel.achievements_description", "Нові досягнення за прогулянки")
        )
        ensureChannels()
    }

    fun ensureChannels() {
        val texts = texts
        val navigation = NotificationChannel(
            CHANNEL_NAVIGATION,
            texts.navigation,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = texts.navigationDescription
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val audio = NotificationChannel(
            CHANNEL_AUDIO,
            texts.audio,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = texts.audioDescription
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val visits = NotificationChannel(
            CHANNEL_VISITS,
            texts.visits,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = texts.visitsDescription }
        val achievements = NotificationChannel(
            CHANNEL_ACHIEVEMENTS,
            texts.achievements,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = texts.achievementsDescription }
        manager.createNotificationChannels(listOf(navigation, audio, visits, achievements))
    }

    /** Whether the user lets the app post notifications (Android 13+ runtime permission). */
    fun canPost(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && manager.areNotificationsEnabled()
    }

    fun showAchievement(id: String, emoji: String, title: String, description: String) {
        if (!canPost()) return
        ensureChannels()
        val notification = Notification.Builder(context, CHANNEL_ACHIEVEMENTS)
            .setSmallIcon(R.drawable.ic_stat_putivnyk)
            .setContentTitle("$emoji $title")
            .setContentText(description)
            .setStyle(Notification.BigTextStyle().bigText(description))
            .setColor(BRAND_COLOR)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_SOCIAL)
            .setContentIntent(openLink(DeepLinks.stats(), REQUEST_STATS))
            .build()
        manager.notify(ACHIEVEMENT_NOTIFICATION_BASE + (id.hashCode() and 0x3FF), notification)
    }

    fun showVisit(placeId: Long, title: String, text: String, listenLabel: String) {
        if (!canPost()) return
        ensureChannels()
        val listenIntent = PendingIntent.getForegroundService(
            context,
            REQUEST_LISTEN_BASE + (placeId % 1000).toInt(),
            Intent(context, AudioGuideService::class.java)
                .setAction(AudioGuideService.ACTION_PLAY_PLACE)
                .putExtra(AudioGuideService.EXTRA_PLACE_ID, placeId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(context, CHANNEL_VISITS)
            .setSmallIcon(R.drawable.ic_stat_putivnyk)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(BRAND_COLOR)
            .setAutoCancel(true)
            .setContentIntent(openLink(DeepLinks.place(placeId), REQUEST_PLACE_BASE + (placeId % 1000).toInt()))
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_action_headphones),
                    listenLabel,
                    listenIntent
                ).build()
            )
            .build()
        manager.notify(VISIT_NOTIFICATION_BASE + (placeId % 1000).toInt(), notification)
    }

    fun showRouteCompleted(title: String, text: String) {
        if (!canPost()) return
        ensureChannels()
        val notification = Notification.Builder(context, CHANNEL_NAVIGATION)
            .setSmallIcon(R.drawable.ic_stat_navigation)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(BRAND_COLOR)
            .setAutoCancel(true)
            .setContentIntent(openLink(DeepLinks.map(), REQUEST_MAP))
            .build()
        manager.notify(ROUTE_COMPLETED_NOTIFICATION_ID, notification)
    }

    fun cancel(id: Int) = manager.cancel(id)

    fun openLink(link: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(link))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        const val CHANNEL_NAVIGATION = "navigation"
        const val CHANNEL_AUDIO = "audio_guide"
        const val CHANNEL_VISITS = "visits"
        const val CHANNEL_ACHIEVEMENTS = "achievements"

        const val NAVIGATION_NOTIFICATION_ID = 1001
        const val AUDIO_NOTIFICATION_ID = 1002
        const val ROUTE_COMPLETED_NOTIFICATION_ID = 1003
        private const val VISIT_NOTIFICATION_BASE = 2000
        private const val ACHIEVEMENT_NOTIFICATION_BASE = 4000

        const val REQUEST_MAP = 10
        const val REQUEST_STATS = 11
        const val REQUEST_STOP_NAVIGATION = 12
        private const val REQUEST_PLACE_BASE = 1000
        private const val REQUEST_LISTEN_BASE = 3000

        /** Brand azure (ui/theme/Color.kt). */
        const val BRAND_COLOR = 0xFF1F5FAE.toInt()
    }
}
