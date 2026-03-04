package ua.kyiv.putivnyk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import ua.kyiv.putivnyk.ui.i18n.trCategory
import ua.kyiv.putivnyk.ui.i18n.trDynamic
import ua.kyiv.putivnyk.ui.i18n.trRiverBank
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.components.LoadingStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailsScreen(
    onBack: () -> Unit,
    onRouteHere: ((Long) -> Unit)? = null,
    viewModel: ua.kyiv.putivnyk.ui.viewmodel.LocationDetailsViewModel = hiltViewModel()
) {
    val place by viewModel.place.collectAsState()
    val context = LocalContext.current
    val isLoaded by viewModel.isLoaded.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val translatedDescription by viewModel.translatedDescription.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val msgAddedFav = tr("details.added_favorites", "Додано в обране")
    val msgRemovedFav = tr("details.removed_favorites", "Видалено з обраного")
    val msgMarkedVisited = tr("details.marked_visited", "Позначено як відвідане")
    val msgUnmarkedVisited = tr("details.unmarked_visited", "Знято позначку відвідання")
    val msgAddedRoute = tr("details.added_route", "Додано до маршруту")
    val msgRouteAddFailed = tr("details.route_add_failed", "Не вдалося додати до маршруту")
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { key ->
            val message = when (key) {
                "added_to_favorites" -> msgAddedFav
                "removed_from_favorites" -> msgRemovedFav
                "marked_visited" -> msgMarkedVisited
                "unmarked_visited" -> msgUnmarkedVisited
                "added_to_route" -> msgAddedRoute
                "route_add_failed" -> msgRouteAddFailed
                else -> key
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        place
                            ?.let { trDynamic(it.name) }
                            ?: tr("details.title", "Деталі локації")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("common.back", "Назад"))
                    }
                }
            )
        }
    ) { padding ->
        val current = place
        if (current == null) {
            if (!isLoaded) {
                LoadingStateView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = tr("details.not_found", "Локацію не знайдено"),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = trDynamic(current.name),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${trCategory(current.category)} • ${trRiverBank(current.riverBank)} • 🔥 ${current.popularity}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (current.imageUrls.isNotEmpty()) {
                item {
                    Text(tr("details.photo", "Фото"), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(current.imageUrls.take(6)) { url ->
                            Card {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(url)
                                        .crossfade(300)
                                        .build(),
                                    contentDescription = tr("details.photo", "Фото локації"),
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(120.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (current.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.height(0.dp))
                        Text(if (current.isFavorite) tr("favorites.in_list", "В обраному") else tr("favorites.add", "Додати в обране"))
                    }
                    OutlinedButton(onClick = { viewModel.toggleVisited() }) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Text(tr("details.mark_visited", "Позначити відвіданим"))
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        viewModel.openOnMap()
                        onBack()
                    }) {
                        Icon(Icons.Filled.Map, contentDescription = null)
                        Text(tr("details.open_map", "Відкрити на карті"))
                    }
                    OutlinedButton(onClick = { viewModel.addToActiveRoute() }) {
                        Text(tr("details.add_active_route", "Додати до активного маршруту"))
                    }
                }
                if (onRouteHere != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onRouteHere(current.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.NearMe, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr("map.route_here", "Маршрут сюди"))
                    }
                }
            }

            item {
                Text(tr("details.translate_description", "Переклад опису"), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(viewModel.availableLanguages.take(12), key = { it.isoCode }) { language ->
                        FilterChip(
                            selected = selectedLanguage == language.isoCode,
                            onClick = { viewModel.setLanguage(language.isoCode) },
                            label = { Text(language.isoCode.uppercase()) },
                            leadingIcon = {
                                if (selectedLanguage == language.isoCode) {
                                    Icon(Icons.Filled.Translate, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }

            if (isTranslating) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${tr("details.description", "Опис")} (${selectedLanguage.uppercase()})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = trDynamic(
                                translatedDescription
                                    ?: current.description
                                    ?: tr("details.no_description", "Опис відсутній")
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (current.tags.isNotEmpty()) {
                item {
                    Text(tr("details.tags", "Теги"), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        current.tags.forEach { tag ->
                            Text("• ${trDynamic(tag)}")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "${tr("details.coordinates", "Координати")}: ${"%.5f".format(current.latitude)}, ${"%.5f".format(current.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
