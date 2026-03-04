package ua.kyiv.putivnyk.ui.maps

import android.os.Bundle
import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.utils.BitmapUtils
import com.google.gson.JsonPrimitive
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import ua.kyiv.putivnyk.BuildConfig
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.R
import ua.kyiv.putivnyk.ui.viewmodel.MapCenter
import ua.kyiv.putivnyk.ui.viewmodel.MapViewportBounds
import java.net.URI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val PLACE_SOURCE_ID = "putivnyk-places-source"
private const val PLACE_LAYER_ID = "putivnyk-places-layer"
private const val CLUSTER_LAYER_ID = "putivnyk-cluster-layer"
private const val CLUSTER_COUNT_LAYER_ID = "putivnyk-cluster-count-layer"
private const val USER_SOURCE_ID = "putivnyk-user-source"
private const val USER_LAYER_ID = "putivnyk-user-layer"
private const val USER_ARROW_LAYER_ID = "putivnyk-user-arrow-layer"
private const val USER_ARROW_ICON_ID = "putivnyk-user-arrow-icon"
private const val PLACE_ICON_ID = "putivnyk-place-icon"
private const val ROUTE_SOURCE_ID = "putivnyk-route-source"
private const val ROUTE_LINE_LAYER_ID = "putivnyk-route-line-layer"
private const val ROUTE_ARROW_SOURCE_ID = "putivnyk-route-arrow-source"
private const val ROUTE_ARROW_LAYER_ID = "putivnyk-route-arrow-layer"
private const val ROUTE_ARROW_ICON_ID = "putivnyk-route-arrow-icon"
private const val ROUTE_ENDPOINTS_SOURCE_ID = "putivnyk-route-endpoints-source"
private const val ROUTE_ENDPOINTS_LAYER_ID = "putivnyk-route-endpoints-layer"

private fun isSupportedStyleUri(value: String): Boolean {
    val parsed = runCatching { URI(value.trim()) }.getOrNull() ?: return false
    return when (parsed.scheme?.lowercase()) {
        "asset" -> !parsed.schemeSpecificPart.isNullOrBlank()
        "https", "http", "file" -> !parsed.host.isNullOrBlank() || parsed.scheme == "file"
        else -> false
    }
}

