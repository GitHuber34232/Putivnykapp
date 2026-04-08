package ua.kyiv.putivnyk.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.PlaceSortMode
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.TransportMode
import ua.kyiv.putivnyk.ui.i18n.trCategory
import ua.kyiv.putivnyk.ui.i18n.trDynamic
import ua.kyiv.putivnyk.ui.i18n.trRiverBank
import ua.kyiv.putivnyk.ui.i18n.trSortMode
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.maps.MapLibreMapView
import ua.kyiv.putivnyk.ui.maps.OfflineMapView
import ua.kyiv.putivnyk.ui.utils.RouteUiFormatter
import ua.kyiv.putivnyk.ui.viewmodel.MapViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    startRouteBuilder: Boolean = false,
    onOpenPlaceDetails: (Long) -> Unit = {},
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val places by viewModel.placesOnMap.collectAsState()
    val selectedPlace by viewModel.selectedPlace.collectAsState()
    val showOnlyFavorites by viewModel.showOnlyFavorites.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val mapCenter by viewModel.mapCenter.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val userBearing by viewModel.userBearing.collectAsState()
    val hasUserLocation by viewModel.hasUserLocation.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val activeRoute by viewModel.activeRoute.collectAsState()
    val routeLinePoints by viewModel.routeLinePoints.collectAsState()
    val pendingAddPlace by viewModel.pendingRouteAddPlace.collectAsState()
    val isCreatingRoute by viewModel.isCreatingRoute.collectAsState()
    val currentWaypointIndex by viewModel.currentWaypointIndex.collectAsState()
    val remainingDistance by viewModel.remainingDistance.collectAsState()
    val remainingMinutes by viewModel.remainingMinutes.collectAsState()
    val routeProgressFraction by viewModel.routeProgressFraction.collectAsState()
    val distanceToNextWaypoint by viewModel.distanceToNextWaypoint.collectAsState()
    val nextWaypointName by viewModel.nextWaypointName.collectAsState()
    val poiPromptPlace by viewModel.poiPromptPlace.collectAsState()
    val isRerouting by viewModel.isRerouting.collectAsState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var bottomPanelHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeRoute) {
        if (activeRoute != null) bottomPanelHeightPx = 0
    }

    val activity = context as? android.app.Activity
    DisposableEffect(activeRoute != null) {
        if (activeRoute != null) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var routeBuilderMode by remember(startRouteBuilder) { mutableStateOf(startRouteBuilder) }
    var routeName by remember { mutableStateOf("") }
    var showRouteNameDialog by remember { mutableStateOf(false) }
    var useOfflineFallback by remember { mutableStateOf(false) }
    val selectedRoutePoints = remember { mutableStateListOf<Place>() }
    val translatedMapTitles = places.associate { place ->
        place.id to trDynamic(place.name)
    }
    val requestLocationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        viewModel.syncPendingFocus()
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    ObserveGpsLocation(
        context = context,
        onLocationChanged = { latitude, longitude, bearing, accuracy ->
            viewModel.updateUserLocation(latitude, longitude, bearing, accuracy)
        },
        onSatellitesChanged = { usedCount, totalCount ->
            viewModel.updateGpsSatellites(usedCount, totalCount)
        }
    )

    ObserveDeviceBearing(
        context = context,
        onBearingChanged = { bearing -> viewModel.updateUserBearing(bearing) }
    )

    LaunchedEffect(selectedPlace?.id, places) {
        val selectedId = selectedPlace?.id ?: return@LaunchedEffect
        val index = places.indexOfFirst { it.id == selectedId }
        if (index >= 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(index)
            }
        }
    }

    val adaptiveMode = remember(configuration.screenWidthDp, configuration.orientation) {
        when {
            configuration.screenWidthDp >= 840 -> MapAdaptiveMode.EXPANDED
            configuration.screenWidthDp >= 600 -> MapAdaptiveMode.MEDIUM
            else -> MapAdaptiveMode.COMPACT
        }
    }

    val cardsHeight = remember(adaptiveMode, configuration.orientation) {
        if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            96.dp
        } else {
            when (adaptiveMode) {
                MapAdaptiveMode.COMPACT -> 112.dp
                MapAdaptiveMode.MEDIUM -> 102.dp
                MapAdaptiveMode.EXPANDED -> 96.dp
            }
        }
    }
    val topPanelPadding = remember(adaptiveMode) {
        when (adaptiveMode) {
            MapAdaptiveMode.COMPACT -> 6.dp
            MapAdaptiveMode.MEDIUM -> 5.dp
            MapAdaptiveMode.EXPANDED -> 4.dp
        }
    }
    val topPanelSpacing = remember(adaptiveMode) {
        when (adaptiveMode) {
            MapAdaptiveMode.COMPACT -> 8.dp
            MapAdaptiveMode.MEDIUM -> 6.dp
            MapAdaptiveMode.EXPANDED -> 6.dp
        }
    }
    val topPanelMaxWidth: Dp = remember(adaptiveMode) {
        when (adaptiveMode) {
            MapAdaptiveMode.COMPACT -> 900.dp
            MapAdaptiveMode.MEDIUM -> 760.dp
            MapAdaptiveMode.EXPANDED -> 680.dp
        }
    }
    val useSideContentDock = remember(adaptiveMode, configuration.orientation) {
        adaptiveMode == MapAdaptiveMode.EXPANDED &&
            configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }

    Box(modifier = Modifier.fillMaxSize()) {
            if (useOfflineFallback) {
                OfflineMapView(
                    modifier = Modifier.fillMaxSize(),
                    center = mapCenter,
                    zoomLevel = zoomLevel,
                    places = places,
                    userLocation = userLocation,
                    showUserLocation = hasUserLocation,
                    onMapMoved = { newCenter, newZoom ->
                        viewModel.updateMapCenter(newCenter.latitude, newCenter.longitude)
                        viewModel.updateZoomLevel(newZoom)
                    },
                    onViewportChanged = { bounds ->
                        viewModel.updateVisibleBounds(bounds)
                    },
                    onPlaceMarkerClick = { place ->
                        if (routeBuilderMode) {
                            if (place.category != PlaceCategory.TOILET &&
                                selectedRoutePoints.none { it.id == place.id } &&
                                selectedRoutePoints.none {
                                    kotlin.math.abs(it.latitude - place.latitude) < 0.0001 &&
                                        kotlin.math.abs(it.longitude - place.longitude) < 0.0001
                                }
                            ) {
                                selectedRoutePoints.add(place)
                            }
                        } else {
                            viewModel.selectPlace(place)
                        }
                    }
                )
            } else {
                MapLibreMapView(
                    modifier = Modifier.fillMaxSize(),
                    center = mapCenter,
                    zoomLevel = zoomLevel,
                    places = if (activeRoute != null) emptyList() else places,
                    placeTitleOverrides = translatedMapTitles,
                    userLocation = userLocation,
                    userBearing = userBearing,
                    showUserLocation = hasUserLocation,
                    isNavigating = activeRoute != null,
                    onMapMoved = { newCenter, newZoom ->
                        viewModel.updateMapCenter(newCenter.latitude, newCenter.longitude)
                        viewModel.updateZoomLevel(newZoom)
                    },
                    onViewportChanged = { bounds ->
                        viewModel.updateVisibleBounds(bounds)
                    },
                    onPlaceMarkerClick = { place ->
                        if (routeBuilderMode) {
                            if (place.category != PlaceCategory.TOILET &&
                                selectedRoutePoints.none { it.id == place.id } &&
                                selectedRoutePoints.none {
                                    kotlin.math.abs(it.latitude - place.latitude) < 0.0001 &&
                                        kotlin.math.abs(it.longitude - place.longitude) < 0.0001
                                }
                            ) {
                                selectedRoutePoints.add(place)
                            }
                        } else {
                            viewModel.selectPlace(place)
                        }
                    },
                    onInitializationFailed = {
                        useOfflineFallback = true
                    },
                    routePoints = routeLinePoints
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(
                        WindowInsets.statusBars
                            .asPaddingValues()
                            .calculateTopPadding() + 8.dp
                    )
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )

            AnimatedVisibility(
                visible = activeRoute == null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .widthIn(max = topPanelMaxWidth),
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(topPanelPadding),
                    verticalArrangement = Arrangement.spacedBy(topPanelSpacing)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text(tr("map.search", "Пошук на карті")) }
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { viewModel.selectCategory(null) },
                                label = { Text(tr("map.all", "Всі")) }
                            )
                        }
                        items(PlaceCategory.entries.filter { it != PlaceCategory.TOILET }) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { viewModel.selectCategory(category) },
                                label = { Text(trCategory(category)) }
                            )
                        }
                    }
                }
            }

            if (activeRoute != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .widthIn(max = topPanelMaxWidth)
                ) {
                    ElevatedCard {
                        Column(
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp, end = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr("map.active_route", "Активний маршрут"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${activeRoute!!.name} (${activeRoute!!.waypoints.size + 2} ${tr("routes.points_label", "точок")})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { viewModel.deactivateRoute() }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = tr("map.deactivate_route", "Деактивувати маршрут")
                                    )
                                }
                            }

                            if (isRerouting) {
                                Row(
                                    modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = tr("routes.rerouting", "Перепрокладання маршруту..."),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (hasUserLocation && remainingDistance > 0) {
                                val totalPoints = (activeRoute!!.waypoints.size + 2)

                                LinearProgressIndicator(
                                    progress = { routeProgressFraction },
                                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    Text(
                                        text = "${currentWaypointIndex + 1}/$totalPoints",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Text(
                                        text = RouteUiFormatter.formatDistance(remainingDistance),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Text(
                                        text = RouteUiFormatter.formatDuration(remainingMinutes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                nextWaypointName?.let { name ->
                                    Text(
                                        text = "→ ${trDynamic(name)} (${RouteUiFormatter.formatDistance(distanceToNextWaypoint)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(end = 8.dp, bottom = 6.dp)
                                    )
                                }

                                TextButton(
                                    onClick = { viewModel.skipWaypoint() },
                                    modifier = Modifier.padding(bottom = 2.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        tr("routes.skip_waypoint", "Пропустити точку"),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            if (hasUserLocation && currentWaypointIndex >= (activeRoute!!.waypoints.size + 1)) {
                                Text(
                                    text = "🎉 ${tr("routes.completed", "Маршрут завершено!")}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                TextButton(
                                    onClick = { viewModel.deactivateAndResetProgress() },
                                    modifier = Modifier.padding(bottom = 2.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        tr("routes.finish", "Завершити"),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }

            }

            if (routeBuilderMode) {
                ElevatedCard(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${tr("map.route_points", "Точки маршруту")}: ${selectedRoutePoints.size}")
                        selectedRoutePoints.takeLast(4).forEachIndexed { index, place ->
                            Text(
                                "${index + 1}. ${trDynamic(place.name)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { selectedRoutePoints.clear() }) {
                                Text(tr("map.clear", "Очистити"))
                            }
                            FilledIconButton(
                                onClick = { showRouteNameDialog = true },
                                enabled = selectedRoutePoints.size >= 2
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = tr("routes.create", "Створити маршрут"))
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = activeRoute == null,
                modifier = Modifier
                    .align(if (useSideContentDock) Alignment.CenterEnd else Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
            Column(
                modifier = Modifier
                    .then(
                        if (useSideContentDock) {
                            Modifier
                                .width(380.dp)
                                .fillMaxHeight(0.50f)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    )
                    .padding(end = if (useSideContentDock) 8.dp else 0.dp)
                    .onSizeChanged { size -> bottomPanelHeightPx = size.height }
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .padding(top = 6.dp, bottom = 6.dp)
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PlaceSortMode.entries) { mode ->
                        FilterChip(
                            selected = sortMode == mode,
                            onClick = { viewModel.setSortMode(mode) },
                            label = { Text(trSortMode(mode)) }
                        )
                    }
                }

                if (bookmarks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(bookmarks.take(8), key = { it.id }) { bookmark ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.applyBookmark(bookmark) },
                                label = { Text(bookmark.title, maxLines = 1) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Bookmark, contentDescription = null)
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = tr("map.delete_bookmark", "Видалити закладку"),
                                        modifier = Modifier.clickable {
                                            viewModel.removeBookmark(bookmark)
                                        }
                                    )
                                }
                            )
                        }
                    }
                }

                if (selectedPlace != null) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = trDynamic(selectedPlace!!.name),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${trCategory(selectedPlace!!.category)} • ${trRiverBank(selectedPlace!!.riverBank)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedPlace!!.description.orEmpty().ifBlank { tr("map.details_hint", "Натисніть на картку нижче для повної інформації") }.let { trDynamic(it) },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FilledTonalButton(
                                onClick = { viewModel.createRouteToPlace(selectedPlace!!) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = hasUserLocation,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NearMe,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (hasUserLocation) tr("map.route_here", "Маршрут сюди")
                                    else tr("map.route_here_no_gps", "Маршрут сюди (очікування GPS…)")
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(places, key = { it.id }) { place ->
                        MapPlaceCard(
                            modifier = Modifier.animateItem(),
                            place = place,
                            height = cardsHeight,
                            onOpen = {
                                viewModel.selectPlace(place)
                            }
                        )
                    }
                }
            }
            }

            FloatingActionButton(
                onClick = { viewModel.centerOnUserLocation() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = with(density) { bottomPanelHeightPx.toDp() } + 16.dp
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = tr("map.center_kyiv", "Центрувати на Києві")
                )
            }

            if (isCreatingRoute) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    ElevatedCard {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(tr("map.creating_route", "Створення маршруту…"))
                        }
                    }
                }
            }
    }

    if (pendingAddPlace != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAddToRouteDialog() },
            title = { Text(tr("map.add_to_route", "Додати до маршруту")) },
            text = {
                Text(
                    "${tr("map.add_to_route_q", "Додати")} \"${trDynamic(pendingAddPlace!!.name)}\" ${tr("map.add_to_route_tail", "до активного маршруту?")}"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmAddToActiveRoute() }) {
                    Text(tr("map.add", "Додати"))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAddToRouteDialog() }) {
                    Text(tr("home.cancel", "Скасувати"))
                }
            }
        )
    }

    if (poiPromptPlace != null) {
        val poiName = trDynamic(poiPromptPlace!!.name)
        val categoryIcon = poiPromptPlace!!.category.icon
        AlertDialog(
            onDismissRequest = { viewModel.dismissPoiPrompt() },
            title = { Text("$categoryIcon ${tr("routes.poi_nearby", "Цікаве місце поруч")}") },
            text = {
                Text(
                    "${tr("routes.poi_visit_question", "Чи бажаєте заглянути до")} \"$poiName\"?"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.acceptPoiVisit() }) {
                    Text(tr("routes.poi_yes", "Так, зайду"))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPoiPrompt() }) {
                    Text(tr("routes.poi_no", "Ні, продовжити"))
                }
            }
        )
    }

    if (showRouteNameDialog) {
        var selectedTransportMode by remember { mutableStateOf(TransportMode.WALKING) }
        AlertDialog(
            onDismissRequest = { showRouteNameDialog = false },
            title = { Text(tr("map.route_name", "Назва маршруту")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = routeName,
                        onValueChange = { routeName = it },
                        singleLine = true,
                        placeholder = { Text(tr("map.route_name_hint", "Наприклад: Вечірній центр")) }
                    )
                    Text(
                        text = tr("routes.transport_mode", "Транспорт"),
                        style = MaterialTheme.typography.labelMedium
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedTransportMode == TransportMode.WALKING,
                            onClick = { selectedTransportMode = TransportMode.WALKING },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text(trDynamic("Пішки")) }
                        SegmentedButton(
                            selected = selectedTransportMode == TransportMode.DRIVING,
                            onClick = { selectedTransportMode = TransportMode.DRIVING },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(trDynamic("Автомобіль")) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createRouteFromPlaces(routeName, selectedRoutePoints.toList(), selectedTransportMode)
                    selectedRoutePoints.clear()
                    showRouteNameDialog = false
                    routeBuilderMode = false
                }) {
                    Text(tr("home.save", "Зберегти"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRouteNameDialog = false }) {
                    Text(tr("home.cancel", "Скасувати"))
                }
            }
        )
    }
}

