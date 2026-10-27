package ua.kyiv.putivnyk.ui.maps

import android.app.Application
import android.content.Context
import android.view.MotionEvent
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mapsforge.core.graphics.Cap
import org.mapsforge.core.graphics.Join
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.InternalRenderTheme
import ua.kyiv.putivnyk.R
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.ui.viewmodel.MapCenter
import ua.kyiv.putivnyk.ui.viewmodel.MapViewportBounds
import java.io.File
import kotlin.math.abs

/** Map rendered from the bundled vector map (assets/maps/kyiv.map); works without internet. */
@Composable
fun OfflineMapView(
    modifier: Modifier = Modifier,
    center: MapCenter,
    zoomLevel: Int,
    places: List<Place>,
    userLocation: MapCenter,
    showUserLocation: Boolean,
    onMapMoved: (MapCenter, Int) -> Unit,
    onViewportChanged: (MapViewportBounds) -> Unit,
    onPlaceMarkerClick: (Place) -> Unit,
    routePoints: List<RoutePoint> = emptyList()
) {
    val context = LocalContext.current
    // Copying the 25 MB map out of the APK must not block the UI thread.
    val mapFile by produceState<File?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { copyAssetMapToInternalStorage(context, "maps/kyiv.map") }.getOrNull()
        }
    }

    Box(modifier = modifier) {
        val file = mapFile
        if (file == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            OfflineMapContent(
                modifier = Modifier.matchParentSize(),
                mapFile = file,
                center = center,
                zoomLevel = zoomLevel,
                places = places,
                userLocation = userLocation,
                showUserLocation = showUserLocation,
                onMapMoved = onMapMoved,
                onViewportChanged = onViewportChanged,
                onPlaceMarkerClick = onPlaceMarkerClick,
                routePoints = routePoints
            )
        }
    }
}

@Composable
private fun OfflineMapContent(
    modifier: Modifier,
    mapFile: File,
    center: MapCenter,
    zoomLevel: Int,
    places: List<Place>,
    userLocation: MapCenter,
    showUserLocation: Boolean,
    onMapMoved: (MapCenter, Int) -> Unit,
    onViewportChanged: (MapViewportBounds) -> Unit,
    onPlaceMarkerClick: (Place) -> Unit,
    routePoints: List<RoutePoint>
) {
    val context = LocalContext.current
    val runtime = remember(context, mapFile) { createMapRuntime(context, mapFile) }
    val currentOnMapMoved by rememberUpdatedState(onMapMoved)
    val currentOnViewportChanged by rememberUpdatedState(onViewportChanged)
    val currentOnPlaceMarkerClick by rememberUpdatedState(onPlaceMarkerClick)

    DisposableEffect(runtime) {
        onDispose {
            runtime.mapView.destroyAll()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            runtime.mapView.apply {
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                        val position = model.mapViewPosition.mapPosition
                        val movedCenter = MapCenter(
                            latitude = position.latLong.latitude,
                            longitude = position.latLong.longitude
                        )
                        val movedZoom = position.zoomLevel.toInt()
                        // Remember where the user left the map so the next update doesn't snap it back.
                        runtime.lastAppliedCenter = movedCenter
                        runtime.lastAppliedZoom = movedZoom
                        currentOnMapMoved(movedCenter, movedZoom)
                        runtime.currentViewportBounds()?.let(currentOnViewportChanged)
                    }
                    false
                }
            }
        },
        update = { mapView ->
            runtime.syncCamera(center, zoomLevel)
            runtime.syncRoute(routePoints)
            runtime.syncMarkers(
                context = context,
                places = places,
                userLocation = userLocation,
                showUserLocation = showUserLocation,
                onPlaceMarkerClick = { place -> currentOnPlaceMarkerClick(place) }
            )
            mapView.repaint()
        }
    )
}