@Composable
fun MapLibreMapView(
    modifier: Modifier = Modifier,
    center: MapCenter,
    zoomLevel: Int,
    places: List<Place>,
    placeTitleOverrides: Map<Long, String> = emptyMap(),
    userLocation: MapCenter,
    userBearing: Float = -1f,
    showUserLocation: Boolean,
    onMapMoved: (MapCenter, Int) -> Unit,
    onViewportChanged: (MapViewportBounds) -> Unit,
    onPlaceMarkerClick: (Place) -> Unit,
    onInitializationFailed: () -> Unit = {},
    routePoints: List<RoutePoint> = emptyList()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val styleUri = BuildConfig.MAPLIBRE_STYLE_URI.trim()

    val mapView = remember(
        context,
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp
    ) {
        runCatching {
            MapLibre.getInstance(context.applicationContext)
            MapView(context)
        }.getOrNull()
    }

    if (mapView == null) {
        LaunchedEffect(Unit) {
            onInitializationFailed()
        }
        return
    }

    if (styleUri.isBlank() || !isSupportedStyleUri(styleUri)) {
        LaunchedEffect(Unit) {
            onInitializationFailed()
        }
        return
    }

    var mapReadyState by remember(mapView) { mutableStateOf<MapLibreRuntimeState?>(null) }

    DisposableEffect(lifecycleOwner, mapView) {
        mapView.onCreate(Bundle())

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }

        val app = context.applicationContext as? android.app.Application
        @Suppress("DEPRECATION")
        val trimCallback = object : android.content.ComponentCallbacks2 {
            @Deprecated("Deprecated in ComponentCallbacks2")
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit
            @Deprecated("Deprecated in ComponentCallbacks2")
            override fun onLowMemory() { mapView.onLowMemory() }
            override fun onTrimMemory(level: Int) {
                if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
                    mapView.onLowMemory()
                }
            }
        }
        app?.registerComponentCallbacks(trimCallback)

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            app?.unregisterComponentCallbacks(trimCallback)
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapReadyState?.let { runtime ->
                runtime.isDestroyed = true
                runtime.map.uiSettings.isScrollGesturesEnabled = false
                runtime.map.uiSettings.isZoomGesturesEnabled = false
                runtime.map.uiSettings.isRotateGesturesEnabled = false
                runtime.map.uiSettings.isTiltGesturesEnabled = false
                runtime.map.uiSettings.isDoubleTapGesturesEnabled = false
                runtime.map.uiSettings.isQuickZoomGesturesEnabled = false
            }
            mapReadyState = null
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    map.uiSettings.isCompassEnabled = false
                    map.uiSettings.setCompassMargins(0, 200, 16, 0)
                    map.uiSettings.isZoomGesturesEnabled = true
                    map.uiSettings.isDoubleTapGesturesEnabled = true
                    map.uiSettings.isQuickZoomGesturesEnabled = true
                    map.uiSettings.isScrollGesturesEnabled = true
                    map.uiSettings.isRotateGesturesEnabled = true
                    map.uiSettings.isTiltGesturesEnabled = true
                    map.setStyle(Style.Builder().fromUri(styleUri)) {
                        map.uiSettings.isCompassEnabled = false
                        mapReadyState = MapLibreRuntimeState(map)

                        map.addOnCameraMoveStartedListener { reason ->
                            val runtime = mapReadyState ?: return@addOnCameraMoveStartedListener
                            if (runtime.isDestroyed) return@addOnCameraMoveStartedListener
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                runtime.isGestureCameraMove = true
                            }
                        }

                        map.addOnCameraIdleListener {
                            val runtime = mapReadyState ?: return@addOnCameraIdleListener
                            if (runtime.isDestroyed) return@addOnCameraIdleListener
                            if (runtime.isGestureCameraMove) {
                                runtime.lastGestureFinishedAtMs = System.currentTimeMillis()
                            }
                            runtime.isGestureCameraMove = false
                            runCatching {
                                val target = map.cameraPosition.target ?: return@addOnCameraIdleListener
                                val zoom = map.cameraPosition.zoom.roundToInt().coerceIn(10, 19)
                                onMapMoved(MapCenter(target.latitude, target.longitude), zoom)
                                map.currentViewportBounds()?.let(onViewportChanged)
                            }
                        }
                    }
                }
            }
        },
        update = {
            val runtime = mapReadyState ?: return@AndroidView
            runtime.syncCamera(center = center, zoomLevel = zoomLevel)
            runtime.syncMarkers(
                context = context,
                places = places,
                placeTitleOverrides = placeTitleOverrides,
                userLocation = userLocation,
                userBearing = userBearing,
                showUserLocation = showUserLocation,
                onPlaceMarkerClick = onPlaceMarkerClick,
                onViewportChanged = onViewportChanged
            )
            runtime.syncRoute(context = context, routePoints = routePoints)
        }
    )
}

