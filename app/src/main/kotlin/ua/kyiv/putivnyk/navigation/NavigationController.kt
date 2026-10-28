package ua.kyiv.putivnyk.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ua.kyiv.putivnyk.data.repository.FeatureSettings
import javax.inject.Inject
import javax.inject.Singleton

/** Starts and stops background navigation (location tracking + lock-screen progress). */
@Singleton
class NavigationController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val featureSettings: FeatureSettings
) {
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Must be called while the app is visible: a location foreground service can only be
     * started from the foreground with the "while in use" location permission.
     */
    fun start(routeId: Long) {
        if (!featureSettings.values.value.backgroundNavigation || !hasLocationPermission()) return
        val intent = Intent(context, NavigationService::class.java)
            .setAction(NavigationService.ACTION_START)
            .putExtra(NavigationService.EXTRA_ROUTE_ID, routeId)
        runCatching { context.startForegroundService(intent) }
            .onFailure { Log.w(TAG, "Navigation service not started", it) }
    }

    fun stop() {
        runCatching { context.stopService(Intent(context, NavigationService::class.java)) }
    }

    private companion object {
        const val TAG = "NavigationController"
    }
}
