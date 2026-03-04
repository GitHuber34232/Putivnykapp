package ua.kyiv.putivnyk.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.ui.i18n.trCategory
import ua.kyiv.putivnyk.ui.i18n.trDynamic
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.components.EmptyStateView
import ua.kyiv.putivnyk.ui.components.LoadingStateView
import ua.kyiv.putivnyk.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenMap: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val recommendations by viewModel.recommendedPlaces.collectAsState()
    val pinnedPlaces by viewModel.pinnedPlaces.collectAsState()
    val isLoaded by viewModel.isLoaded.collectAsState()
    val hasMore by viewModel.hasMoreRecommendations.collectAsState()
    val showFavoriteDialog by viewModel.showFavoriteDialog.collectAsState()
    val showAddPinnedDialog by viewModel.showAddPinnedDialog.collectAsState()
    val selectedPlaceId by viewModel.selectedPlaceId.collectAsState()
    val selectedFavoriteCategory by viewModel.favoriteCategory.collectAsState()
    val availableForPinning by viewModel.availablePlacesForPinning.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val msgPinned = tr("home.place_pinned", "Місце закріплено")
    val msgUnpinned = tr("home.place_unpinned", "Місце відкріплено")
    val msgFavorite = tr("home.added_to_favorites_snack", "Додано в улюблені")
    var showWelcome by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { showWelcome = true }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { event ->
            val msg = when (event) {
                "place_pinned" -> msgPinned
                "place_unpinned" -> msgUnpinned
                "added_to_favorites" -> msgFavorite
                else -> event
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(tr("nav.home", "Головна")) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openAddPinnedDialog() }) {
                Icon(Icons.Filled.Add, contentDescription = tr("home.add_place", "Додати місце"))
            }
        }
    ) { padding ->
        if (!isLoaded) {
            LoadingStateView(
                message = tr("home.loading_reco", "Формуємо персональні рекомендації…"),
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    AnimatedVisibility(
                        visible = showWelcome,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 })
                    ) {
                        Text(
                            text = tr("home.welcome", "Вітаємо!"),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                if (pinnedPlaces.isNotEmpty()) {
                    item {
                        Text(
                            text = tr("home.my_places", "Мої місця"),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(pinnedPlaces, key = { "pinned_${it.id}" }) { place ->
                        PinnedPlaceCard(
                            modifier = Modifier.animateItem(),
                            place = place,
                            onClick = {
                                viewModel.requestOpenMapForPlace(place)
                                onOpenMap()
                            },
                            onUnpin = { viewModel.unpinPlace(place.id) },
                            onAddToFavorites = {
                                viewModel.openFavoriteDialog(place.id)
                            }
                        )
                    }
                }

                item {
                    Text(
                        text = tr("home.recommended", "Рекомендовано для вас"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (recommendations.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Filled.Star,
                            title = tr("home.no_reco", "Немає рекомендацій"),
                            subtitle = tr("home.no_reco_hint", "Додайте улюблені місця, щоб покращити добірку")
                        )
                    }
                } else {
                    items(recommendations, key = { "reco_${it.id}" }) { place ->
                        RecommendationCard(
                            modifier = Modifier.animateItem(),
                            place = place,
                            onClick = {
                                viewModel.requestOpenMapForPlace(place)
                                onOpenMap()
                            },
                            onPin = { viewModel.pinPlace(place.id) },
                            onAddToFavorites = {
                                viewModel.openFavoriteDialog(place.id)
                            }
                        )
                    }

                    if (hasMore) {
                        item {
                            OutlinedButton(
                                onClick = { viewModel.loadMoreRecommendations() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(tr("home.load_more", "Завантажити ще рекомендації"))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFavoriteDialog) {
        var category by remember(selectedFavoriteCategory) { mutableStateOf(selectedFavoriteCategory) }
        val allPlaces by viewModel.availablePlacesForPinning.collectAsState()
        val targetPlace = (pinnedPlaces + recommendations).firstOrNull { it.id == selectedPlaceId }
        AlertDialog(
            onDismissRequest = { viewModel.dismissFavoriteDialog() },
            title = { Text(tr("home.add_favorite", "Додати в улюблені")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        targetPlace
                            ?.let { trDynamic(it.name) }
                            ?: tr("home.choose_place", "Оберіть місце")
                    )
                    Text(
                        tr("home.favorite_category", "Категорія улюбленого (власна):"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        singleLine = true,
                        label = { Text(tr("home.favorite_category_hint", "Напр. Романтика / Їжа / Історія")) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setFavoriteCategory(category)
                    viewModel.saveFavoriteWithCategory()
                }) {
                    Text(tr("home.save", "Зберегти"))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissFavoriteDialog() }) {
                    Text(tr("home.cancel", "Скасувати"))
                }
            }
        )
    }

    if (showAddPinnedDialog) {
        var searchText by remember { mutableStateOf("") }
        val filteredPlaces = availableForPinning.filter {
            if (searchText.isBlank()) true
            else it.name.contains(searchText, ignoreCase = true) ||
                it.nameEn?.contains(searchText, ignoreCase = true) == true
        }.take(20)
        AlertDialog(
            onDismissRequest = { viewModel.dismissAddPinnedDialog() },
            title = { Text(tr("home.add_place", "Додати місце")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        placeholder = { Text(tr("places.search", "Пошук місць...")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredPlaces, key = { it.id }) { place ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.pinPlace(place.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        trDynamic(place.name),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        trCategory(place.category),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAddPinnedDialog() }) {
                    Text(tr("home.cancel", "Скасувати"))
                }
            }
        )
    }
}

@Composable
private fun PinnedPlaceCard(
    modifier: Modifier = Modifier,
    place: Place,
    onClick: () -> Unit,
    onUnpin: () -> Unit,
    onAddToFavorites: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    trDynamic(place.name),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${trCategory(place.category)} • ★ ${String.format("%.1f", place.rating ?: 0.0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = tr("home.more", "Більше"))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("home.add_favorite", "Додати в улюблені")) },
                        onClick = {
                            menuExpanded = false
                            onAddToFavorites()
                        },
                        leadingIcon = { Icon(Icons.Filled.Star, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(tr("home.unpin", "Відкріпити")) },
                        onClick = {
                            menuExpanded = false
                            onUnpin()
                        },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    modifier: Modifier = Modifier,
    place: Place,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onAddToFavorites: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        trDynamic(place.name),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = "${trCategory(place.category)} • ★ ${String.format("%.1f", place.rating ?: 0.0)} • \uD83D\uDD25 ${place.popularity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = place.description.orEmpty().ifBlank { tr("home.tap_to_open_map", "Натисніть, щоб відкрити на карті") }.let { trDynamic(it) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = tr("home.more", "Більше"))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("home.pin_place", "Закріпити")) },
                        onClick = {
                            menuExpanded = false
                            onPin()
                        },
                        leadingIcon = { Icon(Icons.Filled.PushPin, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(tr("home.add_favorite", "Додати в улюблені")) },
                        onClick = {
                            menuExpanded = false
                            onAddToFavorites()
                        },
                        leadingIcon = { Icon(Icons.Filled.Star, null) }
                    )
                }
            }
        }
    }
}