@Composable
private fun MapPlaceCard(
    modifier: Modifier = Modifier,
    place: Place,
    height: androidx.compose.ui.unit.Dp,
    onOpen: () -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .width(280.dp)
            .height(height)
            .clickable(onClick = onOpen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) { index ->
                    val imageUrl = place.imageUrls.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        if (imageUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageUrl)
                                    .crossfade(300)
                                    .build(),
                                contentDescription = tr("details.photo", "Фото локації"),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = "${tr("details.photo", "Фото")} ${index + 1}",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            Text(
                text = trDynamic(place.name),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${trCategory(place.category)} • ★ ${String.format("%.1f", place.rating ?: 0.0)} • 🔥 ${place.popularity} • ${trRiverBank(place.riverBank)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = tr("map.view_hint", "Натисніть для перегляду"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private enum class MapAdaptiveMode {
    COMPACT,
    MEDIUM,
    EXPANDED
}

@SuppressLint("MissingPermission")
@Composable
private fun ObserveGpsLocation(
    context: Context,
    onLocationChanged: (Double, Double, Float, Float) -> Unit,
    onSatellitesChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    var providerRevision by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                providerRevision++
            }
        }
        val filter = android.content.IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    DisposableEffect(context, providerRevision, lifecycleOwner) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            return@DisposableEffect onDispose { }
        }

        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return@DisposableEffect onDispose { }
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val bearing = if (location.hasBearing()) location.bearing else -1f
                val accuracy = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
                onLocationChanged(location.latitude, location.longitude, bearing, accuracy)
            }

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) {
                providerRevision++
            }
            override fun onProviderDisabled(provider: String) = Unit
        }

        val allProviders = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        fun emitBestLastKnown() {
            runCatching {
                val bestLastKnown = allProviders
                    .mapNotNull { provider ->
                        runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                    }
                    .maxByOrNull { location ->
                        val accuracyScore = if (location.hasAccuracy()) -location.accuracy else -1000f
                        val timeScore = location.time / 1000f
                        accuracyScore + timeScore
                    }
                bestLastKnown?.let {
                    val bearing = if (it.hasBearing()) it.bearing else -1f
                    val accuracy = if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE
                    onLocationChanged(it.latitude, it.longitude, bearing, accuracy)
                }
            }
        }

        fun registerGpsUpdates() {
            val providers = allProviders.filter { provider ->
                runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
            }
            emitBestLastKnown()
            providers.forEach { provider ->
                runCatching {
                    val minDistance: Float
                    val minTime: Long
                    if (provider == LocationManager.GPS_PROVIDER && hasFine) {
                        minTime = 500L
                        minDistance = 0f
                    } else {
                        minTime = 1000L
                        minDistance = 5f
                    }
                    locationManager.requestLocationUpdates(
                        provider,
                        minTime,
                        minDistance,
                        listener
                    )
                }
            }
        }

        fun unregisterGpsUpdates() {
            runCatching { locationManager.removeUpdates(listener) }
        }

        var gnssCallback: android.location.GnssStatus.Callback? = null
        if (hasFine && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            gnssCallback = object : android.location.GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
                    var usedCount = 0
                    val totalCount = status.satelliteCount
                    for (i in 0 until totalCount) {
                        if (status.usedInFix(i)) usedCount++
                    }
                    onSatellitesChanged(usedCount, totalCount)
                }
            }
            runCatching {
                locationManager.registerGnssStatusCallback(gnssCallback, android.os.Handler(android.os.Looper.getMainLooper()))
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registerGpsUpdates()
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> registerGpsUpdates()
                Lifecycle.Event.ON_PAUSE -> unregisterGpsUpdates()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            unregisterGpsUpdates()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                gnssCallback?.let { runCatching { locationManager.unregisterGnssStatusCallback(it) } }
            }
        }
    }
}

@Composable
private fun ObserveDeviceBearing(
    context: Context,
    onBearingChanged: (Float) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context, lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensorManager == null || rotationSensor == null) {
            return@DisposableEffect onDispose { }
        }

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var lastEmitTime = 0L
        val throttleMs = 50L
        var smoothedAzimuth = -1f
        val alpha = 0.15f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = System.currentTimeMillis()
                if (now - lastEmitTime < throttleMs) return
                lastEmitTime = now

                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (azimuthDeg < 0f) azimuthDeg += 360f

                smoothedAzimuth = if (smoothedAzimuth < 0f) {
                    azimuthDeg
                } else {

                    var delta = azimuthDeg - smoothedAzimuth
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    var result = smoothedAzimuth + alpha * delta
                    if (result < 0f) result += 360f
                    if (result >= 360f) result -= 360f
                    result
                }

                onBearingChanged(smoothedAzimuth)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun registerSensor() {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }

        fun unregisterSensor() {
            sensorManager.unregisterListener(listener)
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registerSensor()
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> registerSensor()
                Lifecycle.Event.ON_PAUSE -> unregisterSensor()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            unregisterSensor()
        }
    }
}
