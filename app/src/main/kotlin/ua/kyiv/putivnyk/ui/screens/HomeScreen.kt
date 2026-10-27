package ua.kyiv.putivnyk.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.ui.components.EmptyStateView
import ua.kyiv.putivnyk.ui.components.SkeletonListLoader
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.i18n.trCategory
import ua.kyiv.putivnyk.ui.i18n.trDynamic
import ua.kyiv.putivnyk.ui.theme.RatingStar
import ua.kyiv.putivnyk.ui.viewmodel.HomeViewModel
import java.time.LocalTime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenMap: () -> Unit,
    onOpenPlaces: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenPlaceDetails: (Long) -> Unit = {},
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
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { showContent = true }

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

    val openOnMap: (Place) -> Unit = { place ->
        viewModel.requestOpenMapForPlace(place)
        onOpenMap()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddPinnedDialog() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(tr("home.add_place", "Закріпити місце")) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hero") {
                HomeHero(
                    visible = showContent,
                    onOpenMap = onOpenMap,
                    onOpenPlaces = onOpenPlaces,
                    onOpenFavorites = onOpenFavorites
                )
            }

            if (pinnedPlaces.isNotEmpty()) {
                item(key = "pinned_title") {
                    SectionTitle(text = tr("home.my_places", "Мої місця"))
                }
                item(key = "pinned_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pinnedPlaces, key = { "pinned_${it.id}" }) { place ->
                            PinnedPlaceCard(
                                modifier = Modifier.animateItem(),
                                place = place,
                                onClick = { onOpenPlaceDetails(place.id) },
                                onOpenMap = { openOnMap(place) },
                                onUnpin = { viewModel.unpinPlace(place.id) },
                                onAddToFavorites = { viewModel.openFavoriteDialog(place.id) }
                            )
                        }
                    }
                }
            }

            item(key = "reco_title") {
                SectionTitle(text = tr("home.recommended", "Рекомендовано для вас"))
            }

            if (!isLoaded) {
                item(key = "reco_loading") {
                    SkeletonListLoader(itemCount = 3, modifier = Modifier.fillMaxWidth())
                }
            } else if (recommendations.isEmpty()) {
                item(key = "reco_empty") {
                    EmptyStateView(
                        icon = Icons.Filled.Star,
                        title = tr("home.no_reco", "Немає рекомендацій"),
                        subtitle = tr("home.no_reco_hint", "Додайте улюблені місця, щоб покращити добірку"),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    )
                }
            } else {
                items(recommendations, key = { "reco_${it.id}" }) { place ->
                    RecommendationCard(
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = 16.dp),
                        place = place,
                        onClick = { onOpenPlaceDetails(place.id) },
                        onOpenMap = { openOnMap(place) },
                        onPin = { viewModel.pinPlace(place.id) },
                        onAddToFavorites = { viewModel.openFavoriteDialog(place.id) }
                    )
                }

                if (hasMore) {
                    item(key = "reco_more") {
                        OutlinedButton(
                            onClick = { viewModel.loadMoreRecommendations() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(tr("home.load_more", "Показати ще"))
                        }
                    }
                }
            }
        }
    }

    if (showFavoriteDialog) {
        var category by remember(selectedFavoriteCategory) { mutableStateOf(selectedFavoriteCategory) }
        val targetPlace = (pinnedPlaces + recommendations).firstOrNull { it.id == selectedPlaceId }
        AlertDialog(
            onDismissRequest = { viewModel.dismissFavoriteDialog() },
            icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
            title = { Text(tr("home.add_favorite", "Додати в улюблені")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        targetPlace
                            ?.let { trDynamic(it.name) }
                            ?: tr("home.choose_place", "Оберіть місце"),
                        style = MaterialTheme.typography.titleMedium
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
        }.take(30)
        AlertDialog(
            onDismissRequest = { viewModel.dismissAddPinnedDialog() },
            icon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
            title = { Text(tr("home.add_place", "Закріпити місце")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        placeholder = { Text(tr("places.search", "Пошук місць...")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(filteredPlaces, key = { it.id }) { place ->
                            ListItem(
                                headlineContent = {
                                    Text(trDynamic(place.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = { Text("${place.category.icon} ${trCategory(place.category)}") },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.pinPlace(place.id) }
                            )
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
private fun HomeHero(
    visible: Boolean,
    onOpenMap: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenFavorites: () -> Unit
) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> tr("home.greeting_morning", "Доброго ранку!")
        in 12..17 -> tr("home.greeting_day", "Доброго дня!")
        in 18..22 -> tr("home.greeting_evening", "Доброго вечора!")
        else -> tr("home.greeting_night", "Доброї ночі!")
    }
    Box(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.tertiary
                    )
                )
            )
            .padding(20.dp)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = tr("home.welcome_subtitle", "Куди підемо сьогодні в Києві?"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(
                        icon = Icons.Filled.Map,
                        label = tr("nav.map", "Карта"),
                        onClick = onOpenMap,
                        modifier = Modifier.weight(1f)
                    )
                    QuickAction(
                        icon = Icons.Filled.Place,
                        label = tr("home.places", "Місця"),
                        onClick = onOpenPlaces,
                        modifier = Modifier.weight(1f)
                    )
                    QuickAction(
                        icon = Icons.Filled.Favorite,
                        label = tr("home.favorites", "Улюблені"),
                        onClick = onOpenFavorites,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.18f),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)
    )
}

@Composable
private fun PlaceThumbnail(place: Place, modifier: Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(place.category.icon, style = MaterialTheme.typography.headlineMedium)
        place.imageUrls.firstOrNull()?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinnedPlaceCard(
    modifier: Modifier = Modifier,
    place: Place,
    onClick: () -> Unit,
    onOpenMap: () -> Unit,
    onUnpin: () -> Unit,
    onAddToFavorites: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.width(200.dp)
    ) {
        Box {
            PlaceThumbnail(
                place = place,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = tr("home.more", "Більше"))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(tr("events.on_map", "На карті")) },
                            onClick = {
                                menuExpanded = false
                                onOpenMap()
                            },
                            leadingIcon = { Icon(Icons.Filled.Map, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(tr("home.add_favorite", "Додати в улюблені")) },
                            onClick = {
                                menuExpanded = false
                                onAddToFavorites()
                            },
                            leadingIcon = { Icon(Icons.Filled.Favorite, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(tr("home.unpin", "Відкріпити")) },
                            onClick = {
                                menuExpanded = false
                                onUnpin()
                            },
                            leadingIcon = { Icon(Icons.Outlined.PushPin, null) }
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                trDynamic(place.name),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${place.category.icon} ${trCategory(place.category)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendationCard(
    modifier: Modifier = Modifier,
    place: Place,
    onClick: () -> Unit,
    onOpenMap: () -> Unit,
    onPin: () -> Unit,
    onAddToFavorites: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            PlaceThumbnail(
                place = place,
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    trDynamic(place.name),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${place.category.icon} ${trCategory(place.category)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    place.rating?.let { rating ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Filled.Star, contentDescription = null, tint = RatingStar, modifier = Modifier.size(14.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f", rating),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                place.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = trDynamic(description),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = tr("home.more", "Більше"))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("events.on_map", "На карті")) },
                        onClick = {
                            menuExpanded = false
                            onOpenMap()
                        },
                        leadingIcon = { Icon(Icons.Filled.Map, null) }
                    )
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
                        leadingIcon = { Icon(Icons.Filled.Favorite, null) }
                    )
                }
            }
        }
    }
}
