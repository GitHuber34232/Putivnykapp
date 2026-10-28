package ua.kyiv.putivnyk.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.repository.FeatureSettings
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import ua.kyiv.putivnyk.domain.visit.VisitDetector
import ua.kyiv.putivnyk.ui.utils.lastKnownLocation
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/**
 * Optional background mode for automatic "visited" marks: geofences around the nearest
 * unvisited places (Google Play services), so it works with the app closed. Needs the
 * "Allow all the time" location permission; off by default.
 */
@Singleton
class GeofenceRegistrar @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val placeRepository: PlaceRepository,
    private val featureSettings: FeatureSettings
) {
    private val mutex = Mutex()

    fun hasBackgroundPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Keeps the geofences in sync with the setting and with places being visited. */
    @OptIn(FlowPreview::class)
    fun startObserving(scope: CoroutineScope) {
        scope.launch {
            combine(
                featureSettings.values.map { it.backgroundGeofences && it.autoVisited },
                placeRepository.getAllPlaces().map { places -> places.filter { it.isVisited }.map { it.id }.toSet() }
            ) { enabled, visited -> enabled to visited }
                .distinctUntilChanged()
                .debounce(2_000)
                .collect { runCatching { refresh() } }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun refresh(): Unit = mutex.withLock {
        val client = runCatching { LocationServices.getGeofencingClient(context) }.getOrNull() ?: return@withLock
        val settings = featureSettings.current()
        if (!settings.backgroundGeofences || !settings.autoVisited || !hasBackgroundPermission()) {
            runCatching { client.removeGeofences(pendingIntent()).awaitTask() }
            return@withLock
        }
        val origin = context.lastKnownLocation()
        val originLat = origin?.latitude ?: KYIV_CENTER_LAT
        val originLon = origin?.longitude ?: KYIV_CENTER_LON
        val places = placeRepository.getAllPlacesSnapshot()
            .asSequence()
            .filter { !it.isVisited && it.category != PlaceCategory.TOILET }
            .sortedBy { NativeGeoEngine.distanceMeters(originLat, originLon, it.latitude, it.longitude) }
            .take(MAX_GEOFENCES)
            .toList()
        runCatching { client.removeGeofences(pendingIntent()).awaitTask() }
        if (places.isEmpty()) return@withLock
        val geofences = places.map { place ->
            Geofence.Builder()
                .setRequestId(place.id.toString())
                .setCircularRegion(
                    place.latitude,
                    place.longitude,
                    max(VisitDetector.radiusFor(place.category) * 1.5, MIN_RADIUS_METERS).toFloat()
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(LOITERING_DELAY_MILLIS)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofences(geofences)
            .build()
        runCatching { client.addGeofences(request, pendingIntent()).awaitTask() }
            .onFailure { Log.w(TAG, "Geofences not registered", it) }
        Unit
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, GeofenceReceiver::class.java).setAction(GeofenceReceiver.ACTION_GEOFENCE),
        // Play services fills in the event, so the intent has to stay mutable.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            val error = task.exception
            if (error != null) continuation.resumeWithException(error) else continuation.resume(task.result)
        }
    }

    private companion object {
        const val TAG = "GeofenceRegistrar"
        /** Play services allows 100 geofences per app. */
        const val MAX_GEOFENCES = 90
        const val MIN_RADIUS_METERS = 80.0
        const val LOITERING_DELAY_MILLIS = 60_000
        const val KYIV_CENTER_LAT = 50.4501
        const val KYIV_CENTER_LON = 30.5234
    }
}