private class MapRuntime(
    val mapView: MapView
) {
    private val markerLayers: MutableList<Layer> = mutableListOf()
    private var routeLayer: Polyline? = null
    private var lastMarkersSignature: Int? = null
    private var lastRouteSignature: Int? = null
    var lastAppliedCenter: MapCenter? = null
    var lastAppliedZoom: Int? = null

    fun syncCamera(center: MapCenter, zoomLevel: Int) {
        val clampedZoom = zoomLevel.coerceIn(10, 19)
        val previous = lastAppliedCenter
        val centerChanged = previous == null ||
            abs(previous.latitude - center.latitude) > 1e-6 ||
            abs(previous.longitude - center.longitude) > 1e-6
        if (centerChanged) {
            mapView.model.mapViewPosition.setCenter(LatLong(center.latitude, center.longitude))
            lastAppliedCenter = center
        }
        if (lastAppliedZoom != clampedZoom) {
            mapView.model.mapViewPosition.setZoomLevel(clampedZoom.toByte())
            lastAppliedZoom = clampedZoom
        }
    }

    fun syncRoute(routePoints: List<RoutePoint>) {
        val signature = routePoints.hashCode()
        if (signature == lastRouteSignature) return
        lastRouteSignature = signature

        routeLayer?.let { mapView.layerManager.layers.remove(it) }
        routeLayer = null
        if (routePoints.size < 2) return

        val factory = AndroidGraphicFactory.INSTANCE
        val paint = factory.createPaint().apply {
            setColor(factory.createColor(230, 26, 115, 232))
            setStrokeWidth(12f)
            setStyle(Style.STROKE)
            setStrokeCap(Cap.ROUND)
            setStrokeJoin(Join.ROUND)
        }
        val polyline = Polyline(paint, factory).apply {
            latLongs.addAll(routePoints.map { LatLong(it.latitude, it.longitude) })
        }
        // Below the markers, which are re-added on top in syncMarkers.
        mapView.layerManager.layers.add(polyline)
        routeLayer = polyline
        lastMarkersSignature = null
    }

    fun syncMarkers(
        context: Context,
        places: List<Place>,
        userLocation: MapCenter,
        showUserLocation: Boolean,
        onPlaceMarkerClick: (Place) -> Unit
    ) {
        var signature = places.size
        places.forEach { place -> signature = 31 * signature + place.id.hashCode() }
        if (showUserLocation) {
            signature = 31 * signature + userLocation.latitude.hashCode()
            signature = 31 * signature + userLocation.longitude.hashCode()
        }
        if (signature == lastMarkersSignature) return
        lastMarkersSignature = signature

        markerLayers.forEach { mapView.layerManager.layers.remove(it) }
        markerLayers.clear()

        places.forEach { place ->
            val marker = createMarker(
                context = context,
                latitude = place.latitude,
                longitude = place.longitude,
                icon = R.drawable.ic_marker_place,
                onTap = { onPlaceMarkerClick(place) }
            )
            markerLayers += marker
            mapView.layerManager.layers.add(marker)
        }

        if (showUserLocation) {
            val userMarker = createMarker(
                context = context,
                latitude = userLocation.latitude,
                longitude = userLocation.longitude,
                icon = R.drawable.ic_marker_user,
                onTap = null
            )
            markerLayers += userMarker
            mapView.layerManager.layers.add(userMarker)
        }
    }

    fun currentViewportBounds(): MapViewportBounds? {
        val box = runCatching { mapView.boundingBox }.getOrNull() ?: return null
        return MapViewportBounds(
            minLatitude = box.minLatitude,
            maxLatitude = box.maxLatitude,
            minLongitude = box.minLongitude,
            maxLongitude = box.maxLongitude
        )
    }

    private fun createMarker(
        context: Context,
        latitude: Double,
        longitude: Double,
        icon: Int,
        onTap: (() -> Unit)?
    ): Marker {
        val drawable = AppCompatResources.getDrawable(context, icon)
            ?: error("Drawable not found for marker")
        val bitmap = AndroidGraphicFactory.convertToBitmap(drawable)

        return object : Marker(
            LatLong(latitude, longitude),
            bitmap,
            0,
            -bitmap.height / 2
        ) {
            override fun onTap(tapLatLong: LatLong?, layerXY: org.mapsforge.core.model.Point?, tapXY: org.mapsforge.core.model.Point?): Boolean {
                if (onTap == null || layerXY == null || tapXY == null) return false
                // Marker.onTap is called for every tap on the map; only react near this marker.
                if (!contains(layerXY, tapXY)) return false
                onTap()
                return true
            }
        }
    }
}

private fun createMapRuntime(context: Context, mapFile: File): MapRuntime {
    AndroidGraphicFactory.createInstance(context.applicationContext as Application)

    val mapView = MapView(context)
    mapView.setBuiltInZoomControls(true)
    mapView.isClickable = true

    val tileCache = AndroidUtil.createTileCache(
        context,
        "putivnyk_offline_cache",
        mapView.model.displayModel.tileSize,
        1f,
        mapView.model.frameBufferModel.overdrawFactor
    )

    val tileRendererLayer = TileRendererLayer(
        tileCache,
        MapFile(mapFile),
        mapView.model.mapViewPosition,
        AndroidGraphicFactory.INSTANCE
    ).apply {
        setXmlRenderTheme(InternalRenderTheme.DEFAULT)
    }

    mapView.layerManager.layers.add(tileRendererLayer)

    return MapRuntime(mapView = mapView)
}

/** Copies through a temporary file so an interrupted copy never leaves a truncated map behind. */
private fun copyAssetMapToInternalStorage(
    context: Context,
    assetPath: String
): File {
    val targetFile = File(context.filesDir, "kyiv.map")
    val marker = File(context.filesDir, "kyiv.map.version")
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val version = "${info.longVersionCode}:${info.lastUpdateTime}"
    if (targetFile.exists() && marker.exists() && marker.readText() == version) return targetFile

    val tempFile = File(context.filesDir, "kyiv.map.tmp")
    context.assets.open(assetPath).use { input ->
        tempFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    if (!tempFile.renameTo(targetFile)) {
        targetFile.delete()
        if (!tempFile.renameTo(targetFile)) error("Cannot move offline map into place")
    }
    marker.writeText(version)
    return targetFile
}
