package ua.kyiv.putivnyk.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.ui.components.LoadingStateView
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.i18n.trCategory
import ua.kyiv.putivnyk.ui.i18n.trDynamic
import ua.kyiv.putivnyk.ui.i18n.trRiverBank
import ua.kyiv.putivnyk.ui.viewmodel.LocationDetailsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LocationDetailsScreen(
    onBack: () -> Unit,
    onOpenMap: () -> Unit = onBack,
    onRouteHere: (() -> Unit)? = null,
    onListen: ((Long) -> Unit)? = null,
    viewModel: LocationDetailsViewModel = hiltViewModel()
) {
    val place by viewModel.place.collectAsState()
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
    val msgNoActiveRoute = tr("details.no_active_route", "Немає активного маршруту — увімкніть його у вкладці «Маршрути»")
    val msgAlreadyInRoute = tr("details.already_in_route", "Це місце вже є в маршруті")
    val msgTranslationFailed = tr("details.translation_failed", "Не вдалося перекласти. Перевірте з'єднання та спробуйте ще раз")
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { key ->
            val message = when (key) {
                "added_to_favorites" -> msgAddedFav
                "removed_from_favorites" -> msgRemovedFav
                "marked_visited" -> msgMarkedVisited
                "unmarked_visited" -> msgUnmarkedVisited
                "added_to_route" -> msgAddedRoute
                "route_add_failed" -> msgRouteAddFailed
                "no_active_route" -> msgNoActiveRoute
                "already_in_route" -> msgAlreadyInRoute
                "translation_failed" -> msgTranslationFailed
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
                        text = place?.let { trDynamic(it.name) } ?: tr("details.title", "Деталі локації"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("common.back", "Назад"))
                    }
                },
                actions = {
                    val current = place
                    if (current != null) {
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (current.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (current.isFavorite) {
                                    tr("favorites.in_list", "В обраному")
                                } else {
                                    tr("favorites.add", "Додати в обране")
                                },
                                tint = if (current.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tr("details.not_found", "Локацію не знайдено"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    OutlinedButton(onClick = onBack) { Text(tr("common.back", "Назад")) }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "hero") {
                PlaceHero(place = current)
            }

            item(key = "title") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = trDynamic(current.name),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text("${current.category.icon} ${trCategory(current.category)}") }
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(trRiverBank(current.riverBank)) }
                        )
                        current.rating?.let { rating ->
                            AssistChip(
                                onClick = {},
                                label = { Text(String.format(Locale.US, "%.1f", rating)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                        current.visitDuration?.let { minutes ->
                            AssistChip(
                                onClick = {},
                                label = { Text("~$minutes ${tr("routes.min", "хв")}") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                }
            }

            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onRouteHere != null) {
                            Button(
                                onClick = { viewModel.requestRouteHere(onRouteHere) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.NearMe, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tr("map.route_here", "Маршрут сюди"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        FilledTonalButton(
                            onClick = { viewModel.openOnMap(onOpenMap) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("details.open_map", "На карті"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.toggleVisited() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (current.isVisited) Icons.Filled.CheckCircle else Icons.Filled.CheckCircleOutline,
                                contentDescription = null,
                                tint = if (current.isVisited) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (current.isVisited) tr("details.visited", "Відвідано") else tr("details.mark_visited", "Позначити відвіданим"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.addToActiveRoute() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Route, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("details.add_active_route", "До маршруту"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (onListen != null) {
                        FilledTonalButton(
                            onClick = { onListen(current.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("audio.listen_story", "Слухати аудіогід"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            item(key = "description") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tr("details.description", "Опис"),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Filled.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(viewModel.availableLanguages, key = { it.isoCode }) { language ->
                                FilterChip(
                                    selected = selectedLanguage == language.isoCode,
                                    onClick = { viewModel.setLanguage(language.isoCode) },
                                    label = { Text(language.isoCode.uppercase()) }
                                )
                            }
                        }
                        if (isTranslating) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        val originalDescription = current.description?.takeIf { it.isNotBlank() }
                        Text(
                            // The translated text is already in the chosen language; only the fallbacks go
                            // through the UI-language translator.
                            text = translatedDescription
                                ?: originalDescription?.let { trDynamic(it) }
                                ?: tr("details.no_description", "Опис відсутній"),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            val visibleTags = current.tags
                .map { it.removePrefix("fav:").trim() }
                .filter { it.isNotBlank() }
                .distinct()
            if (visibleTags.isNotEmpty()) {
                item(key = "tags") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("details.tags", "Теги"), style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            visibleTags.forEach { tag ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(trDynamic(tagLabel(tag))) }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "coordinates") {
                Text(
                    text = "${tr("details.coordinates", "Координати")}: ${String.format(Locale.US, "%.5f", current.latitude)}, ${String.format(Locale.US, "%.5f", current.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceHero(place: Place) {
    val shape = RoundedCornerShape(28.dp)
    val images = place.imageUrls.take(6)
    if (images.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(place.category.icon, style = MaterialTheme.typography.displayMedium)
        }
        return
    }

    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { images.size })
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(images[page])
                    .crossfade(300)
                    .build(),
                contentDescription = tr("details.photo", "Фото локації"),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        if (images.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(images.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (selected) 1f else 0.55f))
                    )
                }
            }
        }
    }
}

private fun tagLabel(tag: String): String = when (tag.uppercase()) {
    "PARK" -> "Парк"
    "MUSEUM" -> "Музей"
    "THEATER" -> "Театр"
    "RESTAURANT" -> "Ресторан"
    "RELIGION" -> "Релігійна пам'ятка"
    "MONUMENT" -> "Пам'ятка"
    "UNESCO" -> "ЮНЕСКО"
    else -> tag
}
