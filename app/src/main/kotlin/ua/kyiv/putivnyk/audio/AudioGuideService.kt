package ua.kyiv.putivnyk.audio

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.notifications.AppNotifications
import javax.inject.Inject

/**
 * Foreground service (mediaPlayback) that keeps the audio guide playing with the screen off.
 * The media notification's buttons come back here as intents.
 */
@AndroidEntryPoint
class AudioGuideService : Service() {

    @Inject lateinit var engine: AudioGuideEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started with startForegroundService(): it has to become foreground in any case.
        val foreground = runCatching {
            startForeground(
                AppNotifications.AUDIO_NOTIFICATION_ID,
                engine.buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        }.onFailure { Log.w(TAG, "Cannot start the audio guide in the foreground", it) }.isSuccess
        if (!foreground) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_TOGGLE -> engine.togglePlayPause()
            ACTION_NEXT -> engine.next()
            ACTION_PREVIOUS -> engine.previous()
            ACTION_STOP -> engine.stop()
            ACTION_PLAY_PLACE -> intent.getLongExtra(EXTRA_PLACE_ID, -1L)
                .takeIf { it > 0L }
                ?.let { engine.playPlaces(listOf(it)) }
        }
        if (stateJob == null) {
            stateJob = scope.launch {
                engine.state.collect { state ->
                    if (!state.isActive) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "ua.kyiv.putivnyk.audio.START"
        const val ACTION_TOGGLE = "ua.kyiv.putivnyk.audio.TOGGLE"
        const val ACTION_NEXT = "ua.kyiv.putivnyk.audio.NEXT"
        const val ACTION_PREVIOUS = "ua.kyiv.putivnyk.audio.PREVIOUS"
        const val ACTION_STOP = "ua.kyiv.putivnyk.audio.STOP"
        const val ACTION_PLAY_PLACE = "ua.kyiv.putivnyk.audio.PLAY_PLACE"
        const val EXTRA_PLACE_ID = "placeId"
        private const val TAG = "AudioGuideService"
    }
}
