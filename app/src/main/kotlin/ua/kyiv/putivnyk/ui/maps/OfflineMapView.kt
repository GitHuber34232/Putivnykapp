package ua.kyiv.putivnyk.ui.maps

import android.app.Application
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.InternalRenderTheme
import ua.kyiv.putivnyk.R
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.ui.viewmodel.MapCenter
import ua.kyiv.putivnyk.ui.viewmodel.MapViewportBounds
import java.io.File

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
    onPlaceMarkerClick: (Place) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val runtime = remember(context) { createMapRuntime(context) }

    DisposableEffect(Unit) {
        onDispose {
            runtime.mapView.destroyAll()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            runtime.mapView.apply {
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val position = model.mapViewPosition.mapPosition
                        onMapMoved(
                            MapCenter(
                                latitude = position.latLong.latitude,
                                longitude = position.latLong.longitude
                            ),
                            position.zoomLevel.toInt()
                        )
                        runtime.currentViewportBounds()?.let(onViewportChanged)
                    }
                    false
                }
            }
        },
        update = { mapView ->
            val clampedZoom = zoomLevel.coerceIn(10, 19)
            mapView.model.mapViewPosition.setCenter(LatLong(center.latitude, center.longitude))
            mapView.model.mapViewPosition.setZoomLevel(clampedZoom.toByte())

            runtime.replaceMarkerLayers(
                context = context,
                places = places,
                userLocation = userLocation,
                showUserLocation = showUserLocation,
                onPlaceMarkerClick = onPlaceMarkerClick
            )
            runtime.currentViewportBounds()?.let(onViewportChanged)
            mapView.repaint()
        }
    )
}

private class MapRuntime(
    val mapView: MapView,
    private val baseLayer: TileRendererLayer
) {
    private val markerLayers: MutableList<Layer> = mutableListOf()

    fun replaceMarkerLayers(
        context: Context,
        places: List<Place>,
        userLocation: MapCenter,
        showUserLocation: Boolean,
        onPlaceMarkerClick: (Place) -> Unit
    ) {
        markerLayers.forEach { mapView.layerManager.layers.remove(it) }
        markerLayers.clear()

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
                onTap?.invoke()
                return onTap != null
            }
        }
    }
}

private fun createMapRuntime(context: Context): MapRuntime {
    AndroidGraphicFactory.createInstance(context.applicationContext as Application)

    val mapFile = copyAssetMapToInternalStorage(
        context = context,
        assetPath = "maps/kyiv.map"
    )

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

    val mapDataStore = MapFile(mapFile)
    val tileRendererLayer = TileRendererLayer(
        tileCache,
        mapDataStore,
        mapView.model.mapViewPosition,
        AndroidGraphicFactory.INSTANCE
    ).apply {
        setXmlRenderTheme(InternalRenderTheme.DEFAULT)
    }

    mapView.layerManager.layers.add(tileRendererLayer)

    return MapRuntime(
        mapView = mapView,
        baseLayer = tileRendererLayer
    )
}

private fun copyAssetMapToInternalStorage(
    context: Context,
    assetPath: String
): File {
    val targetFile = File(context.filesDir, "kyiv.map")
    if (targetFile.exists()) return targetFile

    context.assets.open(assetPath).use { input ->
        targetFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    return targetFile
}
