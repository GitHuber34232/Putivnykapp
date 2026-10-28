package ua.kyiv.putivnyk.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.di.AppCoroutineScope
import ua.kyiv.putivnyk.widget.WidgetUpdater
import javax.inject.Inject

/** The user lingered at a place with the app closed: mark it visited. */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {

    @Inject lateinit var visitTracker: VisitTracker
    @Inject lateinit var appScope: AppCoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_DWELL) return
        val placeIds = event.triggeringGeofences.orEmpty().mapNotNull { it.requestId.toLongOrNull() }
        if (placeIds.isEmpty()) return
        val pending = goAsync()
        appScope.launch {
            try {
                placeIds.forEach { visitTracker.markVisited(it, VisitTracker.SOURCE_GEOFENCE) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_GEOFENCE = "ua.kyiv.putivnyk.GEOFENCE_EVENT"
    }
}

/** Geofences and widgets do not survive a reboot or an app update. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var geofenceRegistrar: GeofenceRegistrar
    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var appScope: AppCoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        appScope.launch {
            try {
                runCatching { geofenceRegistrar.refresh() }
                runCatching { widgetUpdater.updateAll() }
            } finally {
                pending.finish()
            }
        }
    }
}
