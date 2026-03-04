package ua.kyiv.putivnyk.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.PlaceSortMode
import ua.kyiv.putivnyk.ui.i18n.trCategory
import ua.kyiv.putivnyk.ui.i18n.trDynamic
import ua.kyiv.putivnyk.ui.i18n.trSortMode
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.components.EmptyStateView
import ua.kyiv.putivnyk.ui.components.LoadingStateView
import ua.kyiv.putivnyk.ui.viewmodel.PlacesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    onOpenPlaceDetails: (Long) -> Unit = {},
    viewModel: PlacesViewModel = hiltViewModel()
) {
    val places by viewModel.places.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val showOnlyFavorites by viewModel.showOnlyFavorites.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val isLoaded by viewModel.isLoaded.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("places.title", "Цікаві місця")) },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(tr("places.search", "Пошук місць...")) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, tr("places.search", "Пошук"))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, tr("map.clear", "Очистити"))
                        }
                    }
                },
                singleLine = true
            )
            LazyRow(
                modifier = Modifier.padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text(tr("map.all", "Всі")) }
                    )
                }

                PlaceCategory.entries
                    .filter { it != PlaceCategory.TOILET }
                    .forEach { category ->
                    item {
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { viewModel.selectCategory(category) },
                            label = { Text("${category.icon} ${trCategory(category)}") }
                        )
                    }
                }
            }

            LazyRow(
                modifier = Modifier.padding(bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlaceSortMode.entries.forEach { mode ->
                    item {
                        FilterChip(
                            selected = sortMode == mode,
                            onClick = { viewModel.setSortMode(mode) },
                            label = { Text(trSortMode(mode)) }
                        )
                    }
                }
            }
            if (!isLoaded) {
                LoadingStateView(
                    message = tr("places.loading", "Завантаження місць…")
                )
            } else if (places.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Filled.Search,
                    title = tr("places.not_found", "Місця не знайдено")
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(places, key = { it.id }) { place ->
                        PlaceCard(
                            modifier = Modifier.animateItem(),
                            place = place,
                            onFavoriteClick = { viewModel.toggleFavorite(place.id) },
                            onVisitedClick = { viewModel.toggleVisited(place.id) },
                            onClick = {
                                onOpenPlaceDetails(place.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceCard(
    modifier: Modifier = Modifier,
    place: ua.kyiv.putivnyk.data.model.Place,
    onFavoriteClick: () -> Unit,
    onVisitedClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                    text = trDynamic(place.name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                place.rating?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${place.category.icon} ${trCategory(place.category)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            place.description?.let { description ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = trDynamic(description),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
            }
            place.visitDuration?.let { duration ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⏱️ ~$duration ${tr("routes.min", "хв")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = if (place.isFavorite) {
                            Icons.Filled.Favorite
                        } else {
                            Icons.Filled.FavoriteBorder
                        },
                        contentDescription = tr("favorites.favorite", "Улюблене"),
                        tint = if (place.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                IconButton(onClick = onVisitedClick) {
                    Icon(
                        imageVector = if (place.isVisited) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Filled.CheckCircleOutline
                        },
                        contentDescription = tr("details.mark_visited", "Відвідано"),
                        tint = if (place.isVisited) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
