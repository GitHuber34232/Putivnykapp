package ua.kyiv.putivnyk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.i18n.trDynamic
import ua.kyiv.putivnyk.ui.components.EmptyStateView
import ua.kyiv.putivnyk.ui.components.LoadingStateView
import ua.kyiv.putivnyk.ui.maps.MapLibreMapView
import ua.kyiv.putivnyk.ui.viewmodel.MapCenter
import ua.kyiv.putivnyk.ui.viewmodel.RoutesViewModel
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    onNavigateToMap: () -> Unit = {},
    viewModel: RoutesViewModel = hiltViewModel()
) {
    val routes by viewModel.routes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showOnlyFavorites by viewModel.showOnlyFavorites.collectAsState()
    val isLoaded by viewModel.isLoaded.collectAsState()
    val routeToDelete by viewModel.routeToDelete.collectAsState()
    val isCreatingRoute by viewModel.isCreatingRoute.collectAsState()
    val activeRouteId by viewModel.activeRouteId.collectAsState()
    var selectedRouteDetails by remember { mutableStateOf<Route?>(null) }
    var showCreateRouteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = tr("routes.deleted", "Маршрут видалено")
    val createdMessage = tr("routes.created", "Маршрут створено")
    val createFailedMessage = tr("routes.create_failed", "Не вдалося створити маршрут")

    LaunchedEffect(Unit) {
        viewModel.deleteEvent.collect {
            snackbarHostState.showSnackbar(deletedMessage)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.createEvent.collect { success ->
            snackbarHostState.showSnackbar(
                if (success) createdMessage else createFailedMessage
            )
        }
    }

    if (showCreateRouteDialog) {
        val availablePlaces by viewModel.availablePlaces.collectAsState()
        RouteBuilderMapOverlay(
            viewModel = viewModel,
            availablePlaces = availablePlaces,
            isCreating = isCreatingRoute,
            onDismiss = { showCreateRouteDialog = false },
            onCreate = { name, places ->
                viewModel.createRouteFromPlaces(name, places)
                showCreateRouteDialog = false
            }
        )
    } else {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(tr("routes.title", "Маршрути")) },
                actions = {
                    IconButton(onClick = { viewModel.toggleShowOnlyFavorites() }) {
                        Icon(
                            imageVector = if (showOnlyFavorites) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Filled.FavoriteBorder
                            },
                            contentDescription = tr("routes.only_favorites", "Тільки улюблені")
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isCreatingRoute) {
                FloatingActionButton(
                    onClick = {
                        viewModel.loadAvailablePlaces()
                        showCreateRouteDialog = true
                    }
                ) {
                    Icon(Icons.Filled.Add, tr("routes.create", "Створити маршрут"))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = tr("routes.search", "Пошук маршрутів")) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = tr("map.clear", "Очистити"))
                        }
                    }
                },
                placeholder = { Text(tr("routes.search", "Пошук маршрутів")) }
            )

            if (!isLoaded) {
                LoadingStateView(
                    message = tr("routes.loading", "Завантаження маршрутів…")
                )
            } else if (routes.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Filled.Route,
                    title = tr("routes.no_saved", "Немає збережених маршрутів"),
                    subtitle = tr("routes.create_hint", "Натисніть + щоб створити маршрут")
                )
            } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(routes, key = { it.id }) { route ->
                    val isActive = activeRouteId == route.id
                    RouteCard(
                        modifier = Modifier.animateItem(),
                        route = route,
                        isActive = isActive,
                        onFavoriteClick = { viewModel.toggleFavorite(route.id) },
                        onDeleteClick = { viewModel.requestDeleteRoute(route) },
                        onActivateClick = {
                            if (isActive) {
                                viewModel.deactivateRoute()
                            } else {
                                viewModel.activateRouteOnMap(route.id)
                                onNavigateToMap()
                            }
                        },
                        onReverseClick = { viewModel.reverseRoute(route.id) },
                        onClick = {
                            selectedRouteDetails = route
                        }
                    )
                }
            }
        }
        }
    }

    routeToDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteRoute() },
            title = { Text(tr("routes.delete_title", "Видалити маршрут?")) },
            text = {
                Text(
                    tr("routes.delete_confirm", "Ви впевнені, що хочете видалити маршрут") +
                        " \"${route.name}\"?"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeleteRoute() }) {
                    Text(tr("common.delete", "Видалити"))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteRoute() }) {
                    Text(tr("home.cancel", "Скасувати"))
                }
            }
        )
    }

    selectedRouteDetails?.let { route ->
        var renameValue by remember(route.id) { mutableStateOf(route.name) }
        var waypointName by remember(route.id) { mutableStateOf("") }
        var waypointLat by remember(route.id) { mutableStateOf("") }
        var waypointLon by remember(route.id) { mutableStateOf("") }
        val scrollState = rememberScrollState()
        AlertDialog(
            onDismissRequest = { selectedRouteDetails = null },
            title = { Text(route.name) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        label = { Text(tr("map.route_name", "Назва маршруту")) },
                        singleLine = true
                    )
                    Text(route.description ?: tr("routes.no_description", "Без опису"))
                    Text("${tr("routes.distance", "Відстань")}: ${formatRouteDistance(route.distance)}")
                    Text("${tr("routes.duration", "Тривалість")}: ${formatRouteDuration(route.estimatedDuration)}")
                    Text("${tr("routes.points", "Точок")}: ${route.waypoints.size + 2}")
                    if (route.waypoints.isNotEmpty()) {
                        Text(tr("routes.waypoints", "Проміжні точки:"))
                        route.waypoints.forEachIndexed { index, point ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${index + 1}. ${point.name ?: tr("routes.no_name", "Без назви")} (${String.format("%.4f", point.latitude)}, ${String.format("%.4f", point.longitude)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        viewModel.removeWaypointAt(route.id, index)
                                        selectedRouteDetails = null
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = tr("common.delete", "Видалити"),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = waypointName,
                        onValueChange = { waypointName = it },
                        label = { Text(tr("routes.point_name", "Назва точки")) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = waypointLat,
                        onValueChange = { waypointLat = it },
                        label = { Text(tr("routes.latitude", "Широта")) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = waypointLon,
                        onValueChange = { waypointLon = it },
                        label = { Text(tr("routes.longitude", "Довгота")) },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            val lat = waypointLat.toDoubleOrNull()
                            val lon = waypointLon.toDoubleOrNull()
                            if (lat != null && lon != null) {
                                viewModel.addWaypoint(route.id, lat, lon, waypointName)
                                selectedRouteDetails = null
                            }
                        }) {
                            Text(tr("routes.add_point", "+ Точка"))
                        }
                        TextButton(onClick = {
                            viewModel.removeLastWaypoint(route.id)
                            selectedRouteDetails = null
                        }) {
                            Text(tr("routes.remove_last", "-1 точка"))
                        }
                        TextButton(onClick = {
                            viewModel.clearWaypoints(route.id)
                            selectedRouteDetails = null
                        }) {
                            Text(tr("routes.clear_points", "Очистити точки"))
                        }
                    }
                    if (route.waypoints.size >= 2) {
                        TextButton(onClick = {
                            viewModel.optimizeRoute(route.id)
                            selectedRouteDetails = null
                        }) {
                            Text(tr("routes.optimize", "⚡ Оптимізувати порядок"))
                        }
                    }
                    TextButton(onClick = {
                        viewModel.activateRouteOnMap(route.id)
                        selectedRouteDetails = null
                        onNavigateToMap()
                    }) {
                        Text(tr("routes.show_on_map", "🗺️ Показати на карті"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameRoute(route.id, renameValue)
                    selectedRouteDetails = null
                }) {
                    Text(tr("home.save", "Зберегти"))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedRouteDetails = null }) {
                    Text(tr("common.close", "Закрити"))
                }
            }
        )
    }

    if (isCreatingRoute) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
                .clickable(enabled = false, onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
    }
}

private val CIRCLED_NUMBERS = arrayOf(
    "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩",
    "⑪", "⑫", "⑬", "⑭", "⑮", "⑯", "⑰", "⑱", "⑲", "⑳"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteBuilderMapOverlay(
    viewModel: RoutesViewModel,
    availablePlaces: List<Place>,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, List<Place>) -> Unit
) {
    val selectedPlaces = remember { mutableStateListOf<Place>() }
    var mapCenter by remember { mutableStateOf(MapCenter(50.4501, 30.5234)) }
    var zoomLevel by remember { mutableIntStateOf(13) }
    var showNameDialog by remember { mutableStateOf(false) }

    val translatedTitles = availablePlaces.associate { it.id to trDynamic(it.name) }

    val currentSelected = selectedPlaces.toList()

    LaunchedEffect(currentSelected) {
        viewModel.updateWalkingPreview(currentSelected)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearWalkingPreview() }
    }

    val previewRoutePoints by viewModel.walkingPreview.collectAsState()
    val placeTitleOverrides = remember(currentSelected, translatedTitles) {
        buildMap {
            availablePlaces.forEach { place ->
                put(place.id, translatedTitles[place.id] ?: place.name)
            }
            currentSelected.forEachIndexed { index, place ->
                val num = index + 1
                val prefix = CIRCLED_NUMBERS.getOrElse(num - 1) { "$num." }
                put(place.id, "$prefix ${translatedTitles[place.id] ?: place.name}")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapLibreMapView(
            modifier = Modifier.fillMaxSize(),
            center = mapCenter,
            zoomLevel = zoomLevel,
            places = availablePlaces,
            placeTitleOverrides = placeTitleOverrides,
            userLocation = MapCenter(0.0, 0.0),
            showUserLocation = false,
            onMapMoved = { center, zoom ->
                mapCenter = center
                zoomLevel = zoom
            },
            onViewportChanged = { },
            onPlaceMarkerClick = { place ->
                val existing = selectedPlaces.indexOfFirst { it.id == place.id }
                if (existing >= 0) {
                    selectedPlaces.removeAt(existing)
                } else {
                    selectedPlaces.add(place)
                }
            },
            routePoints = previewRoutePoints
        )

        SmallFloatingActionButton(
            onClick = { if (!isCreating) onDismiss() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Icon(Icons.Filled.Close, contentDescription = tr("common.close", "Закрити"))
        }

        ElevatedCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${tr("routes.selected", "Обрано")}: ${selectedPlaces.size} ${tr("routes.points_label", "точок")}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (selectedPlaces.isNotEmpty()) {
                        TextButton(onClick = { selectedPlaces.clear() }) {
                            Text(tr("map.clear", "Очистити"))
                        }
                    }
                }

                if (selectedPlaces.isNotEmpty()) {
                    val displayPlaces = selectedPlaces.takeLast(5)
                    displayPlaces.forEach { place ->
                        val idx = selectedPlaces.indexOf(place) + 1
                        val prefix = CIRCLED_NUMBERS.getOrElse(idx - 1) { "$idx." }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$prefix ${trDynamic(place.name)}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { selectedPlaces.remove(place) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = tr("common.delete", "Видалити"),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    if (selectedPlaces.size > 5) {
                        Text(
                            "… ${tr("routes.and_more", "і ще")} ${selectedPlaces.size - 5}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        tr("routes.tap_places", "Натисніть на місця на карті, щоб додати їх до маршруту"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = { showNameDialog = true },
                    enabled = selectedPlaces.size >= 2 && !isCreating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(tr("routes.create", "Створити маршрут"))
                }

                if (isCreating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (showNameDialog) {
        var routeName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(tr("routes.new_route", "Новий маршрут")) },
            text = {
                OutlinedTextField(
                    value = routeName,
                    onValueChange = { routeName = it },
                    label = { Text(tr("map.route_name", "Назва маршруту")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onCreate(routeName, selectedPlaces.toList())
                    showNameDialog = false
                }) {
                    Text(tr("routes.create", "Створити"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(tr("home.cancel", "Скасувати"))
                }
            }
        )
    }
}

@Composable
fun RouteCard(
    modifier: Modifier = Modifier,
    route: Route,
    isActive: Boolean = false,
    onFavoriteClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onActivateClick: () -> Unit,
    onReverseClick: () -> Unit = {},
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (isActive) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = if (route.isFavorite) {
                            Icons.Filled.Favorite
                        } else {
                            Icons.Filled.FavoriteBorder
                        },
                        contentDescription = tr("favorites.favorite", "Улюблене"),
                        tint = if (route.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            route.description?.let { description ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = tr("routes.distance", "Відстань"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatRouteDistance(route.distance),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = tr("routes.duration", "Тривалість"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatRouteDuration(route.estimatedDuration),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = tr("routes.points", "Точок"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${route.waypoints.size + 2} ${tr("routes.points_label", "точок")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onActivateClick) {
                    Icon(
                        if (isActive) Icons.Filled.Stop else Icons.Filled.Map,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isActive) tr("routes.deactivate", "Зупинити")
                        else tr("routes.activate", "На карту")
                    )
                }
                IconButton(onClick = onReverseClick) {
                    Icon(
                        Icons.Filled.SwapVert,
                        contentDescription = tr("routes.reverse", "Розвернути маршрут"),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDeleteClick) {
                    Text(tr("common.delete", "Видалити"))
                }
            }
        }
    }
}

@Composable
private fun formatRouteDistance(distanceMeters: Double): String {
    return if (distanceMeters >= 1000.0) {
        String.format("%.1f %s", distanceMeters / 1000.0, tr("routes.km", "км"))
    } else {
        "${distanceMeters.toInt()} ${tr("map.meters_short", "м")}"
    }
}

@Composable
private fun formatRouteDuration(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours ${tr("routes.hours_short", "год")} $minutes ${tr("routes.min", "хв")}"
        hours > 0 -> "$hours ${tr("routes.hours_short", "год")}"
        else -> "$minutes ${tr("routes.min", "хв")}"
    }
}