private data class MapLibreRuntimeState(
    val map: MapLibreMap,
    val placesById: MutableMap<Long, Place> = mutableMapOf(),
    var onPlaceMarkerClick: ((Place) -> Unit)? = null,
    var clickListenerAttached: Boolean = false,
    var isGestureCameraMove: Boolean = false,
    var lastGestureFinishedAtMs: Long = 0L,
    var lastPlacesSignature: Int = Int.MIN_VALUE,
    var lastUserSignature: Int = Int.MIN_VALUE,
    var lastShowUserLocation: Boolean = false,
    var lastUserBearing: Float = -1f,
    var lastRouteSignature: Int = Int.MIN_VALUE,
    @Volatile var isDestroyed: Boolean = false
) {
    fun syncCamera(center: MapCenter, zoomLevel: Int) {
        if (isDestroyed) return
        val now = System.currentTimeMillis()
        if (now - lastGestureFinishedAtMs < 900L) return
        if (isGestureCameraMove) return

        val zoom = zoomLevel.coerceIn(10, 19)
        val current = runCatching { map.cameraPosition }.getOrNull() ?: return
        val currentTarget = current.target ?: return
        val target = LatLng(center.latitude, center.longitude)

        val latitudeDelta = abs(currentTarget.latitude - target.latitude)
        val longitudeDelta = abs(currentTarget.longitude - target.longitude)
        val zoomDelta = abs(current.zoom - zoom)

        if (latitudeDelta < 0.00035 && longitudeDelta < 0.00035 && zoomDelta < 1.2) {
            return
        }

        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(zoom.toDouble())
                    .build()
            ),
            280
        )
    }

    fun syncMarkers(
        context: Context,
        places: List<Place>,
        placeTitleOverrides: Map<Long, String>,
        userLocation: MapCenter,
        userBearing: Float,
        showUserLocation: Boolean,
        onPlaceMarkerClick: (Place) -> Unit,
        onViewportChanged: (MapViewportBounds) -> Unit
    ) {
        if (isDestroyed) return
        val style = map.style ?: return
        this.onPlaceMarkerClick = onPlaceMarkerClick

        ensurePlaceLayers(context = context, style = style)
        ensureUserLayer(context = context, style = style)

        val placesSignature = calculatePlacesSignature(places, placeTitleOverrides)
        if (placesSignature != lastPlacesSignature) {
            placesById.clear()
            places.forEach { place -> placesById[place.id] = place }

            val placeFeatures = places.map { place ->
                val markerTitle = placeTitleOverrides[place.id]
                    ?.takeIf { it.isNotBlank() }
                    ?: place.name
                Feature.fromGeometry(Point.fromLngLat(place.longitude, place.latitude)).apply {
                    addProperty("place_id", JsonPrimitive(place.id))
                    addProperty("title", JsonPrimitive(markerTitle))
                }
            }

            style.getSourceAs<GeoJsonSource>(PLACE_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeatures(placeFeatures))
            lastPlacesSignature = placesSignature
        }

        val userSignature = calculateUserSignature(userLocation)
        val bearingChanged = abs(userBearing - lastUserBearing) > 1f
        if (userSignature != lastUserSignature || showUserLocation != lastShowUserLocation || bearingChanged) {
            val userFeatureCollection = if (showUserLocation) {
                val feature = Feature.fromGeometry(
                    Point.fromLngLat(userLocation.longitude, userLocation.latitude)
                )
                if (userBearing >= 0f) {
                    feature.addProperty("bearing", JsonPrimitive(userBearing.toDouble()))
                }
                FeatureCollection.fromFeature(feature)
            } else {
                FeatureCollection.fromFeatures(emptyList())
            }
            style.getSourceAs<GeoJsonSource>(USER_SOURCE_ID)
                ?.setGeoJson(userFeatureCollection)

            style.getLayer(USER_ARROW_LAYER_ID)?.setProperties(
                org.maplibre.android.style.layers.PropertyFactory.visibility(
                    if (showUserLocation && userBearing >= 0f)
                        Property.VISIBLE else Property.NONE
                )
            )

            lastUserSignature = userSignature
            lastShowUserLocation = showUserLocation
            lastUserBearing = userBearing
        }

        ensureMapClickListener()
    }

    private fun calculatePlacesSignature(
        places: List<Place>,
        placeTitleOverrides: Map<Long, String>
    ): Int {
        var result = places.size
        places.forEach { place ->
            result = 31 * result + place.id.hashCode()
            result = 31 * result + (place.latitude * 10000).roundToInt()
            result = 31 * result + (place.longitude * 10000).roundToInt()
            result = 31 * result + (placeTitleOverrides[place.id]?.hashCode() ?: 0)
        }
        return result
    }

    private fun calculateUserSignature(userLocation: MapCenter): Int {
        var result = (userLocation.latitude * 100000).roundToInt()
        result = 31 * result + (userLocation.longitude * 100000).roundToInt()
        return result
    }

    private fun ensureMapClickListener() {
        if (clickListenerAttached) return

        map.addOnMapClickListener { latLng: LatLng ->
            if (isDestroyed) return@addOnMapClickListener false
            val screenPoint = map.projection.toScreenLocation(latLng)
            val clusterRendered = map.queryRenderedFeatures(screenPoint, CLUSTER_LAYER_ID)
            val clusterFeature = clusterRendered.firstOrNull()
            if (clusterFeature != null) {
                val clusterPointCount = clusterFeature.getNumberProperty("point_count")?.toInt() ?: 0
                val adaptivePadding = when {
                    clusterPointCount >= 120 -> (BuildConfig.MAPLIBRE_CLUSTER_TAP_PADDING * 1.6).toInt()
                    clusterPointCount >= 60 -> (BuildConfig.MAPLIBRE_CLUSTER_TAP_PADDING * 1.35).toInt()
                    clusterPointCount >= 25 -> (BuildConfig.MAPLIBRE_CLUSTER_TAP_PADDING * 1.15).toInt()
                    else -> BuildConfig.MAPLIBRE_CLUSTER_TAP_PADDING
                }
                val adaptiveDurationMs = when {
                    clusterPointCount >= 120 -> (BuildConfig.MAPLIBRE_CLUSTER_TAP_DURATION_MS * 1.35).toInt()
                    clusterPointCount >= 60 -> (BuildConfig.MAPLIBRE_CLUSTER_TAP_DURATION_MS * 1.2).toInt()
                    else -> BuildConfig.MAPLIBRE_CLUSTER_TAP_DURATION_MS
                }

                val expanded = animateToClusterBounds(
                    clusterCenter = latLng,
                    clusterPointCount = clusterPointCount,
                    paddingPx = adaptivePadding,
                    durationMs = adaptiveDurationMs
                )
                if (!expanded) {
                    val nextZoom = (map.cameraPosition.zoom + 2.0).coerceAtMost(19.0)
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(latLng, nextZoom),
                        BuildConfig.MAPLIBRE_CLUSTER_TAP_DURATION_MS
                    )
                }
                return@addOnMapClickListener true
            }

            val rendered = map.queryRenderedFeatures(screenPoint, PLACE_LAYER_ID)
            val feature = rendered.firstOrNull() ?: return@addOnMapClickListener false
            val placeId = feature.getNumberProperty("place_id")?.toLong() ?: return@addOnMapClickListener false
            val place = placesById[placeId] ?: return@addOnMapClickListener false
            onPlaceMarkerClick?.invoke(place)
            true
        }

        clickListenerAttached = true
    }

    private fun animateToClusterBounds(
        clusterCenter: LatLng,
        clusterPointCount: Int,
        paddingPx: Int,
        durationMs: Int
    ): Boolean {
        if (placesById.isEmpty()) return false

        val zoom = map.cameraPosition.zoom
        val baseRadiusDegrees = when {
            zoom >= 17 -> 0.004
            zoom >= 15 -> 0.008
            zoom >= 13 -> 0.015
            zoom >= 11 -> 0.03
            else -> 0.06
        }
        val densityMultiplier = when {
            clusterPointCount >= 120 -> 1.7
            clusterPointCount >= 60 -> 1.45
            clusterPointCount >= 25 -> 1.25
            else -> 1.0
        }
        val radiusDegrees = baseRadiusDegrees * densityMultiplier

        val nearby = placesById.values
            .filter { place ->
                kotlin.math.abs(place.latitude - clusterCenter.latitude) <= radiusDegrees &&
                    kotlin.math.abs(place.longitude - clusterCenter.longitude) <= radiusDegrees
            }
            .ifEmpty {
                placesById.values
                    .sortedBy { place ->
                        val latDelta = place.latitude - clusterCenter.latitude
                        val lonDelta = place.longitude - clusterCenter.longitude
                        latDelta * latDelta + lonDelta * lonDelta
                    }
                    .take((clusterPointCount.coerceIn(6, 64)).coerceAtLeast(6))
            }

        if (nearby.size < BuildConfig.MAPLIBRE_CLUSTER_MIN_POINTS) return false

        val boundsBuilder = LatLngBounds.Builder()
        nearby.take(64).forEach { place ->
            boundsBuilder.include(LatLng(place.latitude, place.longitude))
        }
        boundsBuilder.include(clusterCenter)

        val bounds = runCatching { boundsBuilder.build() }.getOrNull() ?: return false
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx), durationMs)
        return true
    }

    private fun ensurePlaceLayers(context: Context, style: Style) {
        if (style.getImage(PLACE_ICON_ID) == null) {
            val drawable = AppCompatResources.getDrawable(
                context,
                R.drawable.ic_marker_place
            )
            val bitmap = drawable?.let { BitmapUtils.getBitmapFromDrawable(it) }
            if (bitmap != null) {
                style.addImage(PLACE_ICON_ID, bitmap)
            }
        }

        if (style.getSource(PLACE_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(
                    PLACE_SOURCE_ID,
                    FeatureCollection.fromFeatures(emptyList<Feature>()),
                    GeoJsonOptions()
                        .withCluster(true)
                        .withClusterRadius(BuildConfig.MAPLIBRE_CLUSTER_RADIUS)
                )
            )
        }

        if (style.getLayer(CLUSTER_LAYER_ID) == null) {
            style.addLayer(
                CircleLayer(CLUSTER_LAYER_ID, PLACE_SOURCE_ID)
                    .withFilter(has("point_count"))
                    .withProperties(
                        circleColor("#1A73E8"),
                        circleRadius(
                            Expression.step(
                                get("point_count"),
                                literal(16),
                                stop(20, literal(20)),
                                stop(50, literal(24)),
                                stop(100, literal(28))
                            )
                        )
                    )
            )
        }

        if (style.getLayer(CLUSTER_COUNT_LAYER_ID) == null) {
            style.addLayer(
                SymbolLayer(CLUSTER_COUNT_LAYER_ID, PLACE_SOURCE_ID)
                    .withFilter(has("point_count"))
                    .withProperties(
                        textField(get("point_count")),
                        textSize(12f),
                        textColor("#111111"),
                        textAllowOverlap(true),
                        textIgnorePlacement(true)
                    )
            )
        }

        if (style.getLayer(PLACE_LAYER_ID) == null) {
            style.addLayer(
                SymbolLayer(PLACE_LAYER_ID, PLACE_SOURCE_ID)
                    .withFilter(not(has("point_count")))
                    .withProperties(
                    iconImage(PLACE_ICON_ID),
                    iconSize(
                        Expression.interpolate(
                            Expression.linear(),
                            zoom(),
                            stop(10, literal(0.55)),
                            stop(14, literal(0.75)),
                            stop(19, literal(1.0))
                        )
                    ),
                    iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    iconOffset(arrayOf(0f, -0.5f)),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                    textField(get("title")),
                    textSize(11f),
                    textColor("#111111"),
                    textOffset(arrayOf(0f, 1.1f)),
                    textAllowOverlap(false),
                    textIgnorePlacement(false)
                )
            )
        }
    }

    private fun ensureUserLayer(context: Context, style: Style) {
        if (style.getSource(USER_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(
                    USER_SOURCE_ID,
                    FeatureCollection.fromFeatures(emptyList<Feature>())
                )
            )
        }

        if (style.getLayer(USER_LAYER_ID) == null) {
            style.addLayer(
                CircleLayer(USER_LAYER_ID, USER_SOURCE_ID).withProperties(
                    circleColor("#1A73E8"),
                    circleRadius(8f)
                )
            )
        }

        if (style.getImage(USER_ARROW_ICON_ID) == null) {
            val drawable = AppCompatResources.getDrawable(
                context,
                R.drawable.ic_user_direction_arrow
            )
            val bitmap = drawable?.let { BitmapUtils.getBitmapFromDrawable(it) }
            if (bitmap != null) {
                style.addImage(USER_ARROW_ICON_ID, bitmap)
            }
        }

        if (style.getLayer(USER_ARROW_LAYER_ID) == null) {
            style.addLayer(
                SymbolLayer(USER_ARROW_LAYER_ID, USER_SOURCE_ID).withProperties(
                    iconImage(USER_ARROW_ICON_ID),
                    iconSize(0.8f),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                    iconRotate(get("bearing")),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    iconOffset(arrayOf(0f, -2.5f)),
                    org.maplibre.android.style.layers.PropertyFactory.visibility(Property.NONE)
                )
            )
        }
    }

    fun syncRoute(context: Context, routePoints: List<RoutePoint>) {
        if (isDestroyed) return
        val style = map.style ?: return

        val sig = routePoints.hashCode()
        if (sig == lastRouteSignature) return
        lastRouteSignature = sig

        ensureRouteLayer(context = context, style = style)

        if (routePoints.size < 2) {
            style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            style.getSourceAs<GeoJsonSource>(ROUTE_ARROW_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            style.getSourceAs<GeoJsonSource>(ROUTE_ENDPOINTS_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }

        val linePoints = routePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
        val lineString = LineString.fromLngLats(linePoints)
        style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
            ?.setGeoJson(Feature.fromGeometry(lineString))

        val startPt = routePoints.first()
        val endPt = routePoints.last()
        val endpointFeatures = listOf(
            Feature.fromGeometry(Point.fromLngLat(startPt.longitude, startPt.latitude)),
            Feature.fromGeometry(Point.fromLngLat(endPt.longitude, endPt.latitude))
        )
        style.getSourceAs<GeoJsonSource>(ROUTE_ENDPOINTS_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(endpointFeatures))

        val arrowFeatures = mutableListOf<Feature>()
        for (i in 0 until routePoints.size - 1) {
            val from = routePoints[i]
            val to = routePoints[i + 1]
            val midLat = (from.latitude + to.latitude) / 2
            val midLon = (from.longitude + to.longitude) / 2
            val bearing = calculateBearing(from.latitude, from.longitude, to.latitude, to.longitude)
            val feature = Feature.fromGeometry(Point.fromLngLat(midLon, midLat))
            feature.addProperty("bearing", JsonPrimitive(bearing))
            arrowFeatures.add(feature)
        }
        style.getSourceAs<GeoJsonSource>(ROUTE_ARROW_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(arrowFeatures))
    }

    private fun ensureRouteLayer(context: Context, style: Style) {
        if (style.getSource(ROUTE_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(ROUTE_SOURCE_ID, FeatureCollection.fromFeatures(emptyList()))
            )
        }
        if (style.getSource(ROUTE_ARROW_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(ROUTE_ARROW_SOURCE_ID, FeatureCollection.fromFeatures(emptyList()))
            )
        }
        if (style.getSource(ROUTE_ENDPOINTS_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(ROUTE_ENDPOINTS_SOURCE_ID, FeatureCollection.fromFeatures(emptyList()))
            )
        }

        if (style.getLayer(ROUTE_LINE_LAYER_ID) == null) {
            val lineLayer = LineLayer(ROUTE_LINE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                lineColor("#4285F4"),
                lineWidth(5f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )

            val belowLayer = when {
                style.getLayer(USER_LAYER_ID) != null -> USER_LAYER_ID
                style.getLayer(CLUSTER_LAYER_ID) != null -> CLUSTER_LAYER_ID
                else -> null
            }
            if (belowLayer != null) {
                style.addLayerBelow(lineLayer, belowLayer)
            } else {
                style.addLayer(lineLayer)
            }
        }

        if (style.getLayer(ROUTE_ENDPOINTS_LAYER_ID) == null) {
            val endpointsLayer = CircleLayer(ROUTE_ENDPOINTS_LAYER_ID, ROUTE_ENDPOINTS_SOURCE_ID).withProperties(
                circleRadius(7f),
                circleColor("#4285F4"),
                circleStrokeWidth(2.5f),
                circleStrokeColor("#FFFFFF")
            )

            if (style.getLayer(USER_LAYER_ID) != null) {
                style.addLayerBelow(endpointsLayer, USER_LAYER_ID)
            } else {
                style.addLayer(endpointsLayer)
            }
        }

        if (style.getImage(ROUTE_ARROW_ICON_ID) == null) {
            val drawable = AppCompatResources.getDrawable(context, R.drawable.ic_route_direction_arrow)
            val bitmap = drawable?.let { BitmapUtils.getBitmapFromDrawable(it) }
            if (bitmap != null) {
                style.addImage(ROUTE_ARROW_ICON_ID, bitmap)
            }
        }

        if (style.getLayer(ROUTE_ARROW_LAYER_ID) == null) {
            val arrowLayer =
                SymbolLayer(ROUTE_ARROW_LAYER_ID, ROUTE_ARROW_SOURCE_ID).withProperties(
                    iconImage(ROUTE_ARROW_ICON_ID),
                    iconSize(0.6f),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                    iconRotate(get("bearing")),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP)
                )

            if (style.getLayer(USER_LAYER_ID) != null) {
                style.addLayerBelow(arrowLayer, USER_LAYER_ID)
            } else {
                style.addLayer(arrowLayer)
            }
        }
    }
}

private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

private fun org.maplibre.android.maps.MapLibreMap.currentViewportBounds(): MapViewportBounds? {
    val bounds = runCatching { projection.visibleRegion.latLngBounds }.getOrNull() ?: return null
    return MapViewportBounds(
        minLatitude = bounds.latitudeSouth,
        maxLatitude = bounds.latitudeNorth,
        minLongitude = bounds.longitudeWest,
        maxLongitude = bounds.longitudeEast
    )
}
